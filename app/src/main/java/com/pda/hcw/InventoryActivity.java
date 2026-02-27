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
import com.google.android.material.tabs.TabLayout;
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
    private static final String MISSING_CODE_LABEL = "子码缺失";

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
    private MaterialButton refreshTasksButton;

    private TextView tvPendingSummary;

    // 已完成视图：差异明细
    private TextView tvDiffSummary;
    private TextView tvDiffEmpty;
    private TabLayout diffTabLayout;
    private RecyclerView diffRecyclerView;
    private InventoryDiffAdapter diffAdapter;

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
    // 当前任务的扫描缓存：已提交与待提交的唯一码集合（仅在本 Activity 生命周期内生效）
    private final java.util.Set<String> submittedRfids = new java.util.HashSet<>();
    private final java.util.Set<String> pendingBatchRfids = new java.util.HashSet<>();
    private boolean isSubmittingBatch = false;
    private ConsumableApiService.InventoryTaskDetailResult currentTaskDetail;
    private List<LocationSummary> locationSummaries;
    private LocationSummaryAdapter locationSummaryAdapter;
    private LocationSummaryAdapter pendingGridAdapter;
    private List<InventoryTask> taskList;
    private InventoryTaskAdapter taskListAdapter;
    private String pendingTaskId = null;
    private String pendingSummaryText = "尚未拉取库存";

    // 差异明细数据
    private final List<InventoryDiffAdapter.DiffItem> diffMissingItems = new ArrayList<>();
    private final List<InventoryDiffAdapter.DiffItem> diffSurplusItems = new ArrayList<>();
    private int currentDiffTabIndex = 0; // 0:缺失 1:盈余

    // RFID
    private UHFManager uhfManager;
    private boolean isRFIDActive = false;
    private android.os.HandlerThread rfidThread;
    private Handler rfidBgHandler;
    private Runnable rfidRunnable;
    // 连续空读计数，用于触发自恢复
    private int emptyReadRounds = 0;
    // 【性能优化】RFID轮询退避策略
    private static final int RFID_POLL_MIN_INTERVAL = 200;  // 最小轮询间隔
    private static final int RFID_POLL_MAX_INTERVAL = 800;  // 最大轮询间隔
    private int currentPollInterval = RFID_POLL_MIN_INTERVAL;
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
        refreshTasksButton = findViewById(R.id.refresh_tasks_button);

        tvPendingSummary = findViewById(R.id.tv_pending_summary);

        tvDiffSummary = findViewById(R.id.tv_diff_summary);
        tvDiffEmpty = findViewById(R.id.tv_diff_empty);
        diffTabLayout = findViewById(R.id.inventory_diff_tabs);
        diffRecyclerView = findViewById(R.id.diff_recycler_view);

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

        if (diffRecyclerView != null) {
            diffRecyclerView.setLayoutManager(new LinearLayoutManager(this));
            diffAdapter = new InventoryDiffAdapter(item -> showRfidDetailDialog(item.rfid));
            diffRecyclerView.setAdapter(diffAdapter);
        }

        if (diffTabLayout != null) {
            diffTabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
                @Override
                public void onTabSelected(TabLayout.Tab tab) {
                    currentDiffTabIndex = tab.getPosition();
                    updateDiffListForCurrentTab();
                }

                @Override public void onTabUnselected(TabLayout.Tab tab) { }
                @Override public void onTabReselected(TabLayout.Tab tab) {
                    // 重新点击同一Tab时也刷新一次，保证数据与空态一致
                    currentDiffTabIndex = tab.getPosition();
                    updateDiffListForCurrentTab();
                }
            });
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
        // 主入口：不再本地新建任务，改为打开左侧任务列表并刷新
        if (btnCreateTaskCenter != null) {
            btnCreateTaskCenter.setOnClickListener(v -> {
                if (drawerLayout != null) {
                    drawerLayout.openDrawer(GravityCompat.START);
                }
                refreshInventoryTasks();
            });
        }
        // 待确认阶段不再使用，隐藏按钮避免误操作
        if (btnPendingRefresh != null) {
            btnPendingRefresh.setVisibility(View.GONE);
        }
        if (btnPendingConfirm != null) {
            btnPendingConfirm.setVisibility(View.GONE);
        }
        if (btnEndInventory != null) {
            btnEndInventory.setText("提交当前盘点进度");
            btnEndInventory.setOnClickListener(v -> endInventory());
        }
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
        try {
            ConsumableApiService.getInstance(this).fetchInventoryTaskList(
                    new ConsumableApiService.ApiCallback<List<InventoryTask>>() {
                        @Override
                        public void onSuccess(List<InventoryTask> result) {
                            if (result == null) result = new ArrayList<>();
                            // 最近任务排在前面（服务端已按创建时间倒序，这里仅兜底）
                            try {
                                result.sort((a, b) -> {
                                    if (a.getCreateTime() == null || b.getCreateTime() == null) return 0;
                                    return b.getCreateTime().compareTo(a.getCreateTime());
                                });
                            } catch (Exception ignore) {}
                            taskList.clear();
                            taskList.addAll(result);
                            taskListAdapter.updateItems(result);
                            if (taskList.isEmpty()) {
                                showInfo("当前地点暂无进行中的盘点任务，请先在 Web 端创建任务");
                            }
                            if (refreshTasksButton != null) {
                                refreshTasksButton.setEnabled(true);
                                refreshTasksButton.setText("刷新任务");
                            }
                        }

                        @Override
                        public void onFailure(Exception e) {
                            Log.e(TAG, "loadInventoryTasks failed", e);
                            showError(UIUtils.friendlyErrorMessage("加载盘点任务失败", e));
                            if (refreshTasksButton != null) {
                                refreshTasksButton.setEnabled(true);
                                refreshTasksButton.setText("刷新任务");
                            }
                        }
                    }
            );
        } catch (Exception e) {
            Log.e(TAG, "loadInventoryTasks error", e);
            showError("加载盘点任务失败: " + e.getMessage());
            if (refreshTasksButton != null) {
                refreshTasksButton.setEnabled(true);
                refreshTasksButton.setText("刷新任务");
            }
        }
    }

    private void refreshInventoryTasks() {
        if (refreshTasksButton != null) {
            refreshTasksButton.setEnabled(false);
            refreshTasksButton.setText("刷新中...");
        }
        loadInventoryTasks();
    }

    /**
     * 从后端加载当前任务明细，并用其重建 InventorySession 与地点汇总。
     */
    private void reloadTaskDetailAndSession(boolean showErrorToast) {
        final InventoryTask task = currentTask;
        if (task == null) return;
        try {
            ConsumableApiService.getInstance(this).fetchInventoryTaskDetail(
                    task.getTaskId(),
                    new ConsumableApiService.ApiCallback<ConsumableApiService.InventoryTaskDetailResult>() {
                        @Override
                        public void onSuccess(ConsumableApiService.InventoryTaskDetailResult result) {
                            currentTaskDetail = result;
                            rebuildSessionFromDetail(task.getTaskId(), result);
                        }

                        @Override
                        public void onFailure(Exception e) {
                            Log.e(TAG, "reloadTaskDetailAndSession failed", e);
                            if (showErrorToast) {
                                showError(UIUtils.friendlyErrorMessage("加载盘点明细失败", e));
                            }
                        }
                    }
            );
        } catch (Exception e) {
            Log.e(TAG, "reloadTaskDetailAndSession error", e);
            if (showErrorToast) {
                showError("加载盘点明细失败: " + e.getMessage());
            }
        }
    }

    /**
     * 基于后端盘点明细构建内存态 InventorySession，并更新地点卡片。
     */
    private void rebuildSessionFromDetail(String taskId, ConsumableApiService.InventoryTaskDetailResult detail) {
        try {
            inventorySession = new InventorySession(taskId);
            if (detail == null || detail.items == null || detail.items.isEmpty()) {
                // 清空地点网格
                locationSummaries.clear();
                if (locationSummaryAdapter != null) locationSummaryAdapter.notifyDataSetChanged();
                return;
            }

            // 先探测当前任务是否具备“快照状态”字段：
            // - 新版后端：计划内行 snapshotStatus 不为空，盈余行 snapshotStatus 为空；
            // - 旧版/历史任务：可能所有行 snapshotStatus 都为空，此时不能简单认为都是盈余，否则地点网格永远为空。
            boolean hasSnapshotFlag = false;
            for (ConsumableApiService.InventoryTaskDetailItem it : detail.items) {
                if (it != null && it.snapshotStatus != null && !it.snapshotStatus.trim().isEmpty()) {
                    hasSnapshotFlag = true;
                    break;
                }
            }

            java.util.List<InventoryPlanItem> plan = new java.util.ArrayList<>();
            for (ConsumableApiService.InventoryTaskDetailItem it : detail.items) {
                if (it == null) continue;
                // 如果后端已经提供 snapshotStatus，则仅将 snapshotStatus!=null 的快照行视为“计划内”，盈余等补录行不计入计划；
                // 若整单任务不存在 snapshotStatus（历史/旧任务），则退化为“所有明细均视为计划行”，避免地点卡片永远不渲染。
                if (hasSnapshotFlag && it.snapshotStatus == null) continue;
                String rfid = it.rfid;
                if (rfid == null || rfid.trim().isEmpty()) continue;
                int roomId = (it.operatingRoomID != null ? it.operatingRoomID : 0);
                String roomName = it.operatingRoomName;
                String code = it.code;
                String name = it.name;
                String spec = it.spec;
                String manufacturer = it.manufacturer;
                plan.add(new InventoryPlanItem(rfid, roomId, roomName, code, name, spec, manufacturer, null, null));
            }

            // 载入计划
            inventorySession.loadPlan(plan);

            // 写入状态快照 + 根据 scanStatus 标记已盘
            for (ConsumableApiService.InventoryTaskDetailItem it : detail.items) {
                if (it == null) continue;
                if (hasSnapshotFlag && it.snapshotStatus == null) continue;
                String rfid = it.rfid;
                if (rfid == null || rfid.trim().isEmpty()) continue;
                inventorySession.setStatusFor(rfid, it.snapshotStatus, it.latestStatus);
                if ("scanned".equalsIgnoreCase(it.scanStatus)) {
                    // 利用 onTag 将其标记为已盘（计划内标签不会被计为盈余）
                    inventorySession.onTag(rfid);
                }
            }

            // 更新地点卡片
            updateRoomGridFromSession();
        } catch (Exception e) {
            Log.e(TAG, "rebuildSessionFromDetail error", e);
        }
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
                    List<InventoryPlanItem> mapped = new ArrayList<>();
                    for (InventoryPlanItem it : result) {
                        // 直接信任后端返回的地点ID与名称（current_location_id / current_location_name）
                        mapped.add(new InventoryPlanItem(
                                it.getRfid(),
                                it.getRoomId(),
                                it.getRoomName(),
                                it.getCode(),
                                it.getName(),
                                it.getSpec(),
                                it.getManufacturer(),
                                it.getLastUser(),
                                it.getLastUsedAt()
                        ));
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
        task.setRawStatus("running");
        task.setStatus("执行中");
        boolean ok = inventoryDatabase.saveInventoryTask(task);
        if (!ok) {
            showError("保存任务失败");
            return;
        }

        // 任务落库明细
        List<InventoryPlanItem> items = new ArrayList<>();
        for (String r : inventorySession.getAllPlannedRfids()) {
            InventoryPlanItem meta = inventorySession.getPlanMeta(r);
            // 只添加有元数据的项，地点信息完全由后端返回
            if (meta != null) items.add(meta);
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
        int planned = 0;
        int normal = 0;
        int missing = 0;
        int surplus = 0;
        if (currentTask != null) {
            // 总待盘 = 快照数量（plannedCount），保持不变
            planned = Math.max(0, currentTask.getTotalItems());
            // 盘毕 = Web 端“正常”数量（匹配到快照的唯一码数量）
            normal = Math.max(0, currentTask.getNormalCount());
            // 未盘 = Web 端“缺失”数量
            missing = Math.max(0, currentTask.getMissingCount());
            // 盈余 = Web 端“盈余”数量
            surplus = Math.max(0, currentTask.getSurplusCount());
        }
        headerTotalPlanned.setText("总待盘：" + planned);
        headerCounted.setText("盘毕：" + normal);
        headerUncounted.setText("未盘：" + missing);
        headerSurplus.setText("盈余：" + surplus);
    }

    private void updateRoomGridFromSession() {
        if (inventorySession == null) return;
        List<LocationSummary> list = new ArrayList<>();
        for (InventoryRoomStatus s : inventorySession.getRoomStatusesSorted()) {
            LocationSummary ls = new LocationSummary(s.getDisplayName(), s.getPlanned(), s.getCounted());
            ls.setRoomId(s.getRoomId());
            list.add(ls);
        }
        // 使用适配器内部维护的列表，避免仅修改外部 locationSummaries 而不更新 UI
        if (locationSummaryAdapter != null) {
            locationSummaryAdapter.updateItems(list);
        } else {
            locationSummaries.clear();
            locationSummaries.addAll(list);
        }
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
        if (uiState != TaskUIState.EXECUTING || currentTask == null || !"running".equals(currentTask.getRawStatus())) {
            return;
        }
        String epcRawHex = bytesToHexString(tag.EpcId);
        // 接收即格式化：按统一规则转换为业务唯一码
        String epc = com.pda.hcw.rfid.EpcFormatter.normalizeOnReceive(epcRawHex);
        if (epc == null || epc.trim().isEmpty()) return;
        epc = epc.trim();

        // 本轮扫描去重：避免同一轮内重复蜂鸣
        if (seenEpcsThisRun.contains(epc)) {
            return;
        }
        seenEpcsThisRun.add(epc);

        // 记录到待提交批次（避免重复提交已成功上传的标签）
        boolean isNewTag = false;
        if (!submittedRfids.contains(epc)) {
            pendingBatchRfids.add(epc);
            isNewTag = true;
        }

        Log.d(TAG, "扫描到 EPC=" + epc + ", 当前待提交数量=" + pendingBatchRfids.size());

        // 业务增量模式：仅当实际新增采集时蜂鸣
        if (!beepModeRaw && rfidBeepManager != null) {
            try {
                rfidBeepManager.requestBeep();
            } catch (Throwable ignored) {}
        }

        // 实时更新本地会话状态并刷新UI
        if (isNewTag && inventorySession != null) {
            final String finalEpc = epc;
            inventorySession.onTag(finalEpc);
            // 切到主线程刷新UI
            uiHandler.post(() -> {
                // 更新本地统计（基于 inventorySession 计算）
                int counted = inventorySession.getCounted();
                int surplus = inventorySession.getSurplus();
                int planned = inventorySession.getTotalPlanned();
                int uncounted = inventorySession.getUncounted();
                headerTotalPlanned.setText("总待盘：" + planned);
                headerCounted.setText("盘毕：" + counted);
                headerUncounted.setText("未盘：" + uncounted);
                headerSurplus.setText("盈余：" + surplus);
                // 刷新地点卡片
                updateRoomGridFromSession();
            });
        }
    }

    private void openRoomDetail(LocationSummary summary) {
        try {
            if (inventorySession == null) return;
            int roomId = summary.getRoomId() != null ? summary.getRoomId() : 0;
            String loc = summary.getLocation();

            List<String> rfids = inventorySession.getRoomRfidList(roomId, true);
            Map<String, Boolean> state = inventorySession.getRoomRfidStateMap(roomId);
            Map<String, RoomGroupedRfidAdapter.RoomGroupedItem> grouped = new HashMap<>();
            int uncountedTotal = 0;
            boolean hasNonMissingCode = false;

            for (String rfid : rfids) {
                boolean counted = state.getOrDefault(rfid, false);
                if (counted) {
                    continue; // 仅展示未盘
                }
                uncountedTotal++;

                InventoryPlanItem meta = inventorySession.getPlanMeta(rfid);
                String rawCode = meta != null ? meta.getCode() : null;
                String groupCode = rawCode == null ? "" : rawCode.trim();
                if (!groupCode.isEmpty()) {
                    hasNonMissingCode = true;
                } else {
                    groupCode = MISSING_CODE_LABEL;
                }

                RoomGroupedRfidAdapter.RoomGroupedItem group = grouped.get(groupCode);
                if (group == null) {
                    group = new RoomGroupedRfidAdapter.RoomGroupedItem();
                    group.code = groupCode;
                    group.name = meta != null ? meta.getName() : null;
                    group.spec = meta != null ? meta.getSpec() : null;
                    group.manufacturer = meta != null ? meta.getManufacturer() : null;
                    grouped.put(groupCode, group);
                } else if (meta != null) {
                    // 聚合组已有空字段时，尝试用新元数据补齐
                    if (TextUtils.isEmpty(group.name) && !TextUtils.isEmpty(meta.getName())) {
                        group.name = meta.getName();
                    }
                    if (TextUtils.isEmpty(group.spec) && !TextUtils.isEmpty(meta.getSpec())) {
                        group.spec = meta.getSpec();
                    }
                    if (TextUtils.isEmpty(group.manufacturer) && !TextUtils.isEmpty(meta.getManufacturer())) {
                        group.manufacturer = meta.getManufacturer();
                    }
                }
                group.rfids.add(rfid);
            }

            List<RoomGroupedRfidAdapter.RoomGroupedItem> data = new ArrayList<>(grouped.values());
            data.sort((a, b) -> {
                boolean aMissing = MISSING_CODE_LABEL.equals(a.code);
                boolean bMissing = MISSING_CODE_LABEL.equals(b.code);
                if (aMissing != bMissing) return aMissing ? 1 : -1; // 子码缺失固定置后
                String ac = a.code == null ? "" : a.code;
                String bc = b.code == null ? "" : b.code;
                return ac.compareToIgnoreCase(bc);
            });
            if (!data.isEmpty() && !hasNonMissingCode) {
                Log.w(TAG, "当前术间所有未盘标签均缺失子码，roomId=" + roomId + ", roomName=" + loc);
            }

            View view = getLayoutInflater().inflate(R.layout.dialog_room_rfid_list, null);
            androidx.recyclerview.widget.RecyclerView rv = view.findViewById(R.id.rv_rfid_list);
            android.widget.TextView tvSummary = view.findViewById(R.id.tv_summary);
            android.widget.TextView tvEmpty = view.findViewById(R.id.tv_empty);

            rv.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
            RoomGroupedRfidAdapter adapter = new RoomGroupedRfidAdapter(data, this::showRfidDetailDialog);
            rv.setAdapter(adapter);
            updateGroupedSummary(tvSummary, data.size(), uncountedTotal);
            boolean isEmpty = data.isEmpty();
            tvEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            rv.setVisibility(isEmpty ? View.GONE : View.VISIBLE);

            new AlertDialog.Builder(this)
                    .setTitle(loc + " - 未盘明细（按子码）")
                    .setView(view)
                    .setNegativeButton("关闭", null)
                    .show();
        } catch (Exception e) {
            Log.e(TAG, "openRoomDetail failed", e);
            showError("加载明细失败");
        }
    }

    private void updateGroupedSummary(TextView tv, int groupCount, int rfidCount) {
        try {
            tv.setText("子码组: " + groupCount + "  未盘RFID: " + rfidCount);
        } catch (Exception ignore) {}
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
            if (tvStatus != null) {
                // 先用盘点任务中的 snapshotStatus/latestStatus 映射一个初始状态文案
                String snap = inventorySession != null ? inventorySession.getSnapshotStatus(rfid) : null;
                String latest = inventorySession != null ? inventorySession.getLatestStatus(rfid) : null;
                String initial = mapInventoryStatus(latest != null ? latest : snap);
                tvStatus.setText(initial != null ? initial : "—");
            }
            AlertDialog dlg = new AlertDialog.Builder(this)
                    .setTitle("RFID详情")
                    .setView(view)
                    .setPositiveButton("关闭", null)
                    .create();
            dlg.show();
            // 总是调用接口以获取最新库存状态/操作人/时间
            List<String> one = new ArrayList<>(); one.add(rfid);
            ConsumableApiService.getInstance(this).queryConsumableInfoList(one, new ConsumableApiService.ApiCallback<ConsumableApiResponse>() {
                @Override public void onSuccess(ConsumableApiResponse resp) {
                    ConsumableItem info = null;
                    String status = null;
                    // 依次在五个列表中查找
                    for (ConsumableItem it : resp.inStockList) {
                        if (rfid.equals(it.getRfid())) { info = it; status = "在库"; break; }
                    }
                    if (info == null) {
                        for (ConsumableItem it : resp.takenList) {
                            if (rfid.equals(it.getRfid())) { info = it; status = "取用未归还"; break; }
                        }
                    }
                    if (info == null) {
                        for (ConsumableItem it : resp.consumedList) {
                            if (rfid.equals(it.getRfid())) { info = it; status = "已消耗"; break; }
                        }
                    }
                    if (info == null) {
                        for (ConsumableItem it : resp.usedReturnList) {
                            if (rfid.equals(it.getRfid())) { info = it; status = "消耗退回"; break; }
                        }
                    }
                    // 若仍未找到但在 notInStockList 中，则标记为不在库
                    if (info == null && resp.notInStockList != null) {
                        for (String s : resp.notInStockList) { if (rfid.equals(s)) { status = "不在库"; break; } }
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

    /**
     * 将库存状态枚举映射为中文文案。
     * 约定：
     * - inStock    -> 在库
     * - taken      -> 取用未归还
     * - consumed   -> 已消耗
     * - usedReturn -> 消耗退回
     * 其他或空值返回 null，由调用方决定展示占位符。
     */
    private String mapInventoryStatus(String status) {
        if (status == null) return null;
        String s = status.trim();
        if (s.isEmpty()) return null;
        switch (s) {
            case "inStock":
                return "在库";
            case "taken":
                return "取用未归还";
            case "consumed":
                return "已消耗";
            case "usedReturn":
                return "消耗退回";
            default:
                return null;
        }
    }

    // ========== 结束/提交当前批次 ==========
    private void endInventory() {
        if (uiState != TaskUIState.EXECUTING || currentTask == null) {
            showInfo("无执行中的盘点任务");
            return;
        }
        // “结束盘点”仅表示本设备停止当前扫描并提交已采集批次，任务本身仍由 Web 端结束
        if (isRFIDActive) {
            stopRFIDScanning();
        }
        submitCurrentBatch(true);
    }

    /**
     * 提交当前缓存的扫描批次到后端 inventoryTask/submitScanBatch 接口。
     * 若无待提交数据且 forceToast=true，则提示一次；否则静默返回。
     */
    private void submitCurrentBatch(boolean forceToastOnEmpty) {
        if (currentTask == null || !"running".equals(currentTask.getRawStatus())) {
            if (forceToastOnEmpty) {
                showInfo("当前任务状态不允许提交盘点数据");
            }
            return;
        }
        if (isSubmittingBatch) {
            if (forceToastOnEmpty) {
                showInfo("上一批盘点数据正在提交，请稍候");
            }
            return;
        }

        // 去除已提交过的标签，避免重复产生盈余行
        java.util.List<String> batch = new java.util.ArrayList<>();
        for (String code : new java.util.HashSet<>(pendingBatchRfids)) {
            if (code == null) continue;
            String v = code.trim();
            if (v.isEmpty()) continue;
            if (submittedRfids.contains(v)) continue;
            batch.add(v);
        }

        if (batch.isEmpty()) {
            if (forceToastOnEmpty) {
                showInfo("当前无新的标签需要提交");
            }
            return;
        }

        isSubmittingBatch = true;
        showInfo("正在提交本轮盘点数据，共 " + batch.size() + " 个标签…");

        ConsumableApiService.getInstance(this).submitInventoryScanBatch(
                currentTask.getTaskId(),
                batch,
                new ConsumableApiService.ApiCallback<ConsumableApiService.InventoryTaskSummary>() {
                    @Override
                    public void onSuccess(ConsumableApiService.InventoryTaskSummary result) {
                        try {
                            // 更新任务统计
                            if (result != null) {
                                currentTask.setTotalItems(result.plannedCount);
                                currentTask.setScannedCount(result.scannedCount);
                                currentTask.setNormalCount(result.normalCount);
                                currentTask.setMissingCount(result.missingCount);
                                currentTask.setSurplusCount(result.surplusCount);
                            }
                            // 标记已提交标签并清理当前批次
                            submittedRfids.addAll(batch);
                            pendingBatchRfids.removeAll(batch);
                            updateHeaderStats();
                            // 提交成功后刷新盘点明细和地点汇总
                            reloadTaskDetailAndSession(false);
                            showSuccess("盘点批次提交成功，本轮上传 " + batch.size() + " 个标签");
                        } finally {
                            isSubmittingBatch = false;
                        }
                    }

                    @Override
                    public void onFailure(Exception e) {
                        Log.e(TAG, "submitCurrentBatch failed", e);
                        showError(UIUtils.friendlyErrorMessage("盘点批次提交失败", e));
                        // 失败时保留 pendingBatchRfids 以便后续重试
                        isSubmittingBatch = false;
                    }
                }
        );
    }

    private void showFinishedDiff() {
        if (inventorySession == null) return;
        Set<String> planned = inventorySession.getAllPlannedRfids();
        Set<String> counted = inventorySession.getCountedRfids();
        Set<String> surplus = inventorySession.getSurplusRfids();

        List<String> missingList = new ArrayList<>();
        for (String r : planned) {
            if (!counted.contains(r)) {
                missingList.add(r);
            }
        }
        java.util.Collections.sort(missingList);
        List<String> surplusList = new ArrayList<>(surplus);
        java.util.Collections.sort(surplusList);

        // 构建差异明细数据（附带地点信息）
        diffMissingItems.clear();
        for (String r : missingList) {
            InventoryPlanItem meta = inventorySession.getPlanMeta(r);
            String name = meta != null ? meta.getName() : null;
            String spec = meta != null ? meta.getSpec() : null;
            String manu = meta != null ? meta.getManufacturer() : null;
            // 地点信息完全由后端返回
            String roomName = meta != null ? meta.getRoomName() : "";
            diffMissingItems.add(new InventoryDiffAdapter.DiffItem(
                    InventoryDiffAdapter.DiffType.MISSING,
                    r,
                    name,
                    spec,
                    manu,
                    roomName
            ));
        }

        diffSurplusItems.clear();
        for (String r : surplusList) {
            // 盈余条目目前只能依赖RFID本身，其他信息通过详情弹窗查询
            diffSurplusItems.add(new InventoryDiffAdapter.DiffItem(
                    InventoryDiffAdapter.DiffType.SURPLUS,
                    r,
                    null,
                    null,
                    null,
                    null
            ));
        }

        int missingCount = diffMissingItems.size();
        int surplusCount = diffSurplusItems.size();

        // 顶部统计
        if (tvDiffSummary != null) {
            tvDiffSummary.setText("缺失：" + missingCount + " 条   盈余：" + surplusCount + " 条");
        }

        // 更新 Tab 文案上的数量
        if (diffTabLayout != null) {
            TabLayout.Tab missingTab = diffTabLayout.getTabAt(0);
            if (missingTab != null) {
                missingTab.setText("缺失 (" + missingCount + ")");
            }
            TabLayout.Tab surplusTab = diffTabLayout.getTabAt(1);
            if (surplusTab != null) {
                surplusTab.setText("盈余 (" + surplusCount + ")");
            }
        }

        // 默认选中缺失Tab并刷新列表
        currentDiffTabIndex = 0;
        if (diffTabLayout != null) {
            TabLayout.Tab t = diffTabLayout.getTabAt(currentDiffTabIndex);
            if (t != null) t.select();
        }
        updateDiffListForCurrentTab();

        statsHeaderBar.setVisibility(View.VISIBLE);
        updateHeaderStats();
    }

    private void updateDiffListForCurrentTab() {
        if (diffAdapter == null) return;
        List<InventoryDiffAdapter.DiffItem> src =
                currentDiffTabIndex == 0 ? diffMissingItems : diffSurplusItems;
        diffAdapter.setItems(src);
        boolean empty = src == null || src.isEmpty();
        if (tvDiffEmpty != null) {
            tvDiffEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        }
        if (diffRecyclerView != null) {
            diffRecyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
        }
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
            new AlertDialog.Builder(this)
                    .setTitle("提示")
                    .setMessage("盘点任务的作废/取消请在 Web 端库存盘点页面操作，PDA 仅负责扫描与提交盘点数据。")
                    .setPositiveButton("知道了", null)
                    .show();
        } catch (Exception e) {
            Log.e(TAG, "handleVoidCurrentTask error", e);
            showError("操作失败");
        }
    }

    private void startRFIDPolling() {
        currentPollInterval = RFID_POLL_MIN_INTERVAL; // 重置轮询间隔
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
                        // 【性能优化】读到标签时恢复最小轮询间隔
                        currentPollInterval = RFID_POLL_MIN_INTERVAL;
                        for (Reader.TAGINFO tag : tagList) {
                            if (beepModeRaw && rfidBeepManager != null) {
                                try { rfidBeepManager.requestBeep(); } catch (Throwable ignored) {}
                            }
                            handleTagScanned(tag);
                        }
                    } else {
                        // 【性能优化】连续空读时逐步增加轮询间隔（退避策略）
                        emptyReadRounds++;
                        if (currentPollInterval < RFID_POLL_MAX_INTERVAL) {
                            currentPollInterval = Math.min(currentPollInterval * 2, RFID_POLL_MAX_INTERVAL);
                            Log.d(TAG, "空读退避：轮询间隔调整为 " + currentPollInterval + "ms");
                        }
                        // 连续空读达到阈值，做一次自恢复（与UHF-G一致）
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
                // 【性能优化】使用动态轮询间隔
                if (isRFIDActive && rfidBgHandler != null) rfidBgHandler.postDelayed(this, currentPollInterval);
            }
        };
        if (rfidBgHandler != null) rfidBgHandler.postDelayed(rfidRunnable, currentPollInterval);
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
        if (!"running".equals(currentTask.getRawStatus())) {
            showError("当前任务状态为非进行中，PDA 不允许继续扫描");
            return;
        }
        if (isRFIDActive) {
            stopRFIDScanning();
            showInfo("RFID扫描已停止");
            showRFIDStandbyStatus();
            // 是否在停止扫描时自动提交当前批次，由设置项控制（默认关闭）
            boolean autoSubmit = false;
            try {
                android.content.SharedPreferences sp = getSharedPreferences("settings_prefs", Context.MODE_PRIVATE);
                autoSubmit = sp.getBoolean("inventory_auto_submit_on_stop", false);
            } catch (Exception ignore) {}
            if (autoSubmit) {
                // 无数据时静默
                submitCurrentBatch(false);
            }
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
        if (isRFIDActive) {
            stopRFIDScanning();
        }
        currentTask = task;
        pendingTaskId = null;
        submittedRfids.clear();
        pendingBatchRfids.clear();

        if (taskHeaderBar != null) {
            taskHeaderBar.setVisibility(View.VISIBLE);
        }
        if (taskCodeText != null) {
            taskCodeText.setText("任务单号：" + task.getTaskId());
        }

        // 显示状态：优先使用后端原始枚举映射
        String raw = task.getRawStatus();
        String displayStatus;
        if ("running".equals(raw)) {
            displayStatus = "执行中";
        } else if ("review".equals(raw)) {
            displayStatus = "待复核";
        } else if ("closed".equals(raw)) {
            displayStatus = "已完成";
        } else if ("canceled".equals(raw)) {
            displayStatus = "已作废";
        } else if ("draft".equals(raw)) {
            displayStatus = "未开始";
        } else {
            // 兼容旧数据
            displayStatus = task.getStatus();
        }
        if (taskStateText != null) {
            taskStateText.setText(displayStatus);
            taskStateText.setTextColor(getResources().getColor(task.getStatusColor(), null));
        }

        // 使用任务头统计刷新表头
        updateHeaderStats();

        // 每次选择任务时，从后端加载明细并重建地点汇总与RFID状态
        reloadTaskDetailAndSession(true);

        // 统一进入执行视图，但是否允许扫描由状态控制
        setUIState(TaskUIState.EXECUTING);
        if ("running".equals(raw)) {
            initRFID();
            showRFIDStandbyStatus();
            showInfo("已选择盘点任务，请使用物理按键启动RFID扫描");
        } else {
            showInfo("当前任务状态为：" + displayStatus + "，仅供查看，PDA 不再提交盘点数据");
        }

        if (drawerLayout != null) {
            drawerLayout.closeDrawers();
        }
    }

    private void showRFIDStandbyStatus() {
        boolean canScan = uiState == TaskUIState.EXECUTING
                && currentTask != null
                && "running".equals(currentTask.getRawStatus())
                && uhfManager != null
                && uhfManager.isInitialized();
        if (canScan) {
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
        if (uiState == TaskUIState.EXECUTING && currentTask != null && isRFIDActive) {
            // 仅确保停止扫描与硬件，不再修改任务状态（盘点任务生命周期由 Web 管理）
            stopRFIDScanning();
        }
        super.onBackPressed();
    }
}
