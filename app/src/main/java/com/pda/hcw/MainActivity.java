package com.pda.hcw;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.KeyEvent;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
// import android.widget.Toast;
import com.google.android.material.card.MaterialCardView;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    
    // UI组件
    private Toolbar toolbar;
    private TextView userNameText;
    private TextView userRoleText;
    private MaterialCardView userStatusCard;
    private MaterialCardView consumableCheckoutCard;
    private MaterialCardView inventoryCard;
    private MaterialCardView consumableReturnCard;
    private MaterialCardView unreturnQueryCard;
    private MaterialCardView basicDataCard;
    private com.google.android.material.card.MaterialCardView inboundAcceptanceCard;
    private com.google.android.material.card.MaterialCardView transferCard;
    private Button loginButton;
    private ImageView userAvatar;
    private TextView statusText;
    private boolean overtimeBannerClosed = false;
    private StatusBannerView statusBanner; // 内容区Banner（不改Toolbar）
    
    // 设备功能检测
    private boolean hasRFIDSupport = false;
    private boolean hasScannerSupport = true; // 默认支持扫码
    private boolean hasNFCSupport = true; // 默认支持NFC
    
    // 用户登录状态
    private boolean isLoggedIn = false;
    private String currentUsername = "";
    private SharedPreferences sharedPreferences;
    private SessionManager sessionManager;
    private SecurityManager securityManager;
    private PermissionManager permissionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate started");
        super.onCreate(savedInstanceState);
        
        try {
            setContentView(R.layout.activity_main_new);
            Log.d(TAG, "setContentView completed");
            
            // 初始化管理器
            sessionManager = SessionManager.getInstance(this);
            securityManager = SecurityManager.getInstance(this);
            permissionManager = PermissionManager.getInstance(this);
            
            // 初始化SharedPreferences
            sharedPreferences = getSharedPreferences("warehouse_prefs", Context.MODE_PRIVATE);
            
            // 初始化UI组件
            initViews();
            
            // 检测设备功能
            detectDeviceCapabilities();
            
            // 初始化会话
            initializeSession();
            
            // 加载用户状态
            loadUserStatus();
            
            // 设置按钮事件
            setupButtonListeners();
            
            // 设置会话监听器
            setupSessionListener();
            
            Log.d(TAG, "onCreate completed successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate", e);
        }
    }
    
    private void initViews() {
        try {
            // 初始化工具栏
            toolbar = findViewById(R.id.toolbar);
            setSupportActionBar(toolbar);
            
            // 初始化用户状态组件
            userStatusCard = findViewById(R.id.user_status_card);
            userNameText = findViewById(R.id.user_name_text);
            userRoleText = findViewById(R.id.user_role_text);
            userAvatar = findViewById(R.id.user_avatar);
            loginButton = findViewById(R.id.login_button);
            statusText = findViewById(R.id.status_text);
            
            // 初始化功能卡片
            consumableCheckoutCard = findViewById(R.id.consumable_checkout_card);
            inventoryCard = findViewById(R.id.inventory_card);
            unreturnQueryCard = findViewById(R.id.unreturn_query_card);
            consumableReturnCard = findViewById(R.id.consumable_return_card);
            basicDataCard = findViewById(R.id.basic_data_card);
            inboundAcceptanceCard = findViewById(R.id.inbound_acceptance_card);
            transferCard = findViewById(R.id.transfer_card);

            // 安装内容区Banner（不改Toolbar）
            installStatusBanner();
            
            Log.d(TAG, "initViews completed");
        } catch (Exception e) {
            Log.e(TAG, "Error in initViews", e);
        }
    }
    
    private void detectDeviceCapabilities() {
        try {
            // 检测Android版本，判断是否支持RFID
            int androidVersion = android.os.Build.VERSION.SDK_INT;
            
            // Android 10 (API 29) 设备支持RFID
            if (androidVersion >= 29) {
                hasRFIDSupport = true;
            }
            
            // 这里可以添加更多设备检测逻辑
            // 例如检查特定的硬件特征或系统属性
            
            // 更新按钮状态
            updateButtonStates();
            
            Log.d(TAG, "detectDeviceCapabilities completed");
        } catch (Exception e) {
            Log.e(TAG, "Error in detectDeviceCapabilities", e);
        }
    }
    
    private void updateButtonStates() {
        try {
            // 根据设备能力更新卡片状态
            if (!hasRFIDSupport) {
                inventoryCard.setEnabled(true); // 保持启用，使用时提示
                inventoryCard.setAlpha(0.7f);
            }
            
            if (!hasScannerSupport) {
                consumableCheckoutCard.setEnabled(false);
                consumableCheckoutCard.setAlpha(0.5f);
            }
            
            Log.d(TAG, "updateButtonStates completed");
        } catch (Exception e) {
            Log.e(TAG, "Error in updateButtonStates", e);
        }
    }
    
    private void initializeSession() {
        try {
            // 新架构：不再支持会话恢复，需要重新登录
            Log.d(TAG, "新架构：不支持会话恢复，需要重新登录");
            
            // 检查当前会话状态
            boolean hasActiveSession = sessionManager.isSessionActive();
            if (hasActiveSession) {
                UserInfo currentUser = sessionManager.getCurrentUser();
                if (currentUser != null) {
                    isLoggedIn = true;
                    currentUsername = currentUser.getUsername();
                    Log.d(TAG, "当前会话活跃: " + currentUsername);
                }
            }
            
            Log.d(TAG, "initializeSession completed");
        } catch (Exception e) {
            Log.e(TAG, "Error in initializeSession", e);
        }
    }
    
    private void setupSessionListener() {
        try {
            sessionManager.setSessionListener(new SessionManager.SessionListener() {
                @Override
                public void onSessionTimeout() {
                    runOnUiThread(() -> {
                        showPersistentBanner(StatusBannerView.Type.ERROR, "会话超时，请重新登录");
                        forceLogout("会话超时");
                    });
                }
                
                @Override
                public void onSessionWarning(long remainingTime) {
                    runOnUiThread(() -> {
                        String message = String.format("会话将在%d秒后超时", remainingTime / 1000);
                        showPersistentBanner(StatusBannerView.Type.WARNING, message);
                    });
                }
                
                @Override
                public void onSessionDestroyed() {
                    runOnUiThread(() -> {
                        isLoggedIn = false;
                        currentUsername = "";
                        updateUserStatusDisplay();
                    });
                }
            });
            
            Log.d(TAG, "setupSessionListener completed");
        } catch (Exception e) {
            Log.e(TAG, "Error in setupSessionListener", e);
        }
    }
    
    private void loadUserStatus() {
        try {
            // 从会话管理器获取状态
            if (sessionManager.isSessionActive()) {
                UserInfo currentUser = sessionManager.getCurrentUser();
                if (currentUser != null) {
                    isLoggedIn = true;
                    currentUsername = currentUser.getUsername();
                }
            }
            
            updateUserStatusDisplay();
            // 已登录则刷新未归还提醒；未登录隐藏banner
            if (isLoggedIn) {
                refreshOvertimeUnreturnCount();
            } else {
                hideBanner();
            }
            
            // 根据权限更新UI
            if (isLoggedIn) {
                permissionManager.updateUIBasedOnPermissions(this);
            }
            
            Log.d(TAG, "loadUserStatus completed");
        } catch (Exception e) {
            Log.e(TAG, "Error in loadUserStatus", e);
        }
    }
    
    private void updateUserStatusDisplay() {
        try {
            if (isLoggedIn && !currentUsername.isEmpty()) {
                UserInfo currentUser = sessionManager.getCurrentUser();
                if (currentUser != null) {
                    userNameText.setText(currentUser.getDisplayName());
                    userRoleText.setText(currentUser.getRole().getDisplayName());
                } else {
                    userNameText.setText(currentUsername);
                    userRoleText.setText("未知角色");
                }
                loginButton.setText(getString(R.string.main_logout));
                userAvatar.setImageResource(R.drawable.ic_user);
            } else {
                userNameText.setText(getString(R.string.main_user_not_logged_in));
                userRoleText.setText(getString(R.string.main_user_login_prompt));
                loginButton.setText(getString(R.string.main_login));
                userAvatar.setImageResource(R.drawable.ic_user);
                // 未登录时隐藏未归还提醒banner
                hideBanner();
            }
            
            Log.d(TAG, "updateUserStatusDisplay completed");
        } catch (Exception e) {
            Log.e(TAG, "Error in updateUserStatusDisplay", e);
        }
    }

    private void refreshOvertimeUnreturnCount() {
        try {
            if (!isLoggedIn) { hideBanner(); return; }
            UserInfo currentUser = sessionManager.getCurrentUser();
            if (currentUser == null) { hideBanner(); return; }
            int userId;
            try { userId = Integer.parseInt(currentUser.getUserId()); }
            catch (Exception ex) { Log.w(TAG, "无法解析用户ID为整数"); hideBanner(); return; }

            ConsumableApiService.getInstance(this).fetchOvertimeUnreturnCount(
                userId,
                new ConsumableApiService.ApiCallback<Integer>() {
                    @Override public void onSuccess(Integer result) {
                        if (!overtimeBannerClosed && result != null && result > 0) {
                            showOvertimeBannerCount(result);
                        } else {
                            hideBanner();
                        }
                    }
                    @Override public void onFailure(Exception e) {
                        if (!overtimeBannerClosed) {
                            showOvertimeBannerError(getString(R.string.overtime_unreturn_fetch_failed));
                        }
                    }
                }
            );
        } catch (Exception e) {
            Log.e(TAG, "refreshOvertimeUnreturnCount failed", e);
            if (!overtimeBannerClosed) {
                showOvertimeBannerError(getString(R.string.overtime_unreturn_fetch_failed));
            }
        }
    }
    
    private void setupButtonListeners() {
        try {
            // 登录/退出按钮
            loginButton.setOnClickListener(v -> {
                if (isLoggedIn) {
                    logout();
                } else {
                    navigateToNFCLogin();
                }
            });
            
            // 耗材领用卡片
            consumableCheckoutCard.setOnClickListener(v -> {
                if (!isLoggedIn) {
                    showLoginRequiredDialog();
                    return;
                }
                if (!hasScannerSupport) {
                    showFeatureNotSupportedDialog(getString(R.string.main_consumable_checkout));
                    return;
                }
                // 跳转到耗材领用界面
                Intent intent = new Intent(this, ConsumableCheckoutNewActivity.class);
                startActivity(intent);
            });

            // 归还卡片
            if (consumableReturnCard != null) {
                consumableReturnCard.setOnClickListener(v -> {
                    if (!isLoggedIn) { showLoginRequiredDialog(); return; }
                    if (!hasScannerSupport) { showFeatureNotSupportedDialog(getString(R.string.main_consumable_return)); return; }
                    Intent intent = new Intent(this, ConsumableCheckoutNewActivity.class);
                    intent.putExtra("operation_mode", "return");
                    startActivity(intent);
                });
            }

            // 入库验收卡片
            if (inboundAcceptanceCard != null) {
                inboundAcceptanceCard.setOnClickListener(v -> {
                    if (!isLoggedIn) { showLoginRequiredDialog(); return; }
                    // 进入入库验收界面（RFID权限仅在界面内按需提示）
                    Intent intent = new Intent(this, InboundAcceptanceActivity.class);
                    startActivity(intent);
                });
            }
            
            // 库存盘点卡片
            inventoryCard.setOnClickListener(v -> {
                if (!isLoggedIn) {
                    showLoginRequiredDialog();
                    return;
                }
                if (!hasRFIDSupport) {
                    showRFIDNotSupportedDialog();
                    return;
                }
                // 跳转到库存盘点界面
                Intent intent = new Intent(this, InventoryActivity.class);
                startActivity(intent);
            });
            
            // 基础资料卡片
            basicDataCard.setOnClickListener(v -> {
                if (!isLoggedIn) {
                    showLoginRequiredDialog();
                    return;
                }
                // 跳转到基础资料界面
                Intent intent = new Intent(this, BasicDataActivity.class);
                startActivity(intent);
            });

            // 未归还查询（开发中）
            if (unreturnQueryCard != null) {
                unreturnQueryCard.setOnClickListener(v -> {
                    if (!isLoggedIn) { showLoginRequiredDialog(); return; }
                    Intent intent = new Intent(this, OvertimeUnreturnListActivity.class);
                    startActivity(intent);
                });
            }

            // 耗材移库
            if (transferCard != null) {
                transferCard.setOnClickListener(v -> {
                    if (!isLoggedIn) { showLoginRequiredDialog(); return; }
                    Intent intent = new Intent(this, ConsumableTransferActivity.class);
                    startActivity(intent);
                });
            }
            
              
            Log.d(TAG, "setupButtonListeners completed");
        } catch (Exception e) {
            Log.e(TAG, "Error in setupButtonListeners", e);
        }
    }
    
    private void showLoginOptions() {
        try {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getString(R.string.login_title));
            builder.setItems(new String[]{
                getString(R.string.login_password_login), 
                getString(R.string.login_nfc_login)
            }, (dialog, which) -> {
                switch (which) {
                    case 0:
                        // 跳转到账号密码登录
                        Intent loginIntent = new Intent(this, LoginActivity.class);
                        startActivityForResult(loginIntent, 1001);
                        break;
                    case 1:
                        // 跳转到NFC登录
                        Intent nfcIntent = new Intent(this, LoginActivity.class);
                        nfcIntent.putExtra("auto_switch_to_nfc", true);
                        startActivityForResult(nfcIntent, 1002);
                        break;
                }
            });
            builder.show();
        } catch (Exception e) {
            Log.e(TAG, "Error in showLoginOptions", e);
        }
    }
    
    private void logout() {
        try {
            // 结束会话
            sessionManager.endSession("用户主动退出");
            
            // 清除权限缓存
            permissionManager.clearCachedPermissions();
            
            isLoggedIn = false;
            currentUsername = "";
            
            updateUserStatusDisplay();
            hideBanner();
            showQuickSuccess(getString(R.string.tip_logout_success));
            
            // 跳转到登录界面（清空任务栈，返回即退出应用）
            Intent intent = new Intent(this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            
            Log.d(TAG, "logout completed");
        } catch (Exception e) {
            Log.e(TAG, "Error in logout", e);
        }
    }
    
    private void forceLogout(String reason) {
        try {
            // 强制结束会话
            sessionManager.forceEndSession(reason);
            
            // 清除权限缓存
            permissionManager.clearCachedPermissions();
            
            isLoggedIn = false;
            currentUsername = "";
            
            updateUserStatusDisplay();
            hideBanner();
            
            // 跳转到登录界面（清空任务栈，返回即退出应用）
            Intent intent = new Intent(this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            
            Log.d(TAG, "forceLogout completed: " + reason);
        } catch (Exception e) {
            Log.e(TAG, "Error in forceLogout", e);
        }
    }
    
    private void showLoginRequiredDialog() {
        try {
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.error_login_required))
                    .setMessage(getString(R.string.error_login_required))
                    .setPositiveButton(getString(R.string.main_login), (dialog, which) -> navigateToNFCLogin())
                    .setNegativeButton(getString(R.string.common_cancel), null)
                    .show();
        } catch (Exception e) {
            Log.e(TAG, "Error in showLoginRequiredDialog", e);
        }
    }
    
    private void showFeatureNotSupportedDialog(String featureName) {
        try {
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.error_device_not_supported))
                    .setMessage(getString(R.string.error_device_not_supported) + featureName)
                    .setPositiveButton(getString(R.string.common_confirm), null)
                    .show();
        } catch (Exception e) {
            Log.e(TAG, "Error in showFeatureNotSupportedDialog", e);
        }
    }
    
    private void showRFIDNotSupportedDialog() {
        try {
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.error_device_not_supported))
                    .setMessage("当前设备不支持RFID功能\n\n建议使用支持RFID的设备进行库存盘点")
                    .setPositiveButton(getString(R.string.common_confirm), null)
                    .show();
        } catch (Exception e) {
            Log.e(TAG, "Error in showRFIDNotSupportedDialog", e);
        }
    }

    // 内容区Banner：安装到内容区顶部
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

    private void showPersistentBanner(StatusBannerView.Type type, String message) {
        try {
            if (statusBanner == null) installStatusBanner();
            if (statusBanner != null) {
                statusBanner.setSticky(true);
                statusBanner.setClosable(true);
                statusBanner.show(type, message);
            } else {
                showInlineStatus(message, android.R.color.holo_red_dark);
            }
        } catch (Throwable t) {
            Log.w(TAG, "showPersistentBanner failed", t);
        }
    }

    private void hideBanner() {
        try { if (statusBanner != null) statusBanner.hide(); } catch (Throwable ignore) {}
    }

    private void showOvertimeBannerCount(int count) {
        String msg = String.format(getString(R.string.overtime_unreturn_banner), count);
        showPersistentBanner(StatusBannerView.Type.ERROR, msg);
    }

    private void showOvertimeBannerError(String message) {
        showPersistentBanner(StatusBannerView.Type.ERROR, message);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        try {
            if (resultCode == RESULT_OK) {
                // 登录成功，重新加载用户状态
                loadUserStatus();
                showQuickSuccess(getString(R.string.tip_login_success));
                
                // 更新权限UI
                permissionManager.updateUIBasedOnPermissions(this);
                // 每次登录都提示：重置关闭状态并刷新banner
                overtimeBannerClosed = false;
                if (isLoggedIn) refreshOvertimeUnreturnCount();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onActivityResult", e);
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        if (sessionManager != null) {
            sessionManager.updateActivity();
        }
        if (isLoggedIn) {
            refreshOvertimeUnreturnCount();
        } else {
            hideBanner();
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        if (sessionManager != null) {
            sessionManager.updateActivity();
        }
    }
    
    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        if (sessionManager != null) {
            sessionManager.updateActivity();
        }
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }
    
    /**
     * 跳转到NFC登录界面
     */
    private void navigateToNFCLogin() {
        try {
            Intent intent = new Intent(this, LoginActivity.class);
            intent.putExtra("auto_switch_to_nfc", true); // 自动切换到NFC模式
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish(); // 结束当前Activity
            Log.d(TAG, "已跳转到NFC登录界面");
        } catch (Exception e) {
            Log.e(TAG, "跳转到NFC登录界面失败", e);
        }
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        
        if (id == R.id.menu_settings) {
            if (permissionManager.hasPermission(Permissions.SYSTEM_SETTINGS)) {
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
            } else {
                showInlineStatus("您没有系统设置权限", android.R.color.holo_red_dark);
                }
                return true;
            }

        if (id == R.id.menu_api_settings) {
            if (permissionManager.hasPermission(Permissions.SYSTEM_SETTINGS)) {
                Intent intent = new Intent(this, ApiSettingsActivity.class);
                startActivity(intent);
            } else {
                showInlineStatus("您没有系统设置权限", android.R.color.holo_red_dark);
            }
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void showInlineStatus(String message, int colorRes) {
        try {
            if (statusText != null) {
                statusText.setText(message);
                statusText.setTextColor(getResources().getColor(colorRes));
                statusText.setVisibility(android.view.View.VISIBLE);
            }
        } catch (Exception e) {
            Log.w(TAG, "showInlineStatus failed", e);
        }
    }
    
  
    private void showQuickSuccess(String msg) {
        try {
            android.view.View anchor = findViewById(android.R.id.content);
            if (anchor != null) {
                com.google.android.material.snackbar.Snackbar.make(anchor, msg, com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
                        .setDuration(1000)
                        .show();
            }
        } catch (Throwable ignore) {}
    }
}
