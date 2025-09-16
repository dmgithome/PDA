package com.pda.hcw;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;

/**
 * 超时未归还列表（占位页）
 */
public class OvertimeUnreturnListActivity extends BaseActivity {
    private static final String TAG = "OvertimeUnreturnList";

    private TextView emptyView;

    @Override
    protected int getLayoutResourceId() {
        return R.layout.activity_overtime_unreturn_list;
    }

    @Override
    protected String getActivityName() {
        return getString(R.string.overtime_unreturn_title);
    }

    @Override
    protected void initViews() {
        super.initViews();
        emptyView = findViewById(R.id.empty_view);
    }

    @Override
    protected void initBusinessLogic() {
        // 占位页仅展示空视图与说明
        try {
            if (!isLoggedIn()) {
                if (emptyView != null) {
                    emptyView.setText(R.string.error_login_required);
                }
                showInfo(getString(R.string.error_login_required));
                return;
            }

            // 记录打开页面的审计日志（不含PII）
            logAudit("OPEN_PAGE", "overtime_unreturn_list_placeholder");

            if (emptyView != null) {
                emptyView.setVisibility(View.VISIBLE);
                emptyView.setText(R.string.overtime_unreturn_empty);
            }
        } catch (Exception e) {
            Log.e(TAG, "initBusinessLogic failed", e);
            showError(getString(R.string.common_error));
        }
    }
}

