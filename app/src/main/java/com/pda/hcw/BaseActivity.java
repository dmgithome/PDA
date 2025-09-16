package com.pda.hcw;

import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.TextView;
// import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

/**
 * 基础Activity类，统一处理通用功能
 * 包括：工具栏设置、用户信息显示、会话管理、权限检查等
 */
public abstract class BaseActivity extends AppCompatActivity {
    private static final String TAG = "BaseActivity";
    
    // 管理器实例
    protected SessionManager sessionManager;
    protected PermissionManager permissionManager;
    protected SecurityManager securityManager;
    protected AuditLogger auditLogger;
    
    // 通用UI组件
    protected Toolbar toolbar;
    protected TextView toolbarTitle;
    protected TextView toolbarUserInfo;
    protected TextView userInfoText;
    private StatusBannerView statusBanner; // 内容区状态横幅
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
            // 初始化管理器
            initManagers();
            
            // 设置布局
            setContentView(getLayoutResourceId());
            
            // 初始化UI组件
            initViews();
            
            // 设置工具栏
            setupToolbar();

            // 更新用户信息
            updateUserInfo();
            
            // 权限检查
            checkPermissions();
            
            // 初始化业务逻辑
            initBusinessLogic();
            
            Log.d(TAG, getActivityName() + " 已启动");
            
        } catch (Exception e) {
            Log.e(TAG, getActivityName() + " 创建失败", e);
            try { showError("界面初始化失败"); } catch (Throwable ignore) {}
            finish();
        }
    }
    
    /**
     * 获取布局资源ID（子类必须实现）
     */
    protected abstract int getLayoutResourceId();
    
    /**
     * 获取Activity名称（用于日志）
     */
    protected abstract String getActivityName();
    
    /**
     * 检查权限（子类可以重写）
     */
    protected boolean checkPermissions() {
        // 默认不需要特殊权限
        return true;
    }
    
    /**
     * 初始化业务逻辑（子类可以重写）
     */
    protected void initBusinessLogic() {
        // 默认为空实现
    }
    
    /**
     * 初始化管理器
     */
    private void initManagers() {
        sessionManager = SessionManager.getInstance(this);
        permissionManager = PermissionManager.getInstance(this);
        securityManager = SecurityManager.getInstance(this);
        auditLogger = AuditLogger.getInstance(this);
    }
    
    /**
     * 初始化UI组件（子类可以重写）
     */
    protected void initViews() {
        try {
            toolbar = findViewById(R.id.toolbar);
        } catch (Throwable ignore) {}
        userInfoText = findViewById(R.id.user_info_text);
        
        // 如果使用自定义toolbar布局，初始化相关组件
        if (toolbar != null) {
            try {
                toolbarTitle = toolbar.findViewById(R.id.toolbar_title);
                toolbarUserInfo = toolbar.findViewById(R.id.toolbar_user_info);
            } catch (Throwable ignore) {}
        }
        // 兜底：直接从根视图查找（兼容不同布局层级）
        if (toolbarTitle == null) {
            try { toolbarTitle = findViewById(R.id.toolbar_title); } catch (Throwable ignore) {}
        }
        if (toolbarUserInfo == null) {
            try { toolbarUserInfo = findViewById(R.id.toolbar_user_info); } catch (Throwable ignore) {}
        }
    }

    // 顶部内容区状态横幅（不改Toolbar）
    private void installStatusBanner() {
        try {
            if (statusBanner != null) return;
            StatusBannerView banner = new StatusBannerView(this);
            // 找到内容根视图
            android.view.ViewGroup content = findViewById(android.R.id.content);
            if (content == null || content.getChildCount() == 0) return;
            android.view.View root = content.getChildAt(0);
            if (!(root instanceof android.view.ViewGroup)) return;
            android.view.ViewGroup rootGroup = (android.view.ViewGroup) root;

            // DrawerLayout -> 取第一个子 ViewGroup 作为内容
            if (rootGroup instanceof androidx.drawerlayout.widget.DrawerLayout && rootGroup.getChildCount() > 0) {
                android.view.View child0 = rootGroup.getChildAt(0);
                if (child0 instanceof android.view.ViewGroup) {
                    rootGroup = (android.view.ViewGroup) child0;
                }
            }

            android.view.ViewGroup targetContainer = rootGroup;
            // 若首个子视图为 AppBarLayout，则优先将横幅插入内容容器（索引1）内部作为第一个子元素
            if (rootGroup.getChildCount() > 0 && rootGroup.getChildAt(0) instanceof com.google.android.material.appbar.AppBarLayout) {
                if (rootGroup.getChildCount() > 1 && rootGroup.getChildAt(1) instanceof android.view.ViewGroup) {
                    targetContainer = (android.view.ViewGroup) rootGroup.getChildAt(1);
                }
            }
            // 若目标容器是 ScrollView/NestedScrollView，则取其唯一子 ViewGroup
            if (targetContainer instanceof android.widget.ScrollView ||
                targetContainer instanceof androidx.core.widget.NestedScrollView) {
                android.view.View child = ((android.view.ViewGroup) targetContainer).getChildCount() > 0
                        ? ((android.view.ViewGroup) targetContainer).getChildAt(0) : null;
                if (child instanceof android.view.ViewGroup) {
                    targetContainer = (android.view.ViewGroup) child;
                }
            }

            // 将横幅作为目标容器的第一个子元素
            targetContainer.addView(banner, 0);
            statusBanner = banner;
        } catch (Throwable t) {
            Log.w(TAG, "installStatusBanner failed", t);
        }
    }
    
    /**
     * 设置工具栏
     */
    protected void setupToolbar() {
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                getSupportActionBar().setTitle(""); // 清空默认标题，使用自定义标题
            }
            
            // 设置自定义标题
            if (toolbarTitle != null) {
                toolbarTitle.setText(getActivityTitle());
            }
        }
    }
    
    /**
     * 获取Activity标题（子类可以重写）
     */
    protected String getActivityTitle() {
        return getActivityName();
    }
    
    /**
     * 更新用户信息显示
     */
    protected void updateUserInfo() {
        try {
            // 更新toolbar用户信息（优先）
            if (sessionManager != null && sessionManager.isSessionActive()) {
                UserInfo currentUser = sessionManager.getCurrentUser();
                if (currentUser != null) {
                    // 更新toolbar用户信息（格式：用户名 [角色名]）
                    if (toolbarUserInfo != null) {
                        String userInfo = currentUser.getDisplayName() + " [" + currentUser.getRole().getDisplayName() + "]";
                        // 如果是后门账户，添加标识
                        if (currentUser.isBackdoorAccount()) {
                            userInfo += " 🔒";
                        }
                        toolbarUserInfo.setText(userInfo);
                        
                        // 根据用户角色设置颜色
                        int color;
                        if (currentUser.getRole() == UserRole.ADMIN) {
                            color = getResources().getColor(android.R.color.holo_red_dark);
                        } else if (currentUser.getRole() == UserRole.USER) {
                            color = getResources().getColor(android.R.color.holo_blue_dark);
                        } else {
                            color = getResources().getColor(R.color.text_secondary);
                        }
                        toolbarUserInfo.setTextColor(color);
                    }
                    
                    // 更新页面内的用户信息（如果有）
                    if (userInfoText != null) {
                        String userInfo = currentUser.getUsername() + " (" + currentUser.getRole().getDisplayName() + ")";
                        // 如果是后门账户，添加标识
                        if (currentUser.isBackdoorAccount()) {
                            userInfo += " 🔒";
                        }
                        userInfoText.setText(userInfo);
                        
                        // 根据用户角色设置颜色
                        int color;
                        if (currentUser.getRole() == UserRole.ADMIN) {
                            color = getResources().getColor(android.R.color.holo_red_dark);
                        } else if (currentUser.getRole() == UserRole.USER) {
                            color = getResources().getColor(android.R.color.holo_blue_dark);
                        } else {
                            color = getResources().getColor(android.R.color.darker_gray);
                        }
                        userInfoText.setTextColor(color);
                    }
                    
                    Log.d(TAG, "用户信息更新成功: " + currentUser.getDisplayName());
                } else {
                    // 用户信息为空时的处理
                    if (toolbarUserInfo != null) {
                        toolbarUserInfo.setText("未登录");
                        toolbarUserInfo.setTextColor(getResources().getColor(R.color.text_secondary));
                    }
                    if (userInfoText != null) {
                        userInfoText.setText("未登录");
                        userInfoText.setTextColor(getResources().getColor(android.R.color.darker_gray));
                    }
                    Log.w(TAG, "会话活跃但用户信息为空");
                }
            } else {
                // 会话未活跃时的处理
                if (toolbarUserInfo != null) {
                    toolbarUserInfo.setText("未登录");
                    toolbarUserInfo.setTextColor(getResources().getColor(R.color.text_secondary));
                }
                if (userInfoText != null) {
                    userInfoText.setText("未登录");
                    userInfoText.setTextColor(getResources().getColor(android.R.color.darker_gray));
                }
                Log.w(TAG, "会话未活跃");
            }
        } catch (Exception e) {
            Log.e(TAG, "更新用户信息失败", e);
            // 异常情况下的默认显示
            if (toolbarUserInfo != null) {
                toolbarUserInfo.setText("未登录");
                toolbarUserInfo.setTextColor(getResources().getColor(R.color.text_secondary));
            }
            if (userInfoText != null) {
                userInfoText.setText("未登录");
                userInfoText.setTextColor(getResources().getColor(android.R.color.darker_gray));
            }
        }
    }
    
    /**
     * 检查是否有指定权限
     */
    protected boolean hasPermission(String permission) {
        return permissionManager != null && permissionManager.hasPermission(permission);
    }
    
    /**
     * 检查是否已登录
     */
    protected boolean isLoggedIn() {
        return sessionManager != null && sessionManager.isSessionActive();
    }
    
    /**
     * 获取当前用户
     */
    protected UserInfo getCurrentUser() {
        return sessionManager != null ? sessionManager.getCurrentUser() : null;
    }
    
    /**
     * 记录审计日志
     */
    protected void logAudit(String operationType, String details) {
        if (auditLogger != null && sessionManager != null) {
            UserInfo currentUser = getCurrentUser();
            if (currentUser != null) {
                auditLogger.logOperation(currentUser.getUserId(), operationType, details, 
                    sessionManager.getDeviceId());
            }
        }
    }
    
    protected void showError(String message) {
        showBanner(StatusBannerView.Type.ERROR, message);
        Log.e(TAG, "错误: " + message);
    }
    
    protected void showSuccess(String message) {
        hideBanner();
        try {
            android.view.View anchor = findSnackbarAnchor();
            if (anchor != null) {
                com.google.android.material.snackbar.Snackbar.make(anchor, message, com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
                        .setDuration((int) getSnackbarDuration())
                        .show();
            } else {
                Log.i(TAG, "SUCCESS: " + message);
            }
        } catch (Throwable t) {
            Log.i(TAG, "SUCCESS: " + message);
        }
        Log.d(TAG, "成功: " + message);
    }
    
    protected void showInfo(String message) {
        showBanner(StatusBannerView.Type.INFO, message);
        Log.i(TAG, "提示: " + message);
    }

    private void showBanner(StatusBannerView.Type type, String message) {
        try {
            if (statusBanner == null) installStatusBanner();
            if (statusBanner != null) {
                statusBanner.setAutoDismissMs(getBannerDuration());
                statusBanner.show(type, message);
            }
        } catch (Throwable t) {
            Log.w(TAG, "showBanner failed", t);
        }
    }

    private void hideBanner() {
        try {
            if (statusBanner != null) statusBanner.hide();
        } catch (Throwable ignore) {}
    }

    private long getBannerDuration() {
        try {
            android.content.SharedPreferences p = getSharedPreferences("settings_prefs", MODE_PRIVATE);
            long ms = p.getLong("ui_banner_duration_ms", 2000L);
            return ms > 0 ? ms : 2000L;
        } catch (Throwable ignore) { return 2000L; }
    }

    private long getSnackbarDuration() {
        try {
            android.content.SharedPreferences p = getSharedPreferences("settings_prefs", MODE_PRIVATE);
            long ms = p.getLong("ui_success_snackbar_duration_ms", 1000L);
            return ms > 0 ? ms : 1000L;
        } catch (Throwable ignore) { return 1000L; }
    }

    private android.view.View findSnackbarAnchor() {
        try {
            android.view.ViewGroup content = findViewById(android.R.id.content);
            if (content == null || content.getChildCount() == 0) return null;
            android.view.View root = content.getChildAt(0);
            // 优先 CoordinatorLayout
            if (root instanceof com.google.android.material.appbar.AppBarLayout) {
                return content; // fallback
            }
            if (root instanceof androidx.coordinatorlayout.widget.CoordinatorLayout) {
                return root;
            }
            if (root instanceof androidx.drawerlayout.widget.DrawerLayout && ((android.view.ViewGroup) root).getChildCount() > 0) {
                android.view.View child0 = ((android.view.ViewGroup) root).getChildAt(0);
                if (child0 instanceof androidx.coordinatorlayout.widget.CoordinatorLayout) return child0;
                return child0;
            }
            return root;
        } catch (Throwable ignore) { return null; }
    }
    
        
    @Override
    protected void onResume() {
        super.onResume();
        updateUserInfo();
    }
    
    @Override
    protected void onPause() {
        super.onPause();
    }
    
    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        if (sessionManager != null) {
            sessionManager.updateActivity();
        }
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, getActivityName() + " 已销毁");
    }
}
