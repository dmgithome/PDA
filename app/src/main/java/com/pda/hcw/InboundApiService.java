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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

                        // 提取未上架的RFID集合（按库存盘点归一化规则归一化），并收集明细
                        Set<String> plan = new HashSet<>();
                        List<InboundOrderDetail> details = new ArrayList<>();
                        Map<String, InboundOrderDetail> detailMap = new HashMap<>();
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
                                    if (rfid == null || rfid.trim().isEmpty()) continue;
                                    String raw = rfid.trim();
                                    // 与库存盘点一致：接收即格式化（18位十进制优先），未命中规则则保留原样
                                    String canon = null;
                                    try { canon = com.pda.hcw.rfid.EpcFormatter.normalizeOnReceive(raw); } catch (Throwable ignore) {}
                                    String finalRfid = (canon != null && !canon.isEmpty()) ? canon : raw;

                                    InboundOrderDetail detail = detailMap.get(finalRfid);
                                    if (detail == null) {
                                        detail = new InboundOrderDetail();
                                        detail.rfid = finalRfid;
                                        details.add(detail);
                                        detailMap.put(finalRfid, detail);
                                    }
                                    String name = item.optString("name", null);
                                    if (name != null && !name.isEmpty()) detail.name = name;
                                    String spec = item.optString("spec", null);
                                    if (spec != null && !spec.isEmpty()) detail.spec = spec;
                                    String manufacturer = item.optString("manufacturer", null);
                                    if (manufacturer != null && !manufacturer.isEmpty()) detail.manufacturer = manufacturer;
                                    String code = item.optString("code", null);
                                    if (code != null && !code.isEmpty()) detail.code = code;
                                    String unit = item.optString("unit", null);
                                    if (unit != null && !unit.isEmpty()) detail.unit = unit;
                                    String batch = item.optString("batch", null);
                                    if (batch != null && !batch.isEmpty()) detail.batch = batch;
                                    String brand = item.optString("brand", null);
                                    if (brand != null && !brand.isEmpty()) detail.brand = brand;
                                    String productionDate = item.optString("productionDate", null);
                                    if (productionDate != null && !productionDate.isEmpty()) detail.productionDate = productionDate;
                                    String expiryDate = item.optString("expiryDate", null);
                                    if (expiryDate != null && !expiryDate.isEmpty()) detail.expiryDate = expiryDate;
                                    int number = item.optInt("number", 0);
                                    if (number <= 0) number = item.optInt("quantity", 0);
                                    if (number > 0) detail.quantity = number;
                                    detail.baseMatched = isShelfed || detail.baseMatched;

                                    if (!isShelfed) {
                                        plan.add(finalRfid);
                                    }
                                }
                            }
                        }
                        io.plannedRfids = plan;
                        io.details = details;
                        io.detailByRfid = detailMap;
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

    /** 提交有单入库上架记录 (/api/stock/hasOrderInbound) */
    public void submitInboundOrder(InboundOrder order, java.util.Set<String> rfids,
                                   ConsumableApiService.ApiCallback<Boolean> callback) {
        executor.execute(() -> {
            try {
                if (order == null) { postFailure(callback, new Exception("未找到入库单信息")); return; }
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
                String userId = currentUser.getUserId();
                if (userId == null || userId.trim().isEmpty()) { postFailure(callback, new Exception("用户信息缺少ID")); return; }

                java.util.List<String> rfidList = new java.util.ArrayList<>(rfids);
                org.json.JSONArray itemsArray = new org.json.JSONArray();
                java.util.Map<String, InboundOrderDetail> detailMap = order.detailByRfid;
                for (String rfid : rfidList) {
                    org.json.JSONObject itemJson = new org.json.JSONObject();
                    itemJson.put("rfid", rfid);
                    itemJson.put("cabinetID", cabinetId);
                    if (detailMap != null) {
                        InboundOrderDetail detail = detailMap.get(rfid);
                        if (detail != null) {
                            putStringIfNotEmpty(itemJson, "code", detail.code);
                            putStringIfNotEmpty(itemJson, "name", detail.name);
                            putStringIfNotEmpty(itemJson, "spec", detail.spec);
                            putStringIfNotEmpty(itemJson, "batch", detail.batch);
                            putStringIfNotEmpty(itemJson, "productionDate", detail.productionDate);
                            putStringIfNotEmpty(itemJson, "expiryDate", detail.expiryDate);
                            int quantity = detail.quantity > 0 ? detail.quantity : 1;
                            itemJson.put("quantity", quantity);
                        } else {
                            itemJson.put("quantity", 1);
                        }
                    } else {
                        itemJson.put("quantity", 1);
                    }
                    itemJson.put("orderNo", order.code != null ? order.code : "");
                    itemsArray.put(itemJson);
                }

                if (itemsArray.length() == 0) { postFailure(callback, new Exception("无新增可提交")); return; }

                org.json.JSONObject body = new org.json.JSONObject();
                body.put("userID", userId.trim());
                putStringIfNotEmpty(body, "userName", currentUser.getUsername());
                putStringIfNotEmpty(body, "sdCode", sdCode);
                body.put("orderNo", order.code != null ? order.code : "");
                body.put("type", "hasOrder");
                body.put("cabinetID", cabinetId);
                String now = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                        .format(new java.util.Date());
                body.put("createDate", now);
                body.put("items", itemsArray);

                // 默认目标地点（入库验收要求顶层 target_location_id 必填）
                try {
                    SharedPreferences locSp = context.getSharedPreferences("locations_prefs", Context.MODE_PRIVATE);
                    int targetId = locSp.getInt("default_return_location_id", -1);
                    if (targetId <= 0) {
                        postFailure(callback, new Exception("请先在地点维护中设置默认地点"));
                        return;
                    }
                    body.put("target_location_id", targetId);
                } catch (Exception e) {
                    postFailure(callback, new Exception("读取默认地点失败，请重试"));
                    return;
                }

                String url = networkConfig.getFullUrl(ApiEndpoint.DEVICE_INBOUND_ORDER);
                Log.d(TAG, "submitInboundOrder url=" + url);
                String resp = sendHttpRequest(url, "POST", body.toString());
                Log.d(TAG, "submitInboundOrder resp=" + resp);

                // 响应：{ status/code/msg/data }
                org.json.JSONObject root = new org.json.JSONObject(resp);
                int status = root.optInt("status", root.optInt("code", -1));
                int code = root.has("code") ? root.optInt("code", status) : status;
                boolean success = (status == 200 || status == 0 || code == 200 || code == 0);
                Object dataNode = root.opt("data");
                boolean hasData = !(dataNode == null || org.json.JSONObject.NULL.equals(dataNode));
                if (dataNode instanceof org.json.JSONArray) {
                    hasData = ((org.json.JSONArray) dataNode).length() > 0;
                } else if (dataNode instanceof org.json.JSONObject) {
                    hasData = ((org.json.JSONObject) dataNode).length() > 0;
                } else if (dataNode instanceof String) {
                    hasData = !((String) dataNode).isEmpty();
                }
                if (!hasData && root.optBoolean("success", false)) {
                    hasData = true;
                }
                if (success && !hasData) {
                    success = false;
                }
                if (!success) {
                    String msg = root.optString("msg", root.optString("message", "提交失败"));
                    if (dataNode instanceof org.json.JSONArray && ((org.json.JSONArray) dataNode).length() > 0) {
                        msg = msg + " - " + dataNode.toString();
                    } else if (dataNode instanceof org.json.JSONObject && ((org.json.JSONObject) dataNode).length() > 0) {
                        msg = msg + " - " + dataNode.toString();
                    } else if (dataNode instanceof String && !((String) dataNode).isEmpty()) {
                        msg = msg + " - " + dataNode;
                    }
                    Log.e(TAG, "submitInboundOrder error status=" + status + ", code=" + code + ", resp=" + resp);
                    throw new Exception(msg);
                }
                Log.i(TAG, "submitInboundOrder success status=" + status + ", code=" + code + ", message=" + root.optString("message"));
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

    private static void putStringIfNotEmpty(JSONObject target, String key, String value) throws org.json.JSONException {
        if (value == null) return;
        String trimmed = value.trim();
        if (!trimmed.isEmpty()) {
            target.put(key, trimmed);
        }
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
    // 当前PDA会话内已匹配的RFID数量（仅用于UI展示，不参与后端统计）
    public int matchedInSession;
    public java.util.Set<String> plannedRfids = new java.util.HashSet<>();
    public java.util.List<InboundOrderDetail> details = new java.util.ArrayList<>();
    public java.util.Map<String, InboundOrderDetail> detailByRfid = new java.util.HashMap<>();
}

class InboundOrderDetail {
    public String rfid;
    public boolean baseMatched;
    public String name;
    public String spec;
    public String manufacturer;
    public String code;
    public String unit;
    public String batch;
    public String brand;
    public String productionDate;
    public String expiryDate;
    public int quantity = 1;
}
