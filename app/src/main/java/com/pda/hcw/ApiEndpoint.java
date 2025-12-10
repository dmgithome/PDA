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
    // 库存盘点任务
    INVENTORY_TASK_LIST("/api/inventoryTask/list", "库存盘点任务列表"),
    INVENTORY_TASK_SUBMIT_BATCH("/api/inventoryTask/submitScanBatch", "提交盘点扫描批次"),
    // 地点（替代术间，仅查询）
    LOCATIONS_LIST("/api/locations/list", "地点列表"),
    DEFAULT_LOCATION("/api/locations/default", "默认地点查询"),
    // 入库验收
    GET_WAREHOUSING_ORDER("/api/stock/getWarehousingOrder", "获取待上架入库单据"),
    DEVICE_INBOUND_ORDER("/api/stock/hasOrderInbound", "提交有单入库"),

    // 预警/统计
    OVERTIME_UNRETURN_NUMBER("/api/unreturn/getOverTimeUnreturnNumberByID", "超时未归还数量"),

    // 耗材移库（PDA 动作型移库单）
    TRANSFER_PREVIEW_BY_RFID("/api/transfer/preview_by_rfid", "移库预览（RFID）"),
    TRANSFER_PREVIEW_BY_BARCODE("/api/transfer/preview_by_barcode", "移库预览（条码）"),
    TRANSFER_SUBMIT_FROM_PDA("/api/transfer/submit_from_pda", "提交PDA移库"),

    // SPD 仓库
    SPD_REPOSITORIES("/api/spd/repositories", "SPD仓库列表");

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
