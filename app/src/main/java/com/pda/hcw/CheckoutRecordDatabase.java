package com.pda.hcw;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * 领用记录和库存盘点数据库管理类
 */
public class CheckoutRecordDatabase extends SQLiteOpenHelper {
    private static final String TAG = "CheckoutRecordDatabase";
    
    // 数据库信息
    private static final String DATABASE_NAME = "checkout_records.db";
    private static final int DATABASE_VERSION = 9; // v9：确保明细表含rfid列，修复老设备缺列
    
    // 表名
    private static final String TABLE_CHECKOUT_RECORDS = "checkout_records";
    private static final String TABLE_CHECKOUT_RECORD_DETAILS = "checkout_record_details";
    private static final String TABLE_INVENTORY_TASKS = "inventory_tasks";
    private static final String TABLE_INVENTORY_TASK_DETAILS = "inventory_task_details";
    private static final String TABLE_ROLES = "roles";
    private static final String TABLE_SYSTEM_CONFIG = "system_config";
    private static final String TABLE_USER_OPERATION_LOGS = "user_operation_logs";
    private static final String TABLE_OPERATING_ROOMS = "operating_rooms"; // 新增：术间表
    
    // 表字段 - 领用记录表
    private static final String COLUMN_RECORD_ID = "record_id";
    private static final String COLUMN_CHECKOUT_TIME = "checkout_time";
    private static final String COLUMN_OPERATING_ROOM = "operating_room";
    private static final String COLUMN_TOTAL_QUANTITY = "total_quantity";
    private static final String COLUMN_OPERATOR = "operator";
    private static final String COLUMN_OPERATOR_USER_ID = "operator_user_id";
    private static final String COLUMN_ITEMS_JSON = "items_json";
    private static final String COLUMN_CREATED_TIME = "created_time";
    
    // 新增字段 - 支持盘点功能
    private static final String COLUMN_RECORD_TYPE = "record_type"; // checkout/inventory
    private static final String COLUMN_TASK_ID = "task_id"; // 关联盘点任务ID
    private static final String COLUMN_INVENTORY_STATUS = "inventory_status"; // 未执行/暂存/已完成
    
    // 盘点任务表字段
    private static final String COLUMN_TASK_ID_INV = "task_id";
    private static final String COLUMN_TASK_NAME = "task_name";
    private static final String COLUMN_CREATE_TIME = "create_time";
    private static final String COLUMN_TASK_OPERATOR = "task_operator";
    private static final String COLUMN_TOTAL_ITEMS = "total_items";
    private static final String COLUMN_TASK_STATUS = "task_status";
    private static final String COLUMN_EXPECTED_ITEMS_JSON = "expected_items_json";
    private static final String COLUMN_CONSUMED_ITEMS_JSON = "consumed_items_json";
    private static final String COLUMN_SYNC_TIME = "sync_time";
    
      
    // 角色表字段
    private static final String COLUMN_ROLE_ID = "role_id";
    private static final String COLUMN_ROLE_NAME = "role_name";
    private static final String COLUMN_PERMISSIONS = "permissions";
    private static final String COLUMN_DESCRIPTION = "description";
    private static final String COLUMN_IS_SYSTEM = "is_system";
    
    // 领用记录明细表字段
    private static final String COLUMN_DETAIL_ID = "detail_id";
    private static final String COLUMN_RECORD_ID_REF = "record_id";
    private static final String COLUMN_PRODUCT_CODE = "product_code";
    private static final String COLUMN_BARCODE = "barcode";
    private static final String COLUMN_COMMON_NAME = "common_name";
    private static final String COLUMN_SPECIFICATION = "specification";
    private static final String COLUMN_MANUFACTURER = "manufacturer";
    private static final String COLUMN_QUANTITY = "quantity";
    private static final String COLUMN_UNIT = "unit";
    private static final String COLUMN_BATCH_NUMBER = "batch_number";
    private static final String COLUMN_EXPIRY_DATE = "expiry_date";
    
    // 盘点任务明细表字段（优化版，包含inventory_results和surplus_details功能）
    private static final String COLUMN_TASK_ID_REF = "task_id";
    private static final String COLUMN_EXPECTED_QUANTITY = "expected_quantity";
    private static final String COLUMN_ACTUAL_QUANTITY = "actual_quantity";
    private static final String COLUMN_LOCATION = "location";
    private static final String COLUMN_SCAN_STATUS = "scan_status";
    private static final String COLUMN_EPC_CODE = "epc_code";
    private static final String COLUMN_RSSI = "rssi";
    private static final String COLUMN_SCAN_TIME = "scan_time";
    
        
    // 系统配置表字段
    private static final String COLUMN_CONFIG_ID = "config_id";
    private static final String COLUMN_CONFIG_KEY = "config_key";
    private static final String COLUMN_CONFIG_VALUE = "config_value";
    private static final String COLUMN_CONFIG_TYPE = "config_type";
    private static final String COLUMN_UPDATED_TIME = "updated_time";

    // 术间表字段
    private static final String COLUMN_OR_ID = "id";               // 本地自增ID
    private static final String COLUMN_OR_SERVER_ID = "server_id"; // 服务端ID（唯一，可空）
    private static final String COLUMN_OR_NAME = "name";           // 名称（唯一）
    private static final String COLUMN_OR_UPDATED_TIME = "updated_time"; // 更新时间
    
    // 用户操作记录表字段
    private static final String COLUMN_LOG_ID = "log_id";
    private static final String COLUMN_USER_ID_REF = "user_id";
    private static final String COLUMN_OPERATION_TYPE = "operation_type";
    private static final String COLUMN_MODULE_NAME = "module_name";
    private static final String COLUMN_OPERATION_DETAILS = "operation_details";
    private static final String COLUMN_DEVICE_ID = "device_id";
    private static final String COLUMN_IP_ADDRESS = "ip_address";
    private static final String COLUMN_OPERATION_TIME = "operation_time";
    
    // 新增领用记录表字段
    private static final String COLUMN_DOCUMENT_NUMBER = "document_number";
    private static final String COLUMN_RECORD_STATUS = "record_status";
    private static final String COLUMN_SERVER_RECORD_ID = "server_record_id"; // 服务器返回的取用记录ID
    
    // 新增盘点任务表字段
    private static final String COLUMN_TASK_OPERATOR_ID = "operator_id";
    private static final String COLUMN_EXPECTED_ITEMS = "expected_items";
    private static final String COLUMN_ACTUAL_ITEMS = "actual_items";
    private static final String COLUMN_SURPLUS_ITEMS = "surplus_items";
    private static final String COLUMN_MISSING_ITEMS = "missing_items";
    private static final String COLUMN_START_TIME = "start_time";
    private static final String COLUMN_END_TIME = "end_time";
    
    // 创建表语句
    private static final String CREATE_TABLE_CHECKOUT_RECORDS = "CREATE TABLE " + TABLE_CHECKOUT_RECORDS + "(" 
            + COLUMN_RECORD_ID + " TEXT PRIMARY KEY,"
            + COLUMN_CHECKOUT_TIME + " INTEGER NOT NULL,"
            + COLUMN_OPERATING_ROOM + " TEXT NOT NULL,"
            + COLUMN_TOTAL_QUANTITY + " INTEGER NOT NULL,"
            + COLUMN_OPERATOR + " TEXT NOT NULL,"
            + COLUMN_OPERATOR_USER_ID + " TEXT,"
            + COLUMN_ITEMS_JSON + " TEXT NOT NULL,"
            + COLUMN_SERVER_RECORD_ID + " INTEGER,"
            + COLUMN_DOCUMENT_NUMBER + " TEXT,"
            + COLUMN_RECORD_STATUS + " TEXT DEFAULT 'active',"
            + COLUMN_CREATED_TIME + " INTEGER DEFAULT (strftime('%s', 'now')),"
            + COLUMN_RECORD_TYPE + " TEXT DEFAULT 'checkout',"
            + COLUMN_TASK_ID + " TEXT,"
            + COLUMN_INVENTORY_STATUS + " TEXT,"
            + COLUMN_SYNC_TIME + " INTEGER"
            + ")";
    
    private static final String CREATE_TABLE_INVENTORY_TASKS = "CREATE TABLE " + TABLE_INVENTORY_TASKS + "("
            + COLUMN_TASK_ID_INV + " TEXT PRIMARY KEY,"
            + COLUMN_TASK_NAME + " TEXT NOT NULL,"
            + COLUMN_CREATE_TIME + " INTEGER NOT NULL,"
            + COLUMN_TASK_OPERATOR + " TEXT NOT NULL,"
            + COLUMN_TOTAL_ITEMS + " INTEGER DEFAULT 0,"
            + COLUMN_TASK_STATUS + " TEXT DEFAULT '未执行',"
            + COLUMN_EXPECTED_ITEMS_JSON + " TEXT,"
            + COLUMN_CONSUMED_ITEMS_JSON + " TEXT,"
            + COLUMN_SYNC_TIME + " INTEGER,"
            + COLUMN_CREATED_TIME + " INTEGER DEFAULT (strftime('%s', 'now'))"
            + ")";
    
      
    // 角色表创建语句
    private static final String CREATE_TABLE_ROLES = "CREATE TABLE " + TABLE_ROLES + "("
            + COLUMN_ROLE_ID + " TEXT PRIMARY KEY,"
            + COLUMN_ROLE_NAME + " TEXT NOT NULL UNIQUE,"
            + COLUMN_PERMISSIONS + " TEXT NOT NULL,"
            + COLUMN_DESCRIPTION + " TEXT,"
            + COLUMN_IS_SYSTEM + " INTEGER DEFAULT 0,"
            + COLUMN_CREATED_TIME + " INTEGER DEFAULT (strftime('%s', 'now'))"
            + ")";
    
    // 领用记录明细表创建语句
    private static final String CREATE_TABLE_CHECKOUT_RECORD_DETAILS = "CREATE TABLE " + TABLE_CHECKOUT_RECORD_DETAILS + "("
            + COLUMN_DETAIL_ID + " TEXT PRIMARY KEY,"
            + COLUMN_RECORD_ID_REF + " TEXT NOT NULL,"
            + COLUMN_PRODUCT_CODE + " TEXT NOT NULL,"
            + COLUMN_BARCODE + " TEXT NOT NULL,"
            + "rfid TEXT,"
            + COLUMN_COMMON_NAME + " TEXT NOT NULL,"
            + COLUMN_SPECIFICATION + " TEXT NOT NULL,"
            + COLUMN_MANUFACTURER + " TEXT NOT NULL,"
            + COLUMN_QUANTITY + " INTEGER NOT NULL,"
            + COLUMN_UNIT + " TEXT NOT NULL,"
            + COLUMN_BATCH_NUMBER + " TEXT,"
            + COLUMN_EXPIRY_DATE + " INTEGER,"
            + COLUMN_CREATED_TIME + " INTEGER DEFAULT (strftime('%s', 'now')),"
            + "FOREIGN KEY (" + COLUMN_RECORD_ID_REF + ") REFERENCES " + TABLE_CHECKOUT_RECORDS + "(" + COLUMN_RECORD_ID + ") ON DELETE CASCADE"
            + ")";
    
    // 盘点任务明细表创建语句（优化版，合并inventory_results和surplus_details功能）
    private static final String CREATE_TABLE_INVENTORY_TASK_DETAILS = "CREATE TABLE " + TABLE_INVENTORY_TASK_DETAILS + "("
            + COLUMN_DETAIL_ID + " TEXT PRIMARY KEY,"
            + COLUMN_TASK_ID_REF + " TEXT NOT NULL,"
            + COLUMN_PRODUCT_CODE + " TEXT NOT NULL,"
            + COLUMN_BARCODE + " TEXT NOT NULL,"
            + "rfid TEXT,"
            + COLUMN_COMMON_NAME + " TEXT NOT NULL,"
            + COLUMN_SPECIFICATION + " TEXT NOT NULL,"
            + COLUMN_MANUFACTURER + " TEXT NOT NULL,"
            + COLUMN_EXPECTED_QUANTITY + " INTEGER DEFAULT 0,"
            + COLUMN_ACTUAL_QUANTITY + " INTEGER DEFAULT 0,"
            + COLUMN_LOCATION + " TEXT NOT NULL,"
            + COLUMN_SCAN_STATUS + " TEXT DEFAULT '未扫描',"
            + COLUMN_EPC_CODE + " TEXT,"
            + COLUMN_RSSI + " INTEGER DEFAULT 0,"
            + COLUMN_SCAN_TIME + " INTEGER,"
            + COLUMN_CREATED_TIME + " INTEGER DEFAULT (strftime('%s', 'now')),"
            + "FOREIGN KEY (" + COLUMN_TASK_ID_REF + ") REFERENCES " + TABLE_INVENTORY_TASKS + "(" + COLUMN_TASK_ID_INV + ") ON DELETE CASCADE"
            + ")";
    
        
    // 系统配置表创建语句
    private static final String CREATE_TABLE_SYSTEM_CONFIG = "CREATE TABLE " + TABLE_SYSTEM_CONFIG + "("
            + COLUMN_CONFIG_ID + " TEXT PRIMARY KEY,"
            + COLUMN_CONFIG_KEY + " TEXT NOT NULL UNIQUE,"
            + COLUMN_CONFIG_VALUE + " TEXT NOT NULL,"
            + COLUMN_CONFIG_TYPE + " TEXT DEFAULT 'string',"
            + COLUMN_DESCRIPTION + " TEXT,"
            + COLUMN_IS_SYSTEM + " INTEGER DEFAULT 0,"
            + COLUMN_CREATED_TIME + " INTEGER DEFAULT (strftime('%s', 'now')),"
            + COLUMN_UPDATED_TIME + " INTEGER DEFAULT (strftime('%s', 'now'))"
            + ")";
    
    // 用户操作记录表创建语句
    private static final String CREATE_TABLE_USER_OPERATION_LOGS = "CREATE TABLE " + TABLE_USER_OPERATION_LOGS + "("
            + COLUMN_LOG_ID + " TEXT PRIMARY KEY,"
            + COLUMN_USER_ID_REF + " TEXT NOT NULL,"
            + COLUMN_OPERATION_TYPE + " TEXT NOT NULL,"
            + COLUMN_MODULE_NAME + " TEXT NOT NULL,"
            + COLUMN_OPERATION_DETAILS + " TEXT,"
            + COLUMN_DEVICE_ID + " TEXT NOT NULL,"
            + COLUMN_IP_ADDRESS + " TEXT,"
            + COLUMN_OPERATION_TIME + " INTEGER DEFAULT (strftime('%s', 'now')),"
            + COLUMN_CREATED_TIME + " INTEGER DEFAULT (strftime('%s', 'now'))"
            + ")";

    // 术间表创建语句
    private static final String CREATE_TABLE_OPERATING_ROOMS = "CREATE TABLE " + TABLE_OPERATING_ROOMS + "("
            + COLUMN_OR_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
            + COLUMN_OR_SERVER_ID + " INTEGER UNIQUE,"
            + COLUMN_OR_NAME + " TEXT NOT NULL UNIQUE,"
            + COLUMN_OR_UPDATED_TIME + " INTEGER DEFAULT (strftime('%s', 'now'))"
            + ")";
    
    private static CheckoutRecordDatabase instance;
    
    // 连接池管理
    private static final Object databaseLock = new Object();
    private int activeConnections = 0;
    
    public static synchronized CheckoutRecordDatabase getInstance(Context context) {
        if (instance == null) {
            instance = new CheckoutRecordDatabase(context.getApplicationContext());
        }
        return instance;
    }
    
    /**
     * 安全关闭数据库连接
     * 在应用退出或内存紧张时调用
     */
    public void closeDatabase() {
        synchronized (databaseLock) {
            try {
                if (activeConnections > 0) {
                    Log.d(TAG, "Closing database with " + activeConnections + " active connections");
                    activeConnections = 0;
                }
                close();
                Log.d(TAG, "Database closed successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error closing database", e);
            }
        }
    }
    
    private CheckoutRecordDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }
    
    @Override
    public void onCreate(SQLiteDatabase db) {
        try {
            // 创建角色表
            db.execSQL(CREATE_TABLE_ROLES);
            
            // 创建领用记录表
            db.execSQL(CREATE_TABLE_CHECKOUT_RECORDS);
            
            // 创建领用记录明细表
            db.execSQL(CREATE_TABLE_CHECKOUT_RECORD_DETAILS);
            
            // 创建盘点任务表
            db.execSQL(CREATE_TABLE_INVENTORY_TASKS);
            
            // 创建盘点任务明细表（优化版）
            db.execSQL(CREATE_TABLE_INVENTORY_TASK_DETAILS);
            
            // 创建系统配置表
            db.execSQL(CREATE_TABLE_SYSTEM_CONFIG);
            
            // 创建用户操作记录表
            db.execSQL(CREATE_TABLE_USER_OPERATION_LOGS);

            // 创建术间表
            db.execSQL(CREATE_TABLE_OPERATING_ROOMS);
            
            // 创建索引（已移除 user_operation_logs 相关索引）
            createIndexes(db);
            
            // 初始化默认数据
            initializeDefaultData(db);
            
            Log.d(TAG, "Database tables created successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error creating database tables", e);
        }
    }
    
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.d(TAG, "Upgrading database from version " + oldVersion + " to " + newVersion);
        
        try {
            if (oldVersion < 2) {
                // 升级到版本2：添加盘点功能支持
                
                // 1. 为现有checkout_records表添加新字段
                db.execSQL("ALTER TABLE " + TABLE_CHECKOUT_RECORDS + " ADD COLUMN " + COLUMN_RECORD_TYPE + " TEXT DEFAULT 'checkout'");
                db.execSQL("ALTER TABLE " + TABLE_CHECKOUT_RECORDS + " ADD COLUMN " + COLUMN_TASK_ID + " TEXT");
                db.execSQL("ALTER TABLE " + TABLE_CHECKOUT_RECORDS + " ADD COLUMN " + COLUMN_INVENTORY_STATUS + " TEXT");
                
                // 2. 创建盘点任务表
                db.execSQL(CREATE_TABLE_INVENTORY_TASKS);
                
                // 3. 创建盘点任务明细表（优化版）
                db.execSQL(CREATE_TABLE_INVENTORY_TASK_DETAILS);
                
                Log.d(TAG, "Database upgraded to version 2 with inventory support");
            }
            
            if (oldVersion < 3) {
                // 升级到版本3：移除用户表，简化权限管理系统
                
                // 1. 创建角色表
                db.execSQL(CREATE_TABLE_ROLES);
                
                // 2. 创建领用记录明细表
                db.execSQL(CREATE_TABLE_CHECKOUT_RECORD_DETAILS);
                
                // 3. 创建盘点任务明细表
                db.execSQL(CREATE_TABLE_INVENTORY_TASK_DETAILS);
                
                  
                // 5. 创建系统配置表
                db.execSQL(CREATE_TABLE_SYSTEM_CONFIG);
                
                // 6. 创建用户操作记录表
                db.execSQL(CREATE_TABLE_USER_OPERATION_LOGS);
                
                // 7. 为现有checkout_records表添加新字段
                try {
                    db.execSQL("ALTER TABLE " + TABLE_CHECKOUT_RECORDS + " ADD COLUMN " + COLUMN_DOCUMENT_NUMBER + " TEXT");
                    db.execSQL("ALTER TABLE " + TABLE_CHECKOUT_RECORDS + " ADD COLUMN " + COLUMN_RECORD_STATUS + " TEXT DEFAULT 'active'");
                    db.execSQL("ALTER TABLE " + TABLE_CHECKOUT_RECORDS + " ADD COLUMN " + COLUMN_SYNC_TIME + " INTEGER");
                } catch (Exception e) {
                    Log.w(TAG, "Fields may already exist: " + e.getMessage());
                }
                
                // 8. 为现有inventory_tasks表添加新字段
                try {
                    db.execSQL("ALTER TABLE " + TABLE_INVENTORY_TASKS + " ADD COLUMN " + COLUMN_TASK_OPERATOR_ID + " TEXT");
                    db.execSQL("ALTER TABLE " + TABLE_INVENTORY_TASKS + " ADD COLUMN " + COLUMN_EXPECTED_ITEMS + " INTEGER DEFAULT 0");
                    db.execSQL("ALTER TABLE " + TABLE_INVENTORY_TASKS + " ADD COLUMN " + COLUMN_ACTUAL_ITEMS + " INTEGER DEFAULT 0");
                    db.execSQL("ALTER TABLE " + TABLE_INVENTORY_TASKS + " ADD COLUMN " + COLUMN_SURPLUS_ITEMS + " INTEGER DEFAULT 0");
                    db.execSQL("ALTER TABLE " + TABLE_INVENTORY_TASKS + " ADD COLUMN " + COLUMN_MISSING_ITEMS + " INTEGER DEFAULT 0");
                    db.execSQL("ALTER TABLE " + TABLE_INVENTORY_TASKS + " ADD COLUMN " + COLUMN_START_TIME + " INTEGER");
                    db.execSQL("ALTER TABLE " + TABLE_INVENTORY_TASKS + " ADD COLUMN " + COLUMN_END_TIME + " INTEGER");
                } catch (Exception e) {
                    Log.w(TAG, "Fields may already exist: " + e.getMessage());
                }
                
                // 9. 创建索引
                createIndexes(db);
                
                // 10. 创建触发器
                createTriggers(db);
                
                // 11. 初始化默认数据
                initializeDefaultData(db);
                
                Log.d(TAG, "Database upgraded to version 3 with simplified permission management");
            }

            if (oldVersion < 4) {
                // 升级到版本4：新增术间表
                db.execSQL(CREATE_TABLE_OPERATING_ROOMS);
                Log.d(TAG, "Database upgraded to version 4 with operating rooms table");
            }

            if (oldVersion < 5) {
                // 升级到版本5：为领用记录表添加服务器记录ID字段
                try {
                    db.execSQL("ALTER TABLE " + TABLE_CHECKOUT_RECORDS + " ADD COLUMN " + COLUMN_SERVER_RECORD_ID + " INTEGER");
                    Log.d(TAG, "Database upgraded to version 5 with server_record_id column");
                } catch (Exception e) {
                    Log.w(TAG, "server_record_id may already exist: " + e.getMessage());
                }
            }

            if (oldVersion < 6) {
                // v6：补齐历史设备可能缺失的列
                try {
                    ensureColumnExists(db, TABLE_CHECKOUT_RECORDS, COLUMN_SERVER_RECORD_ID, "INTEGER");
                } catch (Exception e) {
                    Log.w(TAG, "ensure server_record_id failed: " + e.getMessage());
                }
                try {
                    ensureColumnExists(db, TABLE_USER_OPERATION_LOGS, COLUMN_USER_ID_REF, "TEXT");
                    ensureColumnExists(db, TABLE_USER_OPERATION_LOGS, COLUMN_OPERATION_TYPE, "TEXT");
                    ensureColumnExists(db, TABLE_USER_OPERATION_LOGS, COLUMN_MODULE_NAME, "TEXT");
                    ensureColumnExists(db, TABLE_USER_OPERATION_LOGS, COLUMN_OPERATION_DETAILS, "TEXT");
                    ensureColumnExists(db, TABLE_USER_OPERATION_LOGS, COLUMN_DEVICE_ID, "TEXT");
                    ensureColumnExists(db, TABLE_USER_OPERATION_LOGS, COLUMN_IP_ADDRESS, "TEXT");
                    ensureColumnExists(db, TABLE_USER_OPERATION_LOGS, COLUMN_OPERATION_TIME, "INTEGER");
                    ensureColumnExists(db, TABLE_USER_OPERATION_LOGS, COLUMN_CREATED_TIME, "INTEGER");
                    Log.d(TAG, "Database upgraded to version 6 with user_operation_logs columns ensured");
                } catch (Exception e) {
                    Log.w(TAG, "ensure user_operation_logs columns failed: " + e.getMessage());
                }
            }

            if (oldVersion < 8) {
                // v8：彻底移除旧触发器
                try { db.execSQL("DROP TRIGGER IF EXISTS log_user_operation"); } catch (Exception ignore) {}
                try { db.execSQL("DROP TRIGGER IF EXISTS update_inventory_task_stats"); } catch (Exception ignore) {}
                Log.d(TAG, "Database upgraded to version 8: triggers removed");
            }

            if (oldVersion < 9) {
                // v9：确保明细表存在 rfid 列，兼容历史设备缺列
                try { ensureColumnExists(db, TABLE_INVENTORY_TASK_DETAILS, "rfid", "TEXT"); } catch (Exception e) { Log.w(TAG, "ensure inventory_task_details.rfid failed: " + e.getMessage()); }
                try { ensureColumnExists(db, TABLE_CHECKOUT_RECORD_DETAILS, "rfid", "TEXT"); } catch (Exception e) { Log.w(TAG, "ensure checkout_record_details.rfid failed: " + e.getMessage()); }
                Log.d(TAG, "Database upgraded to version 9: ensured rfid columns");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error upgrading database", e);
            // 如果升级失败，重新创建所有表
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_CHECKOUT_RECORDS);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_INVENTORY_TASKS);
            onCreate(db);
        }
    }

    /**
     * 确保指定表存在指定列，不存在时以给定类型新增
     */
    private void ensureColumnExists(SQLiteDatabase db, String table, String column, String type) {
        Cursor c = null;
        try {
            c = db.rawQuery("PRAGMA table_info(" + table + ")", null);
            boolean exists = false;
            if (c != null) {
                while (c.moveToNext()) {
                    String name = c.getString(c.getColumnIndexOrThrow("name"));
                    if (column.equalsIgnoreCase(name)) { exists = true; break; }
                }
            }
            if (!exists) {
                String sql = "ALTER TABLE " + table + " ADD COLUMN " + column + " " + type;
                db.execSQL(sql);
                Log.d(TAG, "Added missing column: " + table + "." + column + " (" + type + ")");
            }
        } finally {
            if (c != null) c.close();
        }
    }

    // ===================== 术间管理（本地持久化） =====================

    /**
     * 替换本地术间列表（清空后批量插入）
     */
    public boolean replaceOperatingRooms(java.util.List<OperatingRoom> rooms) {
        synchronized (databaseLock) {
            SQLiteDatabase db = null;
            try {
                db = getWritableDatabase();
                db.beginTransaction();
                db.delete(TABLE_OPERATING_ROOMS, null, null);

                if (rooms != null) {
                    for (OperatingRoom room : rooms) {
                        ContentValues values = new ContentValues();
                        if (room.serverId != null) values.put(COLUMN_OR_SERVER_ID, room.serverId);
                        values.put(COLUMN_OR_NAME, room.name);
                        values.put(COLUMN_OR_UPDATED_TIME, System.currentTimeMillis());
                        db.insert(TABLE_OPERATING_ROOMS, null, values);
                    }
                }

                db.setTransactionSuccessful();
                return true;
            } catch (Exception e) {
                Log.e(TAG, "Error replacing operating rooms", e);
                return false;
            } finally {
                if (db != null) db.endTransaction();
            }
        }
    }

    /**
     * 获取本地术间列表
     */
    public java.util.List<OperatingRoom> getOperatingRooms() {
        java.util.List<OperatingRoom> list = new java.util.ArrayList<>();
        synchronized (databaseLock) {
            SQLiteDatabase db = null;
            Cursor cursor = null;
            try {
                db = getReadableDatabase();
                cursor = db.query(TABLE_OPERATING_ROOMS, null, null, null, null, null, COLUMN_OR_NAME + " ASC");
                if (cursor != null && cursor.moveToFirst()) {
                    int idxId = cursor.getColumnIndex(COLUMN_OR_ID);
                    int idxServerId = cursor.getColumnIndex(COLUMN_OR_SERVER_ID);
                    int idxName = cursor.getColumnIndex(COLUMN_OR_NAME);
                    int idxUpdated = cursor.getColumnIndex(COLUMN_OR_UPDATED_TIME);
                    do {
                        long id = idxId != -1 ? cursor.getLong(idxId) : 0;
                        Integer serverId = (idxServerId != -1 && !cursor.isNull(idxServerId)) ? cursor.getInt(idxServerId) : null;
                        String name = idxName != -1 ? cursor.getString(idxName) : "";
                        long updated = idxUpdated != -1 ? cursor.getLong(idxUpdated) : 0;
                        list.add(new OperatingRoom(id, serverId, name, updated));
                    } while (cursor.moveToNext());
                }
            } catch (Exception e) {
                Log.e(TAG, "Error querying operating rooms", e);
            } finally {
                if (cursor != null) cursor.close();
            }
        }
        return list;
    }

    /**
     * 获取本地术间名称列表
     */
    public java.util.List<String> getOperatingRoomNames() {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (OperatingRoom r : getOperatingRooms()) {
            if (r != null && r.name != null && !r.name.trim().isEmpty()) names.add(r.name);
        }
        return names;
    }

    /**
     * 新增本地术间（可传入服务端ID）
     */
    public long insertOperatingRoom(String name, Integer serverId) {
        synchronized (databaseLock) {
            SQLiteDatabase db = null;
            try {
                db = getWritableDatabase();
                ContentValues values = new ContentValues();
                if (serverId != null) values.put(COLUMN_OR_SERVER_ID, serverId);
                values.put(COLUMN_OR_NAME, name);
                values.put(COLUMN_OR_UPDATED_TIME, System.currentTimeMillis());
                return db.insert(TABLE_OPERATING_ROOMS, null, values);
            } catch (Exception e) {
                Log.e(TAG, "Error inserting operating room", e);
                return -1;
            }
        }
    }

    /**
     * 判断本地是否已存在同名术间
     */
    public boolean existsOperatingRoomByName(String name) {
        synchronized (databaseLock) {
            SQLiteDatabase db = null;
            Cursor cursor = null;
            try {
                db = getReadableDatabase();
                cursor = db.query(TABLE_OPERATING_ROOMS, new String[]{COLUMN_OR_ID}, COLUMN_OR_NAME + "=?", new String[]{name}, null, null, null, "1");
                return cursor != null && cursor.moveToFirst();
            } catch (Exception e) {
                Log.e(TAG, "Error checking room exists", e);
                return false;
            } finally {
                if (cursor != null) cursor.close();
            }
        }
    }
    
    /**
     * 插入领用记录
     */
    public long insertCheckoutRecord(CheckoutRecord record) {
        synchronized (databaseLock) {
            SQLiteDatabase db = null;
            try {
                db = getWritableDatabase();
                ContentValues values = new ContentValues();
                
                values.put(COLUMN_RECORD_ID, record.recordId);
                values.put(COLUMN_CHECKOUT_TIME, record.checkoutTime);
                values.put(COLUMN_OPERATING_ROOM, record.operatingRoom);
                values.put(COLUMN_TOTAL_QUANTITY, record.totalQuantity);
                values.put(COLUMN_OPERATOR, record.operator);
                try {
                    ensureColumnExists(db, TABLE_CHECKOUT_RECORDS, COLUMN_OPERATOR_USER_ID, "TEXT");
                } catch (Exception e) { Log.w(TAG, "ensure operator_user_id failed", e); }
                values.put(COLUMN_OPERATOR_USER_ID, record.operatorUserId);
                values.put(COLUMN_ITEMS_JSON, itemsToJson(record.items));
                if (record.serverRecordId != null) {
                    try {
                        values.put(COLUMN_SERVER_RECORD_ID, record.serverRecordId);
                    } catch (Exception ignore) {
                        // 某些历史设备列缺失，后续将通过ensureColumnExists修复
                    }
                }
                
                // 明确标记记录类型为取用（兼容旧数据默认值）
                try { ensureColumnExists(db, TABLE_CHECKOUT_RECORDS, COLUMN_RECORD_TYPE, "TEXT"); } catch (Exception ignore) {}
                values.put(COLUMN_RECORD_TYPE, "checkout");

                long result = db.insert(TABLE_CHECKOUT_RECORDS, null, values);
                Log.d(TAG, "Inserted checkout record: " + record.recordId + ", result: " + result);
                if (result == -1) {
                    Log.e(TAG, "Insert returned -1. Debug fields: recordId=" + record.recordId
                            + ", checkoutTime=" + record.checkoutTime
                            + ", operatingRoom=" + record.operatingRoom
                            + ", totalQuantity=" + record.totalQuantity
                            + ", operator=" + record.operator
                            + ", itemsSize=" + (record.items != null ? record.items.size() : -1)
                            + ", serverRecordId=" + record.serverRecordId);
                    // 尝试自动修复缺失列后重试一次
                    try {
                        ensureColumnExists(db, TABLE_CHECKOUT_RECORDS, COLUMN_SERVER_RECORD_ID, "INTEGER");
                        result = db.insert(TABLE_CHECKOUT_RECORDS, null, values);
                        Log.d(TAG, "Retry insert after ensuring columns, result: " + result);
                    } catch (Exception ensureEx) {
                        Log.w(TAG, "Retry insert failed after ensure columns", ensureEx);
                    }
                }
                
                // 清理过期记录
                cleanupOldRecords();
                
                return result;
            } catch (Exception e) {
                Log.e(TAG, "Error inserting checkout record", e);
                return -1;
            } finally {
                // 不在这里关闭数据库连接，保持连接池开放
                Log.d(TAG, "Database connection kept open for pool management");
            }
        }
    }

    /**
     * 插入归还记录（record_type = 'return'）
     */
    public long insertReturnRecord(CheckoutRecord record) {
        synchronized (databaseLock) {
            SQLiteDatabase db = null;
            try {
                db = getWritableDatabase();
                ContentValues values = new ContentValues();

                values.put(COLUMN_RECORD_ID, record.recordId);
                values.put(COLUMN_CHECKOUT_TIME, record.checkoutTime);
                // 归还无术间，保持非空字符串
                values.put(COLUMN_OPERATING_ROOM, record.operatingRoom == null ? "" : record.operatingRoom);
                values.put(COLUMN_TOTAL_QUANTITY, record.totalQuantity);
                values.put(COLUMN_OPERATOR, record.operator);
                try { ensureColumnExists(db, TABLE_CHECKOUT_RECORDS, COLUMN_OPERATOR_USER_ID, "TEXT"); } catch (Exception ignore) {}
                values.put(COLUMN_OPERATOR_USER_ID, record.operatorUserId);
                values.put(COLUMN_ITEMS_JSON, itemsToJson(record.items));
                if (record.serverRecordId != null) {
                    try { values.put(COLUMN_SERVER_RECORD_ID, record.serverRecordId); } catch (Exception ignore) {}
                }
                // 标记记录类型
                try { ensureColumnExists(db, TABLE_CHECKOUT_RECORDS, COLUMN_RECORD_TYPE, "TEXT"); } catch (Exception ignore) {}
                values.put(COLUMN_RECORD_TYPE, "return");

                long result = db.insert(TABLE_CHECKOUT_RECORDS, null, values);
                Log.d(TAG, "Inserted return record: " + record.recordId + ", result: " + result);
                if (result == -1) {
                    // 自动修复一次缺列后重试
                    try {
                        ensureColumnExists(db, TABLE_CHECKOUT_RECORDS, COLUMN_SERVER_RECORD_ID, "INTEGER");
                        ensureColumnExists(db, TABLE_CHECKOUT_RECORDS, COLUMN_RECORD_TYPE, "TEXT");
                        result = db.insert(TABLE_CHECKOUT_RECORDS, null, values);
                        Log.d(TAG, "Retry insert return record, result: " + result);
                    } catch (Exception repairEx) {
                        Log.w(TAG, "Retry insert return record failed", repairEx);
                    }
                }

                // 清理过期记录
                cleanupOldRecords();
                return result;
            } catch (Exception e) {
                Log.e(TAG, "Error inserting return record", e);
                return -1;
            }
        }
    }
    
    /**
     * 根据时间过滤获取记录（限制最近10条）
     */
    public List<CheckoutRecord> getRecordsByTimeFilter(TimeFilter filter, String operatorUserId) {
        synchronized (databaseLock) {
            List<CheckoutRecord> records = new ArrayList<>();
            SQLiteDatabase db = null;
            Cursor cursor = null;
            
            try {
                db = getReadableDatabase();
                
                long startTime = getFilterStartTime(filter);
                long endTime = getFilterEndTime(filter);
                
                // 确保存在用于过滤的用户ID列
                try { ensureColumnExists(db, TABLE_CHECKOUT_RECORDS, COLUMN_OPERATOR_USER_ID, "TEXT"); } catch (Exception ignore) {}
                String selection = COLUMN_CHECKOUT_TIME + " >= ? AND " + COLUMN_CHECKOUT_TIME + " <= ? AND " + COLUMN_OPERATOR_USER_ID + " = ?";
                String[] selectionArgs = {String.valueOf(startTime), String.valueOf(endTime), operatorUserId};
                String orderBy = COLUMN_CHECKOUT_TIME + " DESC";
                
                // 限制查询结果为最近10条记录
                String limit = "10";
                
                cursor = db.query(TABLE_CHECKOUT_RECORDS, null, selection, selectionArgs, null, null, orderBy, limit);
                
                Log.d(TAG, "Query records - filter: " + filter + ", startTime: " + startTime + ", endTime: " + endTime);
                
                if (cursor != null && cursor.moveToFirst()) {
                    do {
                        CheckoutRecord record = cursorToCheckoutRecord(cursor);
                        if (record != null) {
                            records.add(record);
                        }
                    } while (cursor.moveToNext());
                }
                
                Log.d(TAG, "Found " + records.size() + " records for filter: " + filter);
                
            } catch (Exception e) {
                Log.e(TAG, "Error getting records by time filter", e);
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
                // 不关闭数据库连接，保持连接池开放
            }
            
            return records;
        }
    }

    /**
     * 根据时间与记录类型过滤获取记录（限制最近10条）
     * @param filter 时间过滤
     * @param operatorUserId 操作人用户ID
     * @param recordType 记录类型："checkout" 或 "return"
     */
    public List<CheckoutRecord> getRecordsByTimeFilterAndType(TimeFilter filter, String operatorUserId, String recordType) {
        synchronized (databaseLock) {
            List<CheckoutRecord> records = new ArrayList<>();
            SQLiteDatabase db = null;
            Cursor cursor = null;

            try {
                db = getReadableDatabase();

                long startTime = getFilterStartTime(filter);
                long endTime = getFilterEndTime(filter);

                // 确保列存在
                try { ensureColumnExists(db, TABLE_CHECKOUT_RECORDS, COLUMN_OPERATOR_USER_ID, "TEXT"); } catch (Exception ignore) {}
                try { ensureColumnExists(db, TABLE_CHECKOUT_RECORDS, COLUMN_RECORD_TYPE, "TEXT"); } catch (Exception ignore) {}

                String selection = COLUMN_CHECKOUT_TIME + " >= ? AND " + COLUMN_CHECKOUT_TIME + " <= ? AND " + COLUMN_OPERATOR_USER_ID + " = ? AND " + COLUMN_RECORD_TYPE + " = ?";
                String[] selectionArgs = {String.valueOf(startTime), String.valueOf(endTime), operatorUserId, recordType};
                String orderBy = COLUMN_CHECKOUT_TIME + " DESC";
                String limit = "10";

                cursor = db.query(TABLE_CHECKOUT_RECORDS, null, selection, selectionArgs, null, null, orderBy, limit);

                if (cursor != null && cursor.moveToFirst()) {
                    do {
                        CheckoutRecord record = cursorToCheckoutRecord(cursor);
                        if (record != null) records.add(record);
                    } while (cursor.moveToNext());
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting records by time filter and type", e);
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
            return records;
        }
    }
    
    /**
     * 根据记录ID获取记录
     */
    public CheckoutRecord getRecordById(String recordId) {
        synchronized (databaseLock) {
            SQLiteDatabase db = null;
            Cursor cursor = null;
            
            try {
                db = getReadableDatabase();
                
                String selection = COLUMN_RECORD_ID + " = ?";
                String[] selectionArgs = {recordId};
                
                cursor = db.query(TABLE_CHECKOUT_RECORDS, null, selection, selectionArgs, null, null, null);
                
                if (cursor != null && cursor.moveToFirst()) {
                    return cursorToCheckoutRecord(cursor);
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error getting record by ID", e);
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
                // 不关闭数据库连接，保持连接池开放
            }
            
            return null;
        }
    }
    
    /**
     * 清理超过7天的记录
     */
    private void cleanupOldRecords() {
        synchronized (databaseLock) {
            SQLiteDatabase db = null;
            try {
                db = getWritableDatabase();
                
                // 删除7天前的记录
                long cutoffTime = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000);
                
                String whereClause = COLUMN_CHECKOUT_TIME + " < ?";
                String[] whereArgs = {String.valueOf(cutoffTime)};
                
                int deletedRows = db.delete(TABLE_CHECKOUT_RECORDS, whereClause, whereArgs);
                
                if (deletedRows > 0) {
                    Log.d(TAG, "Cleaned up " + deletedRows + " old records");
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error cleaning up old records", e);
            } finally {
                // 不关闭数据库连接，保持连接池开放
            }
        }
    }
    
    /**
     * 获取过滤开始时间
     */
    private long getFilterStartTime(TimeFilter filter) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        
        switch (filter) {
            case TODAY:
                return calendar.getTimeInMillis();
            case YESTERDAY:
                calendar.add(Calendar.DAY_OF_MONTH, -1);
                return calendar.getTimeInMillis();
            case DAY_BEFORE:
                calendar.add(Calendar.DAY_OF_MONTH, -2);
                return calendar.getTimeInMillis();
            case THIS_WEEK:
                calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
                return calendar.getTimeInMillis();
            default:
                return 0;
        }
    }
    
    /**
     * 获取过滤结束时间
     */
    private long getFilterEndTime(TimeFilter filter) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 999);
        
        switch (filter) {
            case TODAY:
                return calendar.getTimeInMillis();
            case YESTERDAY:
                calendar.add(Calendar.DAY_OF_MONTH, -1);
                return calendar.getTimeInMillis();
            case DAY_BEFORE:
                calendar.add(Calendar.DAY_OF_MONTH, -2);
                return calendar.getTimeInMillis();
            case THIS_WEEK:
                // 本周结束时间应该是当前时间，而不是周日
                return calendar.getTimeInMillis();
            default:
                return System.currentTimeMillis();
        }
    }
    
    /**
     * 将Cursor转换为CheckoutRecord
     */
    private CheckoutRecord cursorToCheckoutRecord(Cursor cursor) {
        try {
            int recordIdIndex = cursor.getColumnIndex(COLUMN_RECORD_ID);
            int checkoutTimeIndex = cursor.getColumnIndex(COLUMN_CHECKOUT_TIME);
            int operatingRoomIndex = cursor.getColumnIndex(COLUMN_OPERATING_ROOM);
            int totalQuantityIndex = cursor.getColumnIndex(COLUMN_TOTAL_QUANTITY);
            int operatorIndex = cursor.getColumnIndex(COLUMN_OPERATOR);
            int operatorUserIdIndex = -1;
            try { operatorUserIdIndex = cursor.getColumnIndex(COLUMN_OPERATOR_USER_ID); } catch (Exception ignore) {}
            int itemsJsonIndex = cursor.getColumnIndex(COLUMN_ITEMS_JSON);
            int serverRecordIdIndex = cursor.getColumnIndex(COLUMN_SERVER_RECORD_ID);
            
            if (recordIdIndex == -1 || checkoutTimeIndex == -1 || operatingRoomIndex == -1 || 
                totalQuantityIndex == -1 || operatorIndex == -1 || itemsJsonIndex == -1) {
                Log.e(TAG, "Missing required columns in cursor");
                return null;
            }
            
            String recordId = cursor.getString(recordIdIndex);
            long checkoutTime = cursor.getLong(checkoutTimeIndex);
            String operatingRoom = cursor.getString(operatingRoomIndex);
            int totalQuantity = cursor.getInt(totalQuantityIndex);
            String operator = cursor.getString(operatorIndex);
            String itemsJson = cursor.getString(itemsJsonIndex);
            
            List<ConsumableItem> items = jsonToItems(itemsJson);
            
            CheckoutRecord record = new CheckoutRecord(recordId, checkoutTime, operatingRoom, totalQuantity, operator, items);
            if (operatorUserIdIndex != -1 && !cursor.isNull(operatorUserIdIndex)) {
                try { record.operatorUserId = cursor.getString(operatorUserIdIndex); } catch (Exception ignore) {}
            }
            if (serverRecordIdIndex != -1 && !cursor.isNull(serverRecordIdIndex)) {
                record.serverRecordId = cursor.getLong(serverRecordIdIndex);
            }
            return record;
            
        } catch (Exception e) {
            Log.e(TAG, "Error converting cursor to CheckoutRecord", e);
            return null;
        }
    }
    
    /**
     * 将耗材列表转换为JSON字符串
     */
    private String itemsToJson(List<ConsumableItem> items) {
        try {
            if (items == null) {
                return "[]";
            }
            
            JSONArray jsonArray = new JSONArray();
            for (ConsumableItem item : items) {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("productCode", item.productCode);
                // 为与业务系统一致，统一以rfid存储；保留旧字段回写以便历史兼容
                String rfid = item.getRfid();
                if (rfid == null || rfid.isEmpty()) {
                    // 兼容旧数据：若无rfid则用现有barcode回填rfid字段
                    rfid = item.barcode;
                }
                jsonObject.put("rfid", rfid);
                jsonObject.put("commonName", item.commonName);
                jsonObject.put("specification", item.specification);
                jsonObject.put("manufacturer", item.manufacturer);
                jsonObject.put("quantity", item.quantity);
                jsonObject.put("unit", item.unit);
                jsonArray.put(jsonObject);
            }
            return jsonArray.toString();
        } catch (JSONException e) {
            Log.e(TAG, "Error converting items to JSON", e);
            return "[]";
        }
    }

    /**
     * 清空全部取用记录及其明细
     * @return 受影响的记录数（总和）
     */
    public int clearAllCheckoutRecords() {
        synchronized (databaseLock) {
            int affected = 0;
            try {
                SQLiteDatabase db = getWritableDatabase();
                // 先清空明细表，再清空主表
                affected += db.delete(TABLE_CHECKOUT_RECORD_DETAILS, null, null);
                affected += db.delete(TABLE_CHECKOUT_RECORDS, null, null);
                Log.d(TAG, "clearAllCheckoutRecords affected rows=" + affected);
            } catch (Exception e) {
                Log.e(TAG, "clearAllCheckoutRecords failed", e);
            }
            return affected;
        }
    }
    
    /**
     * 将JSON字符串转换为耗材列表
     */
    private List<ConsumableItem> jsonToItems(String json) {
        List<ConsumableItem> items = new ArrayList<>();
        try {
            JSONArray jsonArray = new JSONArray(json);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                String productCode = jsonObject.optString("productCode", "");
                // 兼容读取：先读rfid，若无则读barcode
                String rfid = jsonObject.optString("rfid", null);
                if (rfid == null || rfid.isEmpty()) {
                    rfid = jsonObject.optString("barcode", "");
                }
                String commonName = jsonObject.optString("commonName", "");
                String specification = jsonObject.optString("specification", "");
                String manufacturer = jsonObject.optString("manufacturer", "");
                int quantity = jsonObject.optInt("quantity", 1);
                String unit = jsonObject.optString("unit", "个");

                ConsumableItem item = new ConsumableItem(productCode, rfid, commonName, specification, manufacturer, quantity, unit);
                // 将rfid填入专用字段，并将条码字段也设为rfid用于显示兼容
                item.setRfid(rfid);
                item.setBarcode(rfid);
                items.add(item);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error converting JSON to items", e);
        }
        return items;
    }
    
    // ==================== 盘点功能相关方法 ====================
    
    /**
     * 插入盘点任务
     */
    public long insertInventoryTask(InventoryTask task) {
        synchronized (databaseLock) {
            SQLiteDatabase db = null;
            try {
                db = getWritableDatabase();
                ContentValues values = new ContentValues();
                
                values.put(COLUMN_TASK_ID_INV, task.getTaskId());
                values.put(COLUMN_TASK_NAME, task.getTaskName());
                values.put(COLUMN_CREATE_TIME, task.getCreateTime().getTime());
                values.put(COLUMN_TASK_OPERATOR, task.getOperator());
                values.put(COLUMN_TOTAL_ITEMS, task.getTotalItems());
                values.put(COLUMN_TASK_STATUS, task.getStatus());
                values.put(COLUMN_EXPECTED_ITEMS_JSON, itemsToJson(task.getExpectedItems()));
                values.put(COLUMN_CONSUMED_ITEMS_JSON, itemsToJson(task.getConsumedItems()));
                
                if (task.getSyncTime() != null) {
                    values.put(COLUMN_SYNC_TIME, task.getSyncTime().getTime());
                }
                
                long result = db.insert(TABLE_INVENTORY_TASKS, null, values);
                Log.d(TAG, "Inserted inventory task: " + task.getTaskId() + ", result: " + result);
                return result;
            } catch (Exception e) {
                Log.e(TAG, "Error inserting inventory task", e);
                return -1;
            } finally {
                // 不关闭数据库连接，保持连接池开放
            }
        }
    }
    
    /**
     * 更新盘点任务状态
     */
    public boolean updateInventoryTaskStatus(String taskId, String status) {
        synchronized (databaseLock) {
            SQLiteDatabase db = null;
            try {
                db = getWritableDatabase();
                ContentValues values = new ContentValues();
                values.put(COLUMN_TASK_STATUS, status);
                
                int rowsAffected = db.update(TABLE_INVENTORY_TASKS, values, 
                    COLUMN_TASK_ID_INV + " = ?", new String[]{taskId});
                
                Log.d(TAG, "Updated inventory task status: " + taskId + " to " + status + 
                    ", rows affected: " + rowsAffected);
                
                return rowsAffected > 0;
            } catch (Exception e) {
                Log.e(TAG, "Error updating inventory task status", e);
                return false;
            } finally {
                // 不关闭数据库连接，保持连接池开放
            }
        }
    }
    
    /**
     * 获取本地盘点任务（暂存和已完成）
     */
    public List<InventoryTask> getLocalInventoryTasks() {
        synchronized (databaseLock) {
            List<InventoryTask> tasks = new ArrayList<>();
            SQLiteDatabase db = null;
            Cursor cursor = null;
            
            try {
                db = getReadableDatabase();
                String selection = COLUMN_TASK_STATUS + " IN (?, ?, ?)";
                String[] selectionArgs = {"未执行", "暂存", "已完成"};
                String orderBy = COLUMN_CREATE_TIME + " DESC";
                
                cursor = db.query(TABLE_INVENTORY_TASKS, null, selection, selectionArgs, null, null, orderBy);
                
                if (cursor != null && cursor.moveToFirst()) {
                    do {
                        InventoryTask task = cursorToInventoryTask(cursor);
                        if (task != null) {
                            tasks.add(task);
                        }
                    } while (cursor.moveToNext());
                }
                
                Log.d(TAG, "Found " + tasks.size() + " local inventory tasks");
                
            } catch (Exception e) {
                Log.e(TAG, "Error getting local inventory tasks", e);
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
                // 不关闭数据库连接，保持连接池开放
            }
            
            return tasks;
        }
    }
    
        
      
        
        
    /**
     * 将Cursor转换为InventoryTask
     */
    private InventoryTask cursorToInventoryTask(Cursor cursor) {
        try {
            int taskIdIndex = cursor.getColumnIndex(COLUMN_TASK_ID_INV);
            int taskNameIndex = cursor.getColumnIndex(COLUMN_TASK_NAME);
            int createTimeIndex = cursor.getColumnIndex(COLUMN_CREATE_TIME);
            int operatorIndex = cursor.getColumnIndex(COLUMN_TASK_OPERATOR);
            int totalItemsIndex = cursor.getColumnIndex(COLUMN_TOTAL_ITEMS);
            int statusIndex = cursor.getColumnIndex(COLUMN_TASK_STATUS);
            int expectedItemsIndex = cursor.getColumnIndex(COLUMN_EXPECTED_ITEMS_JSON);
            int consumedItemsIndex = cursor.getColumnIndex(COLUMN_CONSUMED_ITEMS_JSON);
            
            if (taskIdIndex == -1 || taskNameIndex == -1 || createTimeIndex == -1 || 
                operatorIndex == -1 || totalItemsIndex == -1 || statusIndex == -1 || 
                expectedItemsIndex == -1 || consumedItemsIndex == -1) {
                Log.e(TAG, "Missing required columns in cursor for InventoryTask");
                return null;
            }
            
            String taskId = cursor.getString(taskIdIndex);
            String taskName = cursor.getString(taskNameIndex);
            long createTime = cursor.getLong(createTimeIndex);
            String operator = cursor.getString(operatorIndex);
            int totalItems = cursor.getInt(totalItemsIndex);
            String status = cursor.getString(statusIndex);
            
            String expectedItemsJson = cursor.getString(expectedItemsIndex);
            String consumedItemsJson = cursor.getString(consumedItemsIndex);
            
            List<ConsumableItem> expectedItems = jsonToItems(expectedItemsJson);
            List<ConsumableItem> consumedItems = jsonToItems(consumedItemsJson);
            
            InventoryTask task = new InventoryTask();
            task.setTaskId(taskId);
            task.setTaskName(taskName);
            task.setCreateTime(new Date(createTime));
            task.setOperator(operator);
            task.setTotalItems(totalItems);
            task.setStatus(status);
            task.setExpectedItems(expectedItems);
            task.setConsumedItems(consumedItems);
            
            return task;
        } catch (Exception e) {
            Log.e(TAG, "Error converting cursor to InventoryTask", e);
            return null;
        }
    }
    
        
    // ==================== 数据库版本3新增方法 ====================
    
    /**
     * 创建索引
     */
    private void createIndexes(SQLiteDatabase db) {
        try {
            
            // 领用记录表索引
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_checkout_records_time ON " + TABLE_CHECKOUT_RECORDS + "(" + COLUMN_CHECKOUT_TIME + ")");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_checkout_records_operator ON " + TABLE_CHECKOUT_RECORDS + "(" + COLUMN_OPERATOR + ")");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_checkout_records_status ON " + TABLE_CHECKOUT_RECORDS + "(" + COLUMN_RECORD_STATUS + ")");
            
            // 领用记录明细表索引
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_checkout_details_record ON " + TABLE_CHECKOUT_RECORD_DETAILS + "(" + COLUMN_RECORD_ID_REF + ")");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_checkout_details_product ON " + TABLE_CHECKOUT_RECORD_DETAILS + "(" + COLUMN_PRODUCT_CODE + ")");
            
            // 盘点任务表索引
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_inventory_tasks_operator ON " + TABLE_INVENTORY_TASKS + "(" + COLUMN_TASK_OPERATOR + ")");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_inventory_tasks_status ON " + TABLE_INVENTORY_TASKS + "(" + COLUMN_TASK_STATUS + ")");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_inventory_tasks_time ON " + TABLE_INVENTORY_TASKS + "(" + COLUMN_CREATE_TIME + ")");
            
            // 盘点任务明细表索引
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_inventory_details_task ON " + TABLE_INVENTORY_TASK_DETAILS + "(" + COLUMN_TASK_ID_REF + ")");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_inventory_details_location ON " + TABLE_INVENTORY_TASK_DETAILS + "(" + COLUMN_LOCATION + ")");
            
            // 已移除 user_operation_logs 相关索引
            
            Log.d(TAG, "Database indexes created successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error creating database indexes", e);
        }
    }

    // ========== 自定义扩展：盘点任务治理 ==========

    /**
     * 是否存在执行中的盘点任务（状态=暂存）
     */
    public boolean hasActiveInventoryTask() {
        synchronized (databaseLock) {
            SQLiteDatabase db = null;
            Cursor c = null;
            try {
                db = getReadableDatabase();
                c = db.query(TABLE_INVENTORY_TASKS, new String[]{COLUMN_TASK_ID_INV},
                        COLUMN_TASK_STATUS + "=?", new String[]{"暂存"}, null, null, null, "1");
                return c != null && c.moveToFirst();
            } catch (Exception e) {
                Log.e(TAG, "hasActiveInventoryTask failed", e);
                return false;
            } finally {
                if (c != null) c.close();
            }
        }
    }

    /**
     * 作废盘点任务（状态置为已作废）
     */
    public boolean voidInventoryTask(String taskId) {
        if (taskId == null) return false;
        synchronized (databaseLock) {
            SQLiteDatabase db = null;
            try {
                db = getWritableDatabase();
                ContentValues v = new ContentValues();
                v.put(COLUMN_TASK_STATUS, "已作废");
                int rows = db.update(TABLE_INVENTORY_TASKS, v, COLUMN_TASK_ID_INV + "=?", new String[]{taskId});
                Log.d(TAG, "voidInventoryTask: " + taskId + ", rows=" + rows);
                return rows > 0;
            } catch (Exception e) {
                Log.e(TAG, "voidInventoryTask failed", e);
                return false;
            }
        }
    }

    /**
     * 清空全部盘点任务及其明细（一次性维护需求）
     */
    public int clearAllInventoryTasks() {
        synchronized (databaseLock) {
            int affected = 0;
            try {
                SQLiteDatabase db = getWritableDatabase();
                affected += db.delete(TABLE_INVENTORY_TASK_DETAILS, null, null);
                affected += db.delete(TABLE_INVENTORY_TASKS, null, null);
                Log.d(TAG, "clearAllInventoryTasks affected rows=" + affected);
            } catch (Exception e) {
                Log.e(TAG, "clearAllInventoryTasks failed", e);
            }
            return affected;
        }
    }

    /**
     * 批量插入盘点任务明细（任务落库）
     * 将每个计划RFID落入 inventory_task_details，初始 scan_status=未扫描，expected_quantity=1
     */
    public int bulkInsertInventoryTaskDetails(String taskId, java.util.List<com.pda.hcw.inventory.InventoryPlanItem> items) {
        if (taskId == null || items == null || items.isEmpty()) return 0;
        synchronized (databaseLock) {
            SQLiteDatabase db = null;
            int success = 0;
            try {
                db = getWritableDatabase();
                db.beginTransaction();
                for (com.pda.hcw.inventory.InventoryPlanItem it : items) {
                    if (it == null || it.getRfid() == null || it.getRfid().isEmpty()) continue;
                    ContentValues v = new ContentValues();
                    String rfid = it.getRfid();
                    String detailId = "detail_" + taskId + "_" + rfid;
                    v.put(COLUMN_DETAIL_ID, detailId);
                    v.put(COLUMN_TASK_ID_REF, taskId);
                    v.put(COLUMN_PRODUCT_CODE, "");
                    v.put(COLUMN_BARCODE, rfid);
                    v.put("rfid", rfid);
                    v.put(COLUMN_COMMON_NAME, it.getName() != null ? it.getName() : "");
                    v.put(COLUMN_SPECIFICATION, it.getSpec() != null ? it.getSpec() : "");
                    v.put(COLUMN_MANUFACTURER, it.getManufacturer() != null ? it.getManufacturer() : "");
                    v.put(COLUMN_EXPECTED_QUANTITY, 1);
                    v.put(COLUMN_ACTUAL_QUANTITY, 0);
                    String loc = it.getRoomName() != null ? it.getRoomName() : "耗材屋";
                    v.put(COLUMN_LOCATION, loc);
                    v.put(COLUMN_SCAN_STATUS, "未扫描");
                    v.put(COLUMN_EPC_CODE, rfid);
                    v.put(COLUMN_RSSI, 0);
                    // scan_time/created_time 让默认值处理
                    long res = db.insertWithOnConflict(TABLE_INVENTORY_TASK_DETAILS, null, v, SQLiteDatabase.CONFLICT_IGNORE);
                    if (res != -1) success++;
                }
                db.setTransactionSuccessful();
            } catch (Exception e) {
                Log.e(TAG, "bulkInsertInventoryTaskDetails failed", e);
            } finally {
                if (db != null) {
                    try { db.endTransaction(); } catch (Exception ignore) {}
                }
            }
            return success;
        }
    }

    /**
     * 根据任务ID与RFID标记为已扫描
     */
    public boolean markInventoryItemScanned(String taskId, String rfid, int rssi) {
        if (taskId == null || rfid == null || rfid.isEmpty()) return false;
        synchronized (databaseLock) {
            SQLiteDatabase db = null;
            try {
                db = getWritableDatabase();
                ContentValues v = new ContentValues();
                v.put(COLUMN_SCAN_STATUS, "已扫描");
                v.put(COLUMN_ACTUAL_QUANTITY, 1);
                v.put(COLUMN_RSSI, rssi);
                v.put(COLUMN_SCAN_TIME, System.currentTimeMillis());
                int rows = db.update(TABLE_INVENTORY_TASK_DETAILS, v, COLUMN_TASK_ID_REF + "=? AND (" + COLUMN_EPC_CODE + "=? OR rfid=?)", new String[]{taskId, rfid, rfid});

                if (rows == 0) {
                    // 兜底：计划中不存在该RFID，则按“盈余扫描”插入一条明细（不再二次格式化）
                    try {
                        ContentValues nv = new ContentValues();
                        String detailId = "detail_" + taskId + "_" + rfid;
                        nv.put(COLUMN_DETAIL_ID, detailId);
                        nv.put(COLUMN_TASK_ID_REF, taskId);
                        nv.put(COLUMN_PRODUCT_CODE, "");
                        nv.put(COLUMN_BARCODE, rfid);
                        nv.put("rfid", rfid);
                        nv.put(COLUMN_COMMON_NAME, "");
                        nv.put(COLUMN_SPECIFICATION, "");
                        nv.put(COLUMN_MANUFACTURER, "");
                        nv.put(COLUMN_EXPECTED_QUANTITY, 0);
                        nv.put(COLUMN_ACTUAL_QUANTITY, 1);
                        nv.put(COLUMN_LOCATION, "耗材屋");
                        nv.put(COLUMN_SCAN_STATUS, "已扫描");
                        nv.put(COLUMN_EPC_CODE, rfid);
                        nv.put(COLUMN_RSSI, rssi);
                        nv.put(COLUMN_SCAN_TIME, System.currentTimeMillis());
                        long ins = db.insertWithOnConflict(TABLE_INVENTORY_TASK_DETAILS, null, nv, SQLiteDatabase.CONFLICT_IGNORE);
                        if (ins == -1) {
                            Log.w(TAG, "markInventoryItemScanned: fallback insert ignored for duplicate, task=" + taskId + ", rfid=" + rfid);
                        } else {
                            Log.d(TAG, "markInventoryItemScanned: inserted fallback row, task=" + taskId + ", rfid=" + rfid);
                        }
                        return ins != -1;
                    } catch (Exception ie) {
                        Log.e(TAG, "markInventoryItemScanned: fallback insert failed", ie);
                        return false;
                    }
                }
                return true;
            } catch (Exception e) {
                Log.e(TAG, "markInventoryItemScanned failed", e);
                return false;
            }
        }
    }

    private String tryTrim20To18(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.length() == 20 && t.endsWith("00") && isAllDigits(t)) return t.substring(0, 18);
        return null;
    }

    private boolean isAllDigits(String s) {
        if (s == null || s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        return true;
    }

    /**
     * 删除任务的盘点明细
     */
    public int deleteInventoryTaskDetails(String taskId) {
        if (taskId == null) return 0;
        synchronized (databaseLock) {
            try {
                SQLiteDatabase db = getWritableDatabase();
                return db.delete(TABLE_INVENTORY_TASK_DETAILS, COLUMN_TASK_ID_REF + "=?", new String[]{taskId});
            } catch (Exception e) {
                Log.e(TAG, "deleteInventoryTaskDetails failed", e);
                return 0;
            }
        }
    }

    /**
     * 获取任务的计划条目（从 inventory_task_details 读取）
     */
    public java.util.List<com.pda.hcw.inventory.InventoryPlanItem> getInventoryPlannedItems(String taskId) {
        java.util.List<com.pda.hcw.inventory.InventoryPlanItem> list = new java.util.ArrayList<>();
        if (taskId == null) return list;
        synchronized (databaseLock) {
            SQLiteDatabase db = null;
            Cursor c = null;
            try {
                db = getReadableDatabase();
                // 仅恢复计划内条目：expected_quantity > 0
                c = db.query(TABLE_INVENTORY_TASK_DETAILS,
                        new String[]{"rfid", COLUMN_BARCODE, COLUMN_COMMON_NAME, COLUMN_SPECIFICATION, COLUMN_MANUFACTURER, COLUMN_LOCATION},
                        COLUMN_TASK_ID_REF + "=? AND " + COLUMN_EXPECTED_QUANTITY + ">0",
                        new String[]{taskId}, null, null, null);
                if (c != null && c.moveToFirst()) {
                    do {
                        String rfid = c.getString(0);
                        if (rfid == null || rfid.isEmpty()) rfid = c.getString(1);
                        String name = c.getString(2);
                        String spec = c.getString(3);
                        String manu = c.getString(4);
                        String roomName = c.getString(5);
                        if (rfid != null && !rfid.isEmpty()) {
                            list.add(new com.pda.hcw.inventory.InventoryPlanItem(rfid, 0, roomName, name, spec, manu, null, null));
                        }
                    } while (c.moveToNext());
                }
            } catch (Exception e) {
                Log.e(TAG, "getInventoryPlannedItems failed", e);
            } finally {
                if (c != null) c.close();
            }
            return list;
        }
    }

    /**
     * 获取任务已扫描的RFID列表
     */
    public java.util.List<String> getInventoryScannedRfids(String taskId) {
        java.util.List<String> list = new java.util.ArrayList<>();
        if (taskId == null) return list;
        synchronized (databaseLock) {
            SQLiteDatabase db = null;
            Cursor c = null;
            try {
                db = getReadableDatabase();
                c = db.query(TABLE_INVENTORY_TASK_DETAILS,
                        new String[]{"rfid", COLUMN_EPC_CODE, COLUMN_BARCODE},
                        COLUMN_TASK_ID_REF + "=? AND " + COLUMN_SCAN_STATUS + "=?",
                        new String[]{taskId, "已扫描"}, null, null, null);
                if (c != null && c.moveToFirst()) {
                    do {
                        String rfid = c.getString(0);
                        if (rfid == null || rfid.isEmpty()) rfid = c.getString(1);
                        if (rfid == null || rfid.isEmpty()) rfid = c.getString(2);
                        if (rfid != null && !rfid.isEmpty()) list.add(rfid);
                    } while (c.moveToNext());
                }
            } catch (Exception e) {
                Log.e(TAG, "getInventoryScannedRfids failed", e);
            } finally {
                if (c != null) c.close();
            }
            return list;
        }
    }

    /**
     * 重置任务的扫描状态为未扫描（不删除明细）
     */
    public int resetInventoryScanStatus(String taskId) {
        if (taskId == null) return 0;
        synchronized (databaseLock) {
            try {
                SQLiteDatabase db = getWritableDatabase();
                ContentValues v = new ContentValues();
                v.put(COLUMN_SCAN_STATUS, "未扫描");
                v.put(COLUMN_ACTUAL_QUANTITY, 0);
                v.put(COLUMN_RSSI, 0);
                v.putNull(COLUMN_SCAN_TIME);
                return db.update(TABLE_INVENTORY_TASK_DETAILS, v, COLUMN_TASK_ID_REF + "=?", new String[]{taskId});
            } catch (Exception e) {
                Log.e(TAG, "resetInventoryScanStatus failed", e);
                return 0;
            }
        }
    }
    
    /**
     * 创建触发器
     */
    private void createTriggers(SQLiteDatabase db) {
        try {
            // 自动更新盘点任务统计的触发器
            String updateTaskStatsTrigger = "CREATE TRIGGER IF NOT EXISTS update_inventory_task_stats " +
                    "AFTER INSERT ON " + TABLE_INVENTORY_TASK_DETAILS + " " +
                    "BEGIN " +
                    "UPDATE " + TABLE_INVENTORY_TASKS + " " +
                    "SET actual_items = (" +
                    "SELECT COUNT(*) FROM " + TABLE_INVENTORY_TASK_DETAILS + " " +
                    "WHERE task_id = NEW.task_id AND scan_status = '已扫描'" +
                    ") " +
                    "WHERE task_id = NEW.task_id; " +
                    "END;";
            db.execSQL(updateTaskStatsTrigger);
            
            // 自动记录用户操作的触发器
            String logUserOperationTrigger = "CREATE TRIGGER IF NOT EXISTS log_user_operation " +
                    "AFTER INSERT ON " + TABLE_CHECKOUT_RECORDS + " " +
                    "BEGIN " +
                    "INSERT INTO " + TABLE_USER_OPERATION_LOGS + " (log_id, " + COLUMN_USER_ID_REF + ", operation_type, module_name, operation_details, device_id) " +
                    "VALUES ( " +
                    "'log_' || strftime('%s', 'now') || '_' || NEW.record_id, " +
                    "NEW.operator, " +
                    "'consumable_checkout', " +
                    "'耗材领用', " +
                    "'{\"document_number\": \"' || NEW.document_number || '\", \"total_quantity\": ' || NEW.total_quantity || '}', " +
                    "(SELECT config_value FROM " + TABLE_SYSTEM_CONFIG + " WHERE config_key = 'device_id')" +
                    "); " +
                    "END;";
            db.execSQL(logUserOperationTrigger);
            
            Log.d(TAG, "Database triggers created successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error creating database triggers", e);
        }
    }
    
    /**
     * 初始化默认数据
     */
    private void initializeDefaultData(SQLiteDatabase db) {
        try {
            // 插入默认角色（简化版）
            String[] roles = {
                "('admin', '管理员', '[\"consumable_checkout\",\"inventory_manage\",\"basic_data_manage\",\"rfid_operation\",\"system_settings\",\"inventory_view\"]', '管理员角色', 1)",
                "('user', '普通用户', '[\"consumable_checkout\",\"inventory_view\",\"rfid_operation\"]', '普通用户角色', 1)"
            };
            
            for (String role : roles) {
                String insertRole = "INSERT OR IGNORE INTO " + TABLE_ROLES + 
                    " (role_id, role_name, permissions, description, is_system) VALUES " + role;
                db.execSQL(insertRole);
            }
            
            // 插入默认系统配置
            String[] configs = {
                "('session_timeout', '900000', 'integer', '会话超时时间(毫秒)', 1)",
                "('max_login_attempts', '5', 'integer', '最大登录尝试次数', 1)",
                "('data_retention_days', '30', 'integer', '数据保留天数', 1)",
                "('inventory_task_retention', '7', 'integer', '盘点任务保留数量', 1)",
                "('enable_audit_log', '1', 'boolean', '启用审计日志', 1)"
            };
            
            for (String config : configs) {
                String insertConfig = "INSERT OR IGNORE INTO " + TABLE_SYSTEM_CONFIG + 
                    " (config_id, config_key, config_value, config_type, description, is_system) VALUES " + config;
                db.execSQL(insertConfig);
            }
            
            Log.d(TAG, "Default data initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing default data", e);
        }
    }
    
        
    /**
     * 记录用户操作
     */
    public void logUserOperation(String userId, String operationType, String moduleName, String operationDetails, String deviceId) {
        synchronized (databaseLock) {
            SQLiteDatabase db = null;
            try {
                db = getWritableDatabase();
                ContentValues values = new ContentValues();
                
                values.put(COLUMN_LOG_ID, "log_" + System.currentTimeMillis() + "_" + userId);
                try {
                    values.put(COLUMN_USER_ID_REF, userId);
                } catch (Exception ignore) {
                    // 容错：旧表缺列，下面会修复
                }
                values.put(COLUMN_OPERATION_TYPE, operationType);
                values.put(COLUMN_MODULE_NAME, moduleName);
                values.put(COLUMN_OPERATION_DETAILS, operationDetails);
                values.put(COLUMN_DEVICE_ID, deviceId);
                values.put(COLUMN_OPERATION_TIME, System.currentTimeMillis());
                
                long res = db.insert(TABLE_USER_OPERATION_LOGS, null, values);
                if (res == -1) {
                    // 自动修复一次缺列问题并重试
                    try {
                        ensureColumnExists(db, TABLE_USER_OPERATION_LOGS, COLUMN_USER_ID_REF, "TEXT");
                        ensureColumnExists(db, TABLE_USER_OPERATION_LOGS, COLUMN_OPERATION_TYPE, "TEXT");
                        ensureColumnExists(db, TABLE_USER_OPERATION_LOGS, COLUMN_MODULE_NAME, "TEXT");
                        ensureColumnExists(db, TABLE_USER_OPERATION_LOGS, COLUMN_OPERATION_DETAILS, "TEXT");
                        ensureColumnExists(db, TABLE_USER_OPERATION_LOGS, COLUMN_DEVICE_ID, "TEXT");
                        ensureColumnExists(db, TABLE_USER_OPERATION_LOGS, COLUMN_IP_ADDRESS, "TEXT");
                        ensureColumnExists(db, TABLE_USER_OPERATION_LOGS, COLUMN_OPERATION_TIME, "INTEGER");
                        ensureColumnExists(db, TABLE_USER_OPERATION_LOGS, COLUMN_CREATED_TIME, "INTEGER");
                        db.insert(TABLE_USER_OPERATION_LOGS, null, values);
                    } catch (Exception repairEx) {
                        Log.w(TAG, "Repair user_operation_logs columns failed", repairEx);
                    }
                }
                Log.d(TAG, "User operation logged: " + operationType + " by " + userId);
                
                // 清理30天前的操作记录
                cleanupOldOperationLogs();
                
            } catch (Exception e) {
                Log.e(TAG, "Error logging user operation", e);
            }
        }
    }
    
    /**
     * 清理30天前的操作记录
     */
    private void cleanupOldOperationLogs() {
        synchronized (databaseLock) {
            SQLiteDatabase db = null;
            try {
                db = getWritableDatabase();
                
                // 删除30天前的记录
                long cutoffTime = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000);
                
                String whereClause = COLUMN_OPERATION_TIME + " < ?";
                String[] whereArgs = {String.valueOf(cutoffTime)};
                
                int deletedRows = db.delete(TABLE_USER_OPERATION_LOGS, whereClause, whereArgs);
                
                if (deletedRows > 0) {
                    Log.d(TAG, "Cleaned up " + deletedRows + " old operation logs");
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error cleaning up old operation logs", e);
            }
        }
    }
    
    /**
     * 获取用户操作记录
     */
    public List<JSONObject> getUserOperationLogs(String userId, int limit) {
        synchronized (databaseLock) {
            List<JSONObject> logs = new ArrayList<>();
            SQLiteDatabase db = null;
            Cursor cursor = null;
            
            try {
                db = getReadableDatabase();
                
                String selection = COLUMN_USER_ID_REF + " = ?";
                String[] selectionArgs = {userId};
                String orderBy = COLUMN_OPERATION_TIME + " DESC";
                
                cursor = db.query(TABLE_USER_OPERATION_LOGS, null, selection, selectionArgs, null, null, orderBy, String.valueOf(limit));
                
                if (cursor != null && cursor.moveToFirst()) {
                    do {
                        try {
                            int operationTimeIndex = cursor.getColumnIndex(COLUMN_OPERATION_TIME);
                            int operationTypeIndex = cursor.getColumnIndex(COLUMN_OPERATION_TYPE);
                            int moduleNameIndex = cursor.getColumnIndex(COLUMN_MODULE_NAME);
                            int operationDetailsIndex = cursor.getColumnIndex(COLUMN_OPERATION_DETAILS);
                            int deviceIdIndex = cursor.getColumnIndex(COLUMN_DEVICE_ID);
                            
                            if (operationTimeIndex == -1 || operationTypeIndex == -1 || 
                                moduleNameIndex == -1 || operationDetailsIndex == -1 || deviceIdIndex == -1) {
                                Log.e(TAG, "Missing required columns in cursor for operation log");
                                continue;
                            }
                            
                            JSONObject log = new JSONObject();
                            log.put("operation_time", cursor.getLong(operationTimeIndex));
                            log.put("operation_type", cursor.getString(operationTypeIndex));
                            log.put("module_name", cursor.getString(moduleNameIndex));
                            log.put("operation_details", cursor.getString(operationDetailsIndex));
                            log.put("device_id", cursor.getString(deviceIdIndex));
                            
                            logs.add(log);
                        } catch (JSONException e) {
                            Log.e(TAG, "Error creating JSON for operation log", e);
                        }
                    } while (cursor.moveToNext());
                }
                
                Log.d(TAG, "Found " + logs.size() + " operation logs for user: " + userId);
                
            } catch (Exception e) {
                Log.e(TAG, "Error getting user operation logs", e);
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
            
            return logs;
        }
    }

    /**
     * 记录用户操作
     * @param userId 用户ID
     * @param userName 用户名
     * @param operationType 操作类型
     * @param moduleName 功能模块名称
     * @param operationDetails 操作详情
     * @param deviceInfo 设备信息
     * @param operationTime 操作时间
     */
    public void recordUserOperation(String userId, String userName, String operationType, 
                                   String moduleName, String operationDetails, String deviceInfo, String operationTime) {
        synchronized (databaseLock) {
            SQLiteDatabase db = null;
            try {
                db = getWritableDatabase();
                
                ContentValues values = new ContentValues();
                values.put(COLUMN_LOG_ID, "log_" + System.currentTimeMillis() + "_" + userId);
                values.put(COLUMN_USER_ID_REF, userId);
                values.put(COLUMN_OPERATION_TYPE, operationType);
                values.put(COLUMN_MODULE_NAME, moduleName);
                values.put(COLUMN_OPERATION_DETAILS, operationDetails);
                values.put(COLUMN_DEVICE_ID, deviceInfo);
                values.put(COLUMN_IP_ADDRESS, ""); // PDA设备暂无IP地址
                values.put(COLUMN_OPERATION_TIME, System.currentTimeMillis());
                
                long result = db.insert(TABLE_USER_OPERATION_LOGS, null, values);
                
                if (result != -1) {
                    Log.d(TAG, "已记录用户操作: " + userName + " - " + operationType + " - " + moduleName);
                } else {
                    Log.e(TAG, "记录用户操作失败: " + userName);
                }
                
            } catch (Exception e) {
                Log.e(TAG, "记录用户操作异常", e);
            }
        }
    }

    /**
     * 记录用户操作（使用时间戳）
     * @param userId 用户ID
     * @param userName 用户名
     * @param operationType 操作类型
     * @param moduleName 功能模块名称
     * @param operationDetails 操作详情
     * @param deviceInfo 设备信息
     * @param timestamp 时间戳
     */
    public void recordUserOperation(String userId, String userName, String operationType, 
                                   String moduleName, String operationDetails, String deviceInfo, long timestamp) {
        synchronized (databaseLock) {
            SQLiteDatabase db = null;
            try {
                db = getWritableDatabase();
                
                ContentValues values = new ContentValues();
                values.put(COLUMN_LOG_ID, "log_" + timestamp + "_" + userId);
                values.put(COLUMN_USER_ID_REF, userId);
                values.put(COLUMN_OPERATION_TYPE, operationType);
                values.put(COLUMN_MODULE_NAME, moduleName);
                values.put(COLUMN_OPERATION_DETAILS, operationDetails);
                values.put(COLUMN_DEVICE_ID, deviceInfo);
                values.put(COLUMN_IP_ADDRESS, ""); // PDA设备暂无IP地址
                values.put(COLUMN_OPERATION_TIME, timestamp);
                
                long result = db.insert(TABLE_USER_OPERATION_LOGS, null, values);
                
                if (result != -1) {
                    Log.d(TAG, "已记录用户操作: " + userName + " - " + operationType + " - " + moduleName);
                } else {
                    Log.e(TAG, "记录用户操作失败: " + userName);
                }
                
            } catch (Exception e) {
                Log.e(TAG, "记录用户操作异常", e);
            }
        }
    }
}
