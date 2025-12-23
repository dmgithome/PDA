package com.pda.hcw;

import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 未归还列表（仅展示当前用户）
 */
public class OvertimeUnreturnListActivity extends BaseActivity {
    private static final String TAG = "OvertimeUnreturnList";

    private TextView statusText;
    private TextView totalText;
    private TextView emptyView;
    private RecyclerView listView;
    private com.google.android.material.button.MaterialButton refreshButton;
    private UnreturnListAdapter adapter;

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
        statusText = findViewById(R.id.status_text);
        totalText = findViewById(R.id.total_text);
        emptyView = findViewById(R.id.empty_view);
        listView = findViewById(R.id.list);
        refreshButton = findViewById(R.id.refresh_button);

        if (listView != null) {
            listView.setLayoutManager(new LinearLayoutManager(this));
            adapter = new UnreturnListAdapter();
            listView.setAdapter(adapter);
        }
        if (refreshButton != null) {
            refreshButton.setOnClickListener(v -> loadData(true));
        }
    }

    @Override
    protected void initBusinessLogic() {
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
            loadData(false);
        } catch (Exception e) {
            Log.e(TAG, "initBusinessLogic failed", e);
            showError(getString(R.string.common_error));
        }
    }

    private void loadData(boolean fromUser) {
        try {
            if (statusText != null) {
                statusText.setVisibility(View.VISIBLE);
                statusText.setText(fromUser ? "刷新中..." : "加载中...");
            }
            if (emptyView != null) emptyView.setVisibility(View.GONE);

            ConsumableApiService.getInstance(this).fetchMyUnreturnList(new ConsumableApiService.ApiCallback<ConsumableApiService.UnreturnListResult>() {
                @Override
                public void onSuccess(ConsumableApiService.UnreturnListResult result) {
                    List<ConsumableApiService.UnreturnListItem> rows = result != null && result.items != null
                            ? new ArrayList<>(result.items)
                            : new ArrayList<>();

                    // 按取用时间升序（早的在前）排序；空时间放最后
                    Collections.sort(rows, new Comparator<ConsumableApiService.UnreturnListItem>() {
                        @Override
                        public int compare(ConsumableApiService.UnreturnListItem a, ConsumableApiService.UnreturnListItem b) {
                            long ta = parseServerTimeMillis(a == null ? null : a.takeTime);
                            long tb = parseServerTimeMillis(b == null ? null : b.takeTime);
                            return Long.compare(ta, tb);
                        }
                    });

                    int total = result != null && result.total > 0 ? result.total : rows.size();
                    if (totalText != null) totalText.setText("未归还：" + total + "个");

                    if (adapter != null) adapter.updateItems(rows);
                    if (listView != null) listView.setVisibility(rows.isEmpty() ? View.GONE : View.VISIBLE);
                    if (emptyView != null) {
                        emptyView.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
                        if (rows.isEmpty()) emptyView.setText(R.string.overtime_unreturn_empty);
                    }
                    if (statusText != null) statusText.setVisibility(View.GONE);
                    if (fromUser) showSuccess("已刷新");
                }

                @Override
                public void onFailure(Exception e) {
                    String msg = e != null && e.getMessage() != null ? e.getMessage() : getString(R.string.common_error);
                    if (statusText != null) {
                        statusText.setVisibility(View.VISIBLE);
                        statusText.setText(msg);
                    }
                    if (emptyView != null) {
                        emptyView.setVisibility(View.VISIBLE);
                        emptyView.setText(msg);
                    }
                    if (listView != null) listView.setVisibility(View.GONE);
                    showError(msg);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "loadData failed", e);
            showError(getString(R.string.common_error));
        }
    }

    private static long parseServerTimeMillis(String s) {
        if (s == null) return Long.MAX_VALUE;
        String t = s.trim();
        if (t.isEmpty()) return Long.MAX_VALUE;
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(t).getTime();
        } catch (Exception ignore) {
            // 兜底：尽量让解析失败的排到最后
            return Long.MAX_VALUE;
        }
    }
}
