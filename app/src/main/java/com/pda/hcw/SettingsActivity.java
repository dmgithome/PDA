package com.pda.hcw;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
// import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;

/**
 * 系统设置界面
 * 用于配置会话超时时间和其他设置
 */
public class SettingsActivity extends BaseActivity {
    private static final String TAG = "SettingsActivity";
    
    // UI组件
    private Spinner sessionTimeoutSpinner;
    private Button saveButton;
    private Button resetButton;
    private MaterialCardView sessionSettingsCard;
    private MaterialCardView barcodeSettingsCard;
    private MaterialCardView rfidSettingsCard;
    private MaterialCardView networkSettingsCard;
    private MaterialCardView maintenanceSettingsCard;
    private Button clearRecordsButton;
    private TextInputEditText barcodePrefixInput;
    private TextInputEditText barcodeLengthInput;
    private TextInputEditText cabinetIdInput;
    private TextInputEditText sdcodeInput;
    private TextInputEditText apiUrlInput;
    private TextView networkStatusText;
    // RFID UI
    private Spinner rfidPowerSpinner;
    private Spinner rfidRegionSpinner;
    private Spinner rfidSessionSpinner;
    private Spinner rfidTargetSpinner;
    private Spinner rfidQSpinner;
    private SwitchMaterial rfidFastIdSwitch;
    private SwitchMaterial rfidBeepSwitch;
    private SwitchMaterial rfidMultiSwitch;
    private Spinner rfidBeepModeSpinner;
    private TextInputEditText uhfTriggerDelayInput;
    // 会话豁免 UI
    private SwitchMaterial idleExemptSwitch;
    private android.widget.CheckBox cbExemptInventory;
    private android.widget.CheckBox cbExemptCheckout;
    private android.widget.CheckBox cbExemptInbound;
    private android.widget.CheckBox cbExemptHistory;
    
    // 管理器
    private SharedPreferences prefs;
    
    // 网络配置
    private NetworkConfig networkConfig;
    private com.pda.hcw.rfid.SharedUtil rfidShared;
    
    // 用户信息相关
    private TextView userInfoText;
    
    @Override
    protected int getLayoutResourceId() {
        return R.layout.activity_settings;
    }
    
    @Override
    protected String getActivityName() {
        return "设置";
    }
    
    @Override
    protected String getActivityTitle() {
        return "设置";
    }
    
    // 超时时间选项（分钟）
    private static final int[] TIMEOUT_OPTIONS = {5, 10, 15, 30, 60, -1}; // -1表示永不超时
    private static final String[] TIMEOUT_DISPLAYS = {"5分钟", "10分钟", "15分钟", "30分钟", "1小时", "永不超时"};
    // RFID arrays
    private static final int[] POWER_OPTIONS = buildRange(5, 30); // 5..30 dBm
    private static final String[] POWER_DISPLAYS = buildPowerDisplays(POWER_OPTIONS);
    // 按 SharedUtil.freRegion 约定: 0=CA, 1=FCC, 2=EU
    private static final int[] REGION_INDEXES = {1, 2, 0}; // 显示顺序: FCC, EU, CA
    private static final String[] REGION_DISPLAYS = {"美国 (FCC)", "欧洲 (EU)", "加拿大 (CA)"};
    private static final int[] SESSION_OPTIONS = {0, 1, 2, 3};
    private static final String[] SESSION_DISPLAYS = {"S0", "S1", "S2", "S3"};
    private static final int[] TARGET_OPTIONS = {0, 1};
    private static final String[] TARGET_DISPLAYS = {"Target 0", "Target 1"};
    private static final int[] Q_OPTIONS = buildRange(0, 15);
    private static final String[] Q_DISPLAYS = buildNumericDisplays(Q_OPTIONS);
    private static final String[] BEEP_MODE_KEYS = {"biz", "raw"};
    private static final String[] BEEP_MODE_DISPLAYS = {"业务增量（仅计数增加）", "读到即响（UHF-G）"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
            // 初始化管理器
            prefs = getSharedPreferences("settings_prefs", Context.MODE_PRIVATE);
            
            // 初始化网络配置
            networkConfig = NetworkConfig.getInstance(this);
            
            // 权限检查
            if (!hasPermission(Permissions.SYSTEM_SETTINGS)) {
                showError("您没有系统设置权限");
                finish();
                return;
            }
            
            // 加载当前设置
            loadCurrentSettings();
            
            // 设置事件监听
            setupListeners();
            
            Log.d(TAG, "SettingsActivity创建成功");
        } catch (Exception e) {
            Log.e(TAG, "SettingsActivity创建失败", e);
            showError("设置界面初始化失败");
            finish();
        }
    }
    
    @Override
    protected void initViews() {
        super.initViews();
        try {
            sessionTimeoutSpinner = findViewById(R.id.session_timeout_spinner);
            saveButton = findViewById(R.id.save_button);
            resetButton = findViewById(R.id.reset_button);
            sessionSettingsCard = findViewById(R.id.session_settings_card);
            barcodeSettingsCard = findViewById(R.id.barcode_settings_card);
            rfidSettingsCard = findViewById(R.id.rfid_settings_card);
            networkSettingsCard = findViewById(R.id.network_settings_card);
            maintenanceSettingsCard = findViewById(R.id.maintenance_settings_card);
            barcodePrefixInput = findViewById(R.id.barcode_prefix_input);
            barcodeLengthInput = findViewById(R.id.barcode_length_input);
            cabinetIdInput = findViewById(R.id.cabinet_id_input);
            sdcodeInput = findViewById(R.id.sdcode_input);
            apiUrlInput = findViewById(R.id.api_url_input);
            networkStatusText = findViewById(R.id.network_status_text);
            userInfoText = findViewById(R.id.user_info_text);
            clearRecordsButton = findViewById(R.id.clear_records_button);

            // RFID views
            rfidPowerSpinner = findViewById(R.id.rfid_power_spinner);
            rfidRegionSpinner = findViewById(R.id.rfid_region_spinner);
            rfidSessionSpinner = findViewById(R.id.rfid_session_spinner);
            rfidTargetSpinner = findViewById(R.id.rfid_target_spinner);
            rfidQSpinner = findViewById(R.id.rfid_q_spinner);
            rfidFastIdSwitch = findViewById(R.id.rfid_fastid_switch);
            rfidBeepSwitch = findViewById(R.id.rfid_beep_switch);
            rfidMultiSwitch = findViewById(R.id.rfid_multi_switch);
            rfidBeepModeSpinner = findViewById(R.id.rfid_beep_mode_spinner);
            uhfTriggerDelayInput = findViewById(R.id.uhf_trigger_delay_input);

            // 会话豁免视图
            idleExemptSwitch = findViewById(R.id.idle_exempt_switch);
            cbExemptInventory = findViewById(R.id.cb_exempt_inventory);
            cbExemptCheckout = findViewById(R.id.cb_exempt_checkout);
            cbExemptInbound = findViewById(R.id.cb_exempt_inbound);
            cbExemptHistory = findViewById(R.id.cb_exempt_history);
            
            // 设置超时时间选项
            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, 
                android.R.layout.simple_spinner_item, 
                TIMEOUT_DISPLAYS
            );
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            sessionTimeoutSpinner.setAdapter(adapter);

            // RFID adapters
            ArrayAdapter<String> powerAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                POWER_DISPLAYS
            );
            powerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            rfidPowerSpinner.setAdapter(powerAdapter);

            ArrayAdapter<String> regionAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                REGION_DISPLAYS
            );
            regionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            rfidRegionSpinner.setAdapter(regionAdapter);

            ArrayAdapter<String> sessionAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                SESSION_DISPLAYS
            );
            sessionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            rfidSessionSpinner.setAdapter(sessionAdapter);

            ArrayAdapter<String> targetAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                TARGET_DISPLAYS
            );
            targetAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            rfidTargetSpinner.setAdapter(targetAdapter);

            ArrayAdapter<String> qAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                Q_DISPLAYS
            );
            qAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            rfidQSpinner.setAdapter(qAdapter);

            ArrayAdapter<String> beepModeAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                BEEP_MODE_DISPLAYS
            );
            beepModeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            rfidBeepModeSpinner.setAdapter(beepModeAdapter);
            
            Log.d(TAG, "UI组件初始化成功");
        } catch (Exception e) {
            Log.e(TAG, "UI组件初始化失败", e);
        }
    }
    
    private void loadCurrentSettings() {
        try {
            // 获取当前超时设置
            long currentTimeout = sessionManager.getTimeoutDuration();
            int timeoutMinutes = (int) (currentTimeout / (60 * 1000));
            
            // 查找对应的选项
            int selectedIndex = 2; // 默认15分钟
            for (int i = 0; i < TIMEOUT_OPTIONS.length; i++) {
                if (TIMEOUT_OPTIONS[i] == timeoutMinutes) {
                    selectedIndex = i;
                    break;
                }
            }
            
            sessionTimeoutSpinner.setSelection(selectedIndex);

            // 加载条码校验规则设置
            loadBarcodeSettings();

            // 加载RFID设置
            loadRfidSettings();

            // 加载会话豁免设置
            boolean exemptEnabled = prefs.getBoolean("idle_exempt_enabled", false);
            if (idleExemptSwitch != null) idleExemptSwitch.setChecked(exemptEnabled);
            String modulesCsv = prefs.getString("idle_exempt_modules", "");
            java.util.Set<String> set = new java.util.HashSet<>();
            if (modulesCsv != null && !modulesCsv.trim().isEmpty()) {
                for (String s : modulesCsv.split(",")) {
                    if (s != null && !s.trim().isEmpty()) set.add(s.trim());
                }
            }
            if (cbExemptInventory != null) cbExemptInventory.setChecked(set.contains("InventoryActivity"));
            if (cbExemptCheckout != null) cbExemptCheckout.setChecked(set.contains("ConsumableCheckoutNewActivity"));
            if (cbExemptInbound != null) cbExemptInbound.setChecked(set.contains("InboundAcceptanceActivity"));
            if (cbExemptHistory != null) cbExemptHistory.setChecked(set.contains("CheckoutHistoryActivity"));
            
            Log.d(TAG, "加载当前设置成功: " + timeoutMinutes + "分钟");
        } catch (Exception e) {
            Log.e(TAG, "加载当前设置失败", e);
        }
    }
    
    private void loadBarcodeSettings() {
        try {
            // 从SharedPreferences加载条码设置
            String barcodePrefix = prefs.getString("barcode_prefix", "");
            int barcodeLength = prefs.getInt("barcode_length", 0);
            int cabinetId = prefs.getInt("cabinet_id", 0);
            String sdCode = prefs.getString("sd_code", "");
            
            // 设置到UI
            barcodePrefixInput.setText(barcodePrefix);
            if (barcodeLength > 0) {
                barcodeLengthInput.setText(String.valueOf(barcodeLength));
            }
            if (cabinetId > 0) {
                cabinetIdInput.setText(String.valueOf(cabinetId));
            }
            if (sdcodeInput != null) {
                sdcodeInput.setText(sdCode);
            }
            
            Log.d(TAG, "加载条码设置成功: prefix=" + barcodePrefix + ", length=" + barcodeLength + ", cabinetId=" + cabinetId);
        } catch (Exception e) {
            Log.e(TAG, "加载条码设置失败", e);
        }
        
        // 加载网络配置
        loadNetworkSettings();
    }
    
    private void loadNetworkSettings() {
        try {
            // 加载API服务器地址
            String apiUrl = networkConfig.getApiBaseUrl();
            apiUrlInput.setText(apiUrl);
            
            // 更新状态显示
            updateNetworkStatus();
            
            Log.d(TAG, "加载网络设置成功: " + apiUrl);
        } catch (Exception e) {
            Log.e(TAG, "加载网络设置失败", e);
        }
    }
    
    private void updateNetworkStatus() {
        try {
            String status = networkConfig.getConfigStatus();
            networkStatusText.setText(status);
            
            // 根据配置状态设置颜色
            if (networkConfig.isValidConfig()) {
                networkStatusText.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
            } else {
                networkStatusText.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
            }
        } catch (Exception e) {
            Log.e(TAG, "更新网络状态失败", e);
        }
    }
    
    private void setupListeners() {
        try {
            // 保存按钮
            saveButton.setOnClickListener(v -> {
                saveSettings();
            });
            
            // 重置按钮
            resetButton.setOnClickListener(v -> {
                resetSettings();
            });

            // 清空记录按钮
            if (clearRecordsButton != null) {
                clearRecordsButton.setOnClickListener(v -> confirmAndClearAllRecords());
            }
            
            Log.d(TAG, "事件监听器设置成功");
        } catch (Exception e) {
            Log.e(TAG, "事件监听器设置失败", e);
        }
    }

    private void loadRfidSettings() {
        try {
            if (rfidShared == null) {
                rfidShared = new com.pda.hcw.rfid.SharedUtil(this);
            }
            // Power
            int power = rfidShared.getPower();
            int pIndex = indexOf(POWER_OPTIONS, power);
            if (pIndex < 0) pIndex = indexOf(POWER_OPTIONS, 30);
            rfidPowerSpinner.setSelection(pIndex);

            // Region (freRegion: 0=CA,1=FCC,2=EU). REGION_INDEXES is display order
            int r = rfidShared.getWorkFreq();
            int rIndex = indexOf(REGION_INDEXES, r);
            if (rIndex < 0) rIndex = 0;
            rfidRegionSpinner.setSelection(rIndex);

            // Session
            int s = rfidShared.getSession();
            int sIndex = indexOf(SESSION_OPTIONS, s);
            if (sIndex < 0) sIndex = indexOf(SESSION_OPTIONS, 3);
            rfidSessionSpinner.setSelection(sIndex);

            // Target
            int t = rfidShared.getTarget();
            int tIndex = indexOf(TARGET_OPTIONS, t);
            if (tIndex < 0) tIndex = indexOf(TARGET_OPTIONS, 1);
            rfidTargetSpinner.setSelection(tIndex);

            // Q
            int q = rfidShared.getQvalue();
            int qIndex = indexOf(Q_OPTIONS, q);
            if (qIndex < 0) qIndex = indexOf(Q_OPTIONS, 1);
            rfidQSpinner.setSelection(qIndex);

            // FastID (默认关闭)
            boolean fast = rfidShared.getFastId();
            rfidFastIdSwitch.setChecked(fast);

            // Beep 开关（存于 settings_prefs）
            boolean beepEnabled = prefs.getBoolean("rfid_beep_enabled", true);
            if (rfidBeepSwitch != null) rfidBeepSwitch.setChecked(beepEnabled);

            // 多标签模式（存于 settings_prefs，默认 true；并与 session 同步展示）
            boolean multi = prefs.getBoolean("rfid_multi_mode", true);
            if (rfidMultiSwitch != null) rfidMultiSwitch.setChecked(multi);
            // 若用户使用了 Session 下拉，可不强制同步；此处仅显示

            // 蜂鸣逻辑（默认 biz）
            String beepMode = prefs.getString("rfid_beep_mode", "biz");
            int bmIdx = 0; // biz
            if ("raw".equalsIgnoreCase(beepMode)) bmIdx = 1;
            if (rfidBeepModeSpinner != null) rfidBeepModeSpinner.setSelection(bmIdx);

            Log.d(TAG, "加载RFID设置成功: P=" + power + ", R=" + r + ", S=" + s + ", T=" + t + ", Q=" + q + ", FastID=" + fast);
        } catch (Exception e) {
            Log.e(TAG, "加载RFID设置失败", e);
        }

        // 加载 UHF 按键消抖
        try {
            long delay = prefs.getLong("uhf_trigger_delay_ms", 250L);
            if (uhfTriggerDelayInput != null) uhfTriggerDelayInput.setText(String.valueOf(delay));
        } catch (Throwable ignore) {}
    }

    private void confirmAndClearAllRecords() {
        try {
            UIUtils.showConfirmDialog(this,
                "清空所有单据记录",
                "此操作将删除本机所有取用记录与盘点任务，且不可恢复，是否继续？",
                (dialog, which) -> {
                    int checkoutAffected = CheckoutRecordDatabase.getInstance(this).clearAllCheckoutRecords();
                    int inventoryAffected = CheckoutRecordDatabase.getInstance(this).clearAllInventoryTasks();
                    String msg = "已清空单据记录，共删除 " + (checkoutAffected + inventoryAffected) + " 条（取用 + 盘点）";
                    showSuccess(msg);
                    Log.d(TAG, msg);
                }
            );
        } catch (Exception e) {
            Log.e(TAG, "清空单据记录失败", e);
            showError(UIUtils.friendlyErrorMessage("清空失败", e));
        }
    }
    
    private void saveSettings() {
        try {
            // 获取选择的超时时间
            int selectedIndex = sessionTimeoutSpinner.getSelectedItemPosition();
            int timeoutMinutes = TIMEOUT_OPTIONS[selectedIndex];
            
            // 设置超时时间
            long timeoutMs;
            if (timeoutMinutes == -1) {
                timeoutMs = -1; // 永不超时
            } else {
                timeoutMs = timeoutMinutes * 60 * 1000L;
            }
            
            sessionManager.setTimeoutDuration(timeoutMs);
            
            // 保存到SharedPreferences
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt("session_timeout_minutes", timeoutMinutes);
            editor.putLong("session_timeout_ms", timeoutMs);

            // 保存会话豁免设置
            boolean exemptEnabled = idleExemptSwitch != null && idleExemptSwitch.isChecked();
            java.util.List<String> selected = new java.util.ArrayList<>();
            if (cbExemptInventory != null && cbExemptInventory.isChecked()) selected.add("InventoryActivity");
            if (cbExemptCheckout != null && cbExemptCheckout.isChecked()) selected.add("ConsumableCheckoutNewActivity");
            if (cbExemptInbound != null && cbExemptInbound.isChecked()) selected.add("InboundAcceptanceActivity");
            if (cbExemptHistory != null && cbExemptHistory.isChecked()) selected.add("CheckoutHistoryActivity");
            String modulesCsv = android.text.TextUtils.join(",", selected);
            editor.putBoolean("idle_exempt_enabled", exemptEnabled);
            editor.putString("idle_exempt_modules", modulesCsv);
            
            // 保存条码设置
            saveBarcodeSettings(editor);
            
            // 保存网络设置
            saveNetworkSettings();

            // 保存RFID设置并尝试应用
            saveRfidSettings();
            
            editor.apply();
            
            // 记录系统设置变更
            UserInfo currentUser = getCurrentUser();
            if (currentUser != null) {
                AuditLogger.logSystemSetting(
                    currentUser.getUserId(),
                    "会话超时时间",
                    sessionManager.getTimeoutDuration() + "ms",
                    timeoutMs + "ms"
                );
                // 记录豁免设置变更（不含具体用户隐私）
                AuditLogger.logSystemSetting(
                    currentUser.getUserId(),
                    "会话超时豁免",
                    String.valueOf(prefs.getBoolean("idle_exempt_enabled", false)) + ":" + prefs.getString("idle_exempt_modules", ""),
                    String.valueOf(exemptEnabled) + ":" + modulesCsv
                );
            }
            
            // 通知BarcodeValidator强制重新加载配置
            BarcodeValidator.forceReload(this);
            
            showSuccess("设置保存成功");
            Log.d(TAG, "设置保存成功: " + timeoutMinutes + "分钟");
            
        } catch (Exception e) {
            Log.e(TAG, "保存设置失败", e);
            showError("设置保存失败");
        }
    }

    private void saveRfidSettings() {
        try {
            if (rfidShared == null) rfidShared = new com.pda.hcw.rfid.SharedUtil(this);

            // 读取UI值
            int power = POWER_OPTIONS[rfidPowerSpinner.getSelectedItemPosition()];
            int region = REGION_INDEXES[rfidRegionSpinner.getSelectedItemPosition()];
            int session = SESSION_OPTIONS[rfidSessionSpinner.getSelectedItemPosition()];
            int target = TARGET_OPTIONS[rfidTargetSpinner.getSelectedItemPosition()];
            int q = Q_OPTIONS[rfidQSpinner.getSelectedItemPosition()];
            boolean fast = rfidFastIdSwitch.isChecked();
            boolean beepEnabled = rfidBeepSwitch != null && rfidBeepSwitch.isChecked();
            boolean multi = rfidMultiSwitch != null && rfidMultiSwitch.isChecked();
            int bmIdxSel = rfidBeepModeSpinner != null ? rfidBeepModeSpinner.getSelectedItemPosition() : 0;
            String beepMode = BEEP_MODE_KEYS[(bmIdxSel >= 0 && bmIdxSel < BEEP_MODE_KEYS.length) ? bmIdxSel : 0];

            // 边界保护
            if (power < 5) power = 5; if (power > 30) power = 30;
            if (session < 0) session = 0; if (session > 3) session = 3;
            if (target < 0) target = 0; if (target > 1) target = 1;
            if (q < 0) q = 0; if (q > 15) q = 15;

            // 保存SharedUtil
            int oldPower = rfidShared.getPower();
            int oldRegion = rfidShared.getWorkFreq();
            int oldSession = rfidShared.getSession();
            int oldTarget = rfidShared.getTarget();
            int oldQ = rfidShared.getQvalue();
            boolean oldFast = rfidShared.getFastId();

            rfidShared.setPower(power);
            rfidShared.setWorkFreq(region);
            rfidShared.setSession(session);
            rfidShared.setTarget(target);
            rfidShared.setQvalue(q);
            rfidShared.setFastId(fast);

            // 同步多标签模式到全局设置与 session（多标签->S3，单标签->S0）
            SharedPreferences.Editor settingsEditor = prefs.edit();
            settingsEditor.putBoolean("rfid_beep_enabled", beepEnabled);
            settingsEditor.putBoolean("rfid_multi_mode", multi);
            settingsEditor.putString("rfid_beep_mode", beepMode);
            settingsEditor.apply();
            rfidShared.setSession(multi ? 3 : 0);

            // 审计日志（避免PII）
            UserInfo u = getCurrentUser();
            if (u != null) {
                AuditLogger.logSystemSetting(u.getUserId(), "RFID-功率", String.valueOf(oldPower), String.valueOf(power));
                AuditLogger.logSystemSetting(u.getUserId(), "RFID-频段", String.valueOf(oldRegion), String.valueOf(region));
                AuditLogger.logSystemSetting(u.getUserId(), "RFID-Session", String.valueOf(oldSession), String.valueOf(session));
                AuditLogger.logSystemSetting(u.getUserId(), "RFID-Target", String.valueOf(oldTarget), String.valueOf(target));
                AuditLogger.logSystemSetting(u.getUserId(), "RFID-Q", String.valueOf(oldQ), String.valueOf(q));
                AuditLogger.logSystemSetting(u.getUserId(), "RFID-FastID", String.valueOf(oldFast), String.valueOf(fast));
                AuditLogger.logSystemSetting(u.getUserId(), "RFID-Beep", String.valueOf(prefs.getBoolean("rfid_beep_enabled", true)), String.valueOf(beepEnabled));
                AuditLogger.logSystemSetting(u.getUserId(), "RFID-BeepMode", prefs.getString("rfid_beep_mode", "biz"), beepMode);
                AuditLogger.logSystemSetting(u.getUserId(), "RFID-Multi", String.valueOf(prefs.getBoolean("rfid_multi_mode", true)), String.valueOf(multi));
            }

            // 若硬件已初始化，尝试即时应用
            com.pda.hcw.rfid.UHFManager mgr = com.pda.hcw.rfid.UHFManager.getInstance();
            if (mgr.isInitialized()) {
                // 安全停止再应用
                try { mgr.stopAllOperations(); } catch (Exception ignore) {}
                // 应用多标签模式
                mgr.setGen2session(multi);
                boolean ok = mgr.applyUserSettings();
            Log.d(TAG, "应用RFID设置到硬件: " + ok);
            }
            // 保存UHF消抖
            try {
                long delay = 250L;
                if (uhfTriggerDelayInput != null && uhfTriggerDelayInput.getText() != null) {
                    String s = uhfTriggerDelayInput.getText().toString().trim();
                    if (!s.isEmpty()) delay = Long.parseLong(s);
                }
                prefs.edit().putLong("uhf_trigger_delay_ms", Math.max(0L, delay)).apply();
            } catch (Throwable ignore) {}
        } catch (Exception e) {
            Log.e(TAG, "保存RFID设置失败", e);
        }
    }
    
    private void saveBarcodeSettings(SharedPreferences.Editor editor) {
        try {
            // 获取条码设置值
            String barcodePrefix = barcodePrefixInput.getText().toString().trim();
            String barcodeLengthStr = barcodeLengthInput.getText().toString().trim();
            String cabinetIdStr = cabinetIdInput.getText().toString().trim();
            String sdCode = sdcodeInput != null && sdcodeInput.getText() != null ? sdcodeInput.getText().toString().trim() : "";
            
            // 解析数值
            int barcodeLength = 0;
            int cabinetId = 0;
            
            if (!barcodeLengthStr.isEmpty()) {
                try {
                    barcodeLength = Integer.parseInt(barcodeLengthStr);
                    if (barcodeLength < 0) {
                        barcodeLength = 0;
                    }
                } catch (NumberFormatException e) {
                    Log.w(TAG, "条码长度格式错误: " + barcodeLengthStr);
                }
            }
            
            if (!cabinetIdStr.isEmpty()) {
                try {
                    cabinetId = Integer.parseInt(cabinetIdStr);
                    if (cabinetId < 0) {
                        cabinetId = 0;
                    }
                } catch (NumberFormatException e) {
                    Log.w(TAG, "货柜ID格式错误: " + cabinetIdStr);
                }
            }
            
            // 保存到SharedPreferences
            editor.putString("barcode_prefix", barcodePrefix);
            editor.putInt("barcode_length", barcodeLength);
            editor.putInt("cabinet_id", cabinetId);
            editor.putString("sd_code", sdCode);
            
            Log.d(TAG, "条码设置保存成功: prefix=" + barcodePrefix + ", length=" + barcodeLength + ", cabinetId=" + cabinetId + ", sdCode=" + sdCode);
        } catch (Exception e) {
            Log.e(TAG, "保存条码设置失败", e);
        }
    }
    
    private void saveNetworkSettings() {
        try {
            // 获取API服务器地址
            String apiUrl = apiUrlInput.getText().toString().trim();
            
            // 保存到NetworkConfig
            networkConfig.setApiBaseUrl(apiUrl);
            
            // 更新状态显示
            updateNetworkStatus();
            
            Log.d(TAG, "网络设置保存成功: " + apiUrl);
        } catch (Exception e) {
            Log.e(TAG, "保存网络设置失败", e);
        }
    }
    
    private void resetSettings() {
        try {
            // 重置为默认值（15分钟）
            int defaultIndex = 2; // 15分钟
            sessionTimeoutSpinner.setSelection(defaultIndex);
            
            // 重置SessionManager
            sessionManager.setTimeoutDuration(15 * 60 * 1000L);
            
            // 清除SharedPreferences
            SharedPreferences.Editor editor = prefs.edit();
            editor.remove("session_timeout_minutes");
            editor.remove("session_timeout_ms");
            editor.remove("barcode_prefix");
            editor.remove("barcode_length");
            editor.remove("cabinet_id");
            editor.remove("sd_code");
            editor.apply();
            
            // 重置条码设置UI
            barcodePrefixInput.setText("");
            barcodeLengthInput.setText("");
            cabinetIdInput.setText("");
            if (sdcodeInput != null) sdcodeInput.setText("");
            
            // 重置网络设置
            networkConfig.resetToDefault();
            apiUrlInput.setText(networkConfig.getApiBaseUrl());
            updateNetworkStatus();
            
            // 通知BarcodeValidator强制重新加载配置
            BarcodeValidator.forceReload(this);
            
            showSuccess("设置已重置为默认值");
            Log.d(TAG, "设置已重置为默认值");
            
        } catch (Exception e) {
            Log.e(TAG, "重置设置失败", e);
            showError("重置设置失败");
        }
    }
    
    /**
     * 更新用户信息显示
     */
    @Override
    protected void updateUserInfo() {
        try {
            // 先调用BaseActivity的updateUserInfo更新toolbar用户信息
            super.updateUserInfo();
            
            if (userInfoText == null) {
                Log.w(TAG, "userInfoText 未初始化");
                return;
            }
            
            // BaseActivity会自动处理toolbar用户信息显示
            // 这里只处理页面内的用户信息显示（如果有）
            UserInfo currentUser = getCurrentUser();
            if (currentUser != null && userInfoText != null) {
                // 显示用户信息
                String userInfo = currentUser.getUsername() + " (" + currentUser.getRole().getDisplayName() + ")";
                userInfoText.setText(userInfo);
                
                // 根据用户角色设置颜色
                if (currentUser.getRole() == UserRole.ADMIN) {
                    userInfoText.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                } else if (currentUser.getRole() == UserRole.USER) {
                    userInfoText.setTextColor(getResources().getColor(android.R.color.holo_blue_dark));
                } else {
                    userInfoText.setTextColor(getResources().getColor(android.R.color.darker_gray));
                }
                
                Log.d(TAG, "用户信息更新成功: " + userInfo);
            } else if (userInfoText != null) {
                userInfoText.setText("未登录");
                userInfoText.setTextColor(getResources().getColor(android.R.color.darker_gray));
            }
        } catch (Exception e) {
            Log.e(TAG, "更新用户信息失败", e);
            if (userInfoText != null) {
                userInfoText.setText("未登录");
                userInfoText.setTextColor(getResources().getColor(android.R.color.darker_gray));
            }
        }
    }
    
    // BaseActivity已统一处理会话管理，移除重复逻辑
    
    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
    
    /**
     * 获取会话超时设置的显示文本
     * @param timeoutMs 超时时间（毫秒）
     * @return 显示文本
     */
    public static String getTimeoutDisplayText(long timeoutMs) {
        if (timeoutMs == -1) {
            return "永不超时";
        }
        
        int minutes = (int) (timeoutMs / (60 * 1000));
        if (minutes < 60) {
            return minutes + "分钟";
        } else {
            int hours = minutes / 60;
            return hours + "小时";
        }
    }

    // ===== 工具方法：用于构造/查找数组项 =====
    private static int[] buildRange(int start, int end) {
        int[] arr = new int[end - start + 1];
        for (int i = 0; i < arr.length; i++) arr[i] = start + i;
        return arr;
    }

    private static String[] buildPowerDisplays(int[] values) {
        String[] arr = new String[values.length];
        for (int i = 0; i < values.length; i++) arr[i] = values[i] + " dBm";
        return arr;
    }

    private static String[] buildNumericDisplays(int[] values) {
        String[] arr = new String[values.length];
        for (int i = 0; i < values.length; i++) arr[i] = String.valueOf(values[i]);
        return arr;
    }

    private static int indexOf(int[] arr, int v) {
        for (int i = 0; i < arr.length; i++) if (arr[i] == v) return i;
        return -1;
    }
}
