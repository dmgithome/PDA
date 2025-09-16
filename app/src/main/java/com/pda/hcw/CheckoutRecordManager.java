package com.pda.hcw;

import android.content.Context;
import android.util.Log;
import java.util.List;

/**
 * 领用记录管理器 - 重新设计以支持新的领用记录功能
 */
public class CheckoutRecordManager {
    private static final String TAG = "CheckoutRecordManager";
    
    private static CheckoutRecordManager instance;
    private CheckoutRecordDatabase database;
    private Context context;
    
    public static synchronized CheckoutRecordManager getInstance(Context context) {
        if (instance == null) {
            instance = new CheckoutRecordManager(context);
        }
        return instance;
    }
    
    private CheckoutRecordManager(Context context) {
        this.context = context.getApplicationContext();
        this.database = CheckoutRecordDatabase.getInstance(this.context);
    }
    
    /**
     * 生成领用记录
     */
    public boolean generateCheckoutRecord(String operatingRoom, int totalQuantity, 
                                        List<ConsumableItem> items, String operator) {
        try {
            // 生成领用单号
            String recordId = CheckoutRecord.generateRecordId();
            
            // 创建记录对象
            CheckoutRecord record = new CheckoutRecord(
                recordId,
                System.currentTimeMillis(),
                operatingRoom,
                totalQuantity,
                operator,
                items
            );
            try {
                UserInfo u = SessionManager.getInstance(context).getCurrentUser();
                if (u != null) record.operatorUserId = u.getUserId();
            } catch (Exception ignore) {}
            
            // 保存到数据库
            long result = database.insertCheckoutRecord(record);
            
            if (result != -1) {
                Log.d(TAG, "Generated checkout record: " + recordId);
                return true;
            } else {
                Log.e(TAG, "Failed to generate checkout record. room=" + operatingRoom
                        + ", totalQuantity=" + totalQuantity + ", operator=" + operator
                        + ", itemsSize=" + (items != null ? items.size() : -1));
                return false;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error generating checkout record", e);
            return false;
        }
    }

    /**
     * 生成取用记录（支持记录服务器返回的recordId）
     */
    public boolean generateCheckoutRecord(String operatingRoom, int totalQuantity,
                                          List<ConsumableItem> items, String operator, Long serverRecordId) {
        try {
            String recordId = CheckoutRecord.generateRecordId();

            CheckoutRecord record = new CheckoutRecord(
                recordId,
                serverRecordId,
                System.currentTimeMillis(),
                operatingRoom,
                totalQuantity,
                operator,
                items
            );
            try {
                UserInfo u = SessionManager.getInstance(context).getCurrentUser();
                if (u != null) record.operatorUserId = u.getUserId();
            } catch (Exception ignore) {}

            long result = database.insertCheckoutRecord(record);
            if (result != -1) {
                Log.d(TAG, "Generated checkout record with serverRecordId: " + serverRecordId);
                return true;
            } else {
                Log.e(TAG, "Failed to generate checkout record with serverRecordId. room=" + operatingRoom
                        + ", totalQuantity=" + totalQuantity + ", operator=" + operator
                        + ", itemsSize=" + (items != null ? items.size() : -1) + ", serverRecordId=" + serverRecordId);
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error generating checkout record (serverRecordId)", e);
            return false;
        }
    }
    
    /**
     * 根据时间过滤获取记录
     */
    public List<CheckoutRecord> getRecordsByTimeFilter(TimeFilter filter, String operatorUserId) {
        try {
            return database.getRecordsByTimeFilter(filter, operatorUserId);
        } catch (Exception e) {
            Log.e(TAG, "Error getting records by time filter", e);
            return null;
        }
    }
    
    /**
     * 根据记录ID获取记录
     */
    public CheckoutRecord getRecordById(String recordId) {
        try {
            return database.getRecordById(recordId);
        } catch (Exception e) {
            Log.e(TAG, "Error getting record by ID", e);
            return null;
        }
    }
    
    /**
     * 获取当前用户名（简化版本，实际应该从用户系统获取）
     */
    public String getCurrentUser() {
        // 简化版本，返回固定用户名
        // 实际应用中应该从用户系统或SharedPreferences获取
        return "操作员";
    }
}
