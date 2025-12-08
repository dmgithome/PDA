package com.pda.hcw.inventory;

/**
 * 地点卡片展示用的统计模型
 */
public class InventoryRoomStatus {
    private final int roomId;        // 地点ID（由后端返回）
    private final String displayName; // 展示名称（由后端返回）

    private final int planned;       // 计划内RFID数量
    private final int counted;       // 已盘到数量（计划内）

    public InventoryRoomStatus(int roomId, String displayName, int planned, int counted) {
        this.roomId = roomId;
        this.displayName = displayName;
        this.planned = Math.max(0, planned);
        this.counted = Math.max(0, Math.min(planned, counted));
    }

    public int getRoomId() { return roomId; }
    public String getDisplayName() { return displayName; }
    public int getPlanned() { return planned; }
    public int getCounted() { return counted; }

    public int getUncounted() { return Math.max(0, planned - counted); }

    public double getRatio() { return planned == 0 ? 0.0 : (counted * 1.0 / planned); }
}
