package com.pda.hcw;

/**
 * 权限常量定义
 * 定义系统中所有可用的权限标识
 */
public class Permissions {
    
    // 耗材管理权限
    public static final String CONSUMABLE_CHECKOUT = "consumable_checkout";    // 耗材领用
    
    // 库存管理权限
    public static final String INVENTORY_MANAGE = "inventory_manage";          // 库存管理
    public static final String INVENTORY_VIEW = "inventory_view";              // 库存查看
    
    // 基础数据权限
    public static final String BASIC_DATA_MANAGE = "basic_data_manage";        // 基础数据管理
    
    // RFID操作权限
    public static final String RFID_OPERATION = "rfid_operation";              // RFID操作
    
    // 系统设置权限
    public static final String SYSTEM_SETTINGS = "system_settings";            // 系统设置

    /**
     * 权限显示名称映射
     */
    public static String getDisplayName(String permission) {
        switch (permission) {
            case CONSUMABLE_CHECKOUT:
                return "耗材领用";
            case INVENTORY_MANAGE:
                return "库存管理";
            case INVENTORY_VIEW:
                return "库存查看";
            case BASIC_DATA_MANAGE:
                return "基础数据管理";
            case RFID_OPERATION:
                return "RFID操作";
            case SYSTEM_SETTINGS:
                return "系统设置";
            default:
                return "未知权限";
        }
    }

    /**
     * 检查权限是否有效
     * @param permission 权限标识
     * @return 是否有效
     */
    public static boolean isValidPermission(String permission) {
        return permission != null && !permission.isEmpty() && 
               (CONSUMABLE_CHECKOUT.equals(permission) ||
                INVENTORY_MANAGE.equals(permission) ||
                INVENTORY_VIEW.equals(permission) ||
                BASIC_DATA_MANAGE.equals(permission) ||
                RFID_OPERATION.equals(permission) ||
                SYSTEM_SETTINGS.equals(permission));
    }
}