package com.pda.hcw;

/**
 * 地点汇总统计数据模型
 */
public class LocationSummary {
    private String location;         // 地点名称
    private int totalExpected;       // 预期总数
    private int scannedCount;       // 已扫描数
    private Integer roomId;         // 术间ID（可为空，0=耗材屋）
    
    // 默认构造函数
    public LocationSummary() {
        this.totalExpected = 0;
        this.scannedCount = 0;
    }
    
    // 完整构造函数
    public LocationSummary(String location, int totalExpected, int scannedCount) {
        this.location = location;
        this.totalExpected = totalExpected;
        this.scannedCount = scannedCount;
        this.roomId = null;
    }
    
    // Getter和Setter方法
    public String getLocation() {
        return location;
    }
    
    public void setLocation(String location) {
        this.location = location;
    }
    
    public int getTotalExpected() {
        return totalExpected;
    }
    
    public void setTotalExpected(int totalExpected) {
        this.totalExpected = totalExpected;
    }
    
    public int getScannedCount() {
        return scannedCount;
    }
    
    public void setScannedCount(int scannedCount) {
        this.scannedCount = scannedCount;
    }

    public Integer getRoomId() { return roomId; }
    public void setRoomId(Integer roomId) { this.roomId = roomId; }
    
    // 获取未扫描数量
    public int getUnscannedCount() {
        return totalExpected - scannedCount;
    }
    
    // 兼容方法：获取总项目数（与getTotalExpected相同）
    public int getTotalItems() {
        return totalExpected;
    }
    
    // 兼容方法：获取已扫描项目数（与getScannedCount相同）
    public int getScannedItems() {
        return scannedCount;
    }
    
    // 兼容方法：获取完成率（0.0-1.0）
    public double getCompletionRate() {
        if (totalExpected == 0) {
            return 0.0;
        }
        return (double) scannedCount / totalExpected;
    }
    
    // 获取完成百分比
    public double getCompletionPercentage() {
        if (totalExpected == 0) {
            return 0.0;
        }
        return (double) scannedCount / totalExpected * 100;
    }
    
    // 获取格式化的百分比字符串
    public String getFormattedPercentage() {
        return String.format("%.1f%%", getCompletionPercentage());
    }
    
    // 获取进度描述文本
    public String getProgressText() {
        return scannedCount + "/" + totalExpected;
    }
    
    // 获取进度描述文本（带百分比）
    public String getProgressTextWithPercentage() {
        return scannedCount + "/" + totalExpected + " (" + getFormattedPercentage() + ")";
    }
    
    // 检查是否已完成扫描
    public boolean isCompleted() {
        return scannedCount >= totalExpected;
    }
    
    // 检查是否已开始扫描
    public boolean isStarted() {
        return scannedCount > 0;
    }
    
    // 获取状态描述
    public String getStatusDescription() {
        if (totalExpected == 0) {
            return "无数据";
        } else if (isCompleted()) {
            return "已完成";
        } else if (isStarted()) {
            return "进行中";
        } else {
            return "未开始";
        }
    }
    
    // 获取状态对应的颜色资源ID
    public int getStatusColor() {
        if (totalExpected == 0) {
            return android.R.color.darker_gray;
        } else if (isCompleted()) {
            return android.R.color.holo_green_dark;
        } else if (isStarted()) {
            return android.R.color.holo_blue_dark;
        } else {
            return android.R.color.holo_orange_dark;
        }
    }
    
    // 增加已扫描数量
    public void incrementScannedCount() {
        this.scannedCount++;
    }
    
    // 减少已扫描数量
    public void decrementScannedCount() {
        if (this.scannedCount > 0) {
            this.scannedCount--;
        }
    }
    
    // 设置已扫描数量（带边界检查）
    public void setScannedCountWithBounds(int scannedCount) {
        this.scannedCount = Math.max(0, Math.min(totalExpected, scannedCount));
    }
    
    // 重置扫描数量
    public void resetScannedCount() {
        this.scannedCount = 0;
    }
    
    // 更新汇总数据
    public void updateData(int totalExpected, int scannedCount) {
        this.totalExpected = totalExpected;
        this.scannedCount = Math.max(0, Math.min(totalExpected, scannedCount));
    }
    
    // 获取进度条颜色（根据完成度）
    public int getProgressBarColor() {
        double percentage = getCompletionPercentage();
        if (percentage >= 100) {
            return android.R.color.holo_green_dark;
        } else if (percentage >= 80) {
            return android.R.color.holo_blue_dark;
        } else if (percentage >= 50) {
            return android.R.color.holo_orange_dark;
        } else {
            return android.R.color.holo_red_dark;
        }
    }
    
    // 检查是否需要关注（完成度低于50%）
    public boolean needsAttention() {
        return totalExpected > 0 && getCompletionPercentage() < 50;
    }
    
    // 获取优先级描述
    public String getPriorityDescription() {
        if (totalExpected == 0) {
            return "无优先级";
        } else if (needsAttention()) {
            return "高优先级";
        } else if (getCompletionPercentage() < 80) {
            return "中优先级";
        } else {
            return "低优先级";
        }
    }
    
    // 创建副本
    public LocationSummary copy() {
        return new LocationSummary(this.location, this.totalExpected, this.scannedCount);
    }
    
    // 合并另一个汇总数据
    public void merge(LocationSummary other) {
        if (other == null) return;
        
        this.totalExpected += other.totalExpected;
        this.scannedCount += other.scannedCount;
    }
    
    @Override
    public String toString() {
        return "LocationSummary{" +
                "location='" + location + '\'' +
                ", totalExpected=" + totalExpected +
                ", scannedCount=" + scannedCount +
                ", percentage=" + getFormattedPercentage() +
                '}';
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        LocationSummary that = (LocationSummary) obj;
        return location != null && location.equals(that.location);
    }
    
    @Override
    public int hashCode() {
        return location != null ? location.hashCode() : 0;
    }
}
