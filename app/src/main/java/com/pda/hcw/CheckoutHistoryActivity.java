package com.pda.hcw;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
// import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * 领用历史记录界面
 */
public class CheckoutHistoryActivity extends BaseActivity {
    private static final String TAG = "CheckoutHistoryActivity";
    
    // UI组件
    private Button backButton;
    private Button refreshButton;
    private TextView totalCountText;
    private TextView totalQuantityText;
    private EditText recipientFilterEdit;
    private EditText departmentFilterEdit;
    private Button filterButton;
    private Button clearFilterButton;
    private RecyclerView recordsRecyclerView;
    private LinearLayout emptyStateLayout;
    
    // 数据管理
    private CheckoutRecordManager checkoutRecordManager;
    
    // 数据列表
    private List<CheckoutRecord> allRecords;

    @Override
    protected String getActivityName() {
        return "CheckoutHistoryActivity";
    }
    
    @Override
    protected String getActivityTitle() {
        return "取用记录";
    }
    
    @Override
    protected int getLayoutResourceId() {
        return R.layout.activity_checkout_history;
    }
    
    @Override
    protected boolean checkPermissions() {
        if (!hasPermission(Permissions.INVENTORY_VIEW)) {
            showError("您没有库存查看权限");
            finish();
            return false;
        }
        return true;
    }
    
    @Override
    protected void initBusinessLogic() {
        checkoutRecordManager = CheckoutRecordManager.getInstance(this);
        setupRecyclerView();
        setupButtonListeners();
        loadRecords();
    }
    
    @Override
    protected void initViews() {
        try {
            backButton = findViewById(R.id.back_button);
            refreshButton = findViewById(R.id.refresh_button);
            totalCountText = findViewById(R.id.total_count_text);
            totalQuantityText = findViewById(R.id.total_quantity_text);
            recipientFilterEdit = findViewById(R.id.recipient_filter_edit);
            departmentFilterEdit = findViewById(R.id.department_filter_edit);
            filterButton = findViewById(R.id.filter_button);
            clearFilterButton = findViewById(R.id.clear_filter_button);
            recordsRecyclerView = findViewById(R.id.records_recycler_view);
            emptyStateLayout = findViewById(R.id.empty_state_layout);
            
            Log.d(TAG, "UI组件初始化成功");
        } catch (Exception e) {
            Log.e(TAG, "UI组件初始化失败", e);
        }
    }
    
    private void setupRecyclerView() {
        recordsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        // 暂时禁用RecyclerView，因为没有适配器
        recordsRecyclerView.setVisibility(View.GONE);
    }
    
    private void setupButtonListeners() {
        // 返回按钮
        backButton.setOnClickListener(v -> finish());
        
        // 刷新按钮
        refreshButton.setOnClickListener(v -> {
            loadRecords();
            showSuccess("数据已刷新");
        });
        
        // 筛选按钮
        filterButton.setOnClickListener(v -> {
            applyFilter();
        });
        
        // 清除筛选按钮
        clearFilterButton.setOnClickListener(v -> {
            clearFilter();
        });
    }
    
        
    private void loadRecords() {
        // 暂时显示空数据，因为没有getAllCheckoutRecords()方法
        allRecords = new ArrayList<>();
        
        // 更新统计信息
        updateStatistics();
        
        // 显示空状态
        updateEmptyState();
    }
    
    private void applyFilter() {
        // 暂时禁用筛选功能，因为数据结构不匹配
        showInfo("筛选功能暂时不可用");
    }
    
    private void clearFilter() {
        recipientFilterEdit.setText("");
        departmentFilterEdit.setText("");
        showInfo("筛选功能暂时不可用");
    }
    
    private void updateStatistics() {
        int totalCount = allRecords.size();
        int totalQuantity = 0;
        
        for (CheckoutRecord record : allRecords) {
            totalQuantity += record.totalQuantity; // 使用public字段
        }
        
        totalCountText.setText(String.valueOf(totalCount));
        totalQuantityText.setText(String.valueOf(totalQuantity));
    }
    
    private void updateEmptyState() {
        if (allRecords.isEmpty()) {
            emptyStateLayout.setVisibility(View.VISIBLE);
            recordsRecyclerView.setVisibility(View.GONE);
        } else {
            emptyStateLayout.setVisibility(View.GONE);
            recordsRecyclerView.setVisibility(View.VISIBLE);
        }
    }
}
