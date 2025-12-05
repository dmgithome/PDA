package com.pda.hcw;

/**
 * 地点/术间数据模型（兼容 locations 与 surgery 接口）
 */
public class OperatingRoom {
    public long id;          // 本地自增ID
    public Integer serverId; // 服务端返回的ID，可为空
    public String name;      // 名称（术间 / 地点）
    public long updatedTime; // 更新时间戳
    // 以下字段仅在 locations 接口中使用，可为空
    public String type;      // 地点类型：center / room / 其它
    public boolean isActive = true; // 是否启用

    public OperatingRoom() {}

    public OperatingRoom(long id, Integer serverId, String name, long updatedTime) {
        this.id = id;
        this.serverId = serverId;
        this.name = name;
        this.updatedTime = updatedTime;
    }
}
