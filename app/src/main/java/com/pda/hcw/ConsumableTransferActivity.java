package com.pda.hcw;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 耗材移库（PDA 动作型移库单）
 * - 选择目标中心库
 * - 条码/RFID 扫描耗材
 * - 按“源中心库 + 子码”聚合展示
 * - 一次提交，后端按源中心库拆分移库单，整批成功或整批失败
 */
public class ConsumableTransferActivity extends BaseActivity {
    private static final String TAG = "ConsumableTransfer";

    // UI
    private TextView statusText;
    private TextInputLayout targetCenterInputLayout;
    private AutoCompleteTextView targetCenterSpinner;
    private TextInputEditText barcodeInput;
    private RecyclerView detailRecyclerView;
    private TextView transferSummaryText;
    private MaterialButton clearButton;
    private MaterialButton submitButton;

    // RFID UI
    private RecyclerView epcScroller;
    private MaterialButton btnToggleRfid;
    private TextView rfidPowerHint;

    // 数据
    private SubmissionQueue submissions;
    private ConsumableApiService apiService;
    private NetworkConfig networkConfig;
    private final List<String> centerNameList = new ArrayList<>();
    private final List<Integer> centerIdList = new ArrayList<>();
    @Nullable
    private Integer selectedCenterId = null;

    // 按“源中心库 + 子码”聚合的明细
    private static class TransferGroupItem {
        public String groupKey;           // fromId#code
        public int fromLocationId;
        public String fromLocationName;
        public String code;
        public String name;
        public String spec;
        public String manufacturer;
        public String brand;
        public String unit;
        public int quantity;
        public List<String> rfids = new ArrayList<>();
        public boolean isExpanded;
    }

    private final List<TransferGroupItem> groupList = new ArrayList<>();
    private final Map<String, TransferGroupItem> groupMap = new HashMap<>();
    private final Map<String, ConsumableApiService.TransferPreviewItem> selectedByRfid = new HashMap<>();
    private int totalCount = 0;

    private TransferDetailAdapter detailAdapter;

    // RFID（UHF）
    private com.pda.hcw.rfid.UHFManager uhfManager;
    private com.pda.hcw.rfid.RfidBeepManager rfidBeepManager;
    private boolean isRFIDActive = false;
    private android.os.HandlerThread rfidThread;
    private Handler rfidBgHandler;
    private Runnable rfidRunnable;
    private final Set<String> seenEpcsThisRun = new HashSet<>();
    private boolean beepModeRaw = false;
    private int emptyReadRounds = 0;

    @Override
    protected int getLayoutResourceId() {
        return R.layout.activity_consumable_transfer;
    }

    @Override
    protected String getActivityName() {
        return "耗材移库";
    }

    @Override
    protected String getActivityTitle() {
        return getString(R.string.transfer_title);
    }

    @Override
    protected void initViews() {
        super.initViews();
        statusText = findViewById(R.id.status_text);
        targetCenterInputLayout = findViewById(R.id.target_center_input_layout);
        targetCenterSpinner = findViewById(R.id.target_center_spinner);
        barcodeInput = findViewById(R.id.barcode_input);
        detailRecyclerView = findViewById(R.id.transfer_detail_recycler);
        transferSummaryText = findViewById(R.id.transfer_summary_text);
        clearButton = findViewById(R.id.transfer_clear_button);
        submitButton = findViewById(R.id.transfer_submit_button);
        epcScroller = findViewById(R.id.rv_rfid_epc_scroller);
        btnToggleRfid = findViewById(R.id.btn_toggle_rfid);
        rfidPowerHint = findViewById(R.id.rfid_power_hint);

        if (detailRecyclerView != null) {
            detailRecyclerView.setLayoutManager(new LinearLayoutManager(this));
            detailAdapter = new TransferDetailAdapter(groupList, this::removeGroupItem);
            detailRecyclerView.setAdapter(detailAdapter);
        }

        if (epcScroller != null) {
            epcScroller.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
            epcScroller.setItemAnimator(null);
            epcScroller.setAdapter(new EpcRollAdapter());
        }
    }

    @Override
    protected void initBusinessLogic() {
        super.initBusinessLogic();
        apiService = ConsumableApiService.getInstance(this);
        networkConfig = NetworkConfig.getInstance(this);

        submissions = new SubmissionQueue(this, ApiEndpoint.TRANSFER_SUBMIT_FROM_PDA, "transfer", new SubmissionQueue.Listener() {
            @Override public void apply(String slot, org.json.JSONObject body, org.json.JSONObject display,
                                        org.json.JSONObject receipt) throws Exception {
                applyTransferReceipt(body, display, receipt);
            }
            @Override public void showMessage(String message) { showInfo(message); }
        });
        setupTargetCenterSpinner();
        setupBarcodeInput();
        setupButtons();
        updateSummary();
    }

    private void setupTargetCenterSpinner() {
        if (targetCenterSpinner == null) return;
        centerNameList.clear();
        centerIdList.clear();

        apiService.fetchLocations(new ConsumableApiService.ApiCallback<List<OperatingRoom>>() {
            @Override
            public void onSuccess(List<OperatingRoom> result) {
                centerNameList.clear();
                centerIdList.clear();
                if (result != null) {
                    for (OperatingRoom r : result) {
                        if (r == null || r.serverId == null || r.serverId <= 0) continue;
                        // 仅保留启用中的中心库
                        if (r.type != null && !"center".equalsIgnoreCase(r.type)) continue;
                        if (!r.isActive) continue;
                        centerNameList.add(r.name);
                        centerIdList.add(r.serverId);
                    }
                }
                if (centerNameList.isEmpty()) {
                    showInlineStatus("未配置中心库地点，请在基础资料中维护", true);
                }
                ArrayAdapter<String> adapter = new ArrayAdapter<>(ConsumableTransferActivity.this,
                        android.R.layout.simple_dropdown_item_1line, centerNameList);
                targetCenterSpinner.setAdapter(adapter);
            }

            @Override
            public void onFailure(Exception e) {
                showInlineStatus(UIUtils.friendlyErrorMessage("加载地点失败", e), true);
            }
        });

        targetCenterSpinner.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < centerIdList.size()) {
                selectedCenterId = centerIdList.get(position);
            } else {
                selectedCenterId = null;
            }
            clearStatus();
        });
    }

    private void setupBarcodeInput() {
        if (barcodeInput == null) return;

        try {
            barcodeInput.setShowSoftInputOnFocus(false);
        } catch (Throwable ignore) {}

        barcodeInput.setOnEditorActionListener((v, actionId, event) -> {
            boolean imeDone = (actionId == EditorInfo.IME_ACTION_DONE);
            boolean enterKey = (event != null && event.getAction() == KeyEvent.ACTION_DOWN &&
                    (event.getKeyCode() == KeyEvent.KEYCODE_ENTER || event.getKeyCode() == KeyEvent.KEYCODE_NUMPAD_ENTER));
            if (!(imeDone || enterKey)) {
                return false;
            }
            String txt = v.getText() == null ? "" : v.getText().toString().trim();
            if (!txt.isEmpty()) {
                if (selectedCenterId == null) {
                    showError(getString(R.string.transfer_select_center_first));
                } else {
                    handleBarcodeScan(txt);
                }
            }
            barcodeInput.setText("");
            try {
                barcodeInput.requestFocus();
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(barcodeInput.getWindowToken(), 0);
            } catch (Throwable ignore) {}
            return true;
        });
    }

    private void setupButtons() {
        if (clearButton != null) {
            clearButton.setOnClickListener(v -> clearAll());
        }
        if (submitButton != null) {
            submitButton.setOnClickListener(v -> submitTransfer());
        }
        if (btnToggleRfid != null) {
            btnToggleRfid.setOnClickListener(v -> toggleRFIDScanning());
        }
    }

    private void handleBarcodeScan(String barcode) {
        try {
            BarcodeValidator validator = BarcodeValidator.getInstance(this);
            if (validator != null && !validator.validateBarcode(barcode)) {
                showError(validator.getInvalidBarcodeMessage());
                return;
            }
        } catch (Exception ignore) {}

        if (selectedCenterId == null) {
            showError(getString(R.string.transfer_select_center_first));
            return;
        }

        apiService.previewTransferByBarcode(barcode, selectedCenterId, new ConsumableApiService.ApiCallback<ConsumableApiService.TransferPreviewItem>() {
            @Override
            public void onSuccess(ConsumableApiService.TransferPreviewItem result) {
                if (result == null) {
                    showError("移库预览结果为空");
                    return;
                }
                addOrUpdateGroup(result);
            }

            @Override
            public void onFailure(Exception e) {
                showError(UIUtils.friendlyErrorMessage("移库预览失败", e));
            }
        });
    }

    private void addOrUpdateGroup(ConsumableApiService.TransferPreviewItem src) {
        if (src == null || src.rfid == null || src.rfid.trim().isEmpty()) return;
        String rfid = src.rfid.trim();
        if (selectedByRfid.containsKey(rfid)) {
            // 已在本次移库单中，直接忽略
            showInfo("该耗材已在当前移库单中");
            return;
        }
        selectedByRfid.put(rfid, src);

        int fromId = src.fromLocationId;
        String fromName = (src.fromLocationName == null) ? "" : src.fromLocationName;
        String code = src.code == null ? "" : src.code;
        String key = fromId + "#" + code;

        TransferGroupItem group = groupMap.get(key);
        if (group == null) {
            group = new TransferGroupItem();
            group.groupKey = key;
            group.fromLocationId = fromId;
            group.fromLocationName = fromName;
            group.code = code;
            group.name = src.name;
            group.spec = src.spec;
            group.manufacturer = src.manufacturer;
            group.brand = src.brand;
            group.unit = src.unit == null ? "个" : src.unit;
            group.quantity = 0;
            group.isExpanded = false;
            groupMap.put(key, group);
            groupList.add(0, group);
            if (detailAdapter != null) {
                detailAdapter.notifyItemInserted(0);
                detailRecyclerView.scrollToPosition(0);
            }
        }
        group.quantity++;
        group.rfids.add(rfid);
        if (detailAdapter != null) {
            int idx = groupList.indexOf(group);
            if (idx >= 0) detailAdapter.notifyItemChanged(idx);
        }
        totalCount++;
        updateSummary();
        clearStatus();
    }

    private void removeGroupItem(int position) {
        if (position < 0 || position >= groupList.size()) return;
        TransferGroupItem group = groupList.get(position);
        groupList.remove(position);
        groupMap.remove(group.groupKey);
        // 清理 rfid 缓存
        for (String r : group.rfids) {
            selectedByRfid.remove(r);
        }
        totalCount -= group.quantity;
        if (totalCount < 0) totalCount = 0;
        if (detailAdapter != null) {
            detailAdapter.notifyItemRemoved(position);
            detailAdapter.notifyItemRangeChanged(position, groupList.size() - position);
        }
        updateSummary();
    }

    private void clearAll() {
        groupList.clear();
        groupMap.clear();
        selectedByRfid.clear();
        totalCount = 0;
        if (detailAdapter != null) detailAdapter.notifyDataSetChanged();
        updateSummary();
        clearStatus();
    }

    private void updateSummary() {
        if (transferSummaryText != null) {
            if (totalCount <= 0) {
                transferSummaryText.setText(getString(R.string.transfer_total_default));
            } else {
                transferSummaryText.setText(getString(R.string.transfer_total_format, totalCount));
            }
        }
    }

    private void submitTransfer() {
        if (submissions.hasPending("batch")) {
            submissions.resumePending();
            showInfo("正在核对上次移库结果");
            return;
        }
        if (selectedCenterId == null) {
            showError(getString(R.string.transfer_select_center_first));
            return;
        }
        if (selectedByRfid.isEmpty()) {
            showError(getString(R.string.transfer_no_items));
            return;
        }
        UserInfo currentUser = SessionManager.getInstance(this).getCurrentUser();
        if (currentUser == null) {
            showError("用户未登录");
            return;
        }
        List<String> rfids = new ArrayList<>(selectedByRfid.keySet());

        try {
            org.json.JSONObject body = new org.json.JSONObject();
            body.put("to_location_id", selectedCenterId);
            org.json.JSONArray arr = new org.json.JSONArray();
            for (String r : rfids) {
                if (r == null) continue;
                String v = r.trim();
                if (!v.isEmpty()) arr.put(v);
            }
            body.put("rfid_list", arr);

            long userIdNumeric;
            try {
                userIdNumeric = Long.parseLong(currentUser.getUserId());
            } catch (Exception e) {
                userIdNumeric = 0L;
            }
            body.put("operator_id", userIdNumeric);
            String deviceId = SessionManager.getInstance(this).getDeviceId();
            body.put("device_id", deviceId == null ? "" : deviceId);

            org.json.JSONArray items = new org.json.JSONArray();
            for (ConsumableApiService.TransferPreviewItem item : selectedByRfid.values()) {
                items.put(new org.json.JSONObject().put("rfid", item.rfid).put("code", item.code)
                        .put("name", item.name).put("from", item.fromLocationId).put("location", item.fromLocationName)
                        .put("spec", item.spec).put("manufacturer", item.manufacturer).put("brand", item.brand).put("unit", item.unit));
            }
            submissions.submit("batch", body, new org.json.JSONObject().put("items", items));
            showInfo("正在提交移库");
        } catch (Exception e) { showError(UIUtils.friendlyErrorMessage("移库提交失败", e)); }
    }

    private void applyTransferReceipt(org.json.JSONObject body, org.json.JSONObject display, org.json.JSONObject receipt) throws Exception {
        org.json.JSONArray tags = body.getJSONArray("rfid_list");
        if (receipt.getInt("status") != 200) {
            org.json.JSONArray items = display.getJSONArray("items");
            for (int i = 0; i < items.length(); i++) {
                org.json.JSONObject row = items.getJSONObject(i);
                if (selectedByRfid.containsKey(row.getString("rfid"))) continue;
                ConsumableApiService.TransferPreviewItem item = new ConsumableApiService.TransferPreviewItem();
                item.rfid = row.getString("rfid");
                item.code = row.optString("code"); item.name = row.optString("name");
                item.fromLocationId = row.getInt("from"); item.fromLocationName = row.optString("location");
                item.spec = row.optString("spec"); item.manufacturer = row.optString("manufacturer");
                item.brand = row.optString("brand"); item.unit = row.optString("unit");
                addOrUpdateGroup(item);
            }
            showError("移库失败：" + receipt.optString("message"));
            return;
        }
        org.json.JSONObject result = receipt.getJSONObject("data");
        int moved = result.getInt("success_count");
        if (moved != tags.length()) throw new IllegalStateException("移库回执明细不完整");
        java.util.Set<String> completed = new java.util.HashSet<>();
        for (int i = 0; i < tags.length(); i++) completed.add(tags.getString(i));
        for (String rfid : completed) selectedByRfid.remove(rfid);
        for (int i = groupList.size()-1; i >= 0; i--) {
            TransferGroupItem group = groupList.get(i);
            group.rfids.removeAll(completed);
            group.quantity = group.rfids.size();
            if (group.rfids.isEmpty()) { groupMap.remove(group.groupKey); groupList.remove(i); }
        }
        totalCount = selectedByRfid.size();
        detailAdapter.notifyDataSetChanged();
        updateSummary();
        showSuccess(getString(R.string.transfer_submit_success, moved, result.getInt("order_count"))
                + "，待提交 " + totalCount + " 件");
    }

    // ===== RFID 相关逻辑（参考 InboundAcceptanceActivity） =====

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
        if (selectedCenterId == null) {
            showError(getString(R.string.transfer_select_center_first));
            return;
        }
        if (!isRfidConfigReady()) return;
        if (isRFIDActive) {
            stopRFIDScanning();
            showSuccess("RFID扫描已停止");
        } else {
            startRFIDScanning();
            showSuccess("RFID扫描已启动");
            showInfo(getString(R.string.rfid_power_hint));
        }
        updateRfidUi();
    }

    private boolean isRfidConfigReady() {
        try {
            SharedPreferences sp = getSharedPreferences("settings_prefs", MODE_PRIVATE);
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

    private String getRfidDisableReason() {
        try {
            SharedPreferences sp = getSharedPreferences("settings_prefs", MODE_PRIVATE);
            String sd = sp.getString("sd_code", "");
            int cab = BarcodeValidator.getInstance(this).getCabinetId();
            if (sd == null || sd.trim().isEmpty()) return "缺少sdCode";
            if (cab <= 0) return "缺少货柜ID";
        } catch (Throwable ignore) {}
        return null;
    }

    private void startRFIDScanning() {
        if (uhfManager == null || !uhfManager.isInitialized()) initRFID();
        if (uhfManager == null || !uhfManager.isInitialized()) {
            showError("RFID未初始化");
            return;
        }
        if (isRFIDActive) return;
        try {
            uhfManager.stopAllOperations();
        } catch (Exception ignore) {}
        try {
            boolean multi = getSharedPreferences("settings_prefs", MODE_PRIVATE).getBoolean("rfid_multi_mode", true);
            uhfManager.setGen2session(multi);
        } catch (Throwable ignore) {}
        isRFIDActive = true;
        seenEpcsThisRun.clear();
        // 蜂鸣配置
        try {
            boolean multi = getSharedPreferences("settings_prefs", MODE_PRIVATE).getBoolean("rfid_multi_mode", true);
            String mode = getSharedPreferences("settings_prefs", MODE_PRIVATE).getString("rfid_beep_mode", "biz");
            beepModeRaw = "raw".equalsIgnoreCase(mode);
            if (rfidBeepManager != null) {
                rfidBeepManager.setMultiMode(multi);
                rfidBeepManager.start();
            }
        } catch (Throwable ignore) {}

        try {
            uhfManager.asyncStartReading();
        } catch (Throwable t) {
            Log.w(TAG, "asyncStartReading failed", t);
        }
        emptyReadRounds = 0;
        if (rfidThread == null) {
            rfidThread = new android.os.HandlerThread("transfer-rfid-loop");
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
            try {
                Thread.sleep(30);
            } catch (InterruptedException ignore) {}
            uhfManager.stopAllOperations();
            ok = s1 && !uhfManager.isReading();
        } catch (Throwable t) {
            Log.w(TAG, "stopRFIDScanning", t);
            ok = false;
        }
        if (!ok && rfidBeepManager != null) new Thread(() -> rfidBeepManager.alarmNotStopped()).start();
        if (rfidBeepManager != null) rfidBeepManager.stop();
        updateRfidUi();
    }

    private void startRFIDPolling() {
        rfidRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    if (!isRFIDActive || uhfManager == null || !uhfManager.isInitialized()) return;
                    java.util.List<com.uhf.api.cls.Reader.TAGINFO> tagList = uhfManager.tagInventoryRealTime();
                    if (tagList == null || tagList.isEmpty()) {
                        java.util.List<com.uhf.api.cls.Reader.TAGINFO> timerList = uhfManager.tagInventoryByTimer((short) 50);
                        if (timerList != null && !timerList.isEmpty()) tagList = timerList;
                    }

                    if (tagList == null || tagList.isEmpty()) {
                        emptyReadRounds++;
                        if (emptyReadRounds >= 4) {
                            try {
                                uhfManager.asyncStopReading();
                                Thread.sleep(30);
                                uhfManager.asyncStartReading();
                            } catch (Throwable ignore) {}
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
        if (rfidThread != null) {
            try {
                rfidThread.quitSafely();
            } catch (Throwable ignore) {}
            rfidThread = null;
            rfidBgHandler = null;
        }
    }

    private void handleRfidTag(com.uhf.api.cls.Reader.TAGINFO tag) {
        try {
            String epcHex = bytesToHexString(tag.EpcId);
            if (epcHex == null || epcHex.isEmpty()) return;
            if (beepModeRaw && rfidBeepManager != null) {
                try {
                    rfidBeepManager.requestBeep();
                } catch (Throwable ignore) {}
            }
            String rfid18 = com.pda.hcw.rfid.EpcFormatter.normalizeOnReceive(epcHex);
            if (rfid18 == null || rfid18.isEmpty()) return;
            if (!seenEpcsThisRun.add(rfid18)) return;
            if (selectedCenterId == null) return;

            apiService.previewTransferByRfid(rfid18, selectedCenterId, new ConsumableApiService.ApiCallback<ConsumableApiService.TransferPreviewItem>() {
                @Override
                public void onSuccess(ConsumableApiService.TransferPreviewItem result) {
                    addOrUpdateGroup(result);
                }

                @Override
                public void onFailure(Exception e) {
                    showError(UIUtils.friendlyErrorMessage("移库预览失败", e));
                }
            });

            if (epcScroller != null && epcScroller.getAdapter() instanceof EpcRollAdapter) {
                runOnUiThread(() -> {
                    EpcRollAdapter ad = (EpcRollAdapter) epcScroller.getAdapter();
                    ad.addEpc(rfid18);
                    epcScroller.smoothScrollToPosition(Math.max(0, ad.getItemCount() - 1));
                });
            }

            if (!beepModeRaw && rfidBeepManager != null) {
                try {
                    rfidBeepManager.requestBeep();
                } catch (Throwable ignore) {}
            }
        } catch (Throwable t) {
            Log.w(TAG, "handleRfidTag error", t);
        }
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
                    btnToggleRfid.setText("停止RFID扫描 · 高功耗");
                    btnToggleRfid.setBackgroundTintList(ColorStateList.valueOf(activeBg));
                    btnToggleRfid.setTextColor(activeText);
                } else {
                    String reason = getRfidDisableReason();
                    boolean enable = (reason == null);
                    btnToggleRfid.setEnabled(enable);
                    btnToggleRfid.setText(enable ? "启动RFID扫描" : ("启动RFID扫描（" + reason + ")"));
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

    private void showInlineStatus(String msg, boolean isError) {
        if (statusText == null) return;
        statusText.setText(msg == null ? "" : msg);
        statusText.setTextColor(getResources().getColor(isError ? android.R.color.holo_red_dark : R.color.text_secondary));
        statusText.setVisibility(TextUtils.isEmpty(msg) ? View.GONE : View.VISIBLE);
    }

    private void clearStatus() {
        if (statusText != null) {
            statusText.setText("");
            statusText.setVisibility(View.GONE);
        }
    }

    @Override
    protected void showInfo(String msg) {
        showInlineStatus(msg, false);
    }

    @Override
    protected void showError(String msg) {
        showInlineStatus(msg, true);
    }

    @Override
    protected void showSuccess(String msg) {
        showInlineStatus(msg, false);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 退出界面时必须停止 RFID 扫描
        if (isRFIDActive) {
            stopRFIDScanning();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 确保 RFID 完全停止并释放资源
        try {
            if (isRFIDActive) stopRFIDScanning();
        } catch (Throwable ignore) {}
        try {
            if (rfidBeepManager != null) rfidBeepManager.release();
        } catch (Throwable ignore) {}
        try {
            if (uhfManager != null) uhfManager.stopAllOperations();
        } catch (Throwable ignore) {}
    }

    // ===== 适配器：复用 item_consumable_detail 布局 =====

    private static class TransferDetailAdapter extends RecyclerView.Adapter<TransferDetailAdapter.ViewHolder> {
        interface OnItemRemoveListener { void onRemove(int position); }

        private final List<TransferGroupItem> items;
        private final OnItemRemoveListener removeListener;

        TransferDetailAdapter(List<TransferGroupItem> items, OnItemRemoveListener listener) {
            this.items = items;
            this.removeListener = listener;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_consumable_detail, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            TransferGroupItem item = items.get(position);

            holder.serialNoText.setText(String.valueOf(position + 1));
            // 展示“源中心库 + 子码”
            String codeLine = (item.fromLocationName == null || item.fromLocationName.isEmpty())
                    ? item.code
                    : item.fromLocationName + " · " + item.code;
            holder.productCodeText.setText(codeLine);

            holder.commonNameText.setText(item.name == null ? "" : item.name);
            holder.specificationText.setText(item.spec == null ? "" : ("   " + item.spec));
            holder.manufacturerText.setText(item.manufacturer == null ? "" : ("   " + item.manufacturer));
            String unit = (item.unit == null || item.unit.isEmpty()) ? "个" : item.unit;
            holder.quantityText.setText("   数量：" + item.quantity + " " + unit);

            StringBuilder barcodeDetails = new StringBuilder();
            for (int i = 0; i < item.rfids.size(); i++) {
                if (i > 0) barcodeDetails.append("\n");
                barcodeDetails.append(String.format("%d. %s", i + 1, item.rfids.get(i)));
            }
            holder.barcodeText.setText(barcodeDetails.toString());

            holder.barcodeDetailsArea.setVisibility(item.isExpanded ? View.VISIBLE : View.GONE);
            holder.expandIcon.setRotation(item.isExpanded ? 180f : 90f);
            holder.expandIcon.setOnClickListener(v -> {
                int pos = holder.getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    TransferGroupItem current = items.get(pos);
                    current.isExpanded = !current.isExpanded;
                    notifyItemChanged(pos);
                }
            });

            holder.removeButton.setOnClickListener(v -> {
                if (removeListener != null) {
                    removeListener.onRemove(holder.getAdapterPosition());
                }
            });
        }

        @Override
        public int getItemCount() {
            return items == null ? 0 : items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView serialNoText;
            TextView productCodeText;
            TextView barcodeText;
            TextView commonNameText;
            TextView specificationText;
            TextView manufacturerText;
            TextView quantityText;
            android.widget.ImageView expandIcon;
            android.widget.LinearLayout barcodeDetailsArea;
            MaterialButton removeButton;

            ViewHolder(View itemView) {
                super(itemView);
                serialNoText = itemView.findViewById(R.id.serial_no_text);
                productCodeText = itemView.findViewById(R.id.product_code_text);
                barcodeText = itemView.findViewById(R.id.barcode_text);
                commonNameText = itemView.findViewById(R.id.common_name_text);
                specificationText = itemView.findViewById(R.id.specification_text);
                manufacturerText = itemView.findViewById(R.id.manufacturer_text);
                quantityText = itemView.findViewById(R.id.quantity_text);
                expandIcon = itemView.findViewById(R.id.expand_icon);
                barcodeDetailsArea = itemView.findViewById(R.id.barcode_details_area);
                removeButton = itemView.findViewById(R.id.remove_button);
            }
        }
    }

    // 最近读取到的 EPC 滚动列表（与入库验收保持一致的简单实现）
    private static class EpcRollAdapter extends RecyclerView.Adapter<EpcRollAdapter.VH> {
        private static final int MAX = 50;
        private final ArrayList<String> items = new ArrayList<>();
        private final HashSet<String> set = new HashSet<>();

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_rfid_epc, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(VH h, int pos) {
            h.text.setText(items.get(pos));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

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
            items.clear();
            set.clear();
            notifyDataSetChanged();
        }

        static class VH extends RecyclerView.ViewHolder {
            TextView text;

            VH(View v) {
                super(v);
                text = v.findViewById(R.id.text_epc);
            }
        }
    }
}
