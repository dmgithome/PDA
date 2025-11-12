package com.pda.hcw;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * 默认地点同步与读取。
 * 首选使用服务端 /api/locations/default 的结果写入本地，只读使用。
 */
public class DefaultLocationManager {
    private static final String TAG = "DefaultLocMgr";
    private static final String PREFS = "locations_prefs";
    private static final String KEY_ID = "default_return_location_id";   // 兼容旧键名
    private static final String KEY_NAME = "default_return_location_name";
    private static final String KEY_TS = "default_return_location_ts";

    public interface Callback {
        void onComplete(boolean success, String message);
    }

    /** 刷新默认地点（登录后调用） */
    public static void refreshFromServer(Context ctx, Callback cb) {
        ConsumableApiService.getInstance(ctx).fetchDefaultLocation(new ConsumableApiService.ApiCallback<org.json.JSONObject>() {
            @Override
            public void onSuccess(org.json.JSONObject result) {
                try {
                    int id = -1; String name = null;
                    if (result != null) {
                        if (!result.isNull("id")) id = safeToInt(result.opt("id"), -1);
                        name = result.optString("name", null);
                    }
                    SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                    SharedPreferences.Editor ed = sp.edit();
                    if (id > 0) {
                        ed.putInt(KEY_ID, id);
                        if (name != null) ed.putString(KEY_NAME, name);
                        ed.putLong(KEY_TS, System.currentTimeMillis());
                        ed.apply();
                        Log.d(TAG, "默认地点已更新: id=" + id + ", name=" + name);
                        if (cb != null) cb.onComplete(true, "默认地点同步成功");
                    } else {
                        // 清空默认
                        ed.remove(KEY_ID); ed.remove(KEY_NAME); ed.putLong(KEY_TS, System.currentTimeMillis()); ed.apply();
                        Log.d(TAG, "服务端未设置默认地点，已清空本地默认");
                        if (cb != null) cb.onComplete(true, "无默认地点");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "保存默认地点失败", e);
                    if (cb != null) cb.onComplete(false, "保存默认地点失败");
                }
            }

            @Override
            public void onFailure(Exception e) {
                Log.e(TAG, "同步默认地点失败", e);
                if (cb != null) cb.onComplete(false, UIUtils.friendlyErrorMessage("同步默认地点失败", e));
            }
        });
    }

    public static Integer getDefaultLocationId(Context ctx) {
        try {
            int id = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_ID, -1);
            return id > 0 ? id : null;
        } catch (Exception e) { return null; }
    }

    public static String getDefaultLocationName(Context ctx) {
        try { return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_NAME, null); } catch (Exception e) { return null; }
    }

    private static int safeToInt(Object v, int def) {
        try {
            if (v == null) return def;
            if (v instanceof Number) return ((Number) v).intValue();
            String s = String.valueOf(v);
            if (s.trim().isEmpty()) return def;
            return Integer.parseInt(s.trim());
        } catch (Exception e) { return def; }
    }
}

