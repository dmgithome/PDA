package com.pda.hcw;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 先在本机保存本次提交，再发送。业务回执被本地应用之后才能删除原请求。 */
final class SubmissionQueue implements DefaultLifecycleObserver {
    interface Listener {
        void apply(String slot, JSONObject body, JSONObject display, JSONObject receipt)
                throws Exception;

        void showMessage(String message);
    }

    private static final ExecutorService NETWORK = Executors.newFixedThreadPool(3);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final SharedPreferences journal;
    private final Context context;
    private final ApiEndpoint endpoint;
    private final String mode;
    private final Listener listener;
    private final Set<String> sending = new HashSet<>();
    private boolean visible;
    private final Runnable retry = this::resumePending;

    SubmissionQueue(BaseActivity activity, ApiEndpoint endpoint, String mode, Listener listener) {
        this.context = activity.getApplicationContext();
        this.endpoint = endpoint;
        this.mode = mode;
        this.listener = listener;
        journal = context.getSharedPreferences("stock_submissions_v1", Context.MODE_PRIVATE);
        activity.getLifecycle().addObserver(this);
    }

    private String prefix() {
        UserInfo user = SessionManager.getInstance(context).getCurrentUser();
        if (user == null) return null;
        String server = NetworkConfig.getInstance(context).getFullUrl(endpoint);
        return android.net.Uri.encode(server)
                + "/"
                + android.net.Uri.encode(user.getUserId())
                + "/"
                + mode
                + "/";
    }

    boolean hasPending(String slot) {
        String owner = prefix();
        return owner != null && journal.contains(owner + slot);
    }

    void submit(String slot, JSONObject body, JSONObject display) throws JSONException {
        String owner = prefix();
        if (owner == null) throw new IllegalStateException("用户未登录");
        String key = owner + slot;
        if (journal.contains(key)) {
            dispatch(key);
            return;
        }
        body.put("request_id", UUID.randomUUID().toString());
        JSONObject request =
                new JSONObject().put("slot", slot).put("body", body).put("display", display);
        if (!journal.edit().putString(key, request.toString()).commit()) {
            throw new IllegalStateException("本机保存提交清单失败");
        }
        dispatch(key);
    }

    @Override
    public void onResume(@NonNull LifecycleOwner owner) {
        visible = true;
        resumePending();
    }

    @Override
    public void onPause(@NonNull LifecycleOwner owner) {
        visible = false;
        main.removeCallbacks(retry);
    }

    void resumePending() {
        String owner = prefix();
        if (!visible || owner == null) return;
        for (String key : new ArrayList<>(journal.getAll().keySet())) {
            if (key.startsWith(owner)) dispatch(key);
        }
    }

    private void dispatch(String key) {
        if (!visible || !sending.add(key)) return;
        String saved = journal.getString(key, null);
        String owner = prefix();
        if (saved == null || owner == null || !key.startsWith(owner)) {
            sending.remove(key);
            return;
        }
        final String url = NetworkConfig.getInstance(context).getFullUrl(endpoint);
        NETWORK.execute(
                () -> {
                    try {
                        JSONObject entry = new JSONObject(saved);
                        JSONObject body = entry.getJSONObject("body");
                        if (!owner.equals(prefix())) throw new IllegalStateException("登录状态已改变");
                        String response =
                                ConsumableApiService.getInstance(context)
                                        .sendHttpRequest(url, "POST", body.toString());
                        JSONObject receipt = new JSONObject(response);
                        int status = receipt.getInt("status");
                        if (!body.getString("request_id").equals(receipt.optString("request_id"))) {
                            throw new IllegalStateException("回执编号不匹配");
                        }
                        boolean businessError =
                                status >= 400
                                        && status < 500
                                        && status != 401
                                        && status != 403
                                        && status != 408
                                        && status != 429;
                        if (status != 200 && status != 207 && !businessError) {
                            throw new IllegalStateException("尚未收到最终业务回执");
                        }
                        main.post(
                                () -> {
                                    sending.remove(key);
                                    if (!visible
                                            || !owner.equals(prefix())
                                            || !saved.equals(journal.getString(key, null))) return;
                                    try {
                                        listener.apply(
                                                entry.getString("slot"),
                                                body,
                                                entry.getJSONObject("display"),
                                                receipt);
                                        if (!journal.edit().remove(key).commit())
                                            throw new IllegalStateException("本机回执未保存");
                                    } catch (Exception e) {
                                        waitForReceipt(e);
                                    }
                                });
                    } catch (Exception e) {
                        main.post(
                                () -> {
                                    sending.remove(key);
                                    if (visible
                                            && owner.equals(prefix())
                                            && saved.equals(journal.getString(key, null)))
                                        waitForReceipt(e);
                                });
                    }
                });
    }

    private void waitForReceipt(Exception error) {
        android.util.Log.w("SubmissionQueue", "等待确认", error);
        listener.showMessage("结果待确认，已保留原提交。恢复连接后将自动核对");
        main.removeCallbacks(retry);
        main.postDelayed(retry, 10000);
    }
}
