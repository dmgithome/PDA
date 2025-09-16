package com.pda.hcw;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.View;

/**
 * 权限管理器
 * 负责管理用户权限和UI权限控制
 */
public class PermissionManager {
    private static final String TAG = "PermissionManager";
    private static PermissionManager instance;
    
    private SharedPreferences prefs;
    private Context context;

    private PermissionManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences("permission_prefs", Context.MODE_PRIVATE);
    }

    public static synchronized PermissionManager getInstance(Context context) {
        if (instance == null) {
            instance = new PermissionManager(context.getApplicationContext());
        }
        return instance;
    }

    /**
     * 检查当前用户是否有指定权限
     * @param permission 权限标识
     * @return 是否有权限
     */
    public boolean hasPermission(String permission) {
        SessionManager sessionManager = SessionManager.getInstance(context);
        UserInfo currentUser = sessionManager.getCurrentUser();
        
        if (currentUser == null) {
            Log.w(TAG, "没有当前用户，拒绝权限");
            return false;
        }
        
        // 管理员拥有所有权限
        if (currentUser.isAdmin()) {
            Log.d(TAG, "当前用户为管理员，拥有权限: " + permission);
            return true;
        }
        
        // 普通用户根据角色权限检查
        boolean hasPermission = currentUser.hasPermission(permission);
        Log.d(TAG, "当前用户" + (hasPermission ? "有" : "没有") + "权限: " + permission);
        
        return hasPermission;
    }

    /**
     * 检查当前用户是否有任意一个权限
     * @param permissions 权限列表
     * @return 是否有任意一个权限
     */
    public boolean hasAnyPermission(String... permissions) {
        for (String permission : permissions) {
            if (hasPermission(permission)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查当前用户是否有所有权限
     * @param permissions 权限列表
     * @return 是否有所有权限
     */
    public boolean hasAllPermissions(String... permissions) {
        for (String permission : permissions) {
            if (!hasPermission(permission)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 获取当前用户角色
     * @return 用户角色
     */
    public UserRole getCurrentUserRole() {
        SessionManager sessionManager = SessionManager.getInstance(context);
        UserInfo currentUser = sessionManager.getCurrentUser();
        
        if (currentUser == null) {
            return UserRole.USER; // 默认普通用户角色
        }
        
        return currentUser.getRole();
    }

    /**
     * 获取当前用户角色显示名称
     * @return 角色显示名称
     */
    public String getCurrentUserRoleDisplayName() {
        UserRole role = getCurrentUserRole();
        return role != null ? role.getDisplayName() : "未知角色";
    }

    /**
     * 更新Activity的UI基于权限
     * @param activity Activity
     */
    public void updateUIBasedOnPermissions(Activity activity) {
        SessionManager sessionManager = SessionManager.getInstance(context);
        UserInfo currentUser = sessionManager.getCurrentUser();
        
        if (currentUser == null) {
            Log.w(TAG, "没有当前用户，不更新UI权限");
            return;
        }
        
        Log.d(TAG, "更新UI权限");
        
        // 管理员拥有所有权限
        if (currentUser.isAdmin()) {
            Log.d(TAG, "管理员：显示所有功能");
            // 显示所有功能卡片
            setViewVisibility(activity, R.id.consumable_checkout_card, true);
            setViewVisibility(activity, R.id.inventory_card, true);
            setViewVisibility(activity, R.id.basic_data_card, true);
            return;
        }
        
        // 普通用户权限
        Log.d(TAG, "普通用户：显示基础功能");
        // 耗材领用权限
        setViewVisibility(activity, R.id.consumable_checkout_card, true);
        
        // 库存查看权限
        setViewVisibility(activity, R.id.inventory_card, true);
        
        // 基础数据权限 - 普通用户无权访问
        setViewVisibility(activity, R.id.basic_data_card, false);
    }

    /**
     * 设置视图可见性
     * @param activity Activity
     * @param viewId 视图ID
     * @param visible 是否可见
     */
    private void setViewVisibility(Activity activity, int viewId, boolean visible) {
        View view = activity.findViewById(viewId);
        if (view != null) {
            view.setVisibility(visible ? View.VISIBLE : View.GONE);
            Log.d(TAG, "设置视图 " + viewId + " 可见性: " + visible);
        } else {
            Log.w(TAG, "找不到视图: " + viewId);
        }
    }

    /**
     * 检查权限并处理拒绝情况
     * @param permission 权限标识
     * @param errorMessage 错误消息
     * @return 是否有权限
     */
    public boolean checkPermissionWithMessage(String permission, String errorMessage) {
        if (!hasPermission(permission)) {
            Log.w(TAG, "权限检查失败: " + permission);
            // 可以在这里显示Toast或其他提示
            return false;
        }
        return true;
    }

    /**
     * 获取用户权限列表
     * @return 权限列表
     */
    public String[] getCurrentUserPermissions() {
        UserRole role = getCurrentUserRole();
        if (role == null) {
            return new String[0];
        }
        
        return role.getPermissions().toArray(new String[0]);
    }

    /**
     * 检查是否为管理员
     * @return 是否为管理员
     */
    public boolean isAdmin() {
        SessionManager sessionManager = SessionManager.getInstance(context);
        UserInfo currentUser = sessionManager.getCurrentUser();
        
        if (currentUser == null) {
            return false;
        }
        
        return currentUser.isAdmin();
    }

    /**
     * 检查是否为普通用户
     * @return 是否为普通用户
     */
    public boolean isRegularUser() {
        SessionManager sessionManager = SessionManager.getInstance(context);
        UserInfo currentUser = sessionManager.getCurrentUser();
        
        if (currentUser == null) {
            return false;
        }
        
        return currentUser.isRegularUser();
    }

    /**
     * 获取权限描述信息
     * @param permission 权限标识
     * @return 权限描述
     */
    public String getPermissionDescription(String permission) {
        switch (permission) {
            case Permissions.CONSUMABLE_CHECKOUT:
                return "允许进行耗材领用操作";
            case Permissions.INVENTORY_MANAGE:
                return "允许进行库存管理操作";
            case Permissions.INVENTORY_VIEW:
                return "允许查看库存信息";
            case Permissions.BASIC_DATA_MANAGE:
                return "允许管理基础数据";
            case Permissions.RFID_OPERATION:
                return "允许使用RFID功能";
            case Permissions.SYSTEM_SETTINGS:
                return "允许访问系统设置";
            default:
                return "未知权限";
        }
    }

    /**
     * 验证用户是否有权限执行特定操作
     * @param operation 操作类型
     * @return 是否有权限
     */
    public boolean canPerformOperation(String operation) {
        switch (operation) {
            case "checkout":
                return hasPermission(Permissions.CONSUMABLE_CHECKOUT);
            case "inventory_manage":
                return hasPermission(Permissions.INVENTORY_MANAGE);
            case "inventory_view":
                return hasPermission(Permissions.INVENTORY_VIEW);
            case "basic_data":
                return hasPermission(Permissions.BASIC_DATA_MANAGE);
            case "rfid":
                return hasPermission(Permissions.RFID_OPERATION);
            case "settings":
                return hasPermission(Permissions.SYSTEM_SETTINGS);
            default:
                return false;
        }
    }

    /**
     * 缓存用户权限信息
     * @param userInfo 用户信息
     */
    public void cacheUserPermissions(UserInfo userInfo) {
        if (userInfo != null && userInfo.isValid()) {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("cached_user_id", userInfo.getUserId());
            editor.putString("cached_username", userInfo.getUsername());
            editor.putString("cached_user_role", userInfo.getRole().name());
            editor.putLong("cache_time", System.currentTimeMillis());
            editor.apply();
            
            Log.d(TAG, "缓存用户权限已更新: " + userInfo.getRole().getDisplayName());
        }
    }

    /**
     * 清除缓存的权限信息
     */
    public void clearCachedPermissions() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove("cached_user_id");
        editor.remove("cached_username");
        editor.remove("cached_user_role");
        editor.remove("cache_time");
        editor.apply();
        
        Log.d(TAG, "清除缓存的权限信息");
    }
}
