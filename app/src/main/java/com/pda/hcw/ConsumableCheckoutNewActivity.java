package com.pda.hcw;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.content.Context;
import android.content.Intent;
 
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.PopupWindow;
import android.os.Build;
import android.widget.TextView;
// import android.widget.Toast;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import android.text.method.KeyListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConsumableCheckoutNewActivity extends BaseActivity {
    private static final String TAG = "ConsumableCheckoutNew";
    // 模式：取用/归还（默认取用）
    private enum OperationMode { TAKE, RETURN }
    private static final String EXTRA_OPERATION_MODE = "operation_mode"; // 值："take"/"return"
    private OperationMode operationMode = OperationMode.TAKE;
    
    // UI组件
    private DrawerLayout drawerLayout;
    private TextInputLayout roomInputLayout;
    private AutoCompleteTextView roomSpinner;
    private TextInputEditText barcodeInput;
      private TextInputEditText totalCountInput;
    
    // 处理扫码输入的Handler
    private Handler handler = new Handler();
        private RecyclerView detailRecyclerView;
    private MaterialButton clearButton;
    private MaterialButton confirmButton;
    private MaterialButton logoutButton;
    private View leftEdgeIndicator;
    
    // 数据
    private List<ConsumableGroupItem> consumableGroupList;
    private ConsumableDetailAdapter detailAdapter;
    private List<String> roomOptions = new ArrayList<>();
    private int totalCount = 0;
    
    // 领用记录管理器
    private CheckoutRecordManager recordManager;
    
    // 商品编号分组管理
    private Map<String, ConsumableGroupItem> productCodeGroups = new HashMap<>();
    // 选择的耗材RFID -> 完整耗材项缓存（提交到接口时使用）
    private Map<String, ConsumableItem> selectedItemsByRfid = new HashMap<>();
    
    // API服务
    private ConsumableApiService apiService;
    
    // 条码校验器
    private BarcodeValidator barcodeValidator;
    
        
    @Override
    protected int getLayoutResourceId() {
        return R.layout.activity_consumable_checkout_new;
    }
    
    @Override
    protected String getActivityName() {
        return operationMode == OperationMode.RETURN ? "归还" : "取用";
    }
    
    @Override
    protected String getActivityTitle() {
        // 根据模式返回标题
        return operationMode == OperationMode.RETURN ? "归还" : getString(R.string.consumable_checkout_title);
    }
    
    // 当前显示的弹窗
    private PopupWindow currentDialog = null;
    
          private boolean isDialogShowing = false;
    
    // 已添加的条码缓存
    private java.util.Set<String> addedBarcodes = new java.util.HashSet<>();
    // 记录扫码输入框原始KeyListener（用于查看模式禁用/恢复）
    private KeyListener originalBarcodeKeyListener;
    // 扫码输入控制器（封装输入框的启用/禁用/焦点/清空）
    private ScanInputController scanController;
    // UI状态机当前状态
    private CheckoutUiState currentUiState = CheckoutUiState.NEW_CHECKOUT;
    
        
    // 防止递归调用的标志
        
    // 设备自适应条码处理
    private String deviceModel;
    private boolean isHandheldDevice = false; // Handheld-Wireless设备
    private boolean isAlpsDevice = false; // Alps设备
    private int barcodeProcessingDelay = 1000; // 条码处理延迟时间
        
    // 历史记录相关
    private NavigationView navView;
    private RecyclerView historyRecyclerView;
    private CheckoutRecordAdapter historyAdapter;
    private List<CheckoutRecord> historyRecordList;
    private TimeFilter currentTimeFilter;
    private String currentUserId;
    private boolean isViewMode = false; // 查看模式标志
    
    // 已移除本地行项目ID生成逻辑，按需从服务端补齐库存ID
    // 会话和权限管理（由BaseActivity统一管理）

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate started");
        super.onCreate(savedInstanceState);
        
        try {
            // 解析模式
            try {
                String mode = getIntent() != null ? getIntent().getStringExtra(EXTRA_OPERATION_MODE) : null;
                if (mode != null && mode.equalsIgnoreCase("return")) {
                    operationMode = OperationMode.RETURN;
                } else {
                    operationMode = OperationMode.TAKE;
                }
                Log.d(TAG, "Operation mode: " + operationMode);
                // 解析模式后，立刻刷新标题栏
                try { if (toolbarTitle != null) toolbarTitle.setText(getActivityTitle()); } catch (Throwable ignore) {}
            } catch (Exception ignore) {}
            
            // 初始化数据
            consumableGroupList = new ArrayList<>();
            
            // 权限检查
            if (!hasPermission(Permissions.CONSUMABLE_CHECKOUT)) {
                showError("您没有耗材领用权限");
                finish();
                return;
            }
            
            // 初始化领用记录管理器
            recordManager = CheckoutRecordManager.getInstance(this);
            // 历史列表按当前登录用户ID过滤，避免重名
            try {
                UserInfo u = sessionManager.getCurrentUser();
                if (u != null && u.getUserId() != null && !u.getUserId().trim().isEmpty()) {
                    currentUserId = u.getUserId();
                }
            } catch (Exception ignore) {}
            
            // 初始化API服务和条码校验器
            apiService = ConsumableApiService.getInstance(this);
            barcodeValidator = BarcodeValidator.getInstance(this);
            
            // 设备自适应配置
            configureDeviceSettings();
            
            // 初始化历史记录数据
            historyRecordList = new ArrayList<>();
            
            // 设置适配器
            setupRecyclerView();
            
            // 设置事件监听
            setupListeners();

            // 根据模式调整UI（归还隐藏术间、按钮文案）
            applyOperationModeUi();

            // 初始化历史记录视图并加载（此时recordManager已就绪，避免空指针）
            setupHistoryView();
            
                        
            Log.d(TAG, "onCreate completed successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate", e);
        }
    }
    
    
    // ========= 辅助方法 =========
    
    /**
     * 设备自适应配置
     */
    private void configureDeviceSettings() {
        try {
            deviceModel = Build.MODEL;
            String manufacturer = Build.MANUFACTURER;
            
            Log.d(TAG, "Device Model: " + deviceModel);
            Log.d(TAG, "Device Manufacturer: " + manufacturer);
            
            // 检测设备类型
            if (manufacturer != null && manufacturer.toLowerCase().contains("handheld")) {
                isHandheldDevice = true;
                barcodeProcessingDelay = 1500; // Handheld设备需要更长的显示时间
                Log.d(TAG, "Configured for Handheld device");
            } else if (manufacturer != null && manufacturer.toLowerCase().contains("alps")) {
                isAlpsDevice = true;
                barcodeProcessingDelay = 800; // Alps设备可以稍快一些
                Log.d(TAG, "Configured for Alps device");
            } else {
                // 默认配置
                barcodeProcessingDelay = 1000;
                Log.d(TAG, "Using default configuration");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error configuring device settings", e);
            // 使用默认配置
            barcodeProcessingDelay = 1000;
        }
    }
    
        
        
    /**
     * 获取按键动作名称
     */
    private String getKeyActionName(int action) {
        switch (action) {
            case KeyEvent.ACTION_DOWN: return "ACTION_DOWN";
            case KeyEvent.ACTION_UP: return "ACTION_UP";
            case KeyEvent.ACTION_MULTIPLE: return "ACTION_MULTIPLE";
            default: return "UNKNOWN(" + action + ")";
        }
    }
    
    /**
     * 获取输入源名称
     */
    private String getInputSourceName(int source) {
        StringBuilder sb = new StringBuilder();
        if ((source & android.view.InputDevice.SOURCE_KEYBOARD) != 0) sb.append("KEYBOARD|");
        if ((source & android.view.InputDevice.SOURCE_DPAD) != 0) sb.append("DPAD|");
        if ((source & android.view.InputDevice.SOURCE_GAMEPAD) != 0) sb.append("GAMEPAD|");
        if ((source & android.view.InputDevice.SOURCE_TOUCHSCREEN) != 0) sb.append("TOUCHSCREEN|");
        if ((source & android.view.InputDevice.SOURCE_MOUSE) != 0) sb.append("MOUSE|");
        if ((source & android.view.InputDevice.SOURCE_STYLUS) != 0) sb.append("STYLUS|");
        if ((source & android.view.InputDevice.SOURCE_TRACKBALL) != 0) sb.append("TRACKBALL|");
        if ((source & android.view.InputDevice.SOURCE_TOUCHPAD) != 0) sb.append("TOUCHPAD|");
        
        String result = sb.toString();
        if (result.endsWith("|")) {
            result = result.substring(0, result.length() - 1);
        }
        return result.isEmpty() ? "UNKNOWN(" + source + ")" : result;
    }
    
    /**
     * 记录Activity状态
     */
    private void logActivityState(String context) {
        Log.d(TAG, "Activity state [" + context + "]:");
        Log.d(TAG, "  - isFinishing: " + isFinishing());
        Log.d(TAG, "  - isDestroyed: " + isDestroyed());
        Log.d(TAG, "  - hasWindowFocus: " + hasWindowFocus());
        Log.d(TAG, "  - isTaskRoot: " + isTaskRoot());
    }
    
    /**
     * 记录焦点状态
     */
    private void logFocusState(String context) {
        Log.d(TAG, "Focus state [" + context + "]:");
        View currentFocus = getCurrentFocus();
        if (currentFocus != null) {
            Log.d(TAG, "  - currentFocus: " + currentFocus.getClass().getSimpleName() + 
                  " (id: " + currentFocus.getId() + ")");
            Log.d(TAG, "  - currentFocus enabled: " + currentFocus.isEnabled());
            Log.d(TAG, "  - currentFocus visible: " + (currentFocus.getVisibility() == View.VISIBLE));
            Log.d(TAG, "  - currentFocus focusable: " + currentFocus.isFocusable());
        } else {
            Log.d(TAG, "  - currentFocus: null");
        }
        
        if (barcodeInput != null) {
            Log.d(TAG, "  - barcodeInput focused: " + barcodeInput.isFocused());
            Log.d(TAG, "  - barcodeInput hasFocus: " + barcodeInput.hasFocus());
            Log.d(TAG, "  - barcodeInput enabled: " + barcodeInput.isEnabled());
            Log.d(TAG, "  - barcodeInput visible: " + (barcodeInput.getVisibility() == View.VISIBLE));
            Log.d(TAG, "  - barcodeInput focusable: " + barcodeInput.isFocusable());
        }
    }
    
    /**
     * 记录扫码事件
     */
    private void logScanEvent(String method, String scanData, KeyEvent event) {
        Log.d(TAG, "Scan event [" + method + "]:");
        Log.d(TAG, "  - scanData: '" + scanData + "'");
        Log.d(TAG, "  - scanData length: " + scanData.length());
        Log.d(TAG, "  - event timestamp: " + event.getEventTime());
        Log.d(TAG, "  - event device: " + event.getDeviceId());
        Log.d(TAG, "  - event source: " + getInputSourceName(event.getSource()));
    }
    
    
    @Override
    protected void onResume() {
        super.onResume();
        
        long resumeStartTime = System.currentTimeMillis();
        Log.d(TAG, "=== ON_RESUME START ===");
        Log.d(TAG, "onResume() called at timestamp: " + resumeStartTime);
        Log.d(TAG, "Activity state - isFinishing: " + isFinishing() + ", isDestroyed: " + isDestroyed());
        
        // 记录当前Activity状态
        logActivityState("onResume");
        
        // BaseActivity会自动处理会话更新和用户信息刷新
        
        // 重新加载地点列表；取用模式下禁用“默认地点”选项
        try {
            OperatingRoomManager orm = OperatingRoomManager.getInstance(this);
            Integer defId = DefaultLocationManager.getDefaultLocationId(this);
            List<OperatingRoom> rooms = orm.getAllRooms();
            if (rooms != null && !rooms.isEmpty()) {
                roomOptions.clear();
                for (OperatingRoom r : rooms) {
                    if (r == null || r.name == null) continue;
                    if (operationMode == OperationMode.TAKE && defId != null && r.serverId != null && r.serverId.equals(defId)) {
                        // 跳过默认地点
                        continue;
                    }
                    roomOptions.add(r.name);
                }
                ArrayAdapter<String> roomAdapter = new ArrayAdapter<>(this,
                        android.R.layout.simple_dropdown_item_1line, roomOptions);
                roomSpinner.setAdapter(roomAdapter);
            }
        } catch (Exception e) {
            Log.w(TAG, "reload rooms failed", e);
        }

        long resumeEndTime = System.currentTimeMillis();
        Log.d(TAG, "onResume() completed in: " + (resumeEndTime - resumeStartTime) + "ms");        
        Log.d(TAG, "=== ON_RESUME END ===");
    }
    
    // BaseActivity已统一处理会话管理，移除重复逻辑
    
        
    @Override
    protected void initViews() {
        super.initViews();
        try {
            drawerLayout = findViewById(R.id.drawer_layout);
            roomInputLayout = findViewById(R.id.room_input_layout);
            roomSpinner = findViewById(R.id.room_spinner);
            barcodeInput = findViewById(R.id.barcode_input);
                        totalCountInput = findViewById(R.id.total_count_input);
            detailRecyclerView = findViewById(R.id.detail_recycler_view);
            clearButton = findViewById(R.id.clear_button);
            confirmButton = findViewById(R.id.confirm_button);
            logoutButton = findViewById(R.id.logout_button);
            navView = findViewById(R.id.nav_view);
            leftEdgeIndicator = findViewById(R.id.left_edge_indicator);

            // 永久禁用条码框与术间下拉的软键盘弹出（仅键盘仿真扫描，不需IME）
            try {
                if (barcodeInput != null) {
                    barcodeInput.setShowSoftInputOnFocus(false);
                }
            } catch (Throwable ignore) {}
            try {
                if (roomSpinner != null) {
                    roomSpinner.setShowSoftInputOnFocus(false);
                }
            } catch (Throwable ignore) {}
            
            // 设置地点下拉选项（取用模式下禁用“默认地点”）
            try {
                OperatingRoomManager orm = OperatingRoomManager.getInstance(this);
                Integer defId = DefaultLocationManager.getDefaultLocationId(this);
                roomOptions.clear();
                List<OperatingRoom> rooms = orm.getAllRooms();
                if (rooms != null) {
                    for (OperatingRoom r : rooms) {
                        if (r == null || r.name == null) continue;
                        if (operationMode == OperationMode.TAKE && defId != null && r.serverId != null && r.serverId.equals(defId)) {
                            continue; // 禁用默认地点
                        }
                        roomOptions.add(r.name);
                    }
                }
                if (roomOptions.isEmpty()) {
                    roomOptions.add("未配置地点，请在基础资料中更新");
                }
            } catch (Exception e) {
                roomOptions.clear();
                roomOptions.add("读取地点失败");
            }
            ArrayAdapter<String> roomAdapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_dropdown_item_1line, roomOptions);
            roomSpinner.setAdapter(roomAdapter);
            
              
                        
            // 历史记录的加载推迟到 onCreate（recordManager 初始化之后）
            
            Log.d(TAG, "initViews completed");
        } catch (Exception e) {
            Log.e(TAG, "Error in initViews", e);
        }
    }
    
      
    private void setupRecyclerView() {
        try {
            detailAdapter = new ConsumableDetailAdapter(consumableGroupList, this::removeConsumableItem);
            detailRecyclerView.setLayoutManager(new LinearLayoutManager(this));
            detailRecyclerView.setAdapter(detailAdapter);
            
            Log.d(TAG, "setupRecyclerView completed");
        } catch (Exception e) {
            Log.e(TAG, "Error in setupRecyclerView", e);
        }
    }
    
    private void setupHistoryView() {
        try {
            // 获取NavigationView中的RecyclerView
            View headerView = navView.getHeaderView(0);
            historyRecyclerView = headerView.findViewById(R.id.history_recycler_view);

            // 覆盖标题与副标题文本（按模式显示取用/归还）
            try {
                if (headerView instanceof android.view.ViewGroup) {
                    android.view.ViewGroup vg = (android.view.ViewGroup) headerView;
                    if (vg.getChildCount() > 0 && vg.getChildAt(0) instanceof android.view.ViewGroup) {
                        android.view.ViewGroup titleArea = (android.view.ViewGroup) vg.getChildAt(0);
                        if (titleArea.getChildCount() >= 2) {
                            if (titleArea.getChildAt(0) instanceof TextView) {
                                ((TextView) titleArea.getChildAt(0)).setText(operationMode == OperationMode.RETURN ? "归还记录" : "取用记录");
                            }
                            if (titleArea.getChildAt(1) instanceof TextView) {
                                ((TextView) titleArea.getChildAt(1)).setText(operationMode == OperationMode.RETURN ? "查看历史归还记录" : "查看历史取用记录");
                            }
                        }
                    }
                }
            } catch (Exception ignore) {}
            
            if (historyRecyclerView != null) {
                historyRecyclerView.setLayoutManager(new LinearLayoutManager(this));
                historyAdapter = new CheckoutRecordAdapter(historyRecordList, this::onHistoryRecordClick);
                historyRecyclerView.setAdapter(historyAdapter);
                
                Log.d(TAG, "History RecyclerView setup completed");
            }
            
            // 默认显示今日记录（按模式过滤）
            currentTimeFilter = TimeFilter.TODAY;
            loadHistoryRecords(TimeFilter.TODAY);
            
            Log.d(TAG, "History view setup completed - showing today records by default");
            
        } catch (Exception e) {
            Log.e(TAG, "Error in setupHistoryView", e);
        }
    }
    
    private void setupListeners() {
        try {
            Log.d(TAG, "=== SETUP LISTENERS START ===");
            
                        
              
            // 术间选择监听
            roomSpinner.setOnItemClickListener((parent, view, position, id) -> {
                updateTotalCountDisplay();
                // 术间选择后，回焦到条码框且不弹出输入法
                if (scanController == null && barcodeInput != null) {
                    scanController = new ScanInputController(barcodeInput);
                    originalBarcodeKeyListener = scanController.getOriginalKeyListener();
                }
                if (scanController != null && !isViewMode) {
                    scanController.focus();
                    // 兜底隐藏一次输入法（部分设备可能仍尝试显示）
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null && barcodeInput != null) {
                        imm.hideSoftInputFromWindow(barcodeInput.getWindowToken(), 0);
                    }
                }
            });
            
            // 条码输入框回车键监听 - PDA扫码结束触发
            barcodeInput.setOnEditorActionListener((v, actionId, event) -> {
                if (isViewMode) {
                    // 清理残留并阻止输入
                    barcodeInput.setText("");
                    showInfo("当前为查看模式，禁止扫码");
                    return true; // 阻止处理
                }
                if (actionId == EditorInfo.IME_ACTION_DONE || 
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    
                    String barcode = barcodeInput.getText().toString().trim();
                    Log.d(TAG, "Enter key pressed, barcode: " + barcode);
                    
                    if (!barcode.isEmpty()) {
                        // 条码格式校验
                        if (barcodeValidator.validateBarcode(barcode)) {
                            Log.d(TAG, "Barcode validation passed: " + barcode);
                            
                            // 检查是否已添加到明细
                            if (isBarcodeAlreadyAdded(barcode)) {
                                showErrorOptimized("该条码已添加到明细中");
                            } else {
                                // 直接调用API
                                callApiForBarcode(barcode);
                            }
                        } else {
                            // 条码校验失败
                            Log.d(TAG, "Barcode validation failed: " + barcode);
                            showErrorOptimized(barcodeValidator.getInvalidBarcodeMessage());
                        }
                        
                        // 立即清空输入框
                        barcodeInput.setText("");
                        barcodeInput.requestFocus();
                    }
                    
                    return true; // 消费掉回车事件
                }
                return false; // 不消费其他事件
            });
              
            // 清屏按钮
            clearButton.setOnClickListener(v -> clearAllItems());
            
            // 确认按钮（取用/归还）
            confirmButton.setOnClickListener(v -> confirmCheckout());
            
            // 登出按钮
            logoutButton.setOnClickListener(v -> logout());
            
            // 左侧边缘点击事件
            leftEdgeIndicator.setOnClickListener(v -> {
                if (drawerLayout != null) {
                    drawerLayout.openDrawer(findViewById(R.id.nav_view));
                }
            });
            
            Log.d(TAG, "setupListeners completed");
            // 设置默认为新建取用模式
            applyUiState(CheckoutUiState.NEW_CHECKOUT);
        } catch (Exception e) {
            Log.e(TAG, "Error in setupListeners", e);
        }
    }

    /**
     * 根据模式调整UI：
     * - RETURN: 隐藏术间输入，确认按钮显示“确认归还”
     * - TAKE: 保持现状
     */
    private void applyOperationModeUi() {
        try {
            if (operationMode == OperationMode.RETURN) {
                if (roomInputLayout != null) roomInputLayout.setVisibility(View.GONE);
                if (roomSpinner != null) roomSpinner.setVisibility(View.GONE);
                if (confirmButton != null) {
                    if (isViewMode) {
                        // 查看归还记录界面：按钮应为“新建归还”
                        confirmButton.setText(R.string.consumable_new_return);
                    } else {
                        // 新建归还界面：按钮为“确认归还”
                        confirmButton.setText("确认归还");
                    }
                }
            } else {
                if (roomInputLayout != null) roomInputLayout.setVisibility(View.VISIBLE);
                if (roomSpinner != null) roomSpinner.setVisibility(View.VISIBLE);
                // 文案保持为资源字符串（取用）
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in applyOperationModeUi", e);
        }
    }
    
        
    /**
     * 处理扫码数据 - 增强版本
     */
    
    
    
    
    /**
     * 检查条码是否已添加到明细
     */
    private boolean isBarcodeAlreadyAdded(String barcode) {
        boolean isAlreadyAdded = addedBarcodes.contains(barcode);
        Log.d(TAG, "=== BARCODE_CACHE_CHECK ===");
        Log.d(TAG, "Checking barcode: " + barcode);
        Log.d(TAG, "Current cache size: " + addedBarcodes.size());
        Log.d(TAG, "Current cache content: " + addedBarcodes);
        Log.d(TAG, "Is barcode already added: " + isAlreadyAdded);
        Log.d(TAG, "=== BARCODE_CACHE_CHECK END ===");
        return isAlreadyAdded;
    }
    
    /**
     * 添加条码到缓存
     */
    private void addBarcodeToCache(String barcode) {
        Log.d(TAG, "=== ADD_BARCODE_TO_CACHE START ===");
        Log.d(TAG, "Adding barcode: " + barcode);
        Log.d(TAG, "Cache size before adding: " + addedBarcodes.size());
        Log.d(TAG, "Cache content before adding: " + addedBarcodes);
        
        boolean added = addedBarcodes.add(barcode);
        
        Log.d(TAG, "Barcode add result: " + added);
        Log.d(TAG, "Cache size after adding: " + addedBarcodes.size());
        Log.d(TAG, "Cache content after adding: " + addedBarcodes);
        Log.d(TAG, "=== ADD_BARCODE_TO_CACHE END ===");
    }
    
    /**
     * 清空条码缓存（在清空明细时调用）
     */
    private void clearBarcodeCache() {
        Log.d(TAG, "=== CLEAR_BARCODE_CACHE START ===");
        Log.d(TAG, "Cache size before clearing: " + addedBarcodes.size());
        Log.d(TAG, "Cache content before clearing: " + addedBarcodes);
        
        addedBarcodes.clear();
        
        Log.d(TAG, "Cache size after clearing: " + addedBarcodes.size());
        Log.d(TAG, "Barcode cache cleared");
        Log.d(TAG, "=== CLEAR_BARCODE_CACHE END ===");
    }
    
    /**
     * 直接调用API查询耗材信息
     */
    private void callApiForBarcode(String barcode) {
        if (apiService == null) {
            showErrorOptimized("API服务未初始化");
            return;
        }
        
        // 显示API加载Toast
        showInfo("正在查询耗材信息...");
        
        apiService.queryConsumableInfo(barcode, new ConsumableApiService.ApiCallback<ConsumableApiResponse>() {
            @Override
            public void onSuccess(ConsumableApiResponse response) {
                runOnUiThread(() -> {
                    try {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        
                        // 显示成功结果
                        showSuccess("查询成功");
                        
                        // 处理API响应
                        handleApiResponseOptimized(response, barcode);
                        
                    } catch (Exception e) {
                        Log.e(TAG, "Error handling API response", e);
                        showError("处理响应时发生错误");
                    }
                });
            }
            
            @Override
            public void onFailure(Exception e) {
                runOnUiThread(() -> {
                    String errorMessage = UIUtils.friendlyErrorMessage("查询失败", e);
                    if (errorMessage.length() > 50) {
                        errorMessage = errorMessage.substring(0, 50) + "...";
                    }
                    showError(errorMessage);
                });
            }
        });
    }
    

    
        
    /**
     * 处理条码输入 - 性能优化版本
     * 优化点：
     * 1. 防抖处理，避免重复快速扫码
     * 2. 减少不必要的焦点请求和UI更新
     * 3. 优化执行效率，减少冗余操作
     * 4. 性能监控和统计
     */
        
        
        
    
    
    /**
     * 优化的错误提示
     * 合并音效播放和Toast显示
     */
    private void showErrorOptimized(String message) {
        playErrorSound();
        showInfo(message);
    }
    
    // 统一使用 AppToast，删除本地去重实现
    
    
    
    /**
     * 在API响应的4个数组中查找匹配的耗材项
     * @param response API响应
     * @param barcode 扫描的条码
     * @return 匹配的耗材项，如果没有找到返回null
     */
    private ConsumableItem findConsumableInResponse(ConsumableApiResponse response, String barcode) {
        Log.d(TAG, "=== FIND_CONSUMABLE_IN_RESPONSE START ===");
        Log.d(TAG, "Looking for barcode: " + barcode);
        
        // 1. 首先检查notInStockData（字符串数组）
        if (response.hasNotInStockData()) {
            Log.d(TAG, "Checking notInStockData: " + response.notInStockData);
            for (String item : response.notInStockData) {
                if (barcode.equals(item)) {
                    Log.d(TAG, "Found barcode in notInStockData: " + item);
                    showErrorOptimized("[" + barcode + "]耗材未入库");
                    return null;
                }
            }
        }
        
        if (operationMode == OperationMode.TAKE) {
            // 取用模式：takeList 可添加；returnList 阻止
            if (response.hasTakeList()) {
                Log.d(TAG, "Checking takeList with " + response.takeList.size() + " items");
                for (ConsumableItem item : response.takeList) {
                    if (barcode.equals(item.getRfid())) {
                        item.setBarcode(barcode);
                        return item;
                    }
                }
            }
            if (response.hasReturnList()) {
                Log.d(TAG, "Checking returnList with " + response.returnList.size() + " items");
                for (ConsumableItem item : response.returnList) {
                    if (barcode.equals(item.getRfid())) {
                        showErrorOptimized("[" + barcode + "]领用后未归还请先归还");
                        return null;
                    }
                }
            }
        } else {
            // 归还模式：returnList 可添加；takeList 阻止
            if (response.hasReturnList()) {
                Log.d(TAG, "Checking returnList with " + response.returnList.size() + " items");
                for (ConsumableItem item : response.returnList) {
                    if (barcode.equals(item.getRfid())) {
                        item.setBarcode(barcode);
                        return item;
                    }
                }
            }
            if (response.hasTakeList()) {
                Log.d(TAG, "Checking takeList with " + response.takeList.size() + " items");
                for (ConsumableItem item : response.takeList) {
                    if (barcode.equals(item.getRfid())) {
                        showErrorOptimized("[" + barcode + "]未领用，不可归还");
                        return null;
                    }
                }
            }
        }
        
        // 4. 检查errorData（异常数据）
        if (response.hasErrorData()) {
            Log.d(TAG, "Checking errorData with " + response.errorData.size() + " items");
            for (ConsumableItem item : response.errorData) {
                Log.d(TAG, "Comparing with errorData item RFID: " + item.getRfid());
                if (barcode.equals(item.getRfid())) {
                    Log.d(TAG, "Found barcode in errorData: " + item.getRfid());
                    showErrorOptimized("[" + barcode + "]耗材消耗后退回未归还");
                    return null;
                }
            }
        }
        
        Log.d(TAG, "Barcode not found in any array: " + barcode);
        return null;
    }
    
    /**
     * 优化的API响应处理
     * 在4个数组中查找匹配的条码，确保条码缓存正确
     */
    private void handleApiResponseOptimized(ConsumableApiResponse response, String barcode) {
        try {
            Log.d(TAG, "=== HANDLE_API_RESPONSE_OPTIMIZED START ===");
            Log.d(TAG, "handleApiResponseOptimized called with barcode: " + barcode);
            Log.d(TAG, "Response summary: " + response.getSummary());
            
            // 1. 先检查4个列表是否都为空
            if (!response.hasNotInStockData() && !response.hasTakeList() && 
                !response.hasReturnList() && !response.hasErrorData()) {
                Log.d(TAG, "All 4 lists are empty, barcode not in stock");
                showErrorOptimized("[" + barcode + "]未入库耗材");
                return;
            }
            
            // 2. 执行查找
            ConsumableItem foundItem = findConsumableInResponse(response, barcode);
            
            // 3. 如果找到，显示弹窗
            if (foundItem != null) {
                Log.d(TAG, "Found consumable item: " + foundItem.getCommonName() + ", barcode: " + foundItem.getBarcode());
                showConsumableInfoDialog(foundItem, barcode);
            } else {
                // 4. 如果没找到，说明接口数据有问题
                Log.d(TAG, "Barcode not found in any non-empty list, possible API data issue");
                showErrorOptimized("接口数据返回有误，未找到条码: " + barcode);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error in optimized API response handling", e);
            showErrorOptimized("处理耗材信息时发生错误");
        }
    }
    
        
      
        
        
        
        
    private void playAlertSound() {
        new Thread(() -> {
            try {
                android.media.ToneGenerator toneGen = new android.media.ToneGenerator(
                    android.media.AudioManager.STREAM_ALARM, 100);
                toneGen.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 300);
                Thread.sleep(400);
                toneGen.release();
                Log.d(TAG, "Alert sound played successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error playing alert sound", e);
            }
        }).start();
    }
    
    private void playErrorSound() {
        new Thread(() -> {
            try {
                android.media.ToneGenerator toneGen = new android.media.ToneGenerator(
                    android.media.AudioManager.STREAM_ALARM, 100);
                toneGen.startTone(android.media.ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 500);
                Thread.sleep(600);
                toneGen.release();
                Log.d(TAG, "Error sound played successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error playing error sound", e);
            }
        }).start();
    }
    
    
        
    
      
    private void showMessageDialog(String title, String message) {
        try {
            // 创建AlertDialog显示消息
            androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
            builder.setTitle(title);
            builder.setMessage(message);
            builder.setPositiveButton("确定", (dialog, which) -> {
                dialog.dismiss();
                // 确保输入框重新获得焦点
                if (barcodeInput != null) {
                    barcodeInput.post(() -> {
                        barcodeInput.requestFocus();
                        barcodeInput.requestFocusFromTouch();
                    });
                }
            });
            builder.setCancelable(false);
            builder.show();
            
            Log.d(TAG, "Message dialog shown: " + title);
        } catch (Exception e) {
            Log.e(TAG, "Error showing message dialog", e);
            showError(message);
        }
    }
    
    private void showConsumableInfoDialog(ConsumableItem item) {
        showConsumableInfoDialog(item, null);
    }
    
    /**
     * 显示耗材信息弹窗 - 带条码缓存版本
     * 包含完整的焦点管理和状态恢复机制
     * @param item 耗材信息
     * @param barcode 条码（用于缓存）
     */
    private void showConsumableInfoDialog(ConsumableItem item, String barcode) {
        try {
            Log.d(TAG, "=== SHOW_CONSUMABLE_INFO_DIALOG_ENHANCED START ===");
            Log.d(TAG, "showConsumableInfoDialog called for item: " + item.commonName + ", barcode: " + barcode);
            
            // 检查Activity状态
            if (isFinishing() || isDestroyed()) {
                Log.w(TAG, "Activity is finishing or destroyed, cannot show dialog");
                return;
            }
            
            // 如果已有弹窗，先关闭
            if (currentDialog != null && currentDialog.isShowing()) {
                Log.d(TAG, "Closing existing dialog before showing new one");
                dismissDialogEnhanced();
            }
            
            // 标记弹窗显示状态
            isDialogShowing = true;
            
            // 记录当前焦点状态
            logFocusState("before_dialog_show");
            
            // 创建PopupWindow而不是AlertDialog
            View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_consumable_info, null);
            
            Log.d(TAG, "Dialog view inflated successfully");
            
            // 设置耗材信息
            ((TextView) dialogView.findViewById(R.id.dialog_product_code)).setText("商品编号：" + item.productCode);
            ((TextView) dialogView.findViewById(R.id.dialog_barcode)).setText("条码号：" + item.barcode);
            ((TextView) dialogView.findViewById(R.id.dialog_common_name)).setText("通用名称：" + item.commonName);
            ((TextView) dialogView.findViewById(R.id.dialog_specification)).setText("规格型号：" + item.specification);
            ((TextView) dialogView.findViewById(R.id.dialog_manufacturer)).setText("生产企业：" + item.manufacturer);
            ((TextView) dialogView.findViewById(R.id.dialog_unit)).setText("单位：" + item.unit);
            
            Log.d(TAG, "Dialog data set successfully");
            
            // 获取屏幕宽度和高度
            android.view.Display display = getWindowManager().getDefaultDisplay();
            android.graphics.Point size = new android.graphics.Point();
            display.getSize(size);
            int screenWidth = size.x;
            int screenHeight = size.y;
            
            // 设置弹窗宽度与明细卡片一致（屏幕宽度减去32dp边距）
            int popupWidth = screenWidth - (int) (32 * getResources().getDisplayMetrics().density);
            
            // 设置弹窗高度：两个耗材卡片高度 + 8dp间距
            int popupHeight = (int) (388 * getResources().getDisplayMetrics().density); // 190dp * 2 + 8dp
            
            // 创建PopupWindow，设置固定宽度和高度
            currentDialog = new PopupWindow(dialogView, 
                popupWidth, 
                popupHeight);
            
            // 设置PopupWindow属性 - 增强焦点管理
            currentDialog.setFocusable(false); // 关键：不让PopupWindow抢夺焦点
            currentDialog.setOutsideTouchable(false);
            currentDialog.setBackgroundDrawable(null);
            
            // 设置弹窗关闭监听器，确保焦点恢复
            currentDialog.setOnDismissListener(() -> {
                Log.d(TAG, "Dialog dismissed via OnDismissListener");
                handleDialogDismissed();
            });
            
            Log.d(TAG, "PopupWindow properties set - focusable: false, outsideTouchable: false");
            
            // 设置按钮事件 - 增强版本
            dialogView.findViewById(R.id.dialog_cancel_button).setOnClickListener(v -> {
                Log.d(TAG, "Cancel button clicked");
                dismissDialogEnhanced();
            });
            dialogView.findViewById(R.id.dialog_add_button).setOnClickListener(v -> {
                Log.d(TAG, "Add button clicked");
                // 添加耗材到明细
                addConsumableItem(item);
                // 缓存API返回的实际条码防止重复调用，而不是用户输入的条码
                Log.d(TAG, "Caching API returned barcode: " + item.barcode + " instead of input barcode: " + barcode);
                addBarcodeToCache(item.barcode);
                Log.d(TAG, "API returned barcode cached on add button click: " + item.barcode);

                // 缓存用于提交的完整记录（以RFID为唯一键）
                try {
                    if (item != null) {
                        if (item.getRfid() == null || item.getRfid().isEmpty()) {
                            // 按业务约定，缺少RFID即为异常数据，不做兜底
                            Log.w(TAG, "Skip caching item without RFID. productCode=" + item.getProductCode());
                        } else {
                            selectedItemsByRfid.put(item.getRfid(), item);
                            Log.d(TAG, "Cached item for submit by RFID: id=" + item.getId() + ", rfid=" + item.getRfid() + ", barcode=" + item.getBarcode());
                        }
                    }
                } catch (Exception cacheEx) {
                    Log.w(TAG, "Cache item failed", cacheEx);
                }

                dismissDialogEnhanced();
            });
            
            Log.d(TAG, "About to show PopupWindow");
            
            // 计算显示位置：弹窗上边框与第一个耗材卡片上边框对齐，然后往上偏移16dp
            // 第一个耗材卡片位置 = AppBarLayout高度 + 主内容区域margin-top + 术间选择区域高度
            int appBarHeight = (int) (56 * getResources().getDisplayMetrics().density); // AppBarLayout高度
            int contentMarginTop = (int) (56 * getResources().getDisplayMetrics().density); // 主内容区域margin-top
            int roomSelectionHeight = (int) (72 * getResources().getDisplayMetrics().density); // 术间选择区域高度（56dp内容 + 16dp底部padding）
            int topOffset = appBarHeight + contentMarginTop + roomSelectionHeight - (int) (16 * getResources().getDisplayMetrics().density); // 往上偏移16dp
            
            // 在屏幕顶部对齐显示，与第一个耗材卡片上边框对齐
            currentDialog.showAtLocation(findViewById(android.R.id.content), 
                android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL, 0, topOffset);
            
                        
            Log.d(TAG, "PopupWindow shown successfully - currentDialog.isShowing(): " + currentDialog.isShowing());
            Log.d(TAG, "Dialog showing state set to: " + isDialogShowing);
            
            // 记录弹窗显示后的焦点状态
            logFocusState("after_dialog_show");
            
            Log.d(TAG, "=== SHOW_CONSUMABLE_INFO_DIALOG_ENHANCED END ===");
            
        } catch (Exception e) {
            Log.e(TAG, "Error in showConsumableInfoDialog", e);
            isDialogShowing = false; // 重置状态
            showError("显示弹窗时出错: " + e.getMessage());
        }
    }
    
    private void dismissDialog() {
        dismissDialogEnhanced();
    }
    
    /**
     * 增强版弹窗关闭方法
     * 包含焦点恢复和扫码事件缓存处理
     */
    private void dismissDialogEnhanced() {
        Log.d(TAG, "=== DISMISS_DIALOG_ENHANCED START ===");
        
        try {
            if (currentDialog != null && currentDialog.isShowing()) {
                Log.d(TAG, "Dismissing dialog");
                
                // 记录弹窗关闭前的焦点状态
                logFocusState("before_dialog_dismiss");
                
                currentDialog.dismiss();
                currentDialog = null;
                
                Log.d(TAG, "Dialog dismissed successfully");
            } else {
                Log.d(TAG, "No dialog to dismiss or dialog already dismissed");
            }
            
            // 处理弹窗关闭后的状态恢复
            handleDialogDismissed();
            
        } catch (Exception e) {
            Log.e(TAG, "Error in dismissDialogEnhanced", e);
            // 确保状态重置
            isDialogShowing = false;
            currentDialog = null;
        }
        
        Log.d(TAG, "=== DISMISS_DIALOG_ENHANCED END ===");
    }
    
    /**
     * 处理弹窗关闭后的状态恢复
     * 包含焦点恢复和缓存的扫码事件处理
     */
    private void handleDialogDismissed() {
        Log.d(TAG, "=== HANDLE_DIALOG_DISMISSED START ===");
        
        try {
            // 重置弹窗显示状态
            isDialogShowing = false;
            Log.d(TAG, "Dialog showing state reset to: " + isDialogShowing);
            
            // 恢复输入框焦点
            if (barcodeInput != null) {
                barcodeInput.requestFocus();
            }
            
            // 记录焦点恢复后的状态
            handler.postDelayed(() -> {
                logFocusState("after_dialog_dismiss_focus_recovery");
            }, 200);
            
                        
        } catch (Exception e) {
            Log.e(TAG, "Error in handleDialogDismissed", e);
        }
        
        Log.d(TAG, "=== HANDLE_DIALOG_DISMISSED END ===");
    }
    
        
    private void addConsumableItem(ConsumableItem item) {
        try {
            String productCode = item.productCode;
            
            // 检查是否已存在相同商品编号的分组
            if (productCodeGroups.containsKey(productCode)) {
                // 累加数量
                ConsumableGroupItem groupItem = productCodeGroups.get(productCode);
                groupItem.quantity++;
                // 以RFID收集唯一件
                if (item.getRfid() != null && !item.getRfid().isEmpty()) {
                    groupItem.rfids.add(item.getRfid());
                }
                
                // 更新列表显示
                int position = consumableGroupList.indexOf(groupItem);
                if (position >= 0) {
                    detailAdapter.notifyItemChanged(position);
                }
            } else {
                // 创建新的分组
                ConsumableGroupItem groupItem = new ConsumableGroupItem(item);
                productCodeGroups.put(productCode, groupItem);
                
                // 添加到列表顶部
                consumableGroupList.add(0, groupItem);
                detailAdapter.notifyItemInserted(0);
                // 通知其他项目序号变化
                detailAdapter.notifyItemRangeChanged(1, consumableGroupList.size() - 1);
                
                // 滚动到顶部
                detailRecyclerView.scrollToPosition(0);
            }
            
            updateTotalCountDisplay();
            
            Log.d(TAG, "addConsumableItem completed");
        } catch (Exception e) {
            Log.e(TAG, "Error in addConsumableItem", e);
        }
    }
    
    private void removeConsumableItem(int position) {
        try {
            Log.d(TAG, "=== REMOVE_CONSUMABLE_ITEM START ===");
            Log.d(TAG, "Removing item at position: " + position);
            Log.d(TAG, "Current consumableGroupList size: " + consumableGroupList.size());
            Log.d(TAG, "Current addedBarcodes cache size: " + addedBarcodes.size());
            Log.d(TAG, "Current addedBarcodes content: " + addedBarcodes);
            
            if (position >= 0 && position < consumableGroupList.size()) {
                ConsumableGroupItem groupItem = consumableGroupList.get(position);
                
                Log.d(TAG, "Removing groupItem - productCode: " + groupItem.productCode);
                Log.d(TAG, "GroupItem rfids count: " + groupItem.rfids.size());
                Log.d(TAG, "GroupItem rfids: " + groupItem.rfids);

                // 从条码缓存中移除：通过rfid找到对应的scan barcode再移除
                int removedCount = 0;
                for (String rfid : groupItem.rfids) {
                    ConsumableItem cached = selectedItemsByRfid.get(rfid);
                    if (cached != null && cached.getBarcode() != null) {
                        boolean removed = addedBarcodes.remove(cached.getBarcode());
                        Log.d(TAG, "Barcode remove result - barcode: " + cached.getBarcode() + ", removed: " + removed);
                        if (removed) removedCount++;
                    }
                    // 同时清理提交缓存
                    selectedItemsByRfid.remove(rfid);
                }
                Log.d(TAG, "Total barcodes removed from cache: " + removedCount);
                Log.d(TAG, "addedBarcodes cache size after removal: " + addedBarcodes.size());
                Log.d(TAG, "addedBarcodes content after removal: " + addedBarcodes);
                
                // 从分组映射中移除
                productCodeGroups.remove(groupItem.productCode);
                Log.d(TAG, "Removed from productCodeGroups, current size: " + productCodeGroups.size());
                
                // 从列表中移除
                consumableGroupList.remove(position);
                detailAdapter.notifyItemRemoved(position);
                detailAdapter.notifyItemRangeChanged(position, consumableGroupList.size());
                
                updateTotalCountDisplay();
                
                Log.d(TAG, "Item removal completed successfully");
            } else {
                Log.w(TAG, "Invalid position for removal: " + position + ", list size: " + consumableGroupList.size());
            }
            
            Log.d(TAG, "=== REMOVE_CONSUMABLE_ITEM END ===");
        } catch (Exception e) {
            Log.e(TAG, "Error in removeConsumableItem", e);
            // 打印更详细的错误信息
            Log.e(TAG, "Error details - position: " + position + 
                  ", list size: " + (consumableGroupList != null ? consumableGroupList.size() : "null") +
                  ", cache size: " + (addedBarcodes != null ? addedBarcodes.size() : "null"));
        }
    }
    
    private void clearAllItems() {
        try {
            consumableGroupList.clear();
            productCodeGroups.clear();
            detailAdapter.notifyDataSetChanged();
            updateTotalCountDisplay();
            
            // 清除条码缓存
            clearBarcodeCache();
            
            // 如果是查看模式，重置术间字段
            if (isViewMode) {
                if (roomSpinner != null) {
                    roomSpinner.setText("");
                }
            }
            
            Log.d(TAG, "clearAllItems completed");
        } catch (Exception e) {
            Log.e(TAG, "Error in clearAllItems", e);
        }
    }

    /**
     * 根据模式更新界面
     * viewMode=true: 查看模式（隐藏清屏，右侧按钮为“新建取用”，术间与移除置灰）
     * viewMode=false: 新建模式（显示清屏，右侧按钮为“确认取用”，术间与移除可用）
     */
    private void updateModeUI(boolean viewMode) {
        try {
            isViewMode = viewMode;

            // 明细适配器：控制“移除”按钮可用性
            if (detailAdapter != null) {
                detailAdapter.setViewMode(viewMode);
            }

            // 术间置灰/可用
            if (roomInputLayout != null) {
                roomInputLayout.setEnabled(!viewMode);
            }
            if (roomSpinner != null) {
                roomSpinner.setEnabled(!viewMode);
            }

            // 清屏按钮显示/隐藏
            if (clearButton != null) {
                clearButton.setVisibility(viewMode ? View.GONE : View.VISIBLE);
            }

            // 确认/新建按钮
            if (confirmButton != null) {
                confirmButton.setEnabled(true);
                if (viewMode) {
                    // 查看模式：根据取用/归还模式设置“新建取用/新建归还”
                    if (operationMode == OperationMode.RETURN) {
                        confirmButton.setText(R.string.consumable_new_return);
                    } else {
                        confirmButton.setText(R.string.consumable_new_checkout);
                    }
                    confirmButton.setOnClickListener(v -> startNewCheckout());
                } else {
                    confirmButton.setText(R.string.consumable_confirm);
                    confirmButton.setOnClickListener(v -> confirmCheckout());
                }
            }

            // 扫码输入框启用/禁用 + 清理与焦点管理（通过控制器统一处理）
            if (scanController == null && barcodeInput != null) {
                scanController = new ScanInputController(barcodeInput);
                originalBarcodeKeyListener = scanController.getOriginalKeyListener();
            }
            if (scanController != null) {
                if (viewMode) {
                    scanController.disableForViewMode(this);
                } else {
                    scanController.enableForNewCheckout();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in updateModeUI", e);
        }
    }

    private void applyUiState(CheckoutUiState state) {
        try {
            currentUiState = state;
            switch (state) {
                case NEW_CHECKOUT:
                    updateModeUI(false);
                    // 解锁历史抽屉，并允许左侧边缘开启
                    try {
                        if (drawerLayout != null) drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
                        if (leftEdgeIndicator != null) leftEdgeIndicator.setClickable(true);
                        if (confirmButton != null) {
                            confirmButton.setEnabled(true);
                            confirmButton.setText(R.string.consumable_confirm);
                        }
                        if (clearButton != null) clearButton.setEnabled(true);
                        if (logoutButton != null) logoutButton.setEnabled(true);
                    } catch (Exception ignore) {}
                    break;
                case VIEW_RECORD:
                    updateModeUI(true);
                    // 解锁历史抽屉，并允许左侧边缘开启
                    try {
                        if (drawerLayout != null) drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
                        if (leftEdgeIndicator != null) leftEdgeIndicator.setClickable(true);
                    } catch (Exception ignore) {}
                    break;
                case SUBMITTING:
                    // 提交中：禁用交互，防止重复提交或误操作
                    try {
                        if (confirmButton != null) confirmButton.setEnabled(false);
                        if (confirmButton != null) confirmButton.setText(R.string.consumable_submitting);
                        if (clearButton != null) clearButton.setEnabled(false);
                        if (logoutButton != null) logoutButton.setEnabled(false);
                        if (roomInputLayout != null) roomInputLayout.setEnabled(false);
                        if (roomSpinner != null) roomSpinner.setEnabled(false);
                        if (scanController == null && barcodeInput != null) {
                            scanController = new ScanInputController(barcodeInput);
                            originalBarcodeKeyListener = scanController.getOriginalKeyListener();
                        }
                        if (scanController != null) scanController.disableForSubmitting(this);
                        // 锁定历史抽屉
                        if (drawerLayout != null) {
                            drawerLayout.closeDrawer(navView);
                            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
                        }
                        if (leftEdgeIndicator != null) leftEdgeIndicator.setClickable(false);
                    } catch (Exception ignore) {}
                    break;
                case DIALOG_OPEN:
                    // 预留
                    break;
                default:
                    updateModeUI(false);
            }
            // 状态切换后按模式调整一次UI（确保确认按钮文案与术间显隐一致）
            applyOperationModeUi();
        } catch (Exception e) {
            Log.e(TAG, "Error in applyUiState", e);
        }
    }

    /**
     * 扫码输入控制器：封装条码输入框的启用/禁用/焦点/清空逻辑
     */
    private static class ScanInputController {
        private final TextInputEditText input;
        private final KeyListener original;

        ScanInputController(TextInputEditText input) {
            this.input = input;
            this.original = input.getKeyListener();
        }

        KeyListener getOriginalKeyListener() { return original; }

        void disableForViewMode(Context ctx) {
            try {
                input.setText("");
                input.setEnabled(false);
                input.setAlpha(0.6f);
                input.setKeyListener(null);
                input.clearFocus();
                if (ctx instanceof android.app.Activity) {
                    View decor = ((android.app.Activity) ctx).getWindow().getDecorView();
                    if (decor != null) {
                        decor.setFocusable(true);
                        decor.setFocusableInTouchMode(true);
                        decor.requestFocus();
                    }
                }
            } catch (Exception ignore) {}
        }

        void enableForNewCheckout() {
            try {
                input.setEnabled(true);
                input.setAlpha(1.0f);
                if (original != null) input.setKeyListener(original);
                input.setText("");
                input.requestFocus();
            } catch (Exception ignore) {}
        }

        void disableForSubmitting(Context ctx) {
            try {
                input.setEnabled(false);
                input.setAlpha(0.6f);
                input.clearFocus();
                if (ctx instanceof android.app.Activity) {
                    View decor = ((android.app.Activity) ctx).getWindow().getDecorView();
                    if (decor != null) {
                        decor.setFocusable(true);
                        decor.setFocusableInTouchMode(true);
                        decor.requestFocus();
                    }
                }
            } catch (Exception ignore) {}
        }

        void clear() { try { input.setText(""); } catch (Exception ignore) {} }
        void focus() { try { input.requestFocus(); } catch (Exception ignore) {} }
    }

    /**
     * 从查看模式切回新建取用
     */
    private void startNewCheckout() {
        try {
            clearAllItems();
            applyUiState(CheckoutUiState.NEW_CHECKOUT);
            if (barcodeInput != null) {
                barcodeInput.requestFocus();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in startNewCheckout", e);
        }
    }
    
    private void updateTotalCountDisplay() {
        try {
            totalCount = 0;
            for (ConsumableGroupItem groupItem : consumableGroupList) {
                totalCount += groupItem.quantity;
            }
            totalCountInput.setText(String.valueOf(totalCount));
            
            Log.d(TAG, "updateTotalCountDisplay completed, total: " + totalCount);
        } catch (Exception e) {
            Log.e(TAG, "Error in updateTotalCountDisplay", e);
        }
    }
    
    // BaseActivity已统一处理用户信息显示，移除重复逻辑
    
    private void confirmCheckout() {
        try {
            String selectedRoom = roomSpinner.getText().toString().trim();
            // 取用需选择术间；归还无需术间
            if (operationMode == OperationMode.TAKE) {
                if (TextUtils.isEmpty(selectedRoom)) {
                    roomInputLayout.setError(getString(R.string.consumable_room_required));
                    return;
                }
            }
            
            if (consumableGroupList.isEmpty()) {
                showError("请先扫描耗材");
                return;
            }
            // 进入提交中状态，禁用交互
            applyUiState(CheckoutUiState.SUBMITTING);
            // 使用缓存数据构造提交明细（以RFID为唯一键）
            List<ConsumableItem> takeItems = new ArrayList<>();
            List<ConsumableItem> returnItems = new ArrayList<>();
            for (ConsumableGroupItem groupItem : consumableGroupList) {
                for (String rfid : groupItem.rfids) {
                    ConsumableItem full = selectedItemsByRfid.get(rfid);
                    if (full == null || full.getRfid() == null || full.getRfid().isEmpty()) {
                        showError("数据不完整，建议重新扫码: " + rfid);
                        Log.w(TAG, "缺少必要字段: id=" + (full==null?"null":full.getId()) +
                                ", rfid=" + (full==null?"null":full.getRfid()) +
                                ", code=" + (full==null?"null":full.getProductCode()));
                        return;
                    }
                    if (operationMode == OperationMode.TAKE) takeItems.add(full); else returnItems.add(full);
                }
            }

            // 解析地点ID
            Integer operationRoomId = null; // 此处复用字段名，实际含义为地点ID
            try {
                OperatingRoomManager orm = OperatingRoomManager.getInstance(this);
                List<OperatingRoom> rooms = orm.getAllRooms();
                if (rooms != null) {
                    for (OperatingRoom r : rooms) {
                        if (r != null && r.name != null && r.name.equals(selectedRoom)) {
                            operationRoomId = r.serverId; // 选择的去向地点ID
                            break;
                        }
                    }
                }
            } catch (Exception ignore) {}

            // 归还模式的默认去向地点（本地首选项）
            String defaultReturnTo = null;
            try {
                android.content.SharedPreferences sp = getSharedPreferences("locations_prefs", MODE_PRIVATE);
                int defId = sp.getInt("default_return_location_id", -1);
                if (defId > 0) defaultReturnTo = String.valueOf(defId);
            } catch (Exception ignore) {}

            showInfo(operationMode == OperationMode.TAKE ? "正在提交取用数据..." : "正在提交归还数据...");

            // 按 from(current_location_id) 分组逐个提交
            java.util.Map<Integer, java.util.List<ConsumableItem>> groups = new java.util.HashMap<>();
            if (operationMode == OperationMode.TAKE) {
                for (ConsumableItem it : takeItems) {
                    Integer fid = it.getCurrentLocationId();
                    int key = (fid != null && fid > 0) ? fid : 0;
                    groups.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(it);
                }
            } else {
                for (ConsumableItem it : returnItems) {
                    Integer fid = it.getCurrentLocationId();
                    int key = (fid != null && fid > 0) ? fid : 0;
                    groups.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(it);
                }
            }

            final java.util.concurrent.atomic.AtomicInteger pending = new java.util.concurrent.atomic.AtomicInteger(groups.size());
            final boolean[] failed = new boolean[]{false};
            final Long[] lastServerRecordId = new Long[]{null};

            if (groups.isEmpty()) {
                showError("没有可提交的数据");
                applyUiState(CheckoutUiState.NEW_CHECKOUT);
                return;
            }

            for (java.util.Map.Entry<Integer, java.util.List<ConsumableItem>> entry : groups.entrySet()) {
                final String fromIdStr = entry.getKey() > 0 ? String.valueOf(entry.getKey()) : null;
                java.util.List<ConsumableItem> thisTake = (operationMode == OperationMode.TAKE) ? entry.getValue() : new java.util.ArrayList<>();
                java.util.List<ConsumableItem> thisReturn = (operationMode == OperationMode.RETURN) ? entry.getValue() : new java.util.ArrayList<>();

                apiService.createTakeAndReturnLog(
                        operationMode == OperationMode.TAKE ? operationRoomId : null,
                        thisTake,
                        thisReturn,
                        fromIdStr,
                        operationMode == OperationMode.TAKE ? (operationRoomId == null ? null : String.valueOf(operationRoomId)) : defaultReturnTo,
                        new ConsumableApiService.ApiCallback<ConsumableApiService.CreateTakeAndReturnResult>() {
                            @Override
                            public void onSuccess(ConsumableApiService.CreateTakeAndReturnResult result) {
                                if (result != null && result.recordId != null) lastServerRecordId[0] = result.recordId;
                                int left = pending.decrementAndGet();
                                if (left == 0 && !failed[0]) {
                                    runOnUiThread(() -> {
                                        String operator = null;
                                        try { UserInfo u = sessionManager.getCurrentUser(); if (u != null) operator = u.getDisplayName(); } catch (Exception ignore) {}
                                        if (operator == null || operator.isEmpty()) operator = recordManager.getCurrentUser();
                                        boolean ok;
                                        if (operationMode == OperationMode.TAKE) {
                                            ok = recordManager.generateCheckoutRecord(selectedRoom, totalCount, takeItems, operator, lastServerRecordId[0]);
                                        } else {
                                            ok = recordManager.generateReturnRecord(totalCount, returnItems, operator, lastServerRecordId[0]);
                                        }
                                        if (ok) {
                                            showSuccess((operationMode == OperationMode.TAKE ? "取用成功！共 " : "归还成功！共 ") + totalCount + " 项");
                                            clearAllItems();
                                            if (operationMode == OperationMode.TAKE && roomSpinner != null) roomSpinner.setText("");
                                            loadHistoryRecords(TimeFilter.TODAY);
                                            applyUiState(CheckoutUiState.NEW_CHECKOUT);
                                        } else {
                                            Log.e(TAG, "本地记录保存失败: room=" + selectedRoom + ", totalCount=" + totalCount);
                                            showError("本地记录保存失败");
                                            applyUiState(CheckoutUiState.NEW_CHECKOUT);
                                        }
                                    });
                                }
                            }

                            @Override
                            public void onFailure(Exception e) {
                                failed[0] = true;
                                runOnUiThread(() -> {
                                    String msg = UIUtils.friendlyErrorMessage("提交失败", e);
                                    showError(msg);
                                    applyUiState(CheckoutUiState.NEW_CHECKOUT);
                                });
                            }
                        }
                );
            }

        } catch (Exception e) {
            Log.e(TAG, "Error in confirmCheckout", e);
            showError(UIUtils.friendlyErrorMessage(operationMode == OperationMode.TAKE ? "取用失败" : "归还失败", e));
            // 异常时确保恢复交互
            applyUiState(CheckoutUiState.NEW_CHECKOUT);
        }
    }
    
    // 移除时间过滤功能，直接显示今日记录
    
    private void loadHistoryRecords(TimeFilter filter) {
        try {
            String type = (operationMode == OperationMode.RETURN ? "return" : "checkout");
            List<CheckoutRecord> records = recordManager.getRecordsByTimeFilter(filter, currentUserId, type);
            
            if (records != null) {
                historyRecordList.clear();
                historyRecordList.addAll(records);
                historyAdapter.updateRecords(historyRecordList);
                
                Log.d(TAG, "Loaded " + records.size() + " records for filter: " + filter);
            } else {
                Log.e(TAG, "Failed to load records for filter: " + filter);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in loadHistoryRecords", e);
        }
    }
    
    private void onHistoryRecordClick(CheckoutRecord record) {
        try {
            // 加载记录到领用界面
            loadRecordToCheckoutInterface(record);
            
            // 关闭侧边栏
            if (drawerLayout != null) {
                drawerLayout.closeDrawer(navView);
            }
            
            Log.d(TAG, "History record clicked: " + record.recordId);
        } catch (Exception e) {
            Log.e(TAG, "Error in onHistoryRecordClick", e);
        }
    }
    
    private void loadRecordToCheckoutInterface(CheckoutRecord record) {
        try {
            // 清空当前数据
            clearAllItems();
            
            // 设置术间
            if (roomSpinner != null) {
                roomSpinner.setText(record.operatingRoom, false);
            }
            
            // 重新构建耗材分组数据
            productCodeGroups.clear();
            consumableGroupList.clear();
            
            if (record.items != null) {
                for (ConsumableItem item : record.items) {
                    // 检查是否已存在相同商品编号的分组
                    if (productCodeGroups.containsKey(item.productCode)) {
                        // 累加数量
                        ConsumableGroupItem groupItem = productCodeGroups.get(item.productCode);
                        groupItem.quantity++;
                        if (item.getRfid() != null && !item.getRfid().isEmpty()) {
                            groupItem.rfids.add(item.getRfid());
                        }
                    } else {
                        // 创建新的分组
                        ConsumableGroupItem groupItem = new ConsumableGroupItem(item);
                        productCodeGroups.put(item.productCode, groupItem);
                        consumableGroupList.add(groupItem);
                    }
                }
                
                // 更新列表显示
                detailAdapter.notifyDataSetChanged();
                updateTotalCountDisplay();
                
                // 标记记录为已加载
                record.isLoaded = true;
                
                // 进入查看模式：隐藏清屏，右侧按钮切为“新建取用”，术间与移除置灰
                applyUiState(CheckoutUiState.VIEW_RECORD);
                
                showSuccess("已加载取用记录：" + record.recordId);
                Log.d(TAG, "Record loaded to interface: " + record.recordId);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error in loadRecordToCheckoutInterface", e);
            showError("加载记录失败：" + e.getMessage());
        }
    }
    
        
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        
        Log.d(TAG, "=== ON_WINDOW_FOCUS_CHANGED ===");
        Log.d(TAG, "Window focus changed: " + hasFocus);
        
        // 当窗口获得焦点时，确保条码输入框有焦点（查看模式下不抢焦点）。不在此处强制隐藏输入法，避免闪现。
        if (hasFocus && barcodeInput != null) {
            if (isViewMode) {
                Log.d(TAG, "Window has focus, in view mode - skip focusing barcode input");
                return;
            }
            Log.d(TAG, "Window has focus, requesting focus for barcode input");
            barcodeInput.post(() -> {
                Log.d(TAG, "=== WINDOW FOCUS HANDLER START ===");
                
                // 清除其他View的焦点
                if (drawerLayout != null) drawerLayout.clearFocus();
                if (roomSpinner != null) roomSpinner.clearFocus();
                if (totalCountInput != null) totalCountInput.clearFocus();
                
                // 强制设置焦点状态
                barcodeInput.setFocusable(true);
                barcodeInput.setFocusableInTouchMode(true);
                barcodeInput.setActivated(true);
                barcodeInput.setSelected(true);
                
                // 确保输入框获得焦点
                boolean windowFocusResult = barcodeInput.requestFocus();
                boolean windowFocusFromTouchResult = barcodeInput.requestFocusFromTouch();
                
                Log.d(TAG, "windowFocus requestFocus() result: " + windowFocusResult);
                Log.d(TAG, "windowFocus requestFocusFromTouch() result: " + windowFocusFromTouchResult);
                Log.d(TAG, "After window focus request - focusable: " + barcodeInput.isFocusable() + 
                      ", focused: " + barcodeInput.isFocused());
                // 不做IME隐藏，IME已通过属性与控件设置禁止显示
                
                // 检查当前焦点在哪个控件上
                android.view.View currentFocus = getCurrentFocus();
                if (currentFocus != null) {
                    Log.d(TAG, "windowFocus current focus: " + currentFocus.getId() + " (" + currentFocus.getClass().getSimpleName() + ")");
                } else {
                    Log.d(TAG, "windowFocus no view has focus");
                }
                
                Log.d(TAG, "=== WINDOW FOCUS HANDLER END ===");
            });
        }
        
        Log.d(TAG, "=== ON_WINDOW_FOCUS_CHANGED END ===");
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        try {
            if (isViewMode) {
                int keyCode = event.getKeyCode();
                // 屏蔽回车和任何可打印字符，防止查看模式下注入
                if (keyCode == KeyEvent.KEYCODE_ENTER) {
                    return true;
                }
                int unicode = event.getUnicodeChar();
                if (unicode != 0) {
                    return true;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in dispatchKeyEvent guard", e);
        }
        return super.dispatchKeyEvent(event);
    }

    
    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    private void logout() {
        try {
            // 记录登出日志
            UserInfo currentUserInfo = sessionManager.getCurrentUser();
            if (currentUserInfo != null) {
                AuditLogger.logLogout(currentUserInfo.getUserId(), sessionManager.getDeviceId());
            }
            
            // 清除会话
            sessionManager.endSession("user_logout");
            
            // 清除权限缓存
            permissionManager.clearCachedPermissions();
            
            // 显示登出成功提示
            showSuccess("登出成功");
            
            // 跳转到登录界面（清空任务栈，返回即退出应用）
            Intent intent = new Intent(this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            
            Log.d(TAG, "Logout completed successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error in logout", e);
            showError("登出失败：" + e.getMessage());
        }
    }
    
    // 注意：ConsumableItem类已移动到独立文件中
    
    // 耗材分组数据类
    public static class ConsumableGroupItem {
        public int serialNo;
        public String productCode;
        public String commonName;
        public String specification;
        public String manufacturer;
        public int quantity;
        public String unit;
        public List<String> rfids;
        public boolean isExpanded;
        
        public ConsumableGroupItem(ConsumableItem item) {
            this.serialNo = 1; // 序号会在适配器中重新设置
            this.productCode = item.productCode;
            this.commonName = item.commonName;
            this.specification = item.specification;
            this.manufacturer = item.manufacturer;
            this.quantity = item.quantity;
            this.unit = item.unit;
            this.rfids = new ArrayList<>();
            if (item.getRfid() != null && !item.getRfid().isEmpty()) {
                this.rfids.add(item.getRfid());
            }
            this.isExpanded = false;
        }
    }
    
    @Override
    protected void onPause() {
        Log.d(TAG, "=== ON_PAUSE START ===");
        super.onPause();
        
        Log.d(TAG, "=== ON_PAUSE END ===");
    }
    
    @Override
    protected void onDestroy() {
        Log.d(TAG, "=== ON_DESTROY START ===");
        super.onDestroy();
        
                            
        // 统一使用 AppToast，无需本地 Toast 去重状态
        
        // 清理弹窗相关状态
        isDialogShowing = false;
        if (currentDialog != null && currentDialog.isShowing()) {
            currentDialog.dismiss();
            currentDialog = null;
        }
        
        Log.d(TAG, "=== ON_DESTROY END ===");
    }
    
        
    // ========= 辅助方法 =========
    
}
