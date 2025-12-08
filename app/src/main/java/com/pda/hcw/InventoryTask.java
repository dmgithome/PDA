package com.pda.hcw;

import java.util.Date;
import java.util.List;

/**
 * 盘点任务数据模型
 */
public class InventoryTask {
    private String taskId;           // 任务ID
    private String taskName;         // 任务名称
    private Date createTime;        // 创建时间
    private String operator;         // 操作员
    private int totalItems;          // 总物品数（在新盘点中对应 plannedCount）
    private int scannedCount;        // 已盘数量
    private int normalCount;         // 正常数量
    private int missingCount;        // 缺失数量
    private int surplusCount;        // 盈余数量
    /** 后端状态原始枚举：draft/running/review/closed/canceled；兼容旧逻辑为 null 时使用中文 status 字段 */
    private String rawStatus;
    private String status;           // 展示状态：旧版为“未执行/暂存/已完成”，新版为“未开始/执行中/待复核/已完成/已作废”
    private List<ConsumableItem> expectedItems; // 库存明细
    private List<ConsumableItem> consumedItems; // 消耗明细
    private Date syncTime;          // 同步时间
    
    // 默认构造函数
    public InventoryTask() {
        this.status = "未开始";
        this.totalItems = 0;
        this.expectedItems = new java.util.ArrayList<ConsumableItem>();
        this.consumedItems = new java.util.ArrayList<ConsumableItem>();
    }
    
    // 完整构造函数
    public InventoryTask(String taskId, String taskName, Date createTime, String operator, 
                        int totalItems, String status, List<ConsumableItem> expectedItems, 
                        List<ConsumableItem> consumedItems) {
        this.taskId = taskId;
        this.taskName = taskName;
        this.createTime = createTime;
        this.operator = operator;
        this.totalItems = totalItems;
        this.status = status;
        this.expectedItems = expectedItems;
        this.consumedItems = consumedItems;
    }
    
    // Getter和Setter方法
    public String getTaskId() {
        return taskId;
    }
    
    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }
    
    public String getTaskName() {
        return taskName;
    }
    
    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }
    
    public Date getCreateTime() {
        return createTime;
    }
    
    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }
    
    public String getOperator() {
        return operator;
    }
    
    public void setOperator(String operator) {
        this.operator = operator;
    }
    
    public int getTotalItems() {
        return totalItems;
    }
    
    public void setTotalItems(int totalItems) {
        this.totalItems = totalItems;
    }

    public int getScannedCount() {
        return scannedCount;
    }

    public void setScannedCount(int scannedCount) {
        this.scannedCount = scannedCount;
    }

    public int getNormalCount() {
        return normalCount;
    }

    public void setNormalCount(int normalCount) {
        this.normalCount = normalCount;
    }

    public int getMissingCount() {
        return missingCount;
    }

    public void setMissingCount(int missingCount) {
        this.missingCount = missingCount;
    }

    public int getSurplusCount() {
        return surplusCount;
    }

    public void setSurplusCount(int surplusCount) {
        this.surplusCount = surplusCount;
    }

    public String getRawStatus() {
        return rawStatus;
    }

    public void setRawStatus(String rawStatus) {
        this.rawStatus = rawStatus;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public List<ConsumableItem> getExpectedItems() {
        return expectedItems;
    }
    
    public void setExpectedItems(List<ConsumableItem> expectedItems) {
        this.expectedItems = expectedItems;
    }
    
    public List<ConsumableItem> getConsumedItems() {
        return consumedItems;
    }
    
    public void setConsumedItems(List<ConsumableItem> consumedItems) {
        this.consumedItems = consumedItems;
    }
    
    public Date getSyncTime() {
        return syncTime;
    }
    
    public void setSyncTime(Date syncTime) {
        this.syncTime = syncTime;
    }
    
    // 获取格式化的时间字符串
    public String getFormattedTime() {
        if (createTime == null) {
            return "";
        }
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            .format(createTime);
    }
    
    // 获取简化的时间字符串（用于列表显示）
    public String getShortTime() {
        if (createTime == null) {
            return "";
        }
        return new java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
            .format(createTime);
    }
    
    // 获取状态对应的颜色资源ID
    public int getStatusColor() {
        // 优先按后端状态枚举着色，兼容旧中文状态
        if ("draft".equals(rawStatus)) {
            return android.R.color.holo_orange_dark;
        } else if ("running".equals(rawStatus)) {
            return android.R.color.holo_blue_dark;
        } else if ("review".equals(rawStatus)) {
            return android.R.color.holo_orange_light;
        } else if ("closed".equals(rawStatus)) {
            return android.R.color.holo_green_dark;
        } else if ("canceled".equals(rawStatus)) {
            return android.R.color.darker_gray;
        }
        // 兼容中文显示状态
        if ("未开始".equals(status)) {
            return android.R.color.holo_orange_dark;
        } else if ("执行中".equals(status)) {
            return android.R.color.holo_blue_dark;
        } else if ("待复核".equals(status)) {
            return android.R.color.holo_orange_light;
        } else if ("已完成".equals(status)) {
            return android.R.color.holo_green_dark;
        } else if ("已作废".equals(status)) {
            return android.R.color.darker_gray;
        } else {
            return android.R.color.darker_gray;
        }
    }
    
    // 检查任务是否可以进行盘点（新版：执行中状态才可操作）
    public boolean canStartInventory() {
        return "running".equals(rawStatus) || "执行中".equals(status);
    }

    // 检查任务是否可以提交结束采集
    public boolean canSubmit() {
        return "running".equals(rawStatus) || "执行中".equals(status);
    }
    
    // 获取所有物品的并集（库存明细 + 消耗明细）
    public List<ConsumableItem> getAllItems() {
        java.util.List<ConsumableItem> allItems = new java.util.ArrayList<>();
        if (expectedItems != null) {
            allItems.addAll(expectedItems);
        }
        if (consumedItems != null) {
            allItems.addAll(consumedItems);
        }
        return allItems;
    }
    
    // 获取所有EPC编码的集合
    public java.util.Set<String> getAllEpcCodes() {
        java.util.Set<String> epcCodes = new java.util.HashSet<>();
        for (ConsumableItem item : getAllItems()) {
            if (item.getBarcode() != null && !item.getBarcode().isEmpty()) {
                epcCodes.add(item.getBarcode());
            }
        }
        return epcCodes;
    }
    
    // 根据EPC编码查找对应的耗材信息
    public ConsumableItem findItemByEpc(String epcCode) {
        for (ConsumableItem item : getAllItems()) {
            if (epcCode.equals(item.getBarcode())) {
                return item;
            }
        }
        return null;
    }
    
    // 根据地点获取该地点的所有预期物品
    public List<ConsumableItem> getItemsByLocation(String location) {
        List<ConsumableItem> locationItems = new java.util.ArrayList<>();
        for (ConsumableItem item : getAllItems()) {
            // 这里假设ConsumableItem类有location字段，如果没有需要扩展
            // 暂时返回所有物品，后续可以根据需要扩展
            locationItems.add(item);
        }
        return locationItems;
    }
    
    // 获取任务的所有地点列表（地点信息完全由后端返回，此方法不再使用硬编码）
    public java.util.Set<String> getLocations() {
        java.util.Set<String> locations = new java.util.HashSet<>();
        // 地点信息应从后端任务明细中获取，此处返回空集合
        return locations;
    }
    
    @Override
    public String toString() {
        return "InventoryTask{" +
                "taskId='" + taskId + '\'' +
                ", taskName='" + taskName + '\'' +
                ", createTime=" + createTime +
                ", operator='" + operator + '\'' +
                ", totalItems=" + totalItems +
                ", scannedCount=" + scannedCount +
                ", normalCount=" + normalCount +
                ", missingCount=" + missingCount +
                ", surplusCount=" + surplusCount +
                ", rawStatus='" + rawStatus + '\'' +
                ", status='" + status + '\'' +
                ", syncTime=" + syncTime +
                '}';
    }
}
