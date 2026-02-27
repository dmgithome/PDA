package com.pda.hcw.inventory;

import java.io.Serializable;

/**
 * 盘点计划条目（从业务系统获取的库存快照项）
 * 仅要求 rfid 与 roomId；其他字段占位，后续接口补齐时扩展。
 */
public class InventoryPlanItem implements Serializable {
    private final String rfid;
    private final int roomId; // 地点ID（由后端返回）
    private final String roomName; // 地点名称（由后端返回）

    // 可选信息（占位）
    private final String code;          // 子码
    private final String name;          // 耗材名称
    private final String spec;          // 规格型号
    private final String manufacturer;  // 生产企业
    private final String lastUser;      // 最近取用人
    private final Long lastUsedAt;      // 最近取用时间（epoch ms），可空

    public InventoryPlanItem(String rfid, int roomId) {
        this(rfid, roomId, null, null, null, null, null, null, null);
    }

    /**
     * 兼容旧构造：不传子码时默认 null。
     */
    public InventoryPlanItem(
            String rfid,
            int roomId,
            String roomName,
            String name,
            String spec,
            String manufacturer,
            String lastUser,
            Long lastUsedAt
    ) {
        this(rfid, roomId, roomName, null, name, spec, manufacturer, lastUser, lastUsedAt);
    }

    public InventoryPlanItem(
            String rfid,
            int roomId,
            String roomName,
            String code,
            String name,
            String spec,
            String manufacturer,
            String lastUser,
            Long lastUsedAt
    ) {
        this.rfid = rfid == null ? "" : rfid.trim();
        this.roomId = roomId;
        this.roomName = roomName;
        this.code = code;
        this.name = name;
        this.spec = spec;
        this.manufacturer = manufacturer;
        this.lastUser = lastUser;
        this.lastUsedAt = lastUsedAt;
    }

    public String getRfid() { return rfid; }
    public int getRoomId() { return roomId; }
    public String getRoomName() { return roomName; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getSpec() { return spec; }
    public String getManufacturer() { return manufacturer; }
    public String getLastUser() { return lastUser; }
    public Long getLastUsedAt() { return lastUsedAt; }
}
