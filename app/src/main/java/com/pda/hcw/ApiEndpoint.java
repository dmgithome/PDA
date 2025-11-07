package com.pda.hcw;

/**
 * 可配置的业务接口枚举及默认路径。
 * 默认值来自 Apifox《新高值柜硬件接口》OpenAPI。
 */
public enum ApiEndpoint {
    // 认证
    LOGIN("/api/v1/amis/login", "账号登录"),
    NFC_LOGIN("/api/nfc/loginByNFC", "NFC登录"),

    // 耗材/库存
    CONSUME_BY_BARCODE("/api/stock/getConsumeOrReturnConsumablesByBarcode", "扫码查询耗材"),
    GET_ALL_CONSUMABLES("/api/stock/getAllConsumables", "库存拉取"),
    CREATE_TAKE_RETURN_LOG("/api/stock/createTakeAndReturnLog", "创建取用/归还记录"),
    // 入库验收
    GET_WAREHOUSING_ORDER("/api/stock/getWarehousingOrder", "获取待上架入库单据"),
    DEVICE_INBOUND_ORDER("/api/stock/hasOrderInbound", "提交有单入库"),

    // 手术/术间
    GET_OPERATING_ROOM_LIST("/api/surgery/getOperatingRoomList", "获取术间列表"),
    ADD_OPERATING_ROOM("/api/surgery/addOperatingRoom", "新增术间"),
    UPDATE_OPERATING_ROOM("/api/surgery/updateOperatingRoom", "更新术间"),
    DELETE_OPERATING_ROOM("/api/surgery/deleteOperatingRoom", "删除术间(软删除)"),

    // 预警/统计
    OVERTIME_UNRETURN_NUMBER("/api/unreturn/getOverTimeUnreturnNumberByID", "超时未归还数量");

    private final String defaultPath;
    private final String displayName;

    ApiEndpoint(String defaultPath, String displayName) {
        this.defaultPath = defaultPath;
        this.displayName = displayName;
    }

    public String getDefaultPath() {
        return defaultPath;
    }

    public String getDisplayName() {
        return displayName;
    }
}
