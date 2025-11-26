package com.pda.hcw;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import androidx.recyclerview.widget.DiffUtil;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
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
    private static final String PREF_INBOUND_RFID_STORE = "inbound_rfid_prefs";
    private static final String PREF_INBOUND_RFID_FILTER = "inbound_rfid_filter_mode"; // 0:仅未匹配 1:全部

    private EditText barcodeInput;
    private RecyclerView orderList;
    private ProgressBar loading;
    private TextView emptyText;
    private RecyclerView epcScroller;
    private com.google.android.material.button.MaterialButton btnToggleRfid;
    private TextView rfidPowerHint;

    private final List<InboundOrder> orders = new ArrayList<>();
    private final Map<String, Set<String>> deltaByOrder = new HashMap<>(); // orderCode -> rfids in this run
    private InboundOrderAdapter adapter;
    private EpcRollAdapter epcAdapter;

    private InboundApiService inboundApi;

    // ===== RFID 批量验收 =====
    private com.pda.hcw.rfid.UHFManager uhfManager;
    private com.pda.hcw.rfid.RfidBeepManager rfidBeepManager;
    private boolean isRFIDActive = false;
    private android.os.HandlerThread rfidThread;
    private Handler rfidBgHandler;
    private Runnable rfidRunnable;
    private final Set<String> seenEpcsThisRun = new HashSet<>();
    private boolean beepModeRaw = false;
    private int emptyReadRounds = 0;
    // 条码/按键仲裁
    private long lastBarcodeEventAt = 0L;

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
                @Override public void onShowRfid(InboundOrder order) { showInboundOrderRfidDialog(order); }
            });
            orderList.setAdapter(adapter);

            inboundApi = InboundApiService.getInstance(this);

            // 顶部条码框：参照取用界面，兼容 IME_ACTION_DONE 或任意 ENTER 键
            barcodeInput.setOnEditorActionListener((v, actionId, event) -> {
                lastBarcodeEventAt = System.currentTimeMillis();
                boolean imeDone = (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
                boolean enterKey = (event != null && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER || event.getKeyCode() == KeyEvent.KEYCODE_NUMPAD_ENTER));
                if (imeDone || enterKey) {
                    String txt = v.getText() == null ? "" : v.getText().toString();
                    handleSingleScan(txt);
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
            // 新增：底部按钮与水平滚动 EPC 列表
            epcScroller = findViewById(R.id.rv_rfid_epc_scroller);
            btnToggleRfid = findViewById(R.id.btn_toggle_rfid);
            rfidPowerHint = findViewById(R.id.rfid_power_hint);
            if (epcScroller != null) {
                epcScroller.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
                epcAdapter = new EpcRollAdapter();
                epcScroller.setAdapter(epcAdapter);
                epcScroller.setItemAnimator(null);
            }
            if (btnToggleRfid != null) {
                btnToggleRfid.setOnClickListener(v -> {
                    if (isRFIDActive) {
                        stopRFIDScanning();
                        showSuccess("RFID盘点已停止");
                    } else {
                        if (!canStartRfid()) return; // 内部将提示原因
                        startRFIDScanning();
                        showSuccess("RFID盘点已启动");
                        showInfo(getString(R.string.rfid_power_hint));
                    }
                    updateRfidUi();
                    refocusBarcode();
                });
            }
            updateRfidUi();

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
        // 恢复对条码框的聚焦
        try {
            if (barcodeInput != null) {
                barcodeInput.requestFocus();
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(barcodeInput.getWindowToken(), 0);
            }
        } catch (Throwable ignore) {}
        updateRfidUi();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sessionManager != null) sessionManager.updateActivity();
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
                    mergePendingOrders(data);
                    updateEmpty();
                    updateRfidUi();
                }
                @Override public void onFailure(Exception e) {
                    showLoading(false);
                    showError(UIUtils.friendlyErrorMessage("加载待上架清单失败", e));
                    updateEmpty();
                    updateRfidUi();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "loadPendingOrders error", e);
            showLoading(false);
            updateEmpty();
            updateRfidUi();
        }
    }

    private void mergePendingOrders(List<InboundOrder> data) {
        List<InboundOrder> filtered = new ArrayList<>();
        if (data != null) {
            for (InboundOrder o : data) {
                if (o == null) continue;
                if (o.accepted >= o.total) continue;
                applySessionDelta(o);
                filtered.add(o);
            }
        }
        dispatchOrderDiff(filtered);
    }

    private void applySessionDelta(InboundOrder order) {
        if (order == null || TextUtils.isEmpty(order.code)) return;
        Set<String> sessionDelta = deltaByOrder.get(order.code);
        if (sessionDelta == null || sessionDelta.isEmpty()) return;
        try {
            order.accepted = Math.min(order.total, Math.max(0, order.accepted) + sessionDelta.size());
        } catch (Throwable ignore) {}
    }

    private void dispatchOrderDiff(List<InboundOrder> newList) {
        if (adapter == null) {
            orders.clear();
            orders.addAll(newList);
            return;
        }
        List<InboundOrder> oldList = new ArrayList<>(orders);
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new InboundOrderDiffCallback(oldList, newList));
        orders.clear();
        orders.addAll(newList);
        diff.dispatchUpdatesTo(adapter);
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

    private void showInboundOrderRfidDialog(InboundOrder order) {
        try {
            if (order == null) return;
            List<InboundOrderDetail> details = order.details;
            if (details == null || details.isEmpty()) {
                showInfo("该单据暂未获取到RFID数据");
                return;
            }
            View view = getLayoutInflater().inflate(R.layout.dialog_inbound_rfid_list, null);
            RecyclerView rv = view.findViewById(R.id.rv_rfid_list);
            RadioGroup rg = view.findViewById(R.id.rg_filter);
            RadioButton rbOnly = view.findViewById(R.id.rb_only_unmatched);
            RadioButton rbAll = view.findViewById(R.id.rb_all);
            TextView summary = view.findViewById(R.id.tv_summary);

            SharedPreferences sp = getSharedPreferences(PREF_INBOUND_RFID_STORE, MODE_PRIVATE);
            int filter = sp.getInt(PREF_INBOUND_RFID_FILTER, 0);
            boolean showAll = (filter == 1);

            rv.setLayoutManager(new LinearLayoutManager(this));
            Set<String> sessionMatched = deltaByOrder.get(order.code);
            InboundRfidAdapter adapter = new InboundRfidAdapter(details, sessionMatched, showAll, this::showInboundConsumableDetail);
            rv.setAdapter(adapter);
            updateInboundRfidSummary(summary, adapter);

            if (showAll) {
                rbAll.setChecked(true);
            } else {
                rbOnly.setChecked(true);
            }
            rg.setOnCheckedChangeListener((group, checkedId) -> {
                boolean displayAll = checkedId == R.id.rb_all;
                adapter.applyFilter(displayAll);
                updateInboundRfidSummary(summary, adapter);
                sp.edit().putInt(PREF_INBOUND_RFID_FILTER, displayAll ? 1 : 0).apply();
            });

            String title = TextUtils.isEmpty(order.code)
                    ? getString(R.string.inbound_rfid_list_title)
                    : order.code + " - " + getString(R.string.inbound_rfid_list_title);
            new AlertDialog.Builder(this)
                    .setTitle(title)
                    .setView(view)
                    .setNegativeButton("关闭", null)
                    .show();
        } catch (Exception e) {
            Log.e(TAG, "showInboundOrderRfidDialog failed", e);
            showError("加载RFID明细失败");
        }
    }

    private void showInboundConsumableDetail(InboundOrderDetail detail) {
        try {
            if (detail == null) return;
            View view = getLayoutInflater().inflate(R.layout.dialog_inbound_rfid_detail, null);
            TextView tvRfid = view.findViewById(R.id.rfid_value);
            TextView tvName = view.findViewById(R.id.name_value);
            TextView tvSpec = view.findViewById(R.id.spec_value);
            TextView tvManu = view.findViewById(R.id.manufacturer_value);
            if (tvRfid != null) tvRfid.setText(safe(detail.rfid));
            if (tvName != null) tvName.setText(safe(detail.name));
            if (tvSpec != null) tvSpec.setText(safe(detail.spec));
            if (tvManu != null) tvManu.setText(safe(detail.manufacturer));
            new AlertDialog.Builder(this)
                    .setTitle(R.string.inbound_rfid_detail_title)
                    .setView(view)
                    .setPositiveButton("关闭", null)
                    .show();
        } catch (Exception e) {
            Log.e(TAG, "showInboundConsumableDetail failed", e);
            showError("加载耗材详情失败");
        }
    }

    private void updateInboundRfidSummary(TextView tv, InboundRfidAdapter adapter) {
        if (tv == null || adapter == null) return;
        try {
            int total = adapter.getTotal();
            int matched = adapter.getMatchedCount();
            int unmatched = adapter.getUnmatchedCount();
            tv.setText(getString(R.string.inbound_rfid_summary, total, matched, unmatched));
        } catch (Exception e) {
            Log.w(TAG, "updateInboundRfidSummary failed", e);
        }
    }

    private String safe(String s) {
        return (s == null || s.trim().isEmpty()) ? "—" : s.trim();
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
            showSuccess("RFID盘点已停止");
        } else {
            startRFIDScanning();
            showSuccess("RFID盘点已启动");
            showInfo(getString(R.string.rfid_power_hint));
        }
        updateRfidUi();
    }

    private void startRFIDScanning() {
        if (uhfManager == null || !uhfManager.isInitialized()) initRFID();
        if (uhfManager == null || !uhfManager.isInitialized()) { showError("RFID未初始化"); return; }
        if (isRFIDActive) return;
        try { uhfManager.stopAllOperations(); } catch (Exception ignore) {}
        // 与盘点对齐：应用多/单标签模式
        try {
            boolean multi = getSharedPreferences("settings_prefs", MODE_PRIVATE).getBoolean("rfid_multi_mode", true);
            uhfManager.setGen2session(multi);
        } catch (Throwable ignore) {}
        isRFIDActive = true;
        seenEpcsThisRun.clear();
        if (epcAdapter != null) epcAdapter.clear();
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
        updateRfidUi();
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
        updateRfidUi();
    }

    private void startRFIDPolling() {
        rfidRunnable = new Runnable() {
            @Override public void run() {
                try {
                    if (!isRFIDActive || uhfManager == null || !uhfManager.isInitialized()) return;
                    java.util.List<com.uhf.api.cls.Reader.TAGINFO> tagList = uhfManager.tagInventoryRealTime();
                    if (tagList == null || tagList.isEmpty()) {
                        java.util.List<com.uhf.api.cls.Reader.TAGINFO> timerList = uhfManager.tagInventoryByTimer((short) 50);
                        if (timerList != null && !timerList.isEmpty()) tagList = timerList;
                    }

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
            if (epcAdapter != null) runOnUiThread(() -> {
                epcAdapter.addEpc(rfid18);
                if (epcScroller != null) epcScroller.smoothScrollToPosition(Math.max(0, epcAdapter.getItemCount() - 1));
            });
        } catch (Throwable t) {
            Log.w(TAG, "handleRfidTag error", t);
        }
    }

    private boolean canStartRfid() {
        if (orders.isEmpty()) { showInfo("暂无待上架数据"); return false; }
        if (!isInboundConfigReady()) { return false; }
        return true;
    }

    private String getRfidDisableReason() {
        try {
            if (orders.isEmpty()) return "暂无待上架";
            android.content.SharedPreferences sp = getSharedPreferences("settings_prefs", MODE_PRIVATE);
            String sd = sp.getString("sd_code", "");
            int cab = BarcodeValidator.getInstance(this).getCabinetId();
            if (sd == null || sd.trim().isEmpty()) return "缺少sdCode";
            if (cab <= 0) return "缺少货柜ID";
        } catch (Throwable ignore) {}
        return null;
    }

    private void updateRfidUi() {
        try {
            if (btnToggleRfid != null) {
                Context ctx = btnToggleRfid.getContext();
                int activeBg = ContextCompat.getColor(ctx, R.color.rfid_toggle_active);
                int idleBg = ContextCompat.getColor(ctx, R.color.rfid_toggle_idle);
                int disabledBg = ContextCompat.getColor(ctx, R.color.rfid_toggle_disabled);
                int activeText = ContextCompat.getColor(ctx, R.color.rfid_toggle_text_active);
                int idleText = ContextCompat.getColor(ctx, R.color.rfid_toggle_text_idle);
                int disabledText = ContextCompat.getColor(ctx, R.color.text_secondary);
                if (isRFIDActive) {
                    btnToggleRfid.setEnabled(true);
                    btnToggleRfid.setText("停止RFID盘点 · 高功耗");
                    btnToggleRfid.setBackgroundTintList(ColorStateList.valueOf(activeBg));
                    btnToggleRfid.setTextColor(activeText);
                } else {
                    String reason = getRfidDisableReason();
                    boolean enable = (reason == null);
                    btnToggleRfid.setEnabled(enable);
                    btnToggleRfid.setText(enable ? "启动RFID盘点" : ("启动RFID盘点（" + reason + ")"));
                    btnToggleRfid.setBackgroundTintList(ColorStateList.valueOf(enable ? idleBg : disabledBg));
                    btnToggleRfid.setTextColor(enable ? idleText : disabledText);
                }
            }
            if (epcScroller != null) {
                epcScroller.setVisibility(isRFIDActive ? View.VISIBLE : View.GONE);
            }
            if (rfidPowerHint != null) {
                rfidPowerHint.setVisibility(isRFIDActive ? View.VISIBLE : View.GONE);
            }
        } catch (Throwable ignore) {}
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
        if (android.os.Looper.myLooper() != getMainLooper()) {
            final InboundOrder o = order;
            final String r = rfid;
            runOnUiThread(() -> addDelta(o, r));
            return;
        }
        if (order == null || TextUtils.isEmpty(order.code)) return;

        // 已在本次会话中添加过的 RFID 不再重复校验
        Set<String> existing = deltaByOrder.get(order.code);
        if (existing != null && existing.contains(rfid)) {
            return;
        }

        // 调用库存状态接口：仅当标签处于“未入库(notInStock)”状态时才允许上架入库
        java.util.List<String> list = new java.util.ArrayList<>();
        list.add(rfid);
        ConsumableApiService.getInstance(this).queryConsumableInfoList(list, new ConsumableApiService.ApiCallback<ConsumableApiResponse>() {
            @Override public void onSuccess(ConsumableApiResponse resp) {
                handleInboundInventoryCheck(order, rfid, resp);
            }

            @Override public void onFailure(Exception e) {
                Log.w(TAG, "inbound inventory check failed for rfid=" + rfid, e);
                showError(UIUtils.friendlyErrorMessage("获取耗材库存状态失败", e));
            }
        });
    }

    /**
     * 入库验收前的库存状态校验：
     * 仅当标签在 notInStockList 中（且不出现在其他状态列表）时，才允许加入本次验收增量。
     */
    private void handleInboundInventoryCheck(InboundOrder order, String rfid, ConsumableApiResponse resp) {
        try {
            if (isFinishing()) return;
            if (order == null || TextUtils.isEmpty(order.code)) return;

            // 确保订单仍然存在于当前列表中
            InboundOrder target = null;
            for (InboundOrder o : orders) {
                if (o != null && order.code.equals(o.code)) {
                    target = o;
                    break;
                }
            }
            if (target == null) {
                showInfo("当前入库单已更新，忽略标签 " + rfid);
                return;
            }

            boolean inNotInStock = resp != null && resp.notInStockList != null && resp.notInStockList.contains(rfid);
            boolean inInStock = containsRfid(resp == null ? null : resp.inStockList, rfid);
            boolean inTaken = containsRfid(resp == null ? null : resp.takenList, rfid);
            boolean inConsumed = containsRfid(resp == null ? null : resp.consumedList, rfid);
            boolean inUsedReturn = containsRfid(resp == null ? null : resp.usedReturnList, rfid);

            if (inNotInStock && !inInStock && !inTaken && !inConsumed && !inUsedReturn) {
                performAddDelta(target, rfid);
                return;
            }

            // 与 Web 有单入库逻辑保持一致的错误提示
            if (inInStock) {
                showError("耗材 " + rfid + " 已在耗材屋库存中，无法重复入库");
                return;
            }
            if (inTaken) {
                showError("耗材 " + rfid + " 处于取用未归还状态，不能上架入库");
                return;
            }
            if (inConsumed) {
                showError("耗材 " + rfid + " 已消耗，不能上架入库");
                return;
            }
            if (inUsedReturn) {
                showError("耗材 " + rfid + " 为消耗退回记录，不走首次有单上架");
                return;
            }

            // 未命中任何列表或响应为空：状态异常，暂不处理
            showError("标签 " + rfid + " 状态未知，暂不处理");
        } catch (Exception e) {
            Log.e(TAG, "handleInboundInventoryCheck error, rfid=" + rfid, e);
            showError("处理耗材库存状态时发生错误");
        }
    }

    /**
     * 实际执行本次验收增量累加（在通过库存状态校验之后调用）。
     */
    private void performAddDelta(InboundOrder order, String rfid) {
        if (order == null || TextUtils.isEmpty(order.code)) return;
        Set<String> set = deltaByOrder.computeIfAbsent(order.code, k -> new HashSet<>());
        if (!set.add(rfid)) return;
        try {
            order.accepted = Math.min(order.total, order.accepted + 1);
        } catch (Throwable ignore) {}
        adapter.notifyDataSetChanged();
    }

    private boolean containsRfid(java.util.List<ConsumableItem> list, String rfid) {
        if (list == null || rfid == null) return false;
        for (ConsumableItem it : list) {
            if (it != null && rfid.equals(it.getRfid())) return true;
        }
        return false;
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
        final Set<String> submitSet = set;
        final java.util.List<String> submitRfids = (set == null || set.isEmpty())
                ? java.util.Collections.emptyList()
                : new java.util.ArrayList<>(set);
        final int submittedCount = submitRfids.size();
        final int submitOrderIndex = order != null ? orders.indexOf(order) : -1;
        // 调用后端提交
        showInfo("正在提交验收...");
        inboundApi.submitInboundOrder(order, set, new ConsumableApiService.ApiCallback<Boolean>() {
            @Override public void onSuccess(Boolean ok) {
                // 清空本次增量，避免重复提交
                if (submitSet != null) submitSet.clear();
                if (order != null && !TextUtils.isEmpty(order.code)) {
                    deltaByOrder.remove(order.code);
                }
                if (!submitRfids.isEmpty() && order != null && order.plannedRfids != null) {
                    order.plannedRfids.removeAll(submitRfids);
                }
                boolean removed = false;
                if (order != null) {
                    int remaining = order.total - submittedCount;
                    if (remaining <= 0) {
                        if (submitOrderIndex >= 0 && submitOrderIndex < orders.size()) {
                            orders.remove(submitOrderIndex);
                            adapter.notifyItemRemoved(submitOrderIndex);
                            removed = true;
                        } else {
                            orders.remove(order);
                            adapter.notifyDataSetChanged();
                            removed = true;
                        }
                    } else {
                        order.total = remaining;
                        order.accepted = 0;
                        if (submitOrderIndex >= 0 && submitOrderIndex < orders.size()) {
                            adapter.notifyItemChanged(submitOrderIndex);
                        } else {
                            adapter.notifyDataSetChanged();
                        }
                    }
                } else {
                    adapter.notifyDataSetChanged();
                }
                if (removed) {
                    updateEmpty();
                }
                updateRfidUi();
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

    private static class InboundRfidAdapter extends RecyclerView.Adapter<InboundRfidAdapter.VH> {
        interface OnItemClickListener { void onClick(InboundOrderDetail detail); }

        private final List<InboundOrderDetail> all;
        private final List<InboundOrderDetail> shown = new ArrayList<>();
        private final Set<String> sessionMatched;
        private final OnItemClickListener listener;
        private boolean showAll;

        InboundRfidAdapter(List<InboundOrderDetail> source, Set<String> deltaMatched, boolean showAll, OnItemClickListener l) {
            this.all = source == null ? new ArrayList<>() : new ArrayList<>(source);
            this.sessionMatched = (deltaMatched != null) ? deltaMatched : Collections.emptySet();
            this.listener = l;
            setHasStableIds(true);
            applyFilter(showAll);
        }

        private boolean isMatched(InboundOrderDetail detail) {
            if (detail == null) return false;
            if (detail.baseMatched) return true;
            return sessionMatched.contains(detail.rfid);
        }

        void applyFilter(boolean showAll) {
            this.showAll = showAll;
            shown.clear();
            for (InboundOrderDetail d : all) {
                boolean matched = isMatched(d);
                if (!matched || showAll) shown.add(d);
            }
            notifyDataSetChanged();
        }

        int getTotal() { return all.size(); }

        int getMatchedCount() {
            int count = 0;
            for (InboundOrderDetail d : all) {
                if (isMatched(d)) count++;
            }
            return count;
        }

        int getUnmatchedCount() {
            int remaining = getTotal() - getMatchedCount();
            return Math.max(0, remaining);
        }

        @Override
        public long getItemId(int position) {
            InboundOrderDetail detail = shown.get(position);
            if (detail != null && detail.rfid != null) {
                return detail.rfid.hashCode();
            }
            return position;
        }

        @Override
        public VH onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            View v = android.view.LayoutInflater.from(parent.getContext()).inflate(R.layout.item_rfid_row, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(VH vh, int position) {
            InboundOrderDetail detail = shown.get(position);
            boolean matched = isMatched(detail);
            Context ctx = vh.itemView.getContext();
            vh.status.setText(matched
                    ? ctx.getString(R.string.inbound_rfid_status_matched)
                    : ctx.getString(R.string.inbound_rfid_status_unmatched));
            vh.rfid.setText(detail == null ? "" : detail.rfid);
            vh.itemView.setOnClickListener(v -> {
                if (listener != null && detail != null) listener.onClick(detail);
            });
        }

        @Override
        public int getItemCount() { return shown.size(); }

        static class VH extends RecyclerView.ViewHolder {
            final TextView status;
            final TextView rfid;
            VH(View itemView) {
                super(itemView);
                status = itemView.findViewById(R.id.tv_status);
                rfid = itemView.findViewById(R.id.tv_rfid);
            }
        }
    }

    // ===== 适配器（简化版） =====
    private static class InboundOrderAdapter extends RecyclerView.Adapter<InboundOrderViewHolder> {
        interface Listener {
            void onAccept(InboundOrder order);
            void onClearDelta(InboundOrder order);
            void onShowRfid(InboundOrder order);
        }
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
            h.itemView.setOnClickListener(v -> { if (listener != null) listener.onShowRfid(o); });
        }
        @Override public int getItemCount() { return data == null ? 0 : data.size(); }
    }

    private static class InboundOrderDiffCallback extends DiffUtil.Callback {
        private final List<InboundOrder> oldList;
        private final List<InboundOrder> newList;
        InboundOrderDiffCallback(List<InboundOrder> oldList, List<InboundOrder> newList) {
            this.oldList = oldList != null ? oldList : Collections.emptyList();
            this.newList = newList != null ? newList : Collections.emptyList();
        }
        @Override public int getOldListSize() { return oldList.size(); }
        @Override public int getNewListSize() { return newList.size(); }
        @Override public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            String oldCode = safeCode(oldList.get(oldItemPosition));
            String newCode = safeCode(newList.get(newItemPosition));
            return TextUtils.equals(oldCode, newCode);
        }
        @Override public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            InboundOrder oldItem = oldList.get(oldItemPosition);
            InboundOrder newItem = newList.get(newItemPosition);
            if (oldItem == null || newItem == null) return oldItem == newItem;
            if (oldItem.accepted != newItem.accepted) return false;
            if (oldItem.total != newItem.total) return false;
            if (oldItem.docTime != newItem.docTime) return false;
            return TextUtils.equals(oldItem.code, newItem.code);
        }
        private String safeCode(InboundOrder order) {
            return order != null ? order.code : null;
        }
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

    // ===== 水平滚动 EPC 适配器（最近N条，去重） =====
    private static class EpcRollAdapter extends RecyclerView.Adapter<EpcRollAdapter.VH> {
        private static final int MAX = 50;
        private final java.util.ArrayList<String> items = new java.util.ArrayList<>();
        private final java.util.HashSet<String> set = new java.util.HashSet<>();

        @Override public VH onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            android.view.View v = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_rfid_epc, parent, false);
            return new VH(v);
        }
        @Override public void onBindViewHolder(VH h, int pos) { h.text.setText(items.get(pos)); }
        @Override public int getItemCount() { return items.size(); }
        void addEpc(String epc) {
            if (epc == null || epc.isEmpty() || set.contains(epc)) return;
            set.add(epc);
            items.add(epc);
            notifyItemInserted(items.size() - 1);
            if (items.size() > MAX) {
                String removed = items.remove(0);
                set.remove(removed);
                notifyItemRemoved(0);
            }
        }
        void clear() {
            items.clear(); set.clear(); notifyDataSetChanged();
        }
        static class VH extends RecyclerView.ViewHolder { TextView text; VH(View v) { super(v); text = v.findViewById(R.id.text_epc); } }
    }
}
