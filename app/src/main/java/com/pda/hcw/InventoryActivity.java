package com.pda.hcw;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
// import android.widget.Toast;

import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.pda.hcw.inventory.InventoryPlanItem;
import com.pda.hcw.inventory.InventoryRoomStatus;
import com.pda.hcw.inventory.InventorySession;
import com.pda.hcw.rfid.UHFManager;
import com.uhf.api.cls.Reader;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 库存盘点（状态机版）
 */
public class InventoryActivity extends BaseActivity {
    private static final String TAG = "InventoryActivity";

    // UI 状态机
    private enum TaskUIState { NO_TASK, PENDING, EXECUTING, FINISHED }
    private TaskUIState uiState = TaskUIState.NO_TASK;

    // UI
    private DrawerLayout drawerLayout;
    private LinearLayout taskHeaderBar;
    private TextView taskCodeText;
    private TextView taskStateText;
    private LinearLayout statsHeaderBar;
    private TextView headerTotalPlanned;
    private TextView headerCounted;
    private TextView headerUncounted;
    private TextView headerSurplus;

    private View viewNoTask;
    private View viewPending;
    private View viewExecuting;
    private View viewFinished;

    private MaterialButton btnCreateTaskCenter;
    private MaterialButton btnPendingRefresh;
    private MaterialButton btnPendingConfirm;
    private MaterialButton btnEndInventory;
    private MaterialButton btnExportCsv;
    private MaterialButton refreshTasksButton;

    private TextView tvPendingSummary;
    private TextView tvMissingList;
    private TextView tvSurplusList;

    private RecyclerView locationSummaryRecyclerView;
    private RecyclerView pendingGridRecyclerView;
    private RecyclerView taskListRecyclerView;

    private LinearLayout rfidStatusLayout;
    private TextView rfidStatusText;

    // 数据
    private final ExecutorService executorService = Executors.newFixedThreadPool(4);
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private InventoryDatabase inventoryDatabase;
    private CheckoutRecordDatabase checkoutDb;
    private InventoryTask currentTask;
    private InventorySession inventorySession;
    private List<LocationSummary> locationSummaries;
    private LocationSummaryAdapter locationSummaryAdapter;
    private LocationSummaryAdapter pendingGridAdapter;
    private List<InventoryTask> taskList;
    private InventoryTaskAdapter taskListAdapter;
    private String pendingTaskId = null;
    private String pendingSummaryText = "尚未拉取库存";

    // RFID
    private UHFManager uhfManager;
    private boolean isRFIDActive = false;
    private android.os.HandlerThread rfidThread;
    private Handler rfidBgHandler;
    private Runnable rfidRunnable;
    // 连续空读计数，用于触发自恢复
    private int emptyReadRounds = 0;
    // 蜂鸣器
    private com.pda.hcw.rfid.RfidBeepManager rfidBeepManager;
    private boolean beepModeRaw = false; // false=biz(业务增量), true=raw(读到即响)
    // 本轮扫描已见 EPC 集（用于“新标签”判定）
    private final java.util.Set<String> seenEpcsThisRun = new java.util.HashSet<>();
    private KeyReceiver keyReceiver;

    // 弹窗（保留引用防止GC）
    private PopupWindow locationDetailPopup;

    @Override
    protected int getLayoutResourceId() { return R.layout.activity_inventory; }
    @Override
    protected String getActivityName() { return "库存盘点"; }
    @Override
    protected String getActivityTitle() { return "库存盘点"; }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        // 不再提供工具栏菜单项（作废入口移至底部按钮）
        return false;
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            initViews();
            initData();
            setupEventListeners();
            loadInventoryTasks();
            setUIState(TaskUIState.NO_TASK);
        } catch (Exception e) {
            Log.e(TAG, "onCreate init error", e);
            showError("初始化失败");
        }
    }

    @Override
    protected void initViews() {
        super.initViews();
        drawerLayout = findViewById(R.id.drawer_layout);
        taskHeaderBar = findViewById(R.id.task_header_bar);
        taskCodeText = findViewById(R.id.task_code_text);
        taskStateText = findViewById(R.id.task_state_text);
        statsHeaderBar = findViewById(R.id.stats_header_bar);
        headerTotalPlanned = findViewById(R.id.header_total_planned);
        headerCounted = findViewById(R.id.header_counted);
        headerUncounted = findViewById(R.id.header_uncounted);
        headerSurplus = findViewById(R.id.header_surplus);

        viewNoTask = findViewById(R.id.view_no_task);
        viewPending = findViewById(R.id.view_pending);
        viewExecuting = findViewById(R.id.view_executing);
        viewFinished = findViewById(R.id.view_finished);

        btnCreateTaskCenter = findViewById(R.id.btn_create_task_center);
        btnPendingRefresh = findViewById(R.id.btn_pending_refresh);
        btnPendingConfirm = findViewById(R.id.btn_pending_confirm);
        btnEndInventory = findViewById(R.id.btn_end_inventory);
        // 新增作废按钮
        android.widget.Button btnVoid = findViewById(R.id.btn_void_task);
        if (btnVoid != null) btnVoid.setOnClickListener(v -> handleVoidCurrentTask());
        btnExportCsv = findViewById(R.id.btn_export_csv);
        refreshTasksButton = findViewById(R.id.refresh_tasks_button);

        tvPendingSummary = findViewById(R.id.tv_pending_summary);
        tvMissingList = findViewById(R.id.tv_missing_list);
        tvSurplusList = findViewById(R.id.tv_surplus_list);

        locationSummaryRecyclerView = findViewById(R.id.location_summary_recycler_view);
        pendingGridRecyclerView = findViewById(R.id.pending_grid_recycler_view);
        if (locationSummaryRecyclerView != null) {
            locationSummaryRecyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        }
        if (pendingGridRecyclerView != null) {
            pendingGridRecyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        }
        taskListRecyclerView = findViewById(R.id.task_list_recycler_view);
        if (taskListRecyclerView != null) {
            taskListRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        }

        rfidStatusLayout = findViewById(R.id.rfid_status_layout);
        rfidStatusText = findViewById(R.id.rfid_status_text);
    }

    private void initData() {
        inventoryDatabase = InventoryDatabase.getInstance(this);
        checkoutDb = CheckoutRecordDatabase.getInstance(this);
        uhfManager = UHFManager.getInstance();
        rfidBeepManager = new com.pda.hcw.rfid.RfidBeepManager();
        try {
            boolean beepEnabled = getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
                    .getBoolean("rfid_beep_enabled", true);
            rfidBeepManager.setEnabled(beepEnabled);
            boolean multi = getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
                    .getBoolean("rfid_multi_mode", true);
            rfidBeepManager.setMultiMode(multi);
        } catch (Throwable ignored) {}

        locationSummaries = new ArrayList<>();
        locationSummaryAdapter = new LocationSummaryAdapter(locationSummaries, this::openRoomDetail);
        if (locationSummaryRecyclerView != null) {
            locationSummaryRecyclerView.setAdapter(locationSummaryAdapter);
        }

        // 待确认阶段预览网格适配器
        if (pendingGridRecyclerView != null) {
            pendingGridAdapter = new LocationSummaryAdapter(new ArrayList<>(), this::openRoomDetail);
            pendingGridRecyclerView.setAdapter(pendingGridAdapter);
        }

        // 后台RFID线程在开始扫描时创建

        taskList = new ArrayList<>();
        taskListAdapter = new InventoryTaskAdapter(taskList, this::selectInventoryTask);
        if (taskListRecyclerView != null) {
            taskListRecyclerView.setAdapter(taskListAdapter);
        }
    }

    private void setupEventListeners() {
        if (btnCreateTaskCenter != null) btnCreateTaskCenter.setOnClickListener(v -> createNewInventoryTask());
        if (btnPendingRefresh != null) btnPendingRefresh.setOnClickListener(v -> refreshInventoryPlan());
        if (btnPendingConfirm != null) btnPendingConfirm.setOnClickListener(v -> confirmCurrentTask());
        if (btnEndInventory != null) btnEndInventory.setOnClickListener(v -> endInventory());
        if (btnExportCsv != null) btnExportCsv.setOnClickListener(v -> exportInventoryDiff());
        if (refreshTasksButton != null) refreshTasksButton.setOnClickListener(v -> refreshInventoryTasks());

        if (toolbar != null) toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    private void setUIState(TaskUIState state) {
        uiState = state;
        if (viewNoTask != null) viewNoTask.setVisibility(state == TaskUIState.NO_TASK ? View.VISIBLE : View.GONE);
        if (viewPending != null) viewPending.setVisibility(state == TaskUIState.PENDING ? View.VISIBLE : View.GONE);
        if (viewExecuting != null) viewExecuting.setVisibility(state == TaskUIState.EXECUTING ? View.VISIBLE : View.GONE);
        if (viewFinished != null) viewFinished.setVisibility(state == TaskUIState.FINISHED ? View.VISIBLE : View.GONE);
        if (taskHeaderBar != null) taskHeaderBar.setVisibility(state == TaskUIState.NO_TASK ? View.GONE : View.VISIBLE);
        if (statsHeaderBar != null) statsHeaderBar.setVisibility((state == TaskUIState.EXECUTING || state == TaskUIState.FINISHED) ? View.VISIBLE : View.GONE);
        showRFIDStandbyStatus();
    }

    private void loadInventoryTasks() {
        executorService.execute(() -> {
            try {
                List<InventoryTask> allTasks = inventoryDatabase.getLocalTasks();
                allTasks.sort((a, b) -> b.getCreateTime().compareTo(a.getCreateTime()));
                if (allTasks.size() > 7) allTasks = allTasks.subList(0, 7);
                final List<InventoryTask> finalTasks = allTasks;
                uiHandler.post(() -> {
                    taskList.clear();
                    taskList.addAll(finalTasks);
                    taskListAdapter.notifyDataSetChanged();
                    // 若存在“暂存”任务，优先自动加载最近一条
                    for (InventoryTask t : finalTasks) {
                        if (t != null && "暂存".equals(t.getStatus())) {
                            selectInventoryTask(t);
                            break;
                        }
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "loadInventoryTasks error", e);
            }
        });
    }

    private void refreshInventoryTasks() {
        refreshTasksButton.setEnabled(false);
        refreshTasksButton.setText("刷新中...");
        executorService.execute(() -> {
            try {
                Thread.sleep(600);
            } catch (InterruptedException ignored) {}
            uiHandler.post(() -> {
                loadInventoryTasks();
                refreshTasksButton.setEnabled(true);
                refreshTasksButton.setText("刷新任务");
            });
        });
    }

    // ========== 状态：未创建 ==========
    private void createNewInventoryTask() {
        String code = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        pendingTaskId = "PD-" + code;
        inventorySession = new InventorySession(pendingTaskId);
        if (taskHeaderBar != null) taskHeaderBar.setVisibility(View.VISIBLE);
        taskCodeText.setText("任务单号：" + pendingTaskId);
        taskStateText.setText("待确认");
        taskStateText.setTextColor(getResources().getColor(android.R.color.holo_orange_dark, null));
        pendingSummaryText = "尚未拉取库存";
        tvPendingSummary.setText(pendingSummaryText);
        setUIState(TaskUIState.PENDING);
        // 自动拉取库存（不切换到执行界面）
        refreshInventoryPlan();
    }

    private void refreshInventoryPlan() {
        btnPendingRefresh.setEnabled(false);
        tvPendingSummary.setText("库存拉取中...");
        ConsumableApiService.getInstance(this).fetchInventoryInfoList(new ConsumableApiService.ApiCallback<List<InventoryPlanItem>>() {
            @Override public void onSuccess(List<InventoryPlanItem> result) {
                try {
                    Map<String, Integer> roomNameToId = new HashMap<>();
                    List<OperatingRoom> rooms = OperatingRoomManager.getInstance(InventoryActivity.this).getAllRooms();
                    if (rooms != null) for (OperatingRoom r : rooms) if (r != null && r.name != null && r.serverId != null) roomNameToId.put(r.name, r.serverId);
                    List<InventoryPlanItem> mapped = new ArrayList<>();
                    for (InventoryPlanItem it : result) {
                        String rn = it.getRoomName();
                        int roomId = it.getRoomId() > 0 ? it.getRoomId() : (rn != null && roomNameToId.containsKey(rn) ? roomNameToId.get(rn) : 0);
                        mapped.add(new InventoryPlanItem(it.getRfid(), roomId, rn, it.getName(), it.getSpec(), it.getManufacturer(), it.getLastUser(), it.getLastUsedAt()));
                    }
                    inventorySession = new InventorySession(pendingTaskId != null ? pendingTaskId : ("TASK-" + System.currentTimeMillis()));
                    inventorySession.loadPlan(mapped);
                    updateHeaderStats();
                    pendingSummaryText = "已拉取 " + inventorySession.getTotalPlanned() + " 条 (" + new SimpleDateFormat("HH:mm:ss").format(new Date()) + ")";
                    tvPendingSummary.setText(pendingSummaryText);
                    // 在待确认阶段直接展示预览网格（不启用扫描，不切换状态）
                    if (viewPending != null) viewPending.setVisibility(View.VISIBLE);
                    if (viewExecuting != null) viewExecuting.setVisibility(View.GONE);
                    updatePendingGridFromSession();
                } finally {
                    btnPendingRefresh.setEnabled(true);
                }
            }
            @Override public void onFailure(Exception e) {
                btnPendingRefresh.setEnabled(true);
                // 内容区不再重复详细错误，避免与Banner重复
                tvPendingSummary.setText("拉取失败");
                showError(UIUtils.friendlyErrorMessage("加载库存失败", e));
            }
        });
    }

    // 预览逻辑已改为在view_pending内展示pendingGridRecyclerView

    private void confirmCurrentTask() {
        if (pendingTaskId == null || inventorySession == null) {
            showError("请先新建并拉取库存");
            return;
        }
        // 单一执行中任务约束：存在其他执行中任务则禁止确认
        if (checkoutDb.hasActiveInventoryTask()) {
            showError("存在执行中的盘点任务，请先结束或作废");
            return;
        }
        InventoryTask task = new InventoryTask();
        task.setTaskId(pendingTaskId);
        task.setTaskName(pendingTaskId);
        task.setCreateTime(new Date());
        UserInfo u = SessionManager.getInstance(this).getCurrentUser();
        task.setOperator(u != null ? u.getUsername() : "");
        task.setTotalItems(inventorySession.getTotalPlanned());
        task.setStatus("暂存"); // 执行中
        boolean ok = inventoryDatabase.saveInventoryTask(task);
        if (!ok) {
            showError("保存任务失败");
            return;
        }

        // 任务落库明细
        List<InventoryPlanItem> items = new ArrayList<>();
        for (String r : inventorySession.getAllPlannedRfids()) {
            InventoryPlanItem meta = inventorySession.getPlanMeta(r);
            if (meta != null) items.add(meta); else items.add(new InventoryPlanItem(r, 0, "耗材屋", null, null, null, null, null));
        }
        int inserted = checkoutDb.bulkInsertInventoryTaskDetails(task.getTaskId(), items);
        showSuccess("已确认任务，落库明细：" + inserted + " 条");

        // 切至执行中
        currentTask = task;
        taskCodeText.setText("任务单号：" + task.getTaskId());
        taskStateText.setText("执行中");
        taskStateText.setTextColor(getResources().getColor(android.R.color.holo_blue_dark, null));
        setUIState(TaskUIState.EXECUTING);
        updateHeaderStats();
        updateRoomGridFromSession();
        // 初始化UHF，但不自动启动扫描，仅通过物理按键启停
        initRFID();
        showRFIDStandbyStatus();
        // 清理pending标记
        pendingTaskId = null;
    }

    // ========== 执行中 ==========
    private void updateHeaderStats() {
        if (inventorySession == null) return;
        headerTotalPlanned.setText("总待盘：" + inventorySession.getTotalPlanned());
        headerCounted.setText("盘毕：" + inventorySession.getCounted());
        headerUncounted.setText("未盘：" + inventorySession.getUncounted());
        headerSurplus.setText("盈余：" + inventorySession.getSurplus());
    }

    private void updateRoomGridFromSession() {
        if (inventorySession == null) return;
        List<LocationSummary> list = new ArrayList<>();
        for (InventoryRoomStatus s : inventorySession.getRoomStatusesSorted()) {
            LocationSummary ls = new LocationSummary(s.getDisplayName(), s.getPlanned(), s.getCounted());
            ls.setRoomId(s.getRoomId());
            list.add(ls);
        }
        locationSummaries.clear();
        locationSummaries.addAll(list);
        locationSummaryAdapter.notifyDataSetChanged();
    }

    private void updatePendingGridFromSession() {
        if (inventorySession == null || pendingGridAdapter == null) return;
        List<LocationSummary> list = new ArrayList<>();
        for (InventoryRoomStatus s : inventorySession.getRoomStatusesSorted()) {
            LocationSummary ls = new LocationSummary(s.getDisplayName(), s.getPlanned(), 0 /*待确认阶段已盘=0*/);
            ls.setRoomId(s.getRoomId());
            list.add(ls);
        }
        // 更新适配器数据
        try {
            java.lang.reflect.Field f = LocationSummaryAdapter.class.getDeclaredField("items");
            f.setAccessible(true);
            f.set(pendingGridAdapter, list);
        } catch (Throwable ignore) {
            // 兜底：若反射失败则创建新适配器
            pendingGridAdapter = new LocationSummaryAdapter(list, this::openRoomDetail);
            if (pendingGridRecyclerView != null) pendingGridRecyclerView.setAdapter(pendingGridAdapter);
        }
        pendingGridAdapter.notifyDataSetChanged();
    }

    private void handleTagScanned(Reader.TAGINFO tag) {
        if (inventorySession == null) return;
        String epcRawHex = bytesToHexString(tag.EpcId);
        // 接收即格式化：只按两条合规规则转换；未命中则保留原值
        String epc = com.pda.hcw.rfid.EpcFormatter.normalizeOnReceive(epcRawHex);
        boolean accepted = inventorySession.onTag(epc);
        Log.d(TAG, "扫描到EPC=" + epc + ", 接受=" + accepted + ", 当前状态=" + uiState);
        if (!accepted) return;
        // 业务增量模式：仅当实际计数增加时蜂鸣
        if (!beepModeRaw && rfidBeepManager != null) {
            try { rfidBeepManager.requestBeep(); } catch (Throwable ignored) {}
        }
        if (uiState == TaskUIState.EXECUTING) {
            uiHandler.post(() -> {
                updateHeaderStats();
                updateRoomGridFromSession();
            });
            try {
                if (currentTask != null) {
                    boolean ok = checkoutDb.markInventoryItemScanned(currentTask.getTaskId(), epc, tag.RSSI);
                }
            } catch (Exception e) {
                Log.w(TAG, "markInventoryItemScanned failed", e);
            }
        }
    }

    private void openRoomDetail(LocationSummary summary) {
        try {
            if (inventorySession == null) return;
            int roomId = summary.getRoomId() != null ? summary.getRoomId() : 0;
            String loc = summary.getLocation();
            List<String> rfids = inventorySession.getRoomRfidList(roomId, true);
            Map<String, Boolean> state = inventorySession.getRoomRfidStateMap(roomId);
            List<String> display = new ArrayList<>();
            for (String r : rfids) display.add((state.getOrDefault(r, false) ? "[已盘] " : "[未盘] ") + r);
            final String[] items = display.toArray(new String[0]);
            new AlertDialog.Builder(this)
                    .setTitle(loc + " - RFID明细")
                    .setItems(items, (dialog, which) -> {
                        if (which >= 0 && which < rfids.size()) showRfidDetailDialog(rfids.get(which));
                    })
                    .setNegativeButton("关闭", null)
                    .show();
        } catch (Exception e) {
            Log.e(TAG, "openRoomDetail failed", e);
            showError("加载明细失败");
        }
    }

    private void showRfidDetailDialog(String rfid) {
        try {
            View view = getLayoutInflater().inflate(R.layout.dialog_rfid_detail, null);
            TextView tvRfid = view.findViewById(R.id.rfid_value);
            TextView tvName = view.findViewById(R.id.name_value);
            TextView tvSpec = view.findViewById(R.id.spec_value);
            TextView tvManu = view.findViewById(R.id.manufacturer_value);
            TextView tvUser = view.findViewById(R.id.last_user_value);
            TextView tvTime = view.findViewById(R.id.last_used_time_value);
            TextView tvStatus = view.findViewById(R.id.status_value);
            tvRfid.setText(rfid);
            InventoryPlanItem meta = inventorySession != null ? inventorySession.getPlanMeta(rfid) : null;
            if (meta != null) {
                tvName.setText(safe(meta.getName()));
                tvSpec.setText(safe(meta.getSpec()));
                tvManu.setText(safe(meta.getManufacturer()));
                // meta 中 lastUser/lastUsedAt 作为预填数据（若接口返回更新则覆盖）
                String preUser = meta.getLastUser();
                tvUser.setText(isBlankOrUnknown(preUser) ? "—" : preUser);
                if (meta.getLastUsedAt() != null) {
                    tvTime.setText(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(meta.getLastUsedAt())));
                } else {
                    tvTime.setText("—");
                }
            } else {
                tvName.setText("—"); tvSpec.setText("—"); tvManu.setText("—"); tvUser.setText("—"); tvTime.setText("—");
            }
            if (tvStatus != null) tvStatus.setText("—");
            AlertDialog dlg = new AlertDialog.Builder(this)
                    .setTitle("RFID详情")
                    .setView(view)
                    .setPositiveButton("关闭", null)
                    .create();
            dlg.show();
            // 总是调用懒加载接口以获取最新状态/操作人/时间
            List<String> one = new ArrayList<>(); one.add(rfid);
            ConsumableApiService.getInstance(this).queryConsumableInfoList(one, new ConsumableApiService.ApiCallback<ConsumableApiResponse>() {
                @Override public void onSuccess(ConsumableApiResponse resp) {
                    ConsumableItem info = null;
                    String status = null;
                    // 先在可领用中找
                    for (ConsumableItem it : resp.takeList) { if (rfid.equals(it.getRfid())) { info = it; status = "可领用"; break; } }
                    // 再在未归还中找
                    if (info == null) for (ConsumableItem it : resp.returnList) { if (rfid.equals(it.getRfid())) { info = it; status = "未归还"; break; } }
                    // 再在异常条码中找
                    if (info == null) for (ConsumableItem it : resp.errorData) { if (rfid.equals(it.getRfid())) { info = it; status = "异常条码"; break; } }
                    // 若仍未找到但在不在库列表中，则标记为不在库
                    if (info == null && resp.notInStockData != null) {
                        for (String s : resp.notInStockData) { if (rfid.equals(s)) { status = "不在库"; break; } }
                    }

                    final ConsumableItem finalInfo = info;
                    final String finalStatus = status;
                    uiHandler.post(() -> { if (dlg.isShowing()) {
                        if (finalInfo != null) {
                            tvName.setText(safe(finalInfo.getCommonName()));
                            tvSpec.setText(safe(finalInfo.getSpecification()));
                            tvManu.setText(safe(finalInfo.getManufacturer()));
                            // 最后一个操作人/时间（空或“未知”均显示“-”）
                            String opName = finalInfo.getLastOperatorName();
                            String opTime = finalInfo.getLastOperationTime();
                            tvUser.setText(isBlankOrUnknown(opName) ? "—" : opName);
                            tvTime.setText(isBlankOrUnknown(opTime) ? "—" : opTime);
                        } else {
                            // 无详情但有状态（如不在库）
                            tvUser.setText("—");
                            tvTime.setText("—");
                        }
                        if (tvStatus != null) tvStatus.setText(finalStatus == null ? "—" : finalStatus);
                    }});
                }
                @Override public void onFailure(Exception e) { Log.w(TAG, "查询RFID详情失败: " + e.getMessage()); }
            });
        } catch (Exception e) {
            Log.e(TAG, "showRfidDetailDialog failed", e);
            showError("加载RFID详情失败");
        }
    }

    private String safe(String s) { return (s == null || s.isEmpty()) ? "—" : s; }
    private boolean isEmpty(String s) { return s == null || s.isEmpty(); }
    private boolean isBlankOrUnknown(String s) { return s == null || s.trim().isEmpty() || "未知".equals(s.trim()); }

    // ========== 结束盘点 ==========
    private void endInventory() {
        if (uiState != TaskUIState.EXECUTING || currentTask == null) {
            showInfo("无执行中的任务");
            return;
        }
        stopRFIDScanning();
        inventoryDatabase.updateTaskStatus(currentTask.getTaskId(), "已完成");
        currentTask.setStatus("已完成");
        taskStateText.setText("已结束");
        taskStateText.setTextColor(getResources().getColor(android.R.color.holo_green_dark, null));
        setUIState(TaskUIState.FINISHED);
        showFinishedDiff();
        loadInventoryTasks();
    }

    private void showFinishedDiff() {
        if (inventorySession == null) return;
        Set<String> planned = inventorySession.getAllPlannedRfids();
        Set<String> counted = inventorySession.getCountedRfids();
        Set<String> surplus = inventorySession.getSurplusRfids();
        List<String> missingList = new ArrayList<>();
        for (String r : planned) if (!counted.contains(r)) missingList.add(r);
        java.util.Collections.sort(missingList);
        List<String> surplusList = new ArrayList<>(surplus);
        java.util.Collections.sort(surplusList);
        tvMissingList.setText(missingList.isEmpty() ? "—" : TextUtils.join("\n", missingList));
        tvSurplusList.setText(surplusList.isEmpty() ? "—" : TextUtils.join("\n", surplusList));
        statsHeaderBar.setVisibility(View.VISIBLE);
        updateHeaderStats();
    }

    // ========== RFID ==========
    private void initRFID() {
        try {
            if (uhfManager != null && !uhfManager.isInitialized()) {
                Log.d(TAG, "初始化UHF模块...");
                uhfManager.initialize(this);
                Log.d(TAG, "UHF初始化状态=" + uhfManager.isInitialized());
            }
        } catch (Exception e) {
            Log.e(TAG, "initRFID error", e);
        }
    }

    private void startRFIDScanning() {
        if (uhfManager == null || !uhfManager.isInitialized()) {
            // 尝试即刻初始化一次，尽量提升可用性
            try { initRFID(); } catch (Exception ignored) {}
            if (uhfManager == null || !uhfManager.isInitialized()) {
                showError("RFID未初始化");
                return;
            }
        }
        if (isRFIDActive) return;
        // 先确保彻底停止任何残留操作，避免硬件处于半激活状态
        try { uhfManager.stopAllOperations(); } catch (Exception ignored) {}
        isRFIDActive = true;
        Log.d(TAG, "开始RFID扫描");
        rfidStatusLayout.setVisibility(View.VISIBLE);
        rfidStatusText.setText("📡 RFID正在扫描（耗电）\n按物理键停止");
        if (rfidBeepManager != null) {
            try {
                boolean multi = getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
                        .getBoolean("rfid_multi_mode", true);
                rfidBeepManager.setMultiMode(multi);
                String mode = getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
                        .getString("rfid_beep_mode", "biz");
                beepModeRaw = "raw".equalsIgnoreCase(mode);
            } catch (Throwable ignored) {}
            rfidBeepManager.start();
        }
        seenEpcsThisRun.clear();
        // 预热 reader（对齐UHF-G：多标签模式下先进入异步读取，再进行实时/定时拉取）
        try {
            // 根据设置切换多/单标签模式
            boolean multi = getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
                    .getBoolean("rfid_multi_mode", true);
            uhfManager.setGen2session(multi);
            boolean started = uhfManager.asyncStartReading();
            Log.d(TAG, "预热asyncStartReading: " + started);
        } catch (Exception e) {
            Log.w(TAG, "asyncStartReading 预热失败，继续尝试轮询", e);
        }
        emptyReadRounds = 0;
        // 启动后台读取线程
        if (rfidThread == null) {
            rfidThread = new android.os.HandlerThread("rfid-read-loop");
            rfidThread.start();
            rfidBgHandler = new Handler(rfidThread.getLooper());
        }
        startRFIDPolling();
    }

    private void stopRFIDScanning() {
        boolean wasActive = isRFIDActive;
        isRFIDActive = false;
        Log.d(TAG, "停止RFID扫描");
        stopRFIDPolling();
        // 确保彻底停止硬件读取
        boolean stopped = true;
        try {
            boolean ok1 = uhfManager.asyncStopReading();
            try { Thread.sleep(30); } catch (InterruptedException ignored) {}
            uhfManager.stopAllOperations();
            // 基于内部标记校验（尽最大努力）；底层无直接查询接口时依赖返回码与状态
            stopped = ok1 && !uhfManager.isReading();
        } catch (Exception e) {
            Log.w(TAG, "确保停止硬件读取时发生异常", e);
            stopped = false;
        }
        if (!stopped && wasActive && rfidBeepManager != null) {
            // 发出明显的快速蜂鸣提醒注意耗电
            new Thread(() -> rfidBeepManager.alarmNotStopped()).start();
        }
        rfidStatusLayout.setVisibility(View.GONE);
        if (rfidBeepManager != null) rfidBeepManager.stop();
    }

    // 作废当前执行中的任务
    private void handleVoidCurrentTask() {
        try {
            if (uiState != TaskUIState.EXECUTING || currentTask == null) {
                showInfo("无执行中的任务");
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle("作废任务")
                    .setMessage("确认作废当前盘点任务？作废后不可恢复")
                    .setPositiveButton("确定", (d, w) -> {
                        try {
                            if (isRFIDActive) stopRFIDScanning();
                            boolean ok = checkoutDb.voidInventoryTask(currentTask.getTaskId());
                            if (ok) {
                                showSuccess("已作废");
                                currentTask = null;
                                locationSummaries.clear();
                                locationSummaryAdapter.notifyDataSetChanged();
                                setUIState(TaskUIState.NO_TASK);
                                loadInventoryTasks();
                            } else {
                                showError("作废失败");
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "void task failed", e);
                            showError(UIUtils.friendlyErrorMessage("作废失败", e));
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
        } catch (Exception e) {
            Log.e(TAG, "handleVoidCurrentTask error", e);
            showError("操作失败");
        }
    }

    private void startRFIDPolling() {
        rfidRunnable = new Runnable() {
            @Override public void run() {
                if (!isRFIDActive || uhfManager == null || !uhfManager.isInitialized()) return;
                try {
                    // 先试实时读取
                    List<Reader.TAGINFO> tagList = uhfManager.tagInventoryRealTime();
                    // 若为空，回退到定时读取，提升兼容性
                    if (tagList == null || tagList.isEmpty()) {
                        List<Reader.TAGINFO> timerList = uhfManager.tagInventoryByTimer((short) 50);
                        if (timerList != null && !timerList.isEmpty()) {
                            tagList = timerList;
                        }
                    }

                    if (tagList != null) Log.d(TAG, "本轮读取标签数=" + tagList.size());
                    if (tagList != null && !tagList.isEmpty()) {
                        emptyReadRounds = 0;
                        for (Reader.TAGINFO tag : tagList) {
                            if (beepModeRaw && rfidBeepManager != null) {
                                try { rfidBeepManager.requestBeep(); } catch (Throwable ignored) {}
                            }
                            handleTagScanned(tag);
                        }
                    } else {
                        // 连续空读达到阈值，做一次自恢复（与UHF-G一致）
                        emptyReadRounds++;
                        if (emptyReadRounds >= 4) {
                            try {
                                uhfManager.asyncStopReading();
                                Thread.sleep(30);
                                uhfManager.asyncStartReading();
                                Log.d(TAG, "空读自恢复：重启异步读取");
                            } catch (Exception e) {
                                Log.w(TAG, "空读自恢复失败", e);
                            } finally {
                                emptyReadRounds = 0;
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "RFID polling error", e);
                }
                if (isRFIDActive && rfidBgHandler != null) rfidBgHandler.postDelayed(this, 200);
            }
        };
        if (rfidBgHandler != null) rfidBgHandler.postDelayed(rfidRunnable, 200);
    }

    private void stopRFIDPolling() {
        if (rfidBgHandler != null && rfidRunnable != null) rfidBgHandler.removeCallbacks(rfidRunnable);
        if (rfidThread != null) { try { rfidThread.quitSafely(); } catch (Exception ignored) {} rfidThread = null; rfidBgHandler = null; }
    }

    // 重复定义移除：此方法已在上文定义

    private class KeyReceiver extends BroadcastReceiver {
        @Override public void onReceive(Context context, Intent intent) {
            int keyCode = intent.getIntExtra("keyCode", 0);
            if (keyCode == 0) keyCode = intent.getIntExtra("keycode", 0);
            boolean keyDown = intent.getBooleanExtra("keydown", false);
            Log.d(TAG, "物理键事件 keyCode=" + keyCode + ", keyDown=" + keyDown);
            if (!keyDown) { // 按键释放切换
                switch (keyCode) {
                    case KeyEvent.KEYCODE_F1:
                    case KeyEvent.KEYCODE_F2:
                    case KeyEvent.KEYCODE_F3:
                    case KeyEvent.KEYCODE_F4:
                    case KeyEvent.KEYCODE_F8:
                    case KeyEvent.KEYCODE_F7:
                        Log.d(TAG, "触发RFID扫描切换");
                        toggleRFIDScanning();
                        break;
                    default:
                        Log.d(TAG, "未处理的按键:" + keyCode);
                        break;
                }
            }
        }
    }

    // ===== 工具：字节数组转HEX（大写） =====
    private static String toHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "";
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02X", b & 0xFF));
        return sb.toString();
    }

    private void toggleRFIDScanning() {
        if (uiState != TaskUIState.EXECUTING || currentTask == null) {
            showError("请先选择盘点任务");
            return;
        }
        if (isRFIDActive) {
            stopRFIDScanning();
            showInfo("RFID扫描已停止");
            showRFIDStandbyStatus();
        } else {
            startRFIDScanning();
            showInfo("RFID扫描已启动");
        }
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

    private String bytesToHexString(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02X", b & 0xFF));
        return sb.toString();
    }

    // ===== 辅助：与Session一致的RFID表示转换 =====
    private boolean is18Digits(String s) {
        if (s == null || s.length() != 18) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        return true;
    }

    private String toAsciiHex(String digits18) {
        StringBuilder sb = new StringBuilder(36);
        for (int i = 0; i < digits18.length(); i++) {
            char c = digits18.charAt(i);
            sb.append(String.format("%02X", (int) c));
        }
        return sb.toString();
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

    // ========== 侧边栏选择 ==========
    private void selectInventoryTask(InventoryTask task) {
        // 切换任务前强制停止扫描，避免跨任务写入
        if (isRFIDActive) { stopRFIDScanning(); }
        currentTask = task;
        pendingTaskId = null;
        taskHeaderBar.setVisibility(View.VISIBLE);
        taskCodeText.setText("任务单号：" + task.getTaskId());
        String displayStatus = task.getStatus();
        if ("未执行".equals(displayStatus)) displayStatus = "待确认"; else if ("暂存".equals(displayStatus)) displayStatus = "执行中"; else if ("已完成".equals(displayStatus)) displayStatus = "已结束";
        taskStateText.setText(displayStatus);
        taskStateText.setTextColor(getResources().getColor(task.getStatusColor(), null));

        // 恢复会话
        try {
            List<InventoryPlanItem> planned = checkoutDb.getInventoryPlannedItems(task.getTaskId());
            Map<String, Integer> roomNameToId = new HashMap<>();
            List<OperatingRoom> rooms = OperatingRoomManager.getInstance(this).getAllRooms();
            if (rooms != null) for (OperatingRoom r : rooms) if (r != null && r.name != null && r.serverId != null) roomNameToId.put(r.name, r.serverId);
            List<InventoryPlanItem> mapped = new ArrayList<>();
            for (InventoryPlanItem it : planned) {
                String rn = it.getRoomName();
                int roomId = it.getRoomId() > 0 ? it.getRoomId() : (rn != null && roomNameToId.containsKey(rn) ? roomNameToId.get(rn) : 0);
                mapped.add(new InventoryPlanItem(it.getRfid(), roomId, rn, it.getName(), it.getSpec(), it.getManufacturer(), it.getLastUser(), it.getLastUsedAt()));
            }
            inventorySession = new InventorySession(task.getTaskId());
            inventorySession.loadPlan(mapped);
            List<String> scanned = checkoutDb.getInventoryScannedRfids(task.getTaskId());
            for (String r : scanned) inventorySession.onTag(r);
        } catch (Exception e) { Log.w(TAG, "restore session failed", e); }

        initRFID();
        if ("暂存".equals(task.getStatus())) {
            setUIState(TaskUIState.EXECUTING);
            // 恢复后立即刷新头部统计与房间网格
            updateHeaderStats();
            updateRoomGridFromSession();
            showRFIDStandbyStatus();
            showInfo("请使用物理按键启动RFID扫描");
        } else if ("已完成".equals(task.getStatus())) {
            setUIState(TaskUIState.FINISHED);
            // 恢复后刷新统计用于已完成展示
            updateHeaderStats();
            showFinishedDiff();
        } else {
            setUIState(TaskUIState.PENDING);
            tvPendingSummary.setText("已落库计划：" + inventorySession.getTotalPlanned());
            // 待确认视图预览
            updatePendingGridFromSession();
        }

        drawerLayout.closeDrawers();
    }

    // ========== 导出CSV ==========
    private void exportInventoryDiff() {
        if (currentTask == null || inventorySession == null) {
            showInfo("请选择任务");
            return;
        }

        executorService.execute(() -> {
            try {
                Set<String> planned = inventorySession.getAllPlannedRfids();
                Set<String> counted = inventorySession.getCountedRfids();
                Set<String> surplus = inventorySession.getSurplusRfids();
                List<String> missingList = new ArrayList<>();
                for (String r : planned) if (!counted.contains(r)) missingList.add(r);
                List<String> surplusList = new ArrayList<>(surplus);

                final Map<String, ConsumableItem> surplusInfo = new HashMap<>();
                if (!surplusList.isEmpty()) {
                    CountDownLatch latch = new CountDownLatch(1);
                    ConsumableApiService.getInstance(this).queryConsumableInfoList(surplusList, new ConsumableApiService.ApiCallback<ConsumableApiResponse>() {
                        @Override public void onSuccess(ConsumableApiResponse resp) {
                            for (ConsumableItem item : resp.takeList) if (item.getRfid() != null) surplusInfo.put(item.getRfid(), item);
                            for (ConsumableItem item : resp.returnList) if (item.getRfid() != null && !surplusInfo.containsKey(item.getRfid())) surplusInfo.put(item.getRfid(), item);
                            for (ConsumableItem item : resp.errorData) if (item.getRfid() != null && !surplusInfo.containsKey(item.getRfid())) surplusInfo.put(item.getRfid(), item);
                            latch.countDown();
                        }
                        @Override public void onFailure(Exception e) { Log.w(TAG, "盈余补齐失败: " + e.getMessage()); latch.countDown(); }
                    });
                    latch.await(3, TimeUnit.SECONDS);
                }

                StringBuilder csv = new StringBuilder();
                csv.append("rfid,name,spec,manufacturer,diffType,lastUser,lastUsedAt,roomId,roomName\n");
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                for (String r : missingList) {
                    InventoryPlanItem meta = inventorySession.getPlanMeta(r);
                    int roomId = 0; String roomName = ""; String name = ""; String spec = ""; String manufacturer = ""; String lastUser = ""; String lastUsedAt = "";
                    if (meta != null) {
                        roomId = meta.getRoomId();
                        roomName = meta.getRoomName() != null ? meta.getRoomName() : "";
                        name = meta.getName() != null ? meta.getName() : "";
                        spec = meta.getSpec() != null ? meta.getSpec() : "";
                        manufacturer = meta.getManufacturer() != null ? meta.getManufacturer() : "";
                        if (meta.getLastUsedAt() != null) lastUsedAt = sdf.format(new Date(meta.getLastUsedAt()));
                        if (meta.getLastUser() != null) lastUser = meta.getLastUser();
                    }
                    csv.append(String.join(",",
                            escapeCsv(r), escapeCsv(name), escapeCsv(spec), escapeCsv(manufacturer),
                            "缺失", escapeCsv(lastUser), escapeCsv(lastUsedAt), String.valueOf(roomId), escapeCsv(roomName)
                    )).append("\n");
                }
                for (String r : surplusList) {
                    ConsumableItem info = surplusInfo.get(r);
                    String name = info != null ? info.getCommonName() : "";
                    String spec = info != null ? info.getSpecification() : "";
                    String manufacturer = info != null ? info.getManufacturer() : "";
                    csv.append(String.join(",",
                            escapeCsv(r), escapeCsv(name), escapeCsv(spec), escapeCsv(manufacturer),
                            "盈余", "", "", "", ""
                    )).append("\n");
                }

                String code = currentTask.getTaskId();
                String fileName = "PD-DIFF-" + code + ".csv";
                String path = writeCsvToDownloads(fileName, csv.toString());
                uiHandler.post(() -> showSuccess("差异表已导出到下载: " + path));
            } catch (Exception e) {
                Log.e(TAG, "导出差异失败", e);
                uiHandler.post(() -> showError(UIUtils.friendlyErrorMessage("导出差异失败", e)));
            }
        });
    }

    private String escapeCsv(String s) {
        if (s == null) return "";
        boolean needQuote = s.contains(",") || s.contains("\n") || s.contains("\"");
        String v = s.replace("\"", "\"\"");
        return needQuote ? "\"" + v + "\"" : v;
    }

    private String writeCsvToDownloads(String fileName, String content) throws Exception {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            android.content.ContentValues values = new android.content.ContentValues();
            values.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            values.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/csv");
            values.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS);
            Uri uri = getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new Exception("无法创建下载文件");
            try (java.io.OutputStream os = getContentResolver().openOutputStream(uri)) {
                os.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                os.flush();
            }
            return android.os.Environment.DIRECTORY_DOWNLOADS + "/" + fileName;
        } else {
            java.io.File dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
            if (!dir.exists()) dir.mkdirs();
            java.io.File out = new java.io.File(dir, fileName);
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(out)) {
                fos.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                fos.flush();
            }
            return out.getAbsolutePath();
        }
    }

    private void showRFIDStandbyStatus() {
        if (uiState == TaskUIState.EXECUTING && uhfManager != null && uhfManager.isInitialized()) {
            rfidStatusLayout.setVisibility(View.VISIBLE);
            rfidStatusText.setText("⏸️ RFID已就绪\n(按物理键开始扫描)");
            rfidStatusText.setTextColor(getResources().getColor(android.R.color.holo_blue_dark, null));
        } else {
            rfidStatusLayout.setVisibility(View.GONE);
        }
    }

    // ========== 生命周期 ==========
    @Override protected void onResume() {
        super.onResume();
        registerKeyReceiver();
        if (uiState == TaskUIState.EXECUTING && !isRFIDActive) showRFIDStandbyStatus();
    }
    @Override protected void onPause() {
        super.onPause();
        unregisterKeyReceiver();
        if (isRFIDActive) stopRFIDScanning();
    }
    @Override protected void onDestroy() {
        super.onDestroy();
        try { if (uhfManager != null) { stopRFIDScanning(); uhfManager.close(); } } catch (Exception ignored) {}
        try { if (rfidBeepManager != null) { rfidBeepManager.release(); } } catch (Exception ignored) {}
        try { if (inventoryDatabase != null) checkoutDb.closeDatabase(); } catch (Exception ignored) {}
        executorService.shutdown();
    }

    @Override public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
            return;
        }
        if (uiState == TaskUIState.EXECUTING && currentTask != null) {
            // 自动暂存：停止扫描，保留任务为“暂存”，提示并退出
            if (isRFIDActive) stopRFIDScanning();
            showSuccess("已暂存当前盘点");
            // 清理内存态，返回到任务列表
            currentTask = null;
            locationSummaries.clear();
            if (locationSummaryAdapter != null) locationSummaryAdapter.notifyDataSetChanged();
            setUIState(TaskUIState.NO_TASK);
            super.onBackPressed();
            return;
        }
        super.onBackPressed();
    }
}
