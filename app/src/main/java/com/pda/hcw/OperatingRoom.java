package com.pda.hcw;

/**
 * 术间数据模型
 */
public class OperatingRoom {
    public long id;          // 本地自增ID
    public Integer serverId; // 服务端返回的ID，可为空
    public String name;      // 术间名称
    public long updatedTime; // 更新时间戳

    public OperatingRoom() {}

    public OperatingRoom(long id, Integer serverId, String name, long updatedTime) {
        this.id = id;
        this.serverId = serverId;
        this.name = name;
        this.updatedTime = updatedTime;
    }
}

