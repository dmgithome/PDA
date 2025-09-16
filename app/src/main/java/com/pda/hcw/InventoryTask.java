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
    private int totalItems;          // 总物品数
    private String status;           // 状态：未执行(待确认)、暂存(执行中)、已完成(已结束)
    private List<ConsumableItem> expectedItems; // 库存明细
    private List<ConsumableItem> consumedItems; // 消耗明细
    private Date syncTime;          // 同步时间
    
    // 默认构造函数
    public InventoryTask() {
        this.status = "未执行";
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
        // 兼容显示：未执行=待确认(橙)、暂存=执行中(蓝)、已完成=已结束(绿)
        if ("未执行".equals(status) || "待确认".equals(status)) {
            return android.R.color.holo_orange_dark;
        } else if ("暂存".equals(status) || "执行中".equals(status)) {
            return android.R.color.holo_blue_dark;
        } else if ("已完成".equals(status) || "已结束".equals(status)) {
            return android.R.color.holo_green_dark;
        } else {
            return android.R.color.darker_gray;
        }
    }
    
    // 检查任务是否可以进行盘点
    public boolean canStartInventory() {
        return "未执行".equals(status) || "暂存".equals(status);
    }
    
    // 检查任务是否可以提交
    public boolean canSubmit() {
        return "暂存".equals(status);
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
    
    // 获取任务的所有地点列表
    public java.util.Set<String> getLocations() {
        java.util.Set<String> locations = new java.util.HashSet<>();
        // 这里暂时返回固定地点，后续可以根据实际数据扩展
        locations.add("耗材屋");
        locations.add("术间1");
        locations.add("术间2");
        locations.add("术间3");
        locations.add("术间4");
        locations.add("术间5");
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
                ", status='" + status + '\'' +
                ", syncTime=" + syncTime +
                '}';
    }
}
