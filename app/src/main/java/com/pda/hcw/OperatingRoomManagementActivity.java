package com.pda.hcw;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.widget.EditText;
import android.widget.TextView;
// import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class OperatingRoomManagementActivity extends BaseActivity {
    private static final String TAG = "OperatingRoomMgmt";

    private RecyclerView recyclerView;
    private TextView emptyHint;
    private MaterialButton btnRefresh;
    private MaterialButton btnAdd;

    private OperatingRoomAdapter adapter;
    private final List<OperatingRoom> data = new ArrayList<>();
    private OperatingRoomManager manager;

    @Override
    protected int getLayoutResourceId() {
        return R.layout.activity_operating_room_management;
    }

    @Override
    protected String getActivityName() { return "地点维护"; }

    @Override
    protected String getActivityTitle() { return "地点维护（只读）"; }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        manager = OperatingRoomManager.getInstance(this);

        recyclerView = findViewById(R.id.room_recycler_view);
        emptyHint = findViewById(R.id.empty_hint);
        btnRefresh = findViewById(R.id.btn_refresh);
        btnAdd = findViewById(R.id.btn_add);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        // 只读模式：不传入可写监听器，列表项提供“设为默认归还”本地设置
        adapter = new OperatingRoomAdapter(data, null);
        recyclerView.setAdapter(adapter);

        btnRefresh.setOnClickListener(v -> refreshFromServer());
        // 只读，不允许新增
        if (btnAdd != null) btnAdd.setVisibility(android.view.View.GONE);

        loadLocal();
    }

    private void loadLocal() {
        try {
            data.clear();
            data.addAll(manager.getAllRooms());
            adapter.notifyDataSetChanged();
            emptyHint.setVisibility(data.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE);
        } catch (Exception e) {
            Log.e(TAG, "loadLocal failed", e);
            showError("加载本地地点失败");
    }
    }

    private void refreshFromServer() {
        UIUtils.showInfo(this, "正在查询地点列表...");
        manager.refreshFromServer(new ConsumableApiService.ApiCallback<List<OperatingRoom>>() {
            @Override
            public void onSuccess(List<OperatingRoom> result) {
                runOnUiThread(() -> {
                    UIUtils.showSuccess(OperatingRoomManagementActivity.this, "查询成功：" + (result != null ? result.size() : 0) + "条");
                    loadLocal();
                });
            }

            @Override
            public void onFailure(Exception e) {
                runOnUiThread(() -> UIUtils.showError(OperatingRoomManagementActivity.this, UIUtils.friendlyErrorMessage("查询失败", e)));
            }
        });
    }
    // 只读模式：新增/编辑/删除全部移除
}
