package com.pda.hcw;

import java.util.Date;
import java.util.List;

/**
 * 领用记录实体类 - 重新设计以支持新的领用记录功能
 */
public class CheckoutRecord {
    public String recordId;        // 领用单号 (格式: LKYYMMDDHHmmSS + 随机4位)
    public Long serverRecordId;    // 业务系统返回的记录ID（取用记录ID）
    public long checkoutTime;      // 领用时间戳
    public String operatingRoom;   // 术间
    public int totalQuantity;      // 领用总数量
    public String operator;        // 操作人（用户名，仅用于显示）
    public String operatorUserId;  // 操作人用户ID（用于过滤）
    public List<ConsumableItem> items; // 领用明细
    public boolean isLoaded;      // 是否已加载到界面
    
    // 默认构造函数
    public CheckoutRecord() {
        this.isLoaded = false;
    }
    
    // 完整构造函数
    public CheckoutRecord(String recordId, long checkoutTime, String operatingRoom, 
                         int totalQuantity, String operator, List<ConsumableItem> items) {
        this.recordId = recordId;
        this.checkoutTime = checkoutTime;
        this.operatingRoom = operatingRoom;
        this.totalQuantity = totalQuantity;
        this.operator = operator;
        this.items = items;
        this.isLoaded = false;
    }

    // 带服务器记录ID的构造函数
    public CheckoutRecord(String recordId, Long serverRecordId, long checkoutTime, String operatingRoom,
                          int totalQuantity, String operator, List<ConsumableItem> items) {
        this(recordId, checkoutTime, operatingRoom, totalQuantity, operator, items);
        this.serverRecordId = serverRecordId;
    }
    
    // 获取格式化的时间字符串
    public String getFormattedTime() {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            .format(new Date(checkoutTime));
    }
    
    // 获取简化的时间字符串 (用于列表显示)
    public String getShortTime() {
        return new java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
            .format(new Date(checkoutTime));
    }
    
    // 检查记录是否在指定时间范围内
    public boolean isInTimeRange(long startTime, long endTime) {
        return checkoutTime >= startTime && checkoutTime <= endTime;
    }
    
    // 生成领用单号
    public static String generateRecordId() {
        java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("yyMMddHHmmss", java.util.Locale.getDefault());
        String timePart = dateFormat.format(new Date());
        String randomPart = String.format("%04d", (int)(Math.random() * 10000));
        return "LK" + timePart + randomPart;
    }
    
    @Override
    public String toString() {
        return "CheckoutRecord{" +
                "recordId='" + recordId + '\'' +
                ", checkoutTime=" + checkoutTime +
                ", operatingRoom='" + operatingRoom + '\'' +
                ", totalQuantity=" + totalQuantity +
                ", operator='" + operator + '\'' +
                ", items=" + (items != null ? items.size() : 0) + " items" +
                ", isLoaded=" + isLoaded +
                '}';
    }
}
