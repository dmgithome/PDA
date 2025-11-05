package com.pda.hcw;

import android.content.Context;
import android.os.Handler;
import android.content.SharedPreferences;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 入库验收相关 API。
 * 本类保持最小依赖，避免改动既有业务代码。
 */
public class InboundApiService {
    private static final String TAG = "InboundApiService";

    private final Context context;
    private final ExecutorService executor;
    private final Handler mainHandler;
    private final NetworkConfig networkConfig;

    private static InboundApiService instance;

    public static InboundApiService getInstance(Context ctx) {
        if (instance == null) {
            instance = new InboundApiService(ctx.getApplicationContext());
        }
        return instance;
    }

    private InboundApiService(Context ctx) {
        this.context = ctx;
        this.executor = Executors.newFixedThreadPool(3);
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.networkConfig = NetworkConfig.getInstance(ctx);
    }

    /**
     * 拉取待上架入库单据列表。
     * 注意：后续会完善解析逻辑；当前仅搭建调用与基本解析骨架。
     */
    public void fetchPendingOrders(ConsumableApiService.ApiCallback<List<InboundOrder>> callback) {
        executor.execute(() -> {
            try {
                // 登录/配置校验
                if (SessionManager.getInstance(context).getCurrentUser() == null) {
                    postFailure(callback, new Exception("用户未登录"));
                    return;
                }
                if (!networkConfig.isValidConfig()) {
                    postFailure(callback, new Exception("API服务器地址未配置，请先在设置中配置API地址"));
                    return;
                }

                String url = networkConfig.getFullUrl(ApiEndpoint.GET_WAREHOUSING_ORDER);
                String resp = sendHttpRequest(url, "GET", null);

                // 解析（来自 Apifox）：
                // data: [ { orderNo, number, shelfedNumber, children: [ { name, number, shelfedNumber, children:[ { id, name, code, rfid, spec, batch, productionDate, expiryDate, manufacturer, brand, isShelfed, number } ] } ] } ]
                JSONObject root = new JSONObject(resp);
                int status = root.optInt("status", 0);
                if (status != 200) {
                    String msg = root.optString("message", "查询失败");
                    throw new Exception(msg);
                }
                List<InboundOrder> result = new ArrayList<>();
                Object dataNode = root.opt("data");
                if (dataNode instanceof JSONArray) {
                    JSONArray arr = (JSONArray) dataNode;
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.optJSONObject(i);
                        if (o == null) continue;
                        InboundOrder io = new InboundOrder();
                        io.code = o.optString("orderNo", "");
                        io.total = o.optInt("number", 0);
                        io.accepted = o.optInt("shelfedNumber", 0);

                        // 提取未上架的RFID集合（不修改业务系统原始数据）
                        Set<String> plan = new HashSet<>();
                        JSONArray level1 = o.optJSONArray("children");
                        if (level1 != null) {
                            for (int j = 0; j < level1.length(); j++) {
                                JSONObject c1 = level1.optJSONObject(j);
                                if (c1 == null) continue;
                                JSONArray level2 = c1.optJSONArray("children");
                                if (level2 == null) continue;
                                for (int k = 0; k < level2.length(); k++) {
                                    JSONObject item = level2.optJSONObject(k);
                                    if (item == null) continue;
                                    boolean isShelfed = item.optBoolean("isShelfed", false);
                                    String rfid = item.optString("rfid", null);
                                    if (!isShelfed && rfid != null && !rfid.trim().isEmpty()) {
                                        plan.add(rfid.trim());
                                    }
                                }
                            }
                        }
                        io.plannedRfids = plan;
                        result.add(io);
                    }
                }
                postSuccess(callback, result);
            } catch (Exception e) {
                Log.e(TAG, "fetchPendingOrders failed", e);
                postFailure(callback, e);
            }
        });
    }

    /** 提交有单入库上架记录 (/device/inboundOrder) */
    public void submitInboundOrder(String orderNo, java.util.Set<String> rfids,
                                   ConsumableApiService.ApiCallback<Boolean> callback) {
        executor.execute(() -> {
            try {
                // 校验登录
                UserInfo currentUser = SessionManager.getInstance(context).getCurrentUser();
                if (currentUser == null) { postFailure(callback, new Exception("用户未登录")); return; }
                // 校验配置
                SharedPreferences sp = context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE);
                String sdCode = sp.getString("sd_code", "");
                int cabinetId = BarcodeValidator.getInstance(context).getCabinetId();
                if (sdCode == null || sdCode.trim().isEmpty()) { postFailure(callback, new Exception("请先在设置中配置sdCode")); return; }
                if (cabinetId <= 0) { postFailure(callback, new Exception("请先在设置中配置货柜ID")); return; }
                if (rfids == null || rfids.isEmpty()) { postFailure(callback, new Exception("无新增可提交")); return; }

                // 构造请求体
                org.json.JSONObject body = new org.json.JSONObject();
                String rfidsCsv = String.join(",", rfids);
                body.put("rfids", rfidsCsv);
                body.put("sdCode", sdCode);
                body.put("cabinetCode", String.valueOf(cabinetId));
                body.put("userName", currentUser.getUsername());
                String now = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                        .format(new java.util.Date());
                body.put("createDate", now);
                body.put("orderNo", orderNo != null ? orderNo : "");

                String url = networkConfig.getFullUrl(ApiEndpoint.DEVICE_INBOUND_ORDER);
                String resp = sendHttpRequest(url, "POST", body.toString());

                // 响应：{ code, msg, data:[{ cabinetCode, rfids }] }
                org.json.JSONObject root = new org.json.JSONObject(resp);
                int code = root.optInt("code", 0);
                if (code != 200) {
                    String msg = root.optString("msg", "提交失败");
                    throw new Exception(msg);
                }
                postSuccess(callback, Boolean.TRUE);
            } catch (Exception e) {
                Log.e(TAG, "submitInboundOrder failed", e);
                postFailure(callback, e);
            }
        });
    }

    private void postSuccess(ConsumableApiService.ApiCallback<?> cb, Object data) {
        mainHandler.post(() -> {
            @SuppressWarnings("unchecked") ConsumableApiService.ApiCallback<Object> c = (ConsumableApiService.ApiCallback<Object>) cb;
            c.onSuccess(data);
        });
    }

    private void postFailure(ConsumableApiService.ApiCallback<?> cb, Exception e) {
        mainHandler.post(() -> cb.onFailure(e));
    }

    private String sendHttpRequest(String url, String method, String data) throws Exception {
        HttpURLConnection connection = null;
        try {
            URL requestUrl = new URL(url);
            connection = (HttpURLConnection) requestUrl.openConnection();
            connection.setRequestMethod(method);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setUseCaches(false);
            connection.setDoInput(true);

            // Authorization
            UserInfo currentUser = SessionManager.getInstance(context).getCurrentUser();
            if (currentUser != null && currentUser.getToken() != null && !currentUser.getToken().isEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer " + currentUser.getToken());
            }

            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);

            boolean hasBody = data != null && !data.isEmpty();
            boolean methodAllowsBody = "POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method);
            connection.setDoOutput(hasBody && methodAllowsBody);
            if (hasBody && methodAllowsBody) {
                try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream()))) {
                    writer.write(data);
                    writer.flush();
                }
            }

            int responseCode = connection.getResponseCode();
            if ((responseCode / 100) == 2) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    return response.toString();
                }
            } else {
                String errorBody = null;
                try (BufferedReader errReader = new BufferedReader(new InputStreamReader(connection.getErrorStream()))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while (errReader != null && (line = errReader.readLine()) != null) {
                        sb.append(line);
                    }
                    errorBody = sb.length() > 0 ? sb.toString() : null;
                } catch (Exception ignore) {}

                if (errorBody != null && !errorBody.isEmpty()) {
                    try {
                        JSONObject errJson = new JSONObject(errorBody);
                        String msg = errJson.optString("message", null);
                        if (msg != null && !msg.isEmpty()) {
                            throw new Exception(msg + " (HTTP " + responseCode + ")");
                        }
                    } catch (Exception ignore) {}
                    String snippet = errorBody.length() > 200 ? errorBody.substring(0, 200) + "..." : errorBody;
                    throw new Exception("HTTP请求失败，响应码: " + responseCode + ", 错误: " + snippet);
                }
                throw new Exception("HTTP请求失败，响应码: " + responseCode);
            }
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}

class InboundOrder {
    public String code;
    public long docTime;
    public int total;
    public int accepted;
    public java.util.Set<String> plannedRfids = new java.util.HashSet<>();
}
