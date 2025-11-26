package com.pda.hcw;

/**
 * 耗材API响应数据模型
 * 对应 /api/stock/getConsumeOrReturnConsumablesByBarcode 接口的返回数据结构（新版本）。
 *
 * 后端统一根据库存状态返回五个列表：
 * - notInStockList : 未入库 / 不在库（仅包含 rfid 字符串）
 * - inStockList    : 在库，可正常取用
 * - takenList      : 已取用未归还
 * - consumedList   : 已消耗
 * - usedReturnList : 已消耗后退回记录
 */
public class ConsumableApiResponse {
    public int cabinetID;

    // 未入库列表（纯 RFID）
    public java.util.List<String> notInStockList;

    // 按库存状态划分的耗材列表
    public java.util.List<ConsumableItem> inStockList;
    public java.util.List<ConsumableItem> takenList;
    public java.util.List<ConsumableItem> consumedList;
    public java.util.List<ConsumableItem> usedReturnList;
    
    public ConsumableApiResponse() {
        this.notInStockList = new java.util.ArrayList<>();
        this.inStockList = new java.util.ArrayList<>();
        this.takenList = new java.util.ArrayList<>();
        this.consumedList = new java.util.ArrayList<>();
        this.usedReturnList = new java.util.ArrayList<>();
    }
    
    /** 是否存在未入库耗材 */
    public boolean hasNotInStock() {
        return notInStockList != null && !notInStockList.isEmpty();
    }
    
    /** 是否存在在库耗材 */
    public boolean hasInStock() {
        return inStockList != null && !inStockList.isEmpty();
    }
    
    /** 是否存在取用未归还耗材 */
    public boolean hasTaken() {
        return takenList != null && !takenList.isEmpty();
    }
    
    /** 是否存在已消耗耗材 */
    public boolean hasConsumed() {
        return consumedList != null && !consumedList.isEmpty();
    }
    
    /** 是否存在消耗退回记录 */
    public boolean hasUsedReturn() {
        return usedReturnList != null && !usedReturnList.isEmpty();
    }
    
    /**
     * 获取处理结果的总结信息（用于日志与调试）。
     */
    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("处理结果：");
        
        if (hasNotInStock()) {
            sb.append("未入库[").append(notInStockList.size()).append("] ");
        }
        if (hasInStock()) {
            sb.append("在库[").append(inStockList.size()).append("] ");
        }
        if (hasTaken()) {
            sb.append("取用未归还[").append(takenList.size()).append("] ");
        }
        if (hasConsumed()) {
            sb.append("已消耗[").append(consumedList.size()).append("] ");
        }
        if (hasUsedReturn()) {
            sb.append("消耗退回[").append(usedReturnList.size()).append("]");
        }
        
        return sb.toString();
    }
}
