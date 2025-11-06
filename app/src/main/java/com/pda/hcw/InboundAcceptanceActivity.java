package com.pda.hcw;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 入库验收
 * - 顶部条码框支持单个扫码验收
 * - 支持RFID批量验收（EPC解析规则与盘点一致）
 * - 仅展示未全部上架的单据
 * - 每单支持“验收(落库)”与“清空本次增量”
 */
public class InboundAcceptanceActivity extends BaseActivity {
    private static final String TAG = "InboundAcceptance";

    private EditText barcodeInput;
    private RecyclerView orderList;
    private ProgressBar loading;
    private TextView emptyText;

    private final List<InboundOrder> orders = new ArrayList<>();
    private final Map<String, Set<String>> deltaByOrder = new HashMap<>(); // orderCode -> rfids in this run
    private final Map<String, ConsumableItem> rfidInfoCache = new HashMap<>(); // rfid -> item (懒加载)
    private InboundOrderAdapter adapter;

    private InboundApiService inboundApi;
    private com.google.android.material.button.MaterialButton rfidToggleButton;

    // ===== RFID 批量验收 =====
    private com.pda.hcw.rfid.UHFManager uhfManager;
    private com.pda.hcw.rfid.RfidBeepManager rfidBeepManager;
    private boolean isRFIDActive = false;
    private android.os.HandlerThread rfidThread;
    private Handler rfidBgHandler;
    private Runnable rfidRunnable;
    private BroadcastReceiver keyReceiver;
    private final Set<String> seenEpcsThisRun = new HashSet<>();
    private boolean beepModeRaw = false;
    private int emptyReadRounds = 0;
    // 条码/按键仲裁
    private long lastBarcodeEventAt = 0L;
    private Runnable pendingUhfToggle;
    private long funKeyDownAt = 0L;
    private int funKeyDownCode = 0;
    private boolean funKeyLongPressFired = false;
    private long getUhfTriggerDelayMs() {
        try {
            return getSharedPreferences("settings_prefs", MODE_PRIVATE).getLong("uhf_trigger_delay_ms", 250L);
        } catch (Throwable ignore) { return 250L; }
    }

    private long getUhfLongPressThresholdMs() {
        try {
            return getSharedPreferences("settings_prefs", MODE_PRIVATE).getLong("uhf_long_press_ms", 2000L);
        } catch (Throwable ignore) { return 2000L; }
    }

    private boolean isRecentBarcodeEvent(long now, long windowMs) {
        return (now - lastBarcodeEventAt) <= windowMs;
    }

    @Override
    protected int getLayoutResourceId() { return R.layout.activity_inbound_acceptance; }
    @Override
    protected String getActivityName() { return "入库验收"; }
    @Override
    protected String getActivityTitle() { return getString(R.string.inbound_acceptance_title); }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            // 关闭系统默认焦点高亮，避免出现整块蓝色高亮（功能由回焦与拦截保证）
            try { if (android.os.Build.VERSION.SDK_INT >= 26) getWindow().getDecorView().setDefaultFocusHighlightEnabled(false); } catch (Throwable ignore) {}
            barcodeInput = findViewById(R.id.barcode_input);
            orderList = findViewById(R.id.inbound_order_list);
            loading = findViewById(R.id.loading);
            emptyText = findViewById(R.id.empty_text);

            // 空态提示仅用于说明，不可获得焦点/不可点击，避免出现“选中蓝块”错觉
            try {
                if (emptyText != null) {
                    emptyText.setClickable(false);
                    emptyText.setFocusable(false);
                    emptyText.setFocusableInTouchMode(false);
                }
            } catch (Throwable ignore) {}

            orderList.setLayoutManager(new LinearLayoutManager(this));
            try {
                orderList.setFocusable(false);
                orderList.setFocusableInTouchMode(false);
                orderList.setDescendantFocusability(android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS);
                orderList.setOnFocusChangeListener((v, hasFocus) -> { if (hasFocus) refocusBarcode(); });
            } catch (Throwable ignore) {}
            adapter = new InboundOrderAdapter(orders, new InboundOrderAdapter.Listener() {
                @Override public void onAccept(InboundOrder order) { submitAcceptance(order); }
                @Override public void onClearDelta(InboundOrder order) { clearDelta(order); }
            });
            orderList.setAdapter(adapter);

            inboundApi = InboundApiService.getInstance(this);

            // 顶部条码框：回车触发单个验收
            barcodeInput.setOnEditorActionListener((v, actionId, event) -> {
                lastBarcodeEventAt = System.currentTimeMillis();
                if (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN) {
                    handleSingleScan(v.getText() == null ? "" : v.getText().toString());
                    // 维持光标在条码框，且不弹出软键盘
                    try { barcodeInput.requestFocus(); } catch (Throwable ignore) {}
                    try {
                        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                        if (imm != null) imm.hideSoftInputFromWindow(barcodeInput.getWindowToken(), 0);
                    } catch (Throwable ignore) {}
                    return true;
                }
                return false;
            });
            // 吞掉TAB/DPAD，避免键盘导航将焦点移出条码框
            try {
                barcodeInput.setOnKeyListener((v, keyCode, ev) -> {
                    if (ev != null && ev.getAction() == KeyEvent.ACTION_DOWN) {
                        switch (keyCode) {
                            case KeyEvent.KEYCODE_TAB:
                            case KeyEvent.KEYCODE_DPAD_DOWN:
                            case KeyEvent.KEYCODE_DPAD_UP:
                            case KeyEvent.KEYCODE_DPAD_LEFT:
                            case KeyEvent.KEYCODE_DPAD_RIGHT:
                                lastBarcodeEventAt = System.currentTimeMillis();
                                refocusBarcode();
                                return true;
                        }
                    }
                    return false;
                });
            } catch (Throwable ignore) {}
            // 监听输入变化，记录最后条码事件时间（仲裁UHF切换）
            try {
                barcodeInput.addTextChangedListener(new android.text.TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                    @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                        if (count > 0) lastBarcodeEventAt = System.currentTimeMillis();
                    }
                    @Override public void afterTextChanged(android.text.Editable s) {}
                });
            } catch (Throwable ignore) {}

            // 初始聚焦到条码框并禁止软键盘
            try {
                barcodeInput.setShowSoftInputOnFocus(false);
            } catch (Throwable ignore) {}
            barcodeInput.post(() -> {
                try {
                    barcodeInput.setFocusable(true);
                    barcodeInput.setFocusableInTouchMode(true);
                    barcodeInput.setActivated(true);
                    barcodeInput.setSelected(true);
                    barcodeInput.requestFocus();
                    barcodeInput.requestFocusFromTouch();
                } catch (Throwable ignore) {}
            });

            // 屏内按钮已移除；仍保留物理键切换能力（此处不再引用已删除的按钮ID）
            rfidToggleButton = null;
            updateRfidToggleButton();

            // 配置自检：缺失sdCode或货柜ID时，顶部横幅提示
            if (!isInboundConfigReady()) {
                // 仅提示，不阻断页面；用户可先去设置
            }

            // 初始拉取：仅搭建骨架，解析待上架清单将于下一步完善
            loadPendingOrders();
        } catch (Exception e) {
            Log.e(TAG, "init failed", e);
            showError("初始化失败");
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sessionManager != null) sessionManager.updateActivity();
        registerKeyReceiver();
        // 恢复对条码框的聚焦
        try {
            if (barcodeInput != null) {
                barcodeInput.requestFocus();
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(barcodeInput.getWindowToken(), 0);
            }
        } catch (Throwable ignore) {}
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sessionManager != null) sessionManager.updateActivity();
        unregisterKeyReceiver();
        if (isRFIDActive) stopRFIDScanning();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { if (rfidBeepManager != null) rfidBeepManager.release(); } catch (Throwable ignore) {}
        try { if (uhfManager != null) uhfManager.stopAllOperations(); } catch (Throwable ignore) {}
    }

    private void loadPendingOrders() {
        try {
            showLoading(true);
            inboundApi.fetchPendingOrders(new ConsumableApiService.ApiCallback<List<InboundOrder>>() {
                @Override public void onSuccess(List<InboundOrder> data) {
                    showLoading(false);
                    orders.clear();
                    if (data != null) {
                        for (InboundOrder o : data) {
                            // 仅显示未全部上架
                            if (o != null && (o.accepted < o.total)) orders.add(o);
                        }
                    }
                    adapter.notifyDataSetChanged();
                    updateEmpty();
                }
                @Override public void onFailure(Exception e) {
                    showLoading(false);
                    showError(UIUtils.friendlyErrorMessage("加载待上架清单失败", e));
                    updateEmpty();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "loadPendingOrders error", e);
            showLoading(false);
            updateEmpty();
        }
    }

    private void showLoading(boolean show) {
        loading.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void updateEmpty() {
        boolean isEmpty = orders.isEmpty();
        if (isEmpty) {
            try { emptyText.setText(getString(R.string.inbound_no_orders)); } catch (Throwable ignore) {}
            emptyText.setVisibility(View.VISIBLE);
            if (orderList != null) orderList.setVisibility(View.GONE);
        } else {
            emptyText.setVisibility(View.GONE);
            if (orderList != null) orderList.setVisibility(View.VISIBLE);
        }
        // 统一将焦点回到条码输入框，避免空态或容器获得焦点后出现按压高亮
        refocusBarcode();
    }

    private void refocusBarcode() {
        try {
            if (barcodeInput != null) {
                if (orderList != null) orderList.clearFocus();
                barcodeInput.setFocusable(true);
                barcodeInput.setFocusableInTouchMode(true);
                barcodeInput.setActivated(true);
                barcodeInput.setSelected(true);
                barcodeInput.requestFocus();
                try { barcodeInput.requestFocusFromTouch(); } catch (Throwable ignore) {}
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(barcodeInput.getWindowToken(), 0);
            }
        } catch (Throwable ignore) {}
    }

    private void handleSingleScan(String input) {
        String s = input == null ? "" : input.trim();
        if (s.isEmpty()) return;
        barcodeInput.setText("");
        try { barcodeInput.requestFocus(); } catch (Throwable ignore) {}
        try {
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(barcodeInput.getWindowToken(), 0);
        } catch (Throwable ignore) {}

        // 先按取用页规则进行条码格式校验（如配置了前缀/长度）
        try {
            BarcodeValidator validator = BarcodeValidator.getInstance(this);
            if (validator != null && !validator.validateBarcode(s)) {
                showError(validator.getInvalidBarcodeMessage());
                return;
            }
        } catch (Exception ignore) {}

        // 接收即格式化（只按两条合规规则；未命中则保留原值）
        String rfid = com.pda.hcw.rfid.EpcFormatter.normalizeOnReceive(s);
        if (rfid == null || rfid.isEmpty()) {
            // 非上架RFID：自动过滤，不做提示
            return;
        }
        // 只处理属于“待上架单”的RFID；未命中则忽略（自动过滤）
        InboundOrder target = findOrderByPlannedRfid(rfid);
        if (target == null) return;
        addDelta(target, rfid);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            try {
                if (barcodeInput != null) {
                    barcodeInput.post(() -> {
                        try {
                            if (orderList != null) orderList.clearFocus();
                            barcodeInput.setFocusable(true);
                            barcodeInput.setFocusableInTouchMode(true);
                            barcodeInput.setActivated(true);
                            barcodeInput.setSelected(true);
                            barcodeInput.requestFocus();
                            barcodeInput.requestFocusFromTouch();
                        } catch (Throwable ignore) {}
                        try {
                            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                            if (imm != null) imm.hideSoftInputFromWindow(barcodeInput.getWindowToken(), 0);
                        } catch (Throwable ignore) {}
                    });
                }
            } catch (Throwable ignore) {}
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        try {
            int code = event.getKeyCode();
            if (code == KeyEvent.KEYCODE_TAB ||
                code == KeyEvent.KEYCODE_DPAD_UP ||
                code == KeyEvent.KEYCODE_DPAD_DOWN ||
                code == KeyEvent.KEYCODE_DPAD_LEFT ||
                code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                // 拦截TAB/DPAD，防止系统进行焦点搜索导致RecyclerView获焦
                lastBarcodeEventAt = System.currentTimeMillis();
                refocusBarcode();
                return true;
            }
        } catch (Throwable ignore) {}
        return super.dispatchKeyEvent(event);
    }

    // ===== RFID（UHF）批量验收集成 =====
    private void initRFID() {
        if (uhfManager == null) uhfManager = com.pda.hcw.rfid.UHFManager.getInstance();
        if (rfidBeepManager == null) rfidBeepManager = new com.pda.hcw.rfid.RfidBeepManager();
        if (!uhfManager.isInitialized()) {
            boolean ok = uhfManager.initialize(this);
            Log.d(TAG, "UHF initialize: " + ok);
            if (!ok) showError("RFID初始化失败");
        }
    }

    private void toggleRFIDScanning() {
        if (orders.isEmpty()) { showInfo("暂无待上架数据"); return; }
        if (!isInboundConfigReady()) return;
        if (isRFIDActive) {
            stopRFIDScanning();
            showSuccess("已退出RFID盘点，回到条码验收");
        } else {
            startRFIDScanning();
            showSuccess("RFID盘点已启动（长按2秒可退出）");
        }
        updateRfidToggleButton();
    }

    private void startRFIDScanning() {
        if (uhfManager == null || !uhfManager.isInitialized()) initRFID();
        if (uhfManager == null || !uhfManager.isInitialized()) { showError("RFID未初始化"); return; }
        if (isRFIDActive) return;
        try { uhfManager.stopAllOperations(); } catch (Exception ignore) {}
        isRFIDActive = true;
        seenEpcsThisRun.clear();
        // 蜂鸣配置
        try {
            boolean multi = getSharedPreferences("settings_prefs", MODE_PRIVATE).getBoolean("rfid_multi_mode", true);
            String mode = getSharedPreferences("settings_prefs", MODE_PRIVATE).getString("rfid_beep_mode", "biz");
            beepModeRaw = "raw".equalsIgnoreCase(mode);
            if (rfidBeepManager != null) { rfidBeepManager.setMultiMode(multi); rfidBeepManager.start(); }
        } catch (Throwable ignore) {}

        // 预热 async
        try { uhfManager.asyncStartReading(); } catch (Throwable t) { Log.w(TAG, "asyncStartReading failed", t); }
        emptyReadRounds = 0;
        // 启动后台轮询
        if (rfidThread == null) {
            rfidThread = new android.os.HandlerThread("inbound-rfid-loop");
            rfidThread.start();
            rfidBgHandler = new Handler(rfidThread.getLooper());
        }
        startRFIDPolling();
    }

    private void stopRFIDScanning() {
        isRFIDActive = false;
        stopRFIDPolling();
        boolean ok = true;
        try {
            boolean s1 = uhfManager.asyncStopReading();
            try { Thread.sleep(30);} catch (InterruptedException ignore) {}
            uhfManager.stopAllOperations();
            ok = s1 && !uhfManager.isReading();
        } catch (Throwable t) { Log.w(TAG, "stopRFIDScanning", t); ok = false; }
        if (!ok && rfidBeepManager != null) new Thread(() -> rfidBeepManager.alarmNotStopped()).start();
        if (rfidBeepManager != null) rfidBeepManager.stop();
        updateRfidToggleButton();
    }

    private void startRFIDPolling() {
        rfidRunnable = new Runnable() {
            @Override public void run() {
                try {
                    if (!isRFIDActive || uhfManager == null || !uhfManager.isInitialized()) return;
                    java.util.List<com.uhf.api.cls.Reader.TAGINFO> tagList = uhfManager.tagInventoryRealTime();
                    if (tagList == null || tagList.isEmpty()) {
                        // 自恢复
                        emptyReadRounds++;
                        if (emptyReadRounds >= 4) {
                            try { uhfManager.asyncStopReading(); Thread.sleep(30); uhfManager.asyncStartReading(); } catch (Throwable ignore) {}
                            emptyReadRounds = 0;
                        }
                    } else {
                        emptyReadRounds = 0;
                        for (com.uhf.api.cls.Reader.TAGINFO t : tagList) handleRfidTag(t);
                    }
                } catch (Throwable e) {
                    Log.e(TAG, "RFID loop error", e);
                }
                if (isRFIDActive && rfidBgHandler != null) rfidBgHandler.postDelayed(this, 200);
            }
        };
        if (rfidBgHandler != null) rfidBgHandler.postDelayed(rfidRunnable, 200);
    }

    private void stopRFIDPolling() {
        if (rfidBgHandler != null && rfidRunnable != null) rfidBgHandler.removeCallbacks(rfidRunnable);
        if (rfidThread != null) { try { rfidThread.quitSafely(); } catch (Throwable ignore) {} rfidThread = null; rfidBgHandler = null; }
    }

    private void handleRfidTag(com.uhf.api.cls.Reader.TAGINFO tag) {
        try {
            String epcHex = bytesToHexString(tag.EpcId);
            if (epcHex == null || epcHex.isEmpty()) return;
            if (beepModeRaw && rfidBeepManager != null) { try { rfidBeepManager.requestBeep(); } catch (Throwable ignore) {} }
            String rfid18 = com.pda.hcw.rfid.EpcFormatter.normalizeOnReceive(epcHex);
            if (rfid18 == null || rfid18.isEmpty()) return;
            // 去重（本轮）
            if (!seenEpcsThisRun.add(rfid18)) return;
            InboundOrder order = findOrderByPlannedRfid(rfid18);
            if (order == null) return; // 非上架，过滤
            addDelta(order, rfid18);
            if (!beepModeRaw && rfidBeepManager != null) { try { rfidBeepManager.requestBeep(); } catch (Throwable ignore) {} }
        } catch (Throwable t) {
            Log.w(TAG, "handleRfidTag error", t);
        }
    }

    private class KeyReceiver extends BroadcastReceiver {
        @Override public void onReceive(Context context, Intent intent) {
            int keyCode = intent.getIntExtra("keyCode", 0);
            if (keyCode == 0) keyCode = intent.getIntExtra("keycode", 0);
            boolean keyDown = intent.getBooleanExtra("keydown", false);
            switch (keyCode) {
                case KeyEvent.KEYCODE_F1:
                case KeyEvent.KEYCODE_F2:
                case KeyEvent.KEYCODE_F3:
                case KeyEvent.KEYCODE_F4:
                case KeyEvent.KEYCODE_F7:
                case KeyEvent.KEYCODE_F8:
                    long now = System.currentTimeMillis();
                    if (keyDown) {
                        if (funKeyDownAt == 0L) {
                            funKeyDownAt = now;
                            funKeyDownCode = keyCode;
                            funKeyLongPressFired = false;
                        } else {
                            long press = now - funKeyDownAt;
                            long threshold = getUhfLongPressThresholdMs();
                            if (!funKeyLongPressFired && press >= threshold && !isRecentBarcodeEvent(now, 300L)) {
                                toggleRFIDScanning();
                                funKeyLongPressFired = true;
                            }
                        }
                    } else {
                        long press = now - funKeyDownAt;
                        long threshold = getUhfLongPressThresholdMs();
                        if (!funKeyLongPressFired && press >= threshold && !isRecentBarcodeEvent(now, 300L)) {
                            toggleRFIDScanning();
                        } else {
                            Log.d(TAG, "短按功能键，保持条码验收模式 press=" + press);
                        }
                        funKeyDownAt = 0L; funKeyDownCode = 0; funKeyLongPressFired = false;
                    }
                    break;
            }
        }
    }

    private void scheduleUhfToggleWithDebounce() {
        try {
            long delay = getUhfTriggerDelayMs();
            if (rfidBgHandler == null) rfidBgHandler = new Handler(getMainLooper());
            if (pendingUhfToggle != null) { rfidBgHandler.removeCallbacks(pendingUhfToggle); }
            pendingUhfToggle = () -> {
                long now = System.currentTimeMillis();
                if (now - lastBarcodeEventAt < delay) {
                    Log.d(TAG, "UHF切换被条码事件阻止（条码优先）");
                } else {
                    toggleRFIDScanning();
                }
            };
            rfidBgHandler.postDelayed(pendingUhfToggle, delay);
        } catch (Throwable t) {
            // 兜底直接切换
            toggleRFIDScanning();
        }
    }

    private void updateRfidToggleButton() {
        try {
            if (rfidToggleButton != null) {
                rfidToggleButton.setText(isRFIDActive ? R.string.inbound_stop_rfid : R.string.inbound_start_rfid);
            }
        } catch (Throwable ignore) {}
    }

    private void registerKeyReceiver() {
        if (keyReceiver == null) keyReceiver = new KeyReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.rfid.FUN_KEY");
        filter.addAction("android.intent.action.FUN_KEY");
        try { registerReceiver(keyReceiver, filter); } catch (Exception e) { Log.e(TAG, "register key receiver", e); }
    }

    private void unregisterKeyReceiver() {
        if (keyReceiver != null) { try { unregisterReceiver(keyReceiver); } catch (Exception ignored) {} keyReceiver = null; }
    }

    private static String bytesToHexString(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02X", b & 0xFF));
        return sb.toString();
    }

    private InboundOrder findOrderByPlannedRfid(String rfid) {
        for (InboundOrder o : orders) {
            if (o == null) continue;
            if (o.plannedRfids != null && o.plannedRfids.contains(rfid)) return o;
        }
        return null;
    }

    private void addDelta(InboundOrder order, String rfid) {
        Set<String> set = deltaByOrder.computeIfAbsent(order.code, k -> new HashSet<>());
        if (set.add(rfid)) {
            order.accepted = Math.min(order.total, order.accepted + 1);
            adapter.notifyDataSetChanged();
            loadRfidInfoIfNeeded(rfid);
        }
    }

    private void loadRfidInfoIfNeeded(String rfid) {
        if (TextUtils.isEmpty(rfid) || rfidInfoCache.containsKey(rfid)) return;
        List<String> list = new ArrayList<>();
        list.add(rfid);
        ConsumableApiService.getInstance(this).queryConsumableInfoList(list, new ConsumableApiService.ApiCallback<ConsumableApiResponse>() {
            @Override public void onSuccess(ConsumableApiResponse resp) {
                try {
                    if (resp != null && resp.takeList != null) {
                        for (ConsumableItem it : resp.takeList) {
                            if (it != null && rfid.equals(it.getRfid())) {
                                rfidInfoCache.put(rfid, it);
                                break;
                            }
                        }
                    }
                } catch (Exception ignore) {}
            }
            @Override public void onFailure(Exception e) {
                Log.w(TAG, "loadRfidInfoIfNeeded failed: " + rfid, e);
            }
        });
    }

    private void clearDelta(InboundOrder order) {
        Set<String> set = deltaByOrder.get(order.code);
        int ded = 0;
        if (set != null) { ded = set.size(); set.clear(); }
        // 回退已验收进度到清空前的基线（以当前订单accepted减去本次delta的数量为准）
        try { order.accepted = Math.max(0, order.accepted - ded); } catch (Throwable ignore) {}
        showSuccess("已清空本次增量");
        adapter.notifyDataSetChanged();
        refocusBarcode();
    }

    private void submitAcceptance(InboundOrder order) {
        Set<String> set = deltaByOrder.get(order.code);
        int count = set == null ? 0 : set.size();
        if (count == 0) { showInfo("无新增可提交"); return; }
        // 本地配置快速校验，缺失则直接横幅提示
        if (!isInboundConfigReady()) {
            return;
        }
        // 调用后端提交
        showInfo("正在提交验收...");
        inboundApi.submitInboundOrder(order.code, set, new ConsumableApiService.ApiCallback<Boolean>() {
            @Override public void onSuccess(Boolean ok) {
                // 清空本次增量，并刷新UI
                set.clear();
                showSuccess("验收提交成功");
                // 提交成功后刷新待上架清单，保持与服务端一致
                loadPendingOrders();
                refocusBarcode();
            }
            @Override public void onFailure(Exception e) {
                showError(UIUtils.friendlyErrorMessage("验收提交失败", e));
                refocusBarcode();
            }
        });
    }

    private boolean isInboundConfigReady() {
        try {
            android.content.SharedPreferences sp = getSharedPreferences("settings_prefs", MODE_PRIVATE);
            String sd = sp.getString("sd_code", "");
            int cab = BarcodeValidator.getInstance(this).getCabinetId();
            if (sd == null || sd.trim().isEmpty()) {
                showError("请先在设置中配置 sdCode");
                return false;
            }
            if (cab <= 0) {
                showError("请先在设置中配置货柜ID");
                return false;
            }
            return true;
        } catch (Exception e) {
            showError("配置检查失败");
            return false;
        }
    }

    // ===== EPC/条码 -> 18位RFID 解析（与盘点逻辑保持一致） =====
    private boolean is18Digits(String s) {
        if (s == null || s.length() != 18) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        return true;
    }

    private String tryHexToAsciiDigits(String hex) {
        if (hex == null) return null;
        String s = hex.trim();
        if (s.length() % 2 != 0) return null;
        try {
            StringBuilder out = new StringBuilder(s.length() / 2);
            for (int i = 0; i < s.length(); i += 2) {
                int b = Integer.parseInt(s.substring(i, i + 2), 16);
                if (b < 0x30 || b > 0x39) return null;
                out.append((char) b);
            }
            String decoded = out.toString();
            return is18Digits(decoded) ? decoded : null;
        } catch (Exception ignore) {
            return null;
        }
    }

    private String tryTrim20To18(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.length() == 20 && t.endsWith("00")) return t.substring(0, 18);
        return s;
    }

    private String parseTo18Digits(String input) {
        if (TextUtils.isEmpty(input)) return null;
        String s = input.trim();
        if (is18Digits(s)) return s;
        String t = tryTrim20To18(s);
        if (is18Digits(t)) return t;
        // 当作HEX EPC尝试ASCII解码
        String fromHex = tryHexToAsciiDigits(s);
        if (is18Digits(fromHex)) return fromHex;
        return null;
    }

    // ===== 适配器（简化版） =====
    private static class InboundOrderAdapter extends RecyclerView.Adapter<InboundOrderViewHolder> {
        interface Listener { void onAccept(InboundOrder order); void onClearDelta(InboundOrder order); }
        private final List<InboundOrder> data;
        private final Listener listener;
        private final SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        InboundOrderAdapter(List<InboundOrder> data, Listener l) { this.data = data; this.listener = l; }
        @Override public InboundOrderViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            android.view.View v = android.view.LayoutInflater.from(parent.getContext()).inflate(R.layout.item_inbound_order, parent, false);
            return new InboundOrderViewHolder(v);
        }
        @Override public void onBindViewHolder(InboundOrderViewHolder h, int pos) {
            InboundOrder o = data.get(pos);
            h.code.setText(o.code == null ? "" : o.code);
            if (o.docTime > 0) {
                h.time.setText(fmt.format(new Date(o.docTime)));
                h.time.setVisibility(View.VISIBLE);
            } else {
                h.time.setText("");
                h.time.setVisibility(View.GONE);
            }
            h.progress.setText(o.accepted + "/" + o.total);
            h.acceptBtn.setOnClickListener(v -> { if (listener != null) listener.onAccept(o); });
            h.clearBtn.setOnClickListener(v -> { if (listener != null) listener.onClearDelta(o); });
        }
        @Override public int getItemCount() { return data == null ? 0 : data.size(); }
    }
    
    private static class InboundOrderViewHolder extends RecyclerView.ViewHolder {
        TextView code, time, progress;
        MaterialButton acceptBtn, clearBtn;
        InboundOrderViewHolder(View itemView) {
            super(itemView);
            code = itemView.findViewById(R.id.order_code_text);
            time = itemView.findViewById(R.id.order_time_text);
            progress = itemView.findViewById(R.id.order_progress_text);
            acceptBtn = itemView.findViewById(R.id.accept_button);
            clearBtn = itemView.findViewById(R.id.clear_delta_button);
        }
    }
}
