package com.pda.hcw;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 操作审计日志记录器
 * 轻量级日志系统，保留7天数据
 */
public class AuditLogger {
    private static final String TAG = "AuditLogger";
    private static AuditLogger instance;
    
    private Context context;
    private DatabaseHelper dbHelper;
    private SimpleDateFormat dateFormat;
    
    // 日志保留时间（7天）
    private static final long LOG_RETENTION_DAYS = 7;
    private static final long LOG_RETENTION_MS = LOG_RETENTION_DAYS * 24 * 60 * 60 * 1000;
    
    // 最大日志条数限制
    private static final int MAX_LOG_ENTRIES = 100000;

    private AuditLogger(Context context) {
        this.context = context.getApplicationContext();
        this.dbHelper = new DatabaseHelper(context);
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    }

    public static synchronized AuditLogger getInstance(Context context) {
        if (instance == null) {
            instance = new AuditLogger(context.getApplicationContext());
        }
        return instance;
    }

    /**
     * 记录登录操作
     * @param userId 用户ID
     * @param loginType 登录类型（password/nfc）
     * @param deviceId 设备ID
     */
    public static void logLogin(String userId, String loginType, String deviceId) {
        if (instance != null) {
            instance.logOperation(userId, "LOGIN", "登录类型: " + loginType, deviceId);
        }
    }

    /**
     * 记录登出操作
     * @param userId 用户ID
     * @param reason 登出原因
     */
    public static void logLogout(String userId, String reason) {
        if (instance != null) {
            String deviceId = SessionManager.getInstance(instance.context).getDeviceId();
            instance.logOperation(userId, "LOGOUT", "登出原因: " + reason, deviceId);
        }
    }

    /**
     * 记录耗材领用操作
     * @param userId 用户ID
     * @param consumableCode 耗材编码
     * @param quantity 数量
     * @param operationRoom 手术室
     */
    public static void logConsumableCheckout(String userId, String consumableCode, int quantity, String operationRoom) {
        if (instance != null) {
            String details = String.format("耗材编码: %s, 数量: %d, 手术室: %s", 
                consumableCode, quantity, operationRoom);
            String deviceId = SessionManager.getInstance(instance.context).getDeviceId();
            instance.logOperation(userId, "CONSUMABLE_CHECKOUT", details, deviceId);
        }
    }

    /**
     * 记录RFID操作
     * @param userId 用户ID
     * @param operation 操作类型
     * @param tagCount 标签数量
     */
    public static void logRfidOperation(String userId, String operation, int tagCount) {
        if (instance != null) {
            String details = String.format("RFID操作: %s, 标签数量: %d", operation, tagCount);
            String deviceId = SessionManager.getInstance(instance.context).getDeviceId();
            instance.logOperation(userId, "RFID_OPERATION", details, deviceId);
        }
    }

    /**
     * 记录系统设置操作
     * @param userId 用户ID
     * @param setting 设置项
     * @param oldValue 旧值
     * @param newValue 新值
     */
    public static void logSystemSetting(String userId, String setting, String oldValue, String newValue) {
        if (instance != null) {
            String details = String.format("设置项: %s, 旧值: %s, 新值: %s", 
                setting, oldValue, newValue);
            String deviceId = SessionManager.getInstance(instance.context).getDeviceId();
            instance.logOperation(userId, "SYSTEM_SETTING", details, deviceId);
        }
    }

    /**
     * 记录安全事件
     * @param userId 用户ID
     * @param eventType 事件类型
     * @param details 详细信息
     */
    public static void logSecurityEvent(String userId, String eventType, String details) {
        if (instance != null) {
            String deviceId = SessionManager.getInstance(instance.context).getDeviceId();
            instance.logOperation(userId, "SECURITY_EVENT", eventType + ": " + details, deviceId);
        }
    }

    /**
     * 记录通用操作
     * @param userId 用户ID
     * @param operationType 操作类型
     * @param details 详细信息
     * @param deviceId 设备ID
     */
    public void logOperation(String userId, String operationType, String details, String deviceId) {
        try {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            
            // 插入日志记录
            String sql = "INSERT INTO operation_logs (user_id, operation_type, operation_details, device_id, timestamp) VALUES (?, ?, ?, ?, ?)";
            db.execSQL(sql, new Object[]{
                userId,
                operationType,
                details,
                deviceId,
                System.currentTimeMillis()
            });
            
            Log.d(TAG, "记录操作日志: " + operationType);
            
            // 检查是否需要清理旧日志
            cleanupIfNeeded(db);
            
        } catch (Exception e) {
            Log.e(TAG, "记录操作日志失败", e);
        }
    }

    /**
     * 检查并清理旧日志
     * @param db 数据库
     */
    private void cleanupIfNeeded(SQLiteDatabase db) {
        try {
            // 清理超过7天的日志
            long cutoffTime = System.currentTimeMillis() - LOG_RETENTION_MS;
            String deleteSql = "DELETE FROM operation_logs WHERE timestamp < ?";
            db.execSQL(deleteSql, new Object[]{cutoffTime});
            
            // 检查删除的记录数
            android.database.Cursor cursor = db.rawQuery("SELECT changes()", null);
            if (cursor.moveToFirst()) {
                int deletedRows = cursor.getInt(0);
                if (deletedRows > 0) {
                    Log.d(TAG, "清理了 " + deletedRows + " 条旧日志记录");
                }
            }
            cursor.close();
            
            // 检查总记录数，如果超过限制则清理最老的记录
            String countSql = "SELECT COUNT(*) FROM operation_logs";
            android.database.Cursor countCursor = db.rawQuery(countSql, null);
            if (countCursor.moveToFirst()) {
                int totalCount = countCursor.getInt(0);
                countCursor.close();
                
                if (totalCount > MAX_LOG_ENTRIES) {
                    // 删除最老的10000条记录
                    String deleteOldSql = "DELETE FROM operation_logs WHERE timestamp IN (SELECT timestamp FROM operation_logs ORDER BY timestamp ASC LIMIT 10000)";
                    db.execSQL(deleteOldSql);
                    
                    // 检查删除的记录数
                    android.database.Cursor cursor2 = db.rawQuery("SELECT changes()", null);
                    if (cursor2.moveToFirst()) {
                        int oldDeleted = cursor2.getInt(0);
                        Log.d(TAG, "清理了 " + oldDeleted + " 条最老的日志记录");
                    }
                    cursor2.close();
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "清理日志失败", e);
        }
    }

    /**
     * 数据库助手类
     */
    private static class DatabaseHelper extends SQLiteOpenHelper {
        private static final String DATABASE_NAME = "audit_logs.db";
        private static final int DATABASE_VERSION = 1;
        
        private static final String TABLE_OPERATION_LOGS = "operation_logs";
        
        private static final String CREATE_TABLE_SQL = "CREATE TABLE " + TABLE_OPERATION_LOGS + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "user_id TEXT NOT NULL, " +
                "operation_type TEXT NOT NULL, " +
                "operation_details TEXT, " +
                "device_id TEXT, " +
                "timestamp INTEGER NOT NULL" +
                ")";

        public DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            try {
                db.execSQL(CREATE_TABLE_SQL);
                Log.d(TAG, "创建审计日志数据库表");
            } catch (Exception e) {
                Log.e(TAG, "创建数据库表失败", e);
            }
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // 数据库升级逻辑
            if (oldVersion < newVersion) {
                db.execSQL("DROP TABLE IF EXISTS " + TABLE_OPERATION_LOGS);
                onCreate(db);
            }
        }
    }

    /**
     * 获取格式化的时间戳
     * @param timestamp 时间戳
     * @return 格式化的时间字符串
     */
    private String formatTimestamp(long timestamp) {
        return dateFormat.format(new Date(timestamp));
    }

    /**
     * 清理所有日志（用于测试或重置）
     */
    public void clearAllLogs() {
        try {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            db.execSQL("DELETE FROM operation_logs");
            Log.d(TAG, "清理所有操作日志");
        } catch (Exception e) {
            Log.e(TAG, "清理日志失败", e);
        }
    }

    /**
     * 获取日志统计信息
     * @return 统计信息
     */
    public String getStatistics() {
        try {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            
            // 总记录数
            String countSql = "SELECT COUNT(*) FROM operation_logs";
            android.database.Cursor cursor = db.rawQuery(countSql, null);
            int totalCount = 0;
            if (cursor.moveToFirst()) {
                totalCount = cursor.getInt(0);
            }
            cursor.close();
            
            // 今日记录数
            long todayStart = getTodayStart();
            String todaySql = "SELECT COUNT(*) FROM operation_logs WHERE timestamp >= ?";
            cursor = db.rawQuery(todaySql, new String[]{String.valueOf(todayStart)});
            int todayCount = 0;
            if (cursor.moveToFirst()) {
                todayCount = cursor.getInt(0);
            }
            cursor.close();
            
            return String.format("总记录: %d, 今日记录: %d, 保留天数: %d天", 
                totalCount, todayCount, LOG_RETENTION_DAYS);
            
        } catch (Exception e) {
            Log.e(TAG, "获取统计信息失败", e);
            return "获取统计信息失败";
        }
    }

    /**
     * 获取今日开始时间戳
     * @return 今日开始时间戳
     */
    private long getTodayStart() {
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0);
        calendar.set(java.util.Calendar.MINUTE, 0);
        calendar.set(java.util.Calendar.SECOND, 0);
        calendar.set(java.util.Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }
}
