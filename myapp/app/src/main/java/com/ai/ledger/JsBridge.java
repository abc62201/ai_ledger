package com.ai.ledger;

import android.content.Context;
import android.database.Cursor;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class JsBridge {

    private final Context context;
    private final WebView webView;
    private final Handler mainHandler;
    private final VoiceHelper voiceHelper;
    // 新增：用来暂存最后一次语音识别的文本
    private String lastVoiceText = "";

    // 构造函数：传入 WebView 和 Context
    public JsBridge(WebView webView, Context context) {
        this.webView = webView;
        this.context = context;
        this.mainHandler = new Handler(Looper.getMainLooper());

        this.voiceHelper = new VoiceHelper(new VoiceHelper.OnResultListener() {
            @Override
            public void onResult(String text) {
                try {
                    JSONObject json = new JSONObject(text);
                    String pureText = "";

                    if (json.has("partial")) {
                        pureText = json.optString("partial", "");
                    } else if (json.has("text")) {
                        // 收到最终结果，暂存起来，但不要立刻发给 H5
                        pureText = json.optString("text", "");
                        Log.d("JsBridge", "暂存最终文本: " + pureText);
                    }

                    // 只要不是空的，就更新最后一次的文本
                    if (!pureText.isEmpty()) {
                        lastVoiceText = pureText;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    // ==== JS 调用：发送文本数据（核心入口） ====
    // 注意：用户消息气泡已由 JS 端 sendMessage() 即时添加，这里只负责 AI 回复
    @JavascriptInterface
    public void receiveData(String data) {
        Log.d("JsBridge", "收到前端数据: " + data);

        // 1. 创建 AI 回复气泡（流式占位，显示 "..."）
        mainHandler.post(() -> {
            webView.evaluateJavascript("JsHelper.startAiStreaming()", null);
        });

        // 2. 调用 zipuai 进行 AI 流式请求
        zipuai.callApi(data, new zipuai.AiCallback() {
            @Override
            public void onChunk(String chunk) {
                // 流式片段 → 回传给 JS 追加显示
                mainHandler.post(() -> {
                    String js = "JsHelper.appendAiChunk('" + escapeForJs(chunk) + "');";
                    webView.evaluateJavascript(js, null);
                });
            }

            @Override
            public void onComplete(String fullText) {
                // 流式结束，替换流式气泡为解析后的最终结果
                mainHandler.post(() -> {
                    String js = "JsHelper.finishAiStreaming('" + escapeForJs(fullText) + "');";
                    webView.evaluateJavascript(js, null);
                });
            }

            @Override
            public void onError(String error) {
                // 出错，在气泡中显示错误信息
                mainHandler.post(() -> {
                    String js = "JsHelper.failAiStreaming('" + escapeForJs(error) + "');";
                    webView.evaluateJavascript(js, null);
                });
            }
        });
    }

    // ==== JS 调用：接收结构化数据（JSON 格式） ====
    @JavascriptInterface
    public void receiveJson(String json) {
        Log.d("JsBridge", "收到JSON数据: " + json);
        mainHandler.post(() -> {
            Toast.makeText(context, "JSON: " + json, Toast.LENGTH_SHORT).show();
        });
    }

    // ==== JS 调用：启动语音识别 ====
    @JavascriptInterface
    public void startVoice() {
        lastVoiceText = ""; // 每次开始录音前，清空缓存
        mainHandler.post(() -> {
            voiceHelper.startVoice();
        });
    }
//====js 调用停止录音
@JavascriptInterface
public void stopVoice() {
    mainHandler.post(() -> {
        voiceHelper.stopVoice(); // 这里会立刻发送 eof 并停止录音
    });

    // 延迟 3 秒拿最终结果（此时服务器应该已经返回了 text）
    mainHandler.postDelayed(() -> {
        if (!lastVoiceText.isEmpty()) {
            Log.d("JsBridge", "录音结束，发送最终文本给H5: " + lastVoiceText);
            String escapedText = lastVoiceText.replace("\\", "\\\\")
                    .replace("'", "\\'")
                    .replace("\n", "\\n");
            String js = "window.receiveVoiceText('" + escapedText + "')";
            webView.evaluateJavascript(js, null);
            lastVoiceText = "";
        }
    }, 300);
}

    // ==== JS 字符串转义辅助方法 ====
    private String escapeForJs(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"': sb.append("\\\""); break;
                case '\'': sb.append("\\'"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\u2028': sb.append("\\u2028"); break;
                case '\u2029': sb.append("\\u2029"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }
    /**
     * 接收前端用户确认的记账数据，并存入数据库
     * @param json 前端传来的 JSON 字符串
     */
    @JavascriptInterface
    public void confirmFinanceRecord(String json) {
        Log.d("JsBridge", "收到记账数据: " + json);

        new Thread(() -> {
            try {
                JSONObject obj = new JSONObject(json);

                // 1. 解析 JSON，并严格按照你的默认值规则进行兜底
                String label = obj.optString("label", "");
                if (label.isEmpty()) label = "物品"; // 默认值=物品

                String category = obj.optString("category", "");
                // 强制限定分类，若不在指定范围内或为空，一律归为“其他”
                if (category.isEmpty()) category = "其他"; // 默认值=其他
                double unitPrice = obj.optDouble("unit_price", 0.0); // 默认值=0
                int quantity = obj.optInt("quantity", 1); // 默认值=1 (注意这里改成1)
                double totalPrice = obj.optDouble("total_price", 0.0); // 默认值=0
                String incomeExpense = obj.optString("income_expense", "");
                if (incomeExpense.isEmpty()) incomeExpense = "支出"; // 默认值=支出
                String timestamp = obj.optString("timestamp", "");
                // 如果 AI 没给时间或为空，强制使用当前系统时间
                if (timestamp == null || timestamp.trim().isEmpty()) {
                    timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                            .format(new Date());
                }
                String raw_text = obj.optString("raw_text", "");
                // 2. 构建 SQL 插入语句
                String sql = "INSERT INTO finance_records (label, category, unit_price, quantity, total_price, income_expense, timestamp,raw_text) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?,?)";

                // 3. 调用 DbHelper 执行插入
                DbHelper dbHelper = DbHelper.getInstance(context);
                dbHelper.executeSql(sql, label, category, unitPrice, quantity, totalPrice, incomeExpense, timestamp,raw_text);

                Log.d("JsBridge", "✅ 记账数据成功存入数据库");

            } catch (Exception e) {
                Log.e("JsBridge", "❌ 记账数据处理失败: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }
    // Java 端示例
    @JavascriptInterface
    public void queryFinanceData(String sqlCondition) {
        try {
            String sql;
            String condition = sqlCondition.trim();
            // 1. 智能拼接 SQL
            if (sqlCondition.trim().startsWith("SELECT")) {
                // AI 返回了完整 SQL，直接用
                sql = sqlCondition.trim();
            } else if (condition.contains("ORDER BY") && condition.contains("WHERE")) {
                // AI 返回了完整的 WHERE + ORDER BY，直接拼
                sql = "SELECT * FROM finance_records " + sqlCondition.trim();
            } else if (condition.contains("ORDER BY")) {
                // AI 只返回了排序部分（可能包含 LIMIT）
                // 提取 ORDER BY 后面的内容
                int orderIndex = sqlCondition.trim().toUpperCase().indexOf("ORDER BY");
                String orderPart = sqlCondition.trim().substring(orderIndex);
                sql = "SELECT * FROM finance_records " + orderPart;
            } else {
                // 普通筛选条件
                sql = "SELECT * FROM finance_records WHERE " + sqlCondition.trim();
            }
            Log.d("sql=", sql);
            // 2. 执行查询
            Cursor cursor = DbHelper.getInstance(context).querySql(sql);
            JSONArray rowTableArray = new JSONArray();
            double sumTotalPrice = 0.0;
            int sumQuantity = 0;

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    JSONObject row = new JSONObject();
                    row.put("id", cursor.getLong(cursor.getColumnIndexOrThrow("id")));
                    row.put("label", cursor.getString(cursor.getColumnIndexOrThrow("label")));
                    row.put("category", cursor.getString(cursor.getColumnIndexOrThrow("category")));
                    row.put("unit_price", cursor.getDouble(cursor.getColumnIndexOrThrow("unit_price")));
                    row.put("quantity", cursor.getInt(cursor.getColumnIndexOrThrow("quantity")));
                    row.put("total_price", cursor.getDouble(cursor.getColumnIndexOrThrow("total_price")));
                    row.put("income_expense", cursor.getString(cursor.getColumnIndexOrThrow("income_expense")));
                    row.put("timestamp", cursor.getString(cursor.getColumnIndexOrThrow("timestamp")));
                    row.put("raw_text", cursor.getString(cursor.getColumnIndexOrThrow("raw_text")));
                    rowTableArray.put(row);

                    // 累加汇总（确保和上面列名一致）
                    sumTotalPrice += cursor.getDouble(cursor.getColumnIndexOrThrow("total_price"));
                    sumQuantity += cursor.getInt(cursor.getColumnIndexOrThrow("quantity"));
                } while (cursor.moveToNext());
                cursor.close();
            }

            // 3. 组装最终要返回给前端的 JSON 对象
            JSONObject resultJsonObj = new JSONObject();
            // 统一使用 row_table 字段名
            resultJsonObj.put("row_table", rowTableArray);
            resultJsonObj.put("sumTotalPrice", sumTotalPrice);
            resultJsonObj.put("sumQuantity", sumQuantity);

            String jsCallback = "javascript:JsHelper.onQueryResult('" + resultJsonObj.toString() + "')";

            // 4. 回调前端
            new Handler(Looper.getMainLooper()).post(() -> {
                webView.evaluateJavascript(jsCallback, null);
            });

        } catch (Exception e) {
            e.printStackTrace();
            // 即使是报错，也要返回一个带 error 字段的合法 JSON，防止前端 JSON.parse 报错
            String errorJson = "{\"error\": \"查询失败: " + e.getMessage() + "\"}";
            new Handler(Looper.getMainLooper()).post(() -> {
                webView.evaluateJavascript("javascript:JsHelper.onQueryResult(" + errorJson + ")", null);
            });
        }
    }

    /**
     * 前端调用：getDailyData(2026, 9, "支出")
     * 返回 JSON：{"1":5.72, "2":11.58, ...}
     */
    @JavascriptInterface
    public String getDailyData(int year, int month, String type) {
        Cursor cursor = DbHelper.getInstance(context)
                .getDailySummary(year, month, type);

        JSONObject json = new JSONObject();
        try {
            // 【优化点1】提前获取列索引，避免循环中反复查找，同时消除警告
            int dateCol = cursor.getColumnIndex("date");
            int totalCol = cursor.getColumnIndex("daily_total");

            // 【优化点2】加上健壮性判断，防止列名错误导致崩溃
            if (dateCol == -1 || totalCol == -1) {
                return json.toString();
            }

            while (cursor.moveToNext()) {
                String date = cursor.getString(dateCol);

                // date 格式是 "2026-09-01"，提取日号
                if (date != null && date.length() >= 10) {
                    int day = Integer.parseInt(date.substring(8, 10));
                    double total = cursor.getDouble(totalCol);

                    json.put(String.valueOf(day), total);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // 【优化点3】在 finally 中必须判断 cursor 是否为 null 且未关闭
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        }
        return json.toString();
    }

    /**
     * 前端调用：getMonthlyTotal(2026, 9, "支出")
     * 返回该月总支出数字
     */
    @JavascriptInterface
    public double getMonthlyTotal(int year, int month, String type) {
        String yearMonth = year + "-" + String.format("%02d", month);
        Cursor cursor = DbHelper.getInstance(context).querySql(
                "SELECT SUM(total_price) FROM finance_records " +
                        "WHERE income_expense = ? AND SUBSTR(timestamp, 1, 7) = ?",
                type, yearMonth
        );
        double total = 0;
        if (cursor.moveToFirst()) {
            total = cursor.getDouble(0);
        }
        cursor.close();
        return total;
    }

    @JavascriptInterface
    public String ai_data() {
        String data_key = "";
        String data_url = "";
        String data_model = "";

        Cursor cursor = DbHelper.getInstance(context).getAiConfig();
        if (cursor != null && cursor.moveToFirst()) {
            data_key = cursor.getString(cursor.getColumnIndexOrThrow("ai_key"));
            data_url = cursor.getString(cursor.getColumnIndexOrThrow("ai_url"));
            data_model = cursor.getString(cursor.getColumnIndexOrThrow("ai_model"));
            cursor.close();
        }

        // 转成 JSON 字符串
        JSONObject json = new JSONObject();
        try {
            json.put("key", data_key);
            json.put("url", data_url);
            json.put("model", data_model);
        }catch (Exception e) {
            e.printStackTrace();
        }
        Log.d("JsBridge", "成功调用: " + json);
        return json.toString();
    }


    @JavascriptInterface
    public void save_ai_data(String jsonStr) {
        try {
            // 1. 解析前端传来的 JSON 字符串
            JSONObject json = new JSONObject(jsonStr);
            String key = json.getString("key");
            String url = json.getString("url");
            String model = json.getString("model");

            // 2. 直接调用你 DbHelper 里写好的保存函数
            DbHelper.getInstance(context).saveAiConfig(key, url, model);

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }


    /**
     * 前端 set_loan() 调用
     * 返回嵌套结构：[{income_expense, loan_table:[{label, category, total_price}]}]
     */
    @JavascriptInterface
    public String getloanData(int type) {
        // type=0 查"贷款/还款"，type=1 查"借出/收债"
        LinkedHashMap<String, JSONArray> groups = new LinkedHashMap<>();

        Cursor cursor = null;
        try {
            cursor = DbHelper.getInstance(context).getLoanSummary(type);
            Log.d("JsBridge", "聚合数据(type=" + type + "): " + cursor);
            if (cursor != null) {
                int ieCol    = cursor.getColumnIndex("income_expense");
                int labelCol = cursor.getColumnIndex("label");
                int cateCol  = cursor.getColumnIndex("category");
                int priceCol = cursor.getColumnIndex("total_price");

                while (cursor.moveToNext()) {
                    String ie = cursor.getString(ieCol);

                    JSONArray table = groups.get(ie);
                    if (table == null) {
                        table = new JSONArray();
                        groups.put(ie, table);
                    }

                    JSONObject row = new JSONObject();
                    row.put("label",       cursor.getString(labelCol));
                    row.put("category",    cursor.getString(cateCol));
                    row.put("total_price", cursor.getDouble(priceCol));
                    table.put(row);
                }
            }
        } catch (Exception e) {
            Log.e("JsBridge", "getloanData失败: " + e.getMessage());
        } finally {
            if (cursor != null && !cursor.isClosed()) cursor.close();
        }

        JSONArray result = new JSONArray();
        for (Map.Entry<String, JSONArray> entry : groups.entrySet()) {
            JSONObject group = new JSONObject();
            try {
                group.put("income_expense", entry.getKey());
                group.put("loan_table",     entry.getValue());
            } catch (Exception e) {
                Log.e("JsBridge", "组装修失败: " + e.getMessage());
            }
            result.put(group);
        }
        return result.toString();
    }
}
