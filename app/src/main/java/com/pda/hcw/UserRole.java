package com.pda.hcw;

import java.util.Arrays;
import java.util.List;

/**
 * 用户角色枚举
 * 定义系统中支持的角色及其对应的权限
 */
public enum UserRole {
    ADMIN("管理员", Arrays.asList(
        Permissions.CONSUMABLE_CHECKOUT,  // 耗材领用
        Permissions.INVENTORY_MANAGE,    // 库存管理
        Permissions.BASIC_DATA_MANAGE,   // 基础数据管理
        Permissions.RFID_OPERATION,      // RFID操作
        Permissions.SYSTEM_SETTINGS,     // 系统设置
        Permissions.INVENTORY_VIEW       // 库存查看
    )),
    
    USER("普通用户", Arrays.asList(
        Permissions.CONSUMABLE_CHECKOUT,  // 耗材领用
        Permissions.INVENTORY_VIEW,       // 库存查看
        Permissions.RFID_OPERATION        // RFID操作
    ));

    private String displayName;
    private List<String> permissions;

    UserRole(String displayName, List<String> permissions) {
        this.displayName = displayName;
        this.permissions = permissions;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<String> getPermissions() {
        return permissions;
    }

    /**
     * 检查是否有指定权限
     * @param permission 权限标识
     * @return 是否有权限
     */
    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }

    /**
     * 根据用户名获取角色（简化版）
     * @param username 用户名
     * @return 角色枚举
     */
    public static UserRole fromUsername(String username) {
        return "管理员".equals(username) ? ADMIN : USER;
    }

    /**
     * 获取所有可用角色
     * @return 角色数组
     */
    public static UserRole[] getAllRoles() {
        return UserRole.values();
    }

    /**
     * 检查是否为管理员角色
     * @return 是否为管理员
     */
    public boolean isAdmin() {
        return this == ADMIN;
    }

    /**
     * 检查是否为普通用户角色
     * @return 是否为普通用户
     */
    public boolean isRegularUser() {
        return this == USER;
    }
}