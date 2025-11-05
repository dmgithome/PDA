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
    protected String getActivityName() {
        return "术间管理";
    }

    @Override
    protected String getActivityTitle() {
        return "术间管理";
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        manager = OperatingRoomManager.getInstance(this);

        recyclerView = findViewById(R.id.room_recycler_view);
        emptyHint = findViewById(R.id.empty_hint);
        btnRefresh = findViewById(R.id.btn_refresh);
        btnAdd = findViewById(R.id.btn_add);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new OperatingRoomAdapter(data, new OperatingRoomAdapter.OnRoomActionListener() {
            @Override
            public void onEdit(OperatingRoom room) {
                showEditDialog(room);
            }

            @Override
            public void onDelete(OperatingRoom room) {
                confirmDelete(room);
            }
        });
        recyclerView.setAdapter(adapter);

        btnRefresh.setOnClickListener(v -> refreshFromServer());
        btnAdd.setOnClickListener(v -> showAddDialog());

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
            showError("加载本地术间失败");
        }
    }

    private void refreshFromServer() {
        UIUtils.showInfo(this, "正在从业务系统拉取...");
        manager.refreshFromServer(new ConsumableApiService.ApiCallback<List<OperatingRoom>>() {
            @Override
            public void onSuccess(List<OperatingRoom> result) {
                runOnUiThread(() -> {
                    UIUtils.showSuccess(OperatingRoomManagementActivity.this, "更新成功：" + (result != null ? result.size() : 0) + "条");
                    loadLocal();
                });
            }

            @Override
            public void onFailure(Exception e) {
                runOnUiThread(() -> UIUtils.showError(OperatingRoomManagementActivity.this, UIUtils.friendlyErrorMessage("更新失败", e)));
            }
        });
    }

    private void showAddDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("新增术间");
        android.view.View view = LayoutInflater.from(this).inflate(android.R.layout.simple_list_item_1, null);
        TextView tv = view.findViewById(android.R.id.text1);
        tv.setText("");

        // 使用简单输入框对话
        final EditText input = new EditText(this);
        input.setHint("请输入术间名称");
        builder.setView(input);
        builder.setPositiveButton("确定", (dialog, which) -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) {
                UIUtils.showError(this, "术间名称不能为空");
                return;
            }
            manager.addRoom(name, new ConsumableApiService.ApiCallback<OperatingRoom>() {
                @Override
                public void onSuccess(OperatingRoom result) {
                    runOnUiThread(() -> {
                        UIUtils.showSuccess(OperatingRoomManagementActivity.this, "新增成功");
                        loadLocal();
                    });
                }

                @Override
                public void onFailure(Exception e) {
                    runOnUiThread(() -> UIUtils.showError(OperatingRoomManagementActivity.this, UIUtils.friendlyErrorMessage("新增失败", e)));
                }
            });
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void showEditDialog(OperatingRoom room) {
        if (room == null) return;
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("编辑术间");
        final EditText input = new EditText(this);
        input.setText(room.name);
        input.setSelection(input.getText().length());
        builder.setView(input);
        builder.setPositiveButton("确定", (dialog, which) -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) {
                UIUtils.showError(this, "术间名称不能为空");
                return;
            }
            manager.updateRoom(room, name, new ConsumableApiService.ApiCallback<Boolean>() {
                @Override
                public void onSuccess(Boolean result) {
                    runOnUiThread(() -> {
                        UIUtils.showSuccess(OperatingRoomManagementActivity.this, "更新成功");
                        loadLocal();
                    });
                }

                @Override
                public void onFailure(Exception e) {
                    runOnUiThread(() -> UIUtils.showError(OperatingRoomManagementActivity.this, UIUtils.friendlyErrorMessage("更新失败", e)));
                }
            });
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void confirmDelete(OperatingRoom room) {
        if (room == null) return;
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("删除确认");
        builder.setMessage("确定要删除该术间吗？（软删除）");
        builder.setPositiveButton("删除", (dialog, which) -> {
            manager.deleteRoom(room, new ConsumableApiService.ApiCallback<Boolean>() {
                @Override
                public void onSuccess(Boolean result) {
                    runOnUiThread(() -> {
                        UIUtils.showSuccess(OperatingRoomManagementActivity.this, "删除成功");
                        loadLocal();
                    });
                }

                @Override
                public void onFailure(Exception e) {
                    runOnUiThread(() -> UIUtils.showError(OperatingRoomManagementActivity.this, UIUtils.friendlyErrorMessage("删除失败", e)));
                }
            });
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }
}
