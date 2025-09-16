package com.pda.hcw;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
// import android.widget.Toast;
import com.google.android.material.card.MaterialCardView;

public class BasicDataActivity extends BaseActivity {
    private static final String TAG = "BasicDataActivity";
    
    // UI组件
    private MaterialCardView roomManagementCard;
    
    @Override
    protected int getLayoutResourceId() {
        return R.layout.activity_basic_data;
    }
    
    @Override
    protected String getActivityName() {
        return "基础资料";
    }
    
    @Override
    protected String getActivityTitle() {
        return "基础资料";
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
            // 初始化UI组件
            initViews();
            
            // 设置点击事件
            setupClickListeners();
            
            Log.d(TAG, "onCreate completed successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate", e);
        }
    }
    
    @Override
    protected void initViews() {
        super.initViews();
        try {
            roomManagementCard = findViewById(R.id.room_management_card);
            
            Log.d(TAG, "initViews completed");
        } catch (Exception e) {
            Log.e(TAG, "Error in initViews", e);
        }
    }
    
    private void setupClickListeners() {
        try {
            // 术间管理卡片点击事件
            roomManagementCard.setOnClickListener(v -> {
                try {
                    startActivity(new android.content.Intent(this, OperatingRoomManagementActivity.class));
                } catch (Exception e) {
                    showError(UIUtils.friendlyErrorMessage("无法打开术间管理", e));
                }
            });
            
            Log.d(TAG, "setupClickListeners completed");
        } catch (Exception e) {
            Log.e(TAG, "Error in setupClickListeners", e);
        }
    }
    
    @Override
    protected void initBusinessLogic() {
        // 基础资料页面暂无特殊业务逻辑
    }
    
}
