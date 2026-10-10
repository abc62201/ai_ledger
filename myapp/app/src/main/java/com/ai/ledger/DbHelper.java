package com.ai.ledger;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.TextUtils;
import android.util.Log;

import java.util.Collections;

/**
 * 通用 SQLite 助手类
 * 职责：仅负责数据库初始化、表结构创建及原生 SQL 执行。
 * 不包含任何业务实体类依赖。
 */
public class DbHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "yin_finance.db";
    private static final int DB_VERSION = 2;

    // 单例模式
    private static volatile DbHelper instance;

    public static DbHelper getInstance(Context context) {
        if (instance == null) {
            synchronized (DbHelper.class) {
                if (instance == null) {
                    instance = new DbHelper(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private DbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // 在这里定义你的核心表结构
        // 如果以后需要加表，直接在这里追加 execSQL 即可
        String sql = "CREATE TABLE IF NOT EXISTS finance_records (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "label TEXT, " +             // 标签/物品名称
                "category TEXT, " +          // 分类
                "unit_price REAL, " +        // 单价
                "quantity INTEGER, " +       // 数量
                "total_price REAL, " +       // 总价
                "income_expense TEXT, " +    // 收支
                "timestamp TEXT, " +         // 时间
                "raw_text TEXT" +            // 原始发言
                ")";
        db.execSQL(sql);
        String aiConfigSql = "CREATE TABLE IF NOT EXISTS ai_config (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "ai_key TEXT, " +
                "ai_url TEXT, " +
                "ai_model TEXT" +
                ")";
        db.execSQL(aiConfigSql);
        Log.d("DbHelper", "✅ 数据库表结构初始化完成");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.d("DbHelper", "🔄 开始升级数据库：版本 " + oldVersion + " → " + newVersion);

        if (oldVersion < 2) {
            // 1) 给老库补建 ai_config 表（关键修复：v1 老库没有这张表）
            try {
                String aiConfigSql = "CREATE TABLE IF NOT EXISTS ai_config (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "ai_key TEXT, " +
                        "ai_url TEXT, " +
                        "ai_model TEXT" +
                        ")";
                db.execSQL(aiConfigSql);
                Log.d("DbHelper", "✅ 已补建 ai_config 表");
            } catch (Exception e) {
                Log.e("DbHelper", "❌ 补建 ai_config 失败: " + e.getMessage());
            }

            // 2) 给 finance_records 补 raw_text 列
            try {
                Cursor cursor = db.rawQuery("PRAGMA table_info(finance_records)", null);
                boolean columnExists = false;
                while (cursor.moveToNext()) {
                    if ("raw_text".equals(cursor.getString(1))) {
                        columnExists = true;
                        break;
                    }
                }
                cursor.close();
                if (!columnExists) {
                    db.execSQL("ALTER TABLE finance_records ADD COLUMN raw_text TEXT");
                    Log.d("DbHelper", "✅ 已添加 raw_text 列");
                }
            } catch (Exception e) {
                Log.e("DbHelper", "❌ 升级失败，保留旧数据库结构: " + e.getMessage());
            }
        }
        Log.d("DbHelper", "✅ 数据库升级流程完成，当前版本 " + newVersion);
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        if (!db.isReadOnly()) {
            try {
                String aiConfigSql = "CREATE TABLE IF NOT EXISTS ai_config (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "ai_key TEXT, " +
                        "ai_url TEXT, " +
                        "ai_model TEXT" +
                        ")";
                db.execSQL(aiConfigSql);
            } catch (Exception e) {
                Log.e("DbHelper", "onOpen 补建 ai_config 失败: " + e.getMessage());
            }
        }
    }

    /**
     * 执行任意非查询类 SQL (INSERT, UPDATE, DELETE, CREATE)
     * @param sql SQL 语句
     * @param bindArgs 占位符参数 (可选)
     */
    public void executeSql(String sql, Object... bindArgs) {
        SQLiteDatabase db = this.getWritableDatabase();
        try {
            if (bindArgs == null || bindArgs.length == 0) {
                db.execSQL(sql);
            } else {
                db.execSQL(sql, bindArgs);
            }
            Log.d("DbHelper", "✅ SQL执行成功: " + sql);
        } catch (Exception e) {
            Log.e("DbHelper", "❌ SQL执行失败: " + e.getMessage());
        } finally {
            // 注意：getWritableDatabase 获取的 db 不需要每次 close，
            // SQLiteOpenHelper 会管理连接池。但如果是 rawQuery 得到的 cursor 必须 close。
        }
    }

    /**
     * 执行任意查询类 SQL (SELECT)
     * @param sql SQL 语句
     * @param selectionArgs 占位符参数 (可选)
     * @return Cursor 游标 (调用方使用完毕后必须手动调用 cursor.close())
     */
    public Cursor querySql(String sql, String... selectionArgs) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery(sql, selectionArgs);
    }
    // 只接收 SQL 字符串，不需要任何额外的参数
    public Cursor sql_txt(String sql) {
        SQLiteDatabase db = this.getReadableDatabase();
        // 第二个参数直接传 null，表示没有占位符参数
        return db.rawQuery(sql, null);
    }
    /**
     * 查询指定年月的每日支出/收入汇总
     * @param year 年份，如 2026
     * @param month 月份，如 9
     * @param type "支出" 或 "收入"
     * @return Cursor，包含 date 和 daily_total 两列
     */
    public Cursor getDailySummary(int year, int month, String type) {
        String sql = "SELECT " +
                "SUBSTR(timestamp, 1, 10) AS date, " +
                "SUM(total_price) AS daily_total " +
                "FROM finance_records " +
                "WHERE income_expense = ? " +
                "AND SUBSTR(timestamp, 1, 7) = ? " +
                "GROUP BY SUBSTR(timestamp, 1, 10) " +
                "ORDER BY date ASC";

        String yearMonth = year + "-" + String.format("%02d", month);
        return querySql(sql, type, yearMonth);
    }

    public void saveAiConfig(String key, String url, String model) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.execSQL("DELETE FROM ai_config");
            ContentValues values = new ContentValues();
            values.put("ai_key", key);
            values.put("ai_url", url);
            values.put("ai_model", model);
            db.insert("ai_config", null, values);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }
    // 读取 AI 配置
    public Cursor getAiConfig() {
        return querySql("SELECT * FROM ai_config LIMIT 1");
    }


    /**
     * 借贷汇总：按 income_expense、label、category 三键联合分组求和
     * 这样能保证「同 label 不同 category」「同 category 不同 label」各自独立成一行
     * @return Cursor，列: income_expense, label, category, total_price, quantity
     */
    public Cursor getLoanSummary(int type) {
        // type=0 → 贷款/还款；type=1 → 借出/收债
        String[] targets;
        if (type == 1) {
            targets = new String[]{"借出", "收债"};
        } else {
            targets = new String[]{"贷款", "还款"};
        }

        // 用占位符拼接 IN 子句，防止 SQL 注入
        String placeholders = TextUtils.join(",", Collections.nCopies(targets.length, "?"));
        String sql = "SELECT income_expense, label, category, " +
                "SUM(total_price) AS total_price, " +
                "SUM(quantity) AS quantity " +
                "FROM finance_records " +
                "WHERE income_expense IN (" + placeholders + ") " +
                "GROUP BY income_expense, label, category " +
                "ORDER BY income_expense, total_price DESC";

        return querySql(sql, targets);
    }




    // ===== DbHelper.java 新增方法（追加到类末尾即可） =====

    /**
     * 查询指定年份、指定分类的12个月支出/收入汇总
     * @param year 年份，如 2026
     * @param category 分类名称，如 "水电"
     * @param type "支出" 或 "收入"
     * @return Cursor，包含 month 和 month_total 两列
     */
    public Cursor getMonthlyCategoryTotal(int year, String category, String type) {
        String sql = "SELECT " +
                "CAST(SUBSTR(timestamp, 6, 2) AS INTEGER) AS month, " +
                "SUM(total_price) AS month_total " +
                "FROM finance_records " +
                "WHERE income_expense = ? " +
                "AND category = ? " +
                "AND SUBSTR(timestamp, 1, 4) = ? " +
                "GROUP BY SUBSTR(timestamp, 6, 2) " +
                "ORDER BY month ASC";

        return querySql(sql, type, category, String.valueOf(year));
    }



}