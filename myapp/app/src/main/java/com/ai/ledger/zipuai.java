package com.ai.ledger;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import com.ai.ledger.BuildConfig;
/**
 * 智谱 AI 流式调用工具类
 * 独立于任何 Activity，通过回调接口返回流式片段和最终结果
 */
public class zipuai {

    // ==== 配置常量 ====
    private static  String API_KEY = "";
    private static  String API_URL = "";
    private static  String MODEL   = "";
    private static String templatePrompt="";
    private static  String raw_text   = "";
    public static void init(Context context) {
        try {
            Cursor cursor = DbHelper.getInstance(context).getAiConfig();
            if (cursor != null && cursor.moveToFirst()) {
                API_KEY  = cursor.getString(cursor.getColumnIndexOrThrow("ai_key"));
                API_URL  = cursor.getString(cursor.getColumnIndexOrThrow("ai_url"));
                MODEL    = cursor.getString(cursor.getColumnIndexOrThrow("ai_model"));
                cursor.close();
            }
        } catch (Exception e) {
            Log.e("zipuai", "读取 AI 配置失败，使用默认值: " + e.getMessage());
            // 保持类里写死的默认 API_KEY / API_URL / MODEL 不变
        }
    }

    /**
     * 通过 Gitee 原始文本直链拉取 Prompt（无需任何令牌）
     */
    private static String getPromptFromGitee() {
        // ⚠️ 请替换为你实际文件的 Gitee 原始链接（Raw Link）
        String rawUrl = "https://gitee.com/abc62201/ai_ledger/raw/master/SYSTEM_PROMPT.txt";

        try {
            java.net.URL url = new java.net.URL(rawUrl);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);

            int code = conn.getResponseCode();
            android.util.Log.d("GITEE_DEBUG", "Gitee 直链返回状态码: " + code);
            if (code != 200) {
                return null;
            }

            // 直接读取纯文本内容，不再需要 JSON 解析和 Base64 解码
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();
            conn.disconnect();

            String finalResult = sb.toString();
            android.util.Log.d("GITEE_DEBUG", "成功拉取远程 Prompt，长度: " + finalResult.length());
            return finalResult;

        } catch (Exception e) {
            android.util.Log.e("GITEE_DEBUG", "拉取远程 Prompt 异常: " + e.getMessage(), e);
            return null;
        }
    }
    private static String getPromptFromGitee1() {
        String apiUrl = BuildConfig.GITEE_FULL_URL;

        try {
            java.net.URL url = new java.net.URL(apiUrl);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);

            int code = conn.getResponseCode();
            android.util.Log.d("GITEE_DEBUG", "Gitee 返回状态码: " + code);
            if (code != 200) {
                return null;
            }

            // 1. 读完整 JSON
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();
            conn.disconnect();

            // 2. 用官方 JSON 解析器直接取 content（不再靠猜字段顺序）
            org.json.JSONObject obj = new org.json.JSONObject(sb.toString());
            String base64Data = obj.getString("content");

            // 3. Android 原生 Base64 解码（NO_WRAP 去掉可能夹带的换行）
            byte[] decodedBytes = android.util.Base64.decode(base64Data,
                    android.util.Base64.DEFAULT | android.util.Base64.NO_WRAP);
            String finalResult = new String(decodedBytes, "UTF-8");

            android.util.Log.d("GITEE_DEBUG", "成功拉取，长度: " + finalResult.length());
            return finalResult;

        } catch (Exception e) {
            android.util.Log.e("GITEE_DEBUG", "解析异常: " + e.getMessage(), e);
            return null;
        }
    }
    public static String ai_str() {

        String currentTime = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                .format(new java.util.Date());
        // 获取今天的日期
        java.time.LocalDate today = java.time.LocalDate.now();

// 1. 今天
        String todayStr = today.toString();
// 2. 昨天
        String yesterdayStr = today.minusDays(1).toString();
// 3. 前天
        String dayBeforeYesterdayStr = today.minusDays(2).toString();
// 4. 本周一
        String thisWeekStart = today.minusDays(today.getDayOfWeek().getValue() - 1).toString();
// 5. 本周日
        String thisWeekEnd = today.plusDays(7 - today.getDayOfWeek().getValue()).toString();
// 6. 上个月1号
        String lastMonthStart = today.minusMonths(1).withDayOfMonth(1).toString();
// 7. 上个月最后一天
        String lastMonthEnd = today.minusMonths(1).withDayOfMonth(today.minusMonths(1).lengthOfMonth()).toString();
// 8. 去年1月1号
        String lastYearStart = today.minusYears(1).withDayOfYear(1).toString();
// 9. 去年12月31号
        String lastYearEnd = today.minusYears(1).withDayOfYear(today.minusYears(1).lengthOfYear()).toString();
        if (templatePrompt.isEmpty()) {
            String pulled = getPromptFromGitee();
            if (pulled != null && !pulled.isEmpty()) {
                templatePrompt = pulled;   // 只有真正拉到才缓存，失败则保持空串，下次自动重试
            }
        }
        try {
            String finalPrompt = templatePrompt
                    .replace("{{currentTime}}", currentTime)
                    .replace("{{todayStr}}", todayStr)
                    .replace("{{yesterdayStr}}", yesterdayStr)
                    .replace("{{dayBeforeYesterdayStr}}", dayBeforeYesterdayStr)
                    .replace("{{thisWeekStart}}", thisWeekStart)
                    .replace("{{thisWeekEnd}}", thisWeekEnd)
                    .replace("{{lastMonthStart}}", lastMonthStart)
                    .replace("{{lastMonthEnd}}", lastMonthEnd)
                    .replace("{{lastYearStart}}", lastYearStart)
                    .replace("{{lastYearEnd}}", lastYearEnd);
            Log.d("zipuai", "拉取数据: " + finalPrompt);
            return finalPrompt;

        } catch (Exception e) {
            // 兜底：万一 Gitee 拉不下来，返回一个本地默认提示词
            return "你是一个精准的个人财务助理。现在的准确时间是：" + currentTime + "。请严格基于这个时间来推算。";
        }
    }


    // ==== 回调接口 ====
    public interface AiCallback {
        /** 流式片段（每次收到 SSE 数据块调用一次） */
        void onChunk(String chunk);
        /** 流式结束，传入已解析+本地校验后的最终文本（JSON 格式） */
        void onComplete(String fullText);
        /** 请求出错 */
        void onError(String error);
    }

    // ==== 公开调用方法 ====
    public static void callApi(String userText, final AiCallback callback) {
        if (userText == null || userText.trim().isEmpty()) {
            if (callback != null) callback.onError("输入为空");
            return;
        }
        raw_text=userText;
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                BufferedReader reader = null;
                final StringBuilder aiBuffer = new StringBuilder();

                try {
                    // 组装请求体
                    JSONObject body = new JSONObject();
                    body.put("model", MODEL);
                    body.put("temperature", 0.1);
                    body.put("stream", true);

                    JSONArray messages = new JSONArray();
                    messages.put(new JSONObject().put("role", "system").put("content", ai_str()));
                    messages.put(new JSONObject().put("role", "user").put("content", userText));
                    body.put("messages", messages);

                    URL url = new URL(API_URL);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(60000);
                    conn.setDoOutput(true);
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setRequestProperty("Accept", "text/event-stream");
                    conn.setRequestProperty("Authorization", "Bearer " + API_KEY);

                    OutputStream os = conn.getOutputStream();
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                    os.flush();
                    os.close();

                    int code = conn.getResponseCode();
                    if (code != HttpURLConnection.HTTP_OK) {
                        final String err = "HTTP " + code;
                        if (callback != null) callback.onError(err);
                        return;
                    }

                    reader = new BufferedReader(
                            new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty() || !line.startsWith("data:")) continue;
                        String data = line.substring(5).trim();
                        if ("[DONE]".equals(data)) break;
                        try {
                            JSONObject obj = new JSONObject(data);
                            JSONArray choices = obj.optJSONArray("choices");
                            if (choices == null || choices.length() == 0) continue;
                            JSONObject delta = choices.getJSONObject(0).optJSONObject("delta");
                            if (delta == null) continue;
                            String piece = delta.optString("content", "");
                            if (!piece.isEmpty()) {
                                aiBuffer.append(piece);
                                if (callback != null) {
                                    callback.onChunk(piece);
                                }
                            }
                        } catch (Exception ignore) {
                            // 单个 SSE 分片解析失败可跳过
                        }
                    }

                    // 流式结束，解析 + 本地双重校验 → 返回 JSON
                    final String resultText = parseAndCalc(aiBuffer.toString());
                    if (callback != null) {
                        callback.onComplete(resultText);
                    }

                } catch (final Exception e) {
                    if (callback != null) {
                        callback.onError("❌ 错误: " + e.getMessage());
                    }
                } finally {
                    try { if (reader != null) reader.close(); } catch (Exception ignore) {}
                    if (conn != null) conn.disconnect();
                }
            }
        }).start();
    }

    // ==== 解析 6 字段并本地重算总价，返回 JSON 字符串 ====
    // ==== 解析入口，根据意图分发到不同处理函数 ====
    public static String parseAndCalc(String raw) {
        JSONObject json = new JSONObject();
        try {
            if (raw == null || raw.trim().isEmpty()) {
                json.put("success", false);
                json.put("error", "无返回内容");
                return json.toString();
            }
            String[] parts = raw.split(";");
            if (parts.length < 1) {
                json.put("success", false);
                json.put("error", "格式错误");
                json.put("rawData", raw);
                return json.toString();
            }

            // 获取意图
            String intent = parts[0].trim();

            // 根据意图分发
            if ("记账".equals(intent)) {
                return handleAccounting(parts);
            } else if ("查询".equals(intent)) {
                return handleQuery(parts);
            } else if ("闲聊".equals(intent)) {
                return handleChat(parts);
            } else {
                // 未知意图
                json.put("success", false);
                json.put("error", "未知意图");
                json.put("rawData", raw);
                return json.toString();
            }

        } catch (Exception e) {
            try {
                json.put("success", false);
                json.put("error", "解析失败");
                json.put("message", e.getMessage());
                json.put("rawData", raw);
            } catch (Exception ignore) {}
            return json.toString();
        }
    }

    // ==== 处理记账意图 ====
    private static String handleAccounting(String[] parts) throws JSONException {
        JSONObject json = new JSONObject();
        try {
            // 格式: 记账;分类;单价;标签;收支;数量;总价
            if (parts.length < 7) {
                json.put("success", false);
                json.put("error", "记账字段缺失");
                return json.toString();
            }

            String category = parts[1].trim();
            double unitPrice = parseDouble(parts[2]);
            String label = parts[3].trim();
            String incomeExpense = parts[4].trim();
            int quantity = parseInt(parts[5]);
            // 总价由后端计算，不使用AI返回的值，更可靠
            double calcTotal = round2(unitPrice * quantity);

            // 获取当前系统时间
            String timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                    .format(new java.util.Date());

            json.put("success", true);
            json.put("intent", "记账");
            json.put("category", category);
            json.put("unit_price", unitPrice);
            json.put("label", label);
            json.put("income_expense", incomeExpense);
            json.put("quantity", quantity);
            json.put("total_price", calcTotal);
            json.put("timestamp", timestamp);
            json.put("raw_text",raw_text);

        } catch (Exception e) {
            json.put("success", false);
            json.put("error", "记账解析失败");
            json.put("message", e.getMessage());
        }
        Log.d("zipuai", "测试借贷: " + json);
        return json.toString();
    }

    // ==== 处理查询意图 ====
    private static String handleQuery(String[] parts) throws JSONException {
        JSONObject json = new JSONObject();
        try {
            // 格式: 查询;sql命令关系
            if (parts.length < 2) {
                json.put("success", false);
                json.put("error", "查询字段缺失");
                return json.toString();
            }

            // 将剩余部分重新拼接成完整的SQL条件，因为条件里可能包含分号（虽然概率小，但更稳妥）
            StringBuilder sqlBuilder = new StringBuilder();
            for (int i = 1; i < parts.length; i++) {
                if (i > 1) sqlBuilder.append(";");
                sqlBuilder.append(parts[i]);
            }
            String sqlCondition = sqlBuilder.toString().trim();

            json.put("success", true);
            json.put("intent", "查询");
            json.put("sql_condition", sqlCondition);

        } catch (Exception e) {
            json.put("success", false);
            json.put("error", "查询解析失败");
            json.put("message", e.getMessage());
        }
        return json.toString();
    }

    // ==== 处理闲聊意图 ====
    private static String handleChat(String[] parts) throws JSONException {
        JSONObject json = new JSONObject();
        try {
            // 格式: 闲聊;称呼;内容
            if (parts.length < 3) {
                json.put("success", false);
                json.put("error", "闲聊字段缺失");
                return json.toString();
            }

            String greeting = parts[1].trim();
            // 内容部分可能包含分号，需要拼接
            StringBuilder contentBuilder = new StringBuilder();
            for (int i = 2; i < parts.length; i++) {
                if (i > 2) contentBuilder.append(";");
                contentBuilder.append(parts[i]);
            }
            String content = contentBuilder.toString().trim();

            json.put("success", true);
            json.put("intent", "闲聊");
            json.put("greeting", greeting);
            json.put("content", content);

        } catch (Exception e) {
            json.put("success", false);
            json.put("error", "闲聊解析失败");
            json.put("message", e.getMessage());
        }
        return json.toString();
    }

    // ==== 工具函数 ====
    private static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }

    private static double parseDouble(String str) {
        try {
            return Double.parseDouble(str.replaceAll("[^0-9.]", ""));
        } catch (Exception e) {
            return 0.0;
        }
    }

    private static int parseInt(String str) {
        try {
            return (int) Double.parseDouble(str.replaceAll("[^0-9.]", ""));
        } catch (Exception e) {
            return 1;
        }
    }
}