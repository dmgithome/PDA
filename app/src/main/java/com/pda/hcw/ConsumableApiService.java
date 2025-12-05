package com.pda.hcw;

import android.content.Context;
import android.os.Handler;
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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.pda.hcw.inventory.InventoryPlanItem;

/**
 * 耗材API服务类
 * 处理与业务系统的API调用
 */
public class ConsumableApiService {
    private static final String TAG = "ConsumableApiService";
    
    private final Context context;
    private final ExecutorService executorService;
    private final Handler mainHandler;
    private final NetworkConfig networkConfig;
    
    // 单例模式
    private static ConsumableApiService instance;
    
    public static ConsumableApiService getInstance(Context context) {
        if (instance == null) {
            instance = new ConsumableApiService(context.getApplicationContext());
        }
        return instance;
    }

    /**
     * 根据用户ID获取超时未归还耗材数量（GET /api/unreturn/getOverTimeUnreturnNumberByID?ID=<userId>）
     */
    public void fetchOvertimeUnreturnCount(int userId, ApiCallback<Integer> callback) {
        executorService.execute(() -> {
            try {
                // 登录与配置校验
                UserInfo currentUser = SessionManager.getInstance(context).getCurrentUser();
                if (currentUser == null) {
                    mainHandler.post(() -> callback.onFailure(new Exception("用户未登录")));
                    return;
                }
                if (!networkConfig.isValidConfig()) {
                    mainHandler.post(() -> callback.onFailure(new Exception("API服务器地址未配置，请先在设置中配置API地址")));
                    return;
                }

                String base = networkConfig.getFullUrl(ApiEndpoint.OVERTIME_UNRETURN_NUMBER);
                String apiUrl = base + (base.contains("?") ? "&" : "?") + "ID=" + userId;
                String resp = sendHttpRequest(apiUrl, "GET", null);

                org.json.JSONObject json = new org.json.JSONObject(resp);
                int status = json.optInt("status", 0);
                if (status != 200) {
                    String msg = json.optString("message", "查询失败");
                    throw new Exception(msg);
                }
                org.json.JSONObject data = json.optJSONObject("data");
                int number = 0;
                if (data != null) {
                    number = data.optInt("number", 0);
                }

                final int resultNumber = Math.max(0, number);
                mainHandler.post(() -> callback.onSuccess(resultNumber));
            } catch (Exception e) {
                Log.e(TAG, "fetchOvertimeUnreturnCount failed", e);
                mainHandler.post(() -> callback.onFailure(e));
            }
        });
    }

    private ConsumableApiService(Context context) {
        this.context = context;
        this.executorService = Executors.newFixedThreadPool(3);
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.networkConfig = NetworkConfig.getInstance(context);
    }
    
    /**
     * 调用耗材查询API
     * @param barcode 条码
     * @param callback 回调接口
     */
    public void queryConsumableInfo(String barcode, ApiCallback<ConsumableApiResponse> callback) {
        executorService.execute(() -> {
            try {
                // 获取当前用户
                UserInfo currentUser = SessionManager.getInstance(context).getCurrentUser();
                
                if (currentUser == null) {
                    mainHandler.post(() -> callback.onFailure(new Exception("用户未登录")));
                    return;
                }
                
                // 检查网络配置
                if (!networkConfig.isValidConfig()) {
                    mainHandler.post(() -> callback.onFailure(new Exception("API服务器地址未配置，请先在设置中配置API地址")));
                    return;
                }
                
                // 构建请求数据（新接口仅需要 dataList）
                JSONObject requestData = new JSONObject();
                JSONArray dataList = new JSONArray();
                dataList.put(barcode);
                requestData.put("dataList", dataList);

                // 使用新的查询接口
                String apiUrl = networkConfig.getFullUrl(ApiEndpoint.CONSUME_BY_BARCODE);
                Log.d(TAG, "发送API请求到: " + apiUrl);
                Log.d(TAG, "请求数据: " + requestData.toString());
                
                // 发送HTTP请求
                String response = sendHttpRequest(apiUrl, "POST", requestData.toString());
                
                Log.d(TAG, "API响应: " + response);
                
                // 解析响应
                ConsumableApiResponse apiResponse = parseApiResponse(response);
                
                // 回调结果
                mainHandler.post(() -> callback.onSuccess(apiResponse));
                
            } catch (Exception e) {
                Log.e(TAG, "API调用失败", e);
                mainHandler.post(() -> callback.onFailure(e));
            }
        });
    }
    
    /**
     * 发送HTTP请求
     * @param url 请求URL
     * @param method 请求方法
     * @param data 请求数据
     * @return 响应数据
     */
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

            // 添加Bearer Token认证（避免在日志中输出完整token）
            UserInfo currentUser = SessionManager.getInstance(context).getCurrentUser();
            if (currentUser != null && currentUser.getToken() != null && !currentUser.getToken().isEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer " + currentUser.getToken());
                Log.d(TAG, "已添加Authorization header");
            } else {
                Log.w(TAG, "用户未登录或token为空，请求可能失败");
            }

            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);

            // 仅在需要时启用输出流
            boolean hasBody = data != null && !data.isEmpty();
            boolean methodAllowsBody = "POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method);
            connection.setDoOutput(hasBody && methodAllowsBody);

            // 发送请求数据（仅POST/PUT/PATCH并且有数据时）
            if (hasBody && methodAllowsBody) {
                try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream()))) {
                    writer.write(data);
                    writer.flush();
                }
            }

            // 获取响应
            int responseCode = connection.getResponseCode();
            // 将所有2xx都视为成功
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
                // 尝试读取错误响应体，提取更友好的错误信息
                String errorBody = null;
                try (BufferedReader errReader = new BufferedReader(new InputStreamReader(connection.getErrorStream()))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while (errReader != null && (line = errReader.readLine()) != null) {
                        sb.append(line);
                    }
                    errorBody = sb.length() > 0 ? sb.toString() : null;
                } catch (Exception ignore) {
                }

                // 如果是JSON，尽量解析message字段
                if (errorBody != null && !errorBody.isEmpty()) {
                    try {
                        org.json.JSONObject errJson = new org.json.JSONObject(errorBody);
                        String msg = errJson.optString("message", null);
                        if (msg != null && !msg.isEmpty()) {
                            throw new Exception(msg + " (HTTP " + responseCode + ")");
                        }
                    } catch (Exception ignore) {
                        // 非JSON或解析失败，继续抛出通用错误并附上部分响应体
                    }
                    // 附带部分错误体，避免过长
                    String snippet = errorBody.length() > 200 ? errorBody.substring(0, 200) + "..." : errorBody;
                    throw new Exception("HTTP请求失败，响应码: " + responseCode + ", 错误: " + snippet);
                }

                throw new Exception("HTTP请求失败，响应码: " + responseCode);
            }

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * 批量根据院内码获取耗材信息
     */
    public void queryConsumableInfoList(java.util.List<String> barcodes, ApiCallback<ConsumableApiResponse> callback) {
        executorService.execute(() -> {
            try {
                UserInfo currentUser = SessionManager.getInstance(context).getCurrentUser();
                if (currentUser == null) {
                    mainHandler.post(() -> callback.onFailure(new Exception("用户未登录")));
                    return;
                }
                if (!networkConfig.isValidConfig()) {
                    mainHandler.post(() -> callback.onFailure(new Exception("API服务器地址未配置，请先在设置中配置API地址")));
                    return;
                }

                org.json.JSONObject requestData = new org.json.JSONObject();
                org.json.JSONArray dataList = new org.json.JSONArray();
                if (barcodes != null) {
                    for (String bc : barcodes) {
                        if (bc != null && !bc.trim().isEmpty()) dataList.put(bc.trim());
                    }
                }
                requestData.put("dataList", dataList);

                String apiUrl = networkConfig.getFullUrl(ApiEndpoint.CONSUME_BY_BARCODE);
                String response = sendHttpRequest(apiUrl, "POST", requestData.toString());
                ConsumableApiResponse apiResponse = parseApiResponse(response);
                mainHandler.post(() -> callback.onSuccess(apiResponse));
            } catch (Exception e) {
                Log.e(TAG, "queryConsumableInfoList failed", e);
                mainHandler.post(() -> callback.onFailure(e));
            }
        });
    }

    /**
     * 移库预览结果（PDA 耗材移库用）
     */
    public static class TransferPreviewItem {
        public String rfid;
        public int fromLocationId;
        public String fromLocationName;
        public String code;
        public String name;
        public String spec;
        public String manufacturer;
        public String brand;
        public String unit;
        public String status;
    }

    /**
     * 根据条码预览移库耗材（POST /api/transfer/preview_by_barcode）
     */
    public void previewTransferByBarcode(String barcode, int toLocationId, ApiCallback<TransferPreviewItem> callback) {
        executorService.execute(() -> {
            try {
                UserInfo currentUser = SessionManager.getInstance(context).getCurrentUser();
                if (currentUser == null) {
                    mainHandler.post(() -> callback.onFailure(new Exception("用户未登录")));
                    return;
                }
                if (!networkConfig.isValidConfig()) {
                    mainHandler.post(() -> callback.onFailure(new Exception("API服务器地址未配置，请先在设置中配置API地址")));
                    return;
                }
                org.json.JSONObject body = new org.json.JSONObject();
                body.put("barcode", barcode == null ? "" : barcode.trim());
                body.put("to_location_id", toLocationId);

                String url = networkConfig.getFullUrl(ApiEndpoint.TRANSFER_PREVIEW_BY_BARCODE);
                Log.d(TAG, "previewTransferByBarcode(POST): " + url + ", body=" + body);
                String resp = sendHttpRequest(url, "POST", body.toString());

                TransferPreviewItem item = parseTransferPreviewItem(resp, barcode);
                mainHandler.post(() -> callback.onSuccess(item));
            } catch (Exception e) {
                Log.e(TAG, "previewTransferByBarcode failed", e);
                mainHandler.post(() -> callback.onFailure(e));
            }
        });
    }

    /**
     * 根据 RFID 预览移库耗材（POST /api/transfer/preview_by_rfid）
     */
    public void previewTransferByRfid(String rfid, int toLocationId, ApiCallback<TransferPreviewItem> callback) {
        executorService.execute(() -> {
            try {
                UserInfo currentUser = SessionManager.getInstance(context).getCurrentUser();
                if (currentUser == null) {
                    mainHandler.post(() -> callback.onFailure(new Exception("用户未登录")));
                    return;
                }
                if (!networkConfig.isValidConfig()) {
                    mainHandler.post(() -> callback.onFailure(new Exception("API服务器地址未配置，请先在设置中配置API地址")));
                    return;
                }
                org.json.JSONObject body = new org.json.JSONObject();
                body.put("rfid", rfid == null ? "" : rfid.trim());
                body.put("to_location_id", toLocationId);

                String url = networkConfig.getFullUrl(ApiEndpoint.TRANSFER_PREVIEW_BY_RFID);
                Log.d(TAG, "previewTransferByRfid(POST): " + url + ", body=" + body);
                String resp = sendHttpRequest(url, "POST", body.toString());

                TransferPreviewItem item = parseTransferPreviewItem(resp, rfid);
                mainHandler.post(() -> callback.onSuccess(item));
            } catch (Exception e) {
                Log.e(TAG, "previewTransferByRfid failed", e);
                mainHandler.post(() -> callback.onFailure(e));
            }
        });
    }

    /**
     * 解析移库预览响应，提取单条耗材信息。
     *
     * 响应形如：
     * {
     *   "status":200,
     *   "message":"...",
     *   "data":{
     *     "dictionary_id": number,
     *     "code": "HZ-001",
     *     "name": "...",
     *     "spec": "...",
     *     "batch": "...",
     *     "manufacturer": "...",
     *     "brand": "...",
     *     "unit": "...",
     *     "status": "inStock",
     *     "current_location_id": "1",
     *     "current_location_name": "中心库A"
     *   }
     * }
     */
    private TransferPreviewItem parseTransferPreviewItem(String response, String rfidOrBarcode) throws Exception {
        org.json.JSONObject root = new org.json.JSONObject(response);
        int status = root.optInt("status", root.optInt("code", 0));
        if (status != 200) {
            String msg = root.optString("message", root.optString("msg", "移库预览失败"));
            throw new Exception(msg);
        }
        org.json.JSONObject data = root.optJSONObject("data");
        if (data == null) {
            throw new Exception("移库预览返回数据为空");
        }
        TransferPreviewItem item = new TransferPreviewItem();
        item.rfid = rfidOrBarcode;
        item.code = data.optString("code", "");
        item.name = data.optString("name", "");
        item.spec = data.optString("spec", "");
        item.manufacturer = data.optString("manufacturer", "");
        item.brand = data.optString("brand", "");
        item.unit = data.optString("unit", "");
        item.status = data.optString("status", "");
        try {
            String locIdStr = data.optString("current_location_id", "");
            if (locIdStr != null && !locIdStr.trim().isEmpty()) {
                item.fromLocationId = Integer.parseInt(locIdStr.trim());
            } else {
                item.fromLocationId = 0;
            }
        } catch (Exception ignore) {
            item.fromLocationId = 0;
        }
        item.fromLocationName = data.optString("current_location_name", "");
        return item;
    }

    /**
     * 获取业务系统库存快照（新建盘点任务用）
     * POST /api/stock/getAllConsumables
     * 请求体：{ page:number, pagesize:number }
     * 响应体：{ status:number, message:string, data:{ page:number, pagesize:number, total:number, data: [] } }
     * data[].operatingRoom: { id:number|null, name:string|null }。
     * 规则：operatingRoom 同时具备 id 与 name 视为具体术间；任一缺失则归类为“耗材屋”。
     */
    public void fetchInventoryInfoList(ApiCallback<List<InventoryPlanItem>> callback) {
        executorService.execute(() -> {
            try {
                // 登录与配置校验
                UserInfo currentUser = SessionManager.getInstance(context).getCurrentUser();
                if (currentUser == null) {
                    mainHandler.post(() -> callback.onFailure(new Exception("用户未登录")));
                    return;
                }
                if (!networkConfig.isValidConfig()) {
                    mainHandler.post(() -> callback.onFailure(new Exception("API服务器地址未配置，请先在设置中配置API地址")));
                    return;
                }
                String url = networkConfig.getFullUrl(ApiEndpoint.GET_ALL_CONSUMABLES);
                org.json.JSONObject req = new org.json.JSONObject();
                req.put("page", 1);
                req.put("pagesize", 10000);
                Log.d(TAG, "库存拉取(POST): " + url + ", body=" + req);
                String response = sendHttpRequest(url, "POST", req.toString());

                // 严格按 Apifox 定义解析
                List<InventoryPlanItem> plan = new ArrayList<>();
                org.json.JSONObject root = new org.json.JSONObject(response);
                int status = root.optInt("status", 0);
                if (status != 200) {
                    String msg = root.optString("message", "查询失败");
                    throw new Exception(msg);
                }
                org.json.JSONObject dataObj = root.optJSONObject("data");
                org.json.JSONArray arr = dataObj != null ? dataObj.optJSONArray("data") : null; // 注意是 data.data（数组）
                if (arr == null) arr = new org.json.JSONArray();
                for (int i = 0; i < arr.length(); i++) {
                    org.json.JSONObject obj = arr.optJSONObject(i);
                    if (obj == null) continue;
                    // 以 current_location_id 聚合；名称优先用 current_location_name
                    int roomId = 0;
                    String roomName = null;
                    if (!obj.isNull("current_location_id")) {
                        try { roomId = obj.optInt("current_location_id", 0); } catch (Exception ignore) {}
                    }
                    roomName = obj.optString("current_location_name", null);
                    if (roomId <= 0 || roomName == null || roomName.isEmpty()) { roomId = 0; roomName = "耗材屋"; }

                    String rfid = obj.optString("rfid", null);
                    if (rfid == null || rfid.trim().isEmpty()) continue; // 严格按定义，无rfid则跳过
                    String name = obj.optString("name", null);
                    String spec = obj.optString("spec", null);
                    String manufacturer = obj.optString("manufacturer", null);
                    String lastUser = null; // 接口未返回，预留
                    Long lastUsedAt = null;

                    plan.add(new InventoryPlanItem(rfid.trim(), roomId, roomName, name, spec, manufacturer, lastUser, lastUsedAt));
                }

                final List<InventoryPlanItem> result = plan;
                mainHandler.post(() -> callback.onSuccess(result));

            } catch (Exception e) {
                Log.e(TAG, "fetchInventoryInfoList failed", e);
                mainHandler.post(() -> callback.onFailure(e));
            }
        });
    }

    /**
     * 盘点任务列表（PDA 使用）：POST /api/inventoryTask/list
     * 当前实现：拉取所有 running 任务，不做地点过滤。
     */
    public void fetchInventoryTaskList(ApiCallback<List<InventoryTask>> callback) {
        executorService.execute(() -> {
            try {
                UserInfo currentUser = SessionManager.getInstance(context).getCurrentUser();
                if (currentUser == null) {
                    mainHandler.post(() -> callback.onFailure(new Exception("用户未登录")));
                    return;
                }
                if (!networkConfig.isValidConfig()) {
                    mainHandler.post(() -> callback.onFailure(new Exception("API服务器地址未配置，请先在设置中配置API地址")));
                    return;
                }

                String url = networkConfig.getFullUrl(ApiEndpoint.INVENTORY_TASK_LIST);
                org.json.JSONObject body = new org.json.JSONObject();
                body.put("page", 1);
                body.put("pagesize", 50);
                // 仅拉取进行中的任务；若后续需要查看历史，可扩展参数
                org.json.JSONArray statusList = new org.json.JSONArray();
                statusList.put("running");
                body.put("statusList", statusList);

                Log.d(TAG, "fetchInventoryTaskList(POST): " + url + ", body=" + body);
                String response = sendHttpRequest(url, "POST", body.toString());

                org.json.JSONObject root = new org.json.JSONObject(response);
                int status = root.optInt("status", root.optInt("code", 0));
                if (status != 200) {
                    String msg = root.optString("message", root.optString("msg", "查询失败"));
                    throw new Exception(msg);
                }
                org.json.JSONObject data = root.optJSONObject("data");
                org.json.JSONArray items = data != null ? data.optJSONArray("items") : null;
                if (items == null) items = new org.json.JSONArray();

                List<InventoryTask> tasks = new ArrayList<>();
                for (int i = 0; i < items.length(); i++) {
                    org.json.JSONObject obj = items.optJSONObject(i);
                    if (obj == null) continue;

                    InventoryTask task = new InventoryTask();
                    task.setTaskId(obj.optString("taskId", ""));
                    String name = obj.optString("taskName", "");
                    if (name == null || name.trim().isEmpty()) {
                        name = task.getTaskId();
                    }
                    task.setTaskName(name);

                    // 创建时间
                    String createdAt = obj.optString("created_at", null);
                    if (createdAt != null && !createdAt.trim().isEmpty()) {
                        try {
                            java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault());
                            task.setCreateTime(fmt.parse(createdAt));
                        } catch (Exception ignore) {}
                    }

                    // 创建人
                    String creator = obj.optString("createdByName", "");
                    task.setOperator(creator == null ? "" : creator);

                    // 状态：保留原始枚举以便 PDA 逻辑判断，同时提供中文展示值
                    String rawStatus = obj.optString("status", "draft");
                    task.setRawStatus(rawStatus);
                    String displayStatus;
                    switch (rawStatus) {
                        case "running":
                            displayStatus = "执行中";
                            break;
                        case "review":
                            displayStatus = "待复核";
                            break;
                        case "closed":
                            displayStatus = "已完成";
                            break;
                        case "canceled":
                            displayStatus = "已作废";
                            break;
                        case "draft":
                        default:
                            displayStatus = "未开始";
                            break;
                    }
                    task.setStatus(displayStatus);

                    // 计划数量及统计字段
                    int planned = obj.optInt("plannedCount", 0);
                    int scanned = obj.optInt("scannedCount", 0);
                    int normal = obj.optInt("normalCount", 0);
                    int missing = obj.optInt("missingCount", 0);
                    int surplus = obj.optInt("surplusCount", 0);
                    task.setTotalItems(planned);
                    task.setScannedCount(scanned);
                    task.setNormalCount(normal);
                    task.setMissingCount(missing);
                    task.setSurplusCount(surplus);
                    tasks.add(task);
                }

                List<InventoryTask> finalTasks = tasks;
                mainHandler.post(() -> callback.onSuccess(finalTasks));
            } catch (Exception e) {
                Log.e(TAG, "fetchInventoryTaskList failed", e);
                mainHandler.post(() -> callback.onFailure(e));
            }
        });
    }

    /**
     * 提交盘点扫描批次：POST /api/inventoryTask/submitScanBatch
     * 返回最新的任务统计汇总。
     */
    public void submitInventoryScanBatch(String taskId,
                                         List<String> rfidList,
                                         ApiCallback<InventoryTaskSummary> callback) {
        executorService.execute(() -> {
            try {
                if (taskId == null || taskId.trim().isEmpty()) {
                    mainHandler.post(() -> callback.onFailure(new IllegalArgumentException("taskId 不能为空")));
                    return;
                }
                if (rfidList == null || rfidList.isEmpty()) {
                    mainHandler.post(() -> callback.onFailure(new IllegalArgumentException("rfidList 不能为空")));
                    return;
                }

                UserInfo currentUser = SessionManager.getInstance(context).getCurrentUser();
                if (currentUser == null) {
                    mainHandler.post(() -> callback.onFailure(new Exception("用户未登录")));
                    return;
                }
                if (!networkConfig.isValidConfig()) {
                    mainHandler.post(() -> callback.onFailure(new Exception("API服务器地址未配置，请先在设置中配置API地址")));
                    return;
                }

                String url = networkConfig.getFullUrl(ApiEndpoint.INVENTORY_TASK_SUBMIT_BATCH);
                org.json.JSONObject body = new org.json.JSONObject();
                body.put("taskId", taskId);

                org.json.JSONArray arr = new org.json.JSONArray();
                for (String r : rfidList) {
                    if (r == null) continue;
                    String v = r.trim();
                    if (!v.isEmpty()) arr.put(v);
                }
                body.put("rfidList", arr);

                // 附加设备与用户信息（后端字段均为可选）
                SessionManager sm = SessionManager.getInstance(context);
                String deviceId = sm.getDeviceId();
                body.put("deviceId", deviceId == null ? "" : deviceId);
                String deviceName;
                try {
                    deviceName = android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL;
                } catch (Exception e) {
                    deviceName = "PDA";
                }
                body.put("deviceName", deviceName);

                long userIdNumeric;
                try {
                    userIdNumeric = Long.parseLong(currentUser.getUserId());
                } catch (Exception e) {
                    userIdNumeric = 0L;
                }
                body.put("userId", userIdNumeric);
                body.put("userName", currentUser.getUsername());

                Log.d(TAG, "submitInventoryScanBatch(POST): " + url + ", body=" + body);
                String response = sendHttpRequest(url, "POST", body.toString());

                org.json.JSONObject root = new org.json.JSONObject(response);
                int status = root.optInt("status", root.optInt("code", 0));
                if (status != 200) {
                    String msg = root.optString("message", root.optString("msg", "提交失败"));
                    throw new Exception(msg);
                }
                org.json.JSONObject data = root.optJSONObject("data");
                InventoryTaskSummary summary = new InventoryTaskSummary();
                if (data != null) {
                    summary.taskId = data.optString("taskId", taskId);
                    summary.plannedCount = data.optInt("plannedCount", 0);
                    summary.scannedCount = data.optInt("scannedCount", 0);
                    summary.normalCount = data.optInt("normalCount", 0);
                    summary.missingCount = data.optInt("missingCount", 0);
                    summary.surplusCount = data.optInt("surplusCount", 0);
                } else {
                    summary.taskId = taskId;
                }

                InventoryTaskSummary finalSummary = summary;
                mainHandler.post(() -> callback.onSuccess(finalSummary));
            } catch (Exception e) {
                Log.e(TAG, "submitInventoryScanBatch failed", e);
                mainHandler.post(() -> callback.onFailure(e));
            }
        });
    }

    /**
     * 查询盘点任务明细（GET /api/inventoryTask/detail）
     * 仅用于 PDA 初始化/刷新地点汇总与RFID明细。
     */
    public void fetchInventoryTaskDetail(String taskId,
                                         ApiCallback<InventoryTaskDetailResult> callback) {
        executorService.execute(() -> {
            try {
                if (taskId == null || taskId.trim().isEmpty()) {
                    mainHandler.post(() -> callback.onFailure(new IllegalArgumentException("taskId 不能为空")));
                    return;
                }
                if (!networkConfig.isValidConfig()) {
                    mainHandler.post(() -> callback.onFailure(new Exception("API服务器地址未配置，请先在设置中配置API地址")));
                    return;
                }

                String base = networkConfig.getFullUrl(ApiEndpoint.INVENTORY_TASK_LIST)
                        .replace("/inventoryTask/list", "/inventoryTask/detail");
                String url = base + "?taskId=" + java.net.URLEncoder.encode(taskId, "UTF-8")
                        + "&page=1&pagesize=10000";

                Log.d(TAG, "fetchInventoryTaskDetail(GET): " + url);
                String resp = sendHttpRequest(url, "GET", null);

                org.json.JSONObject root = new org.json.JSONObject(resp);
                int status = root.optInt("status", root.optInt("code", 0));
                if (status != 200) {
                    String msg = root.optString("message", root.optString("msg", "查询失败"));
                    throw new Exception(msg);
                }
                org.json.JSONObject data = root.optJSONObject("data");
                org.json.JSONObject detail = data != null ? data.optJSONObject("detail") : null;
                org.json.JSONArray items = detail != null ? detail.optJSONArray("items") : null;
                if (items == null) items = new org.json.JSONArray();

                InventoryTaskDetailResult result = new InventoryTaskDetailResult();
                for (int i = 0; i < items.length(); i++) {
                    org.json.JSONObject obj = items.optJSONObject(i);
                    if (obj == null) continue;
                    InventoryTaskDetailItem it = new InventoryTaskDetailItem();
                    it.rfid = obj.optString("rfid", null);
                    if (obj.has("operatingRoomID") && !obj.isNull("operatingRoomID")) {
                        try {
                            it.operatingRoomID = obj.optInt("operatingRoomID", 0);
                        } catch (Exception ignore) {
                            it.operatingRoomID = null;
                        }
                    }
                    it.operatingRoomName = obj.optString("operatingRoomName", null);
                    it.name = obj.optString("name", null);
                    it.spec = obj.optString("spec", null);
                    it.manufacturer = obj.optString("manufacturer", null);
                    it.snapshotStatus = obj.optString("snapshotStatus", null);
                    if (it.snapshotStatus != null && it.snapshotStatus.trim().isEmpty()) it.snapshotStatus = null;
                    it.latestStatus = obj.optString("latestStatus", null);
                    if (it.latestStatus != null && it.latestStatus.trim().isEmpty()) it.latestStatus = null;
                    it.scanStatus = obj.optString("scanStatus", null);
                    result.items.add(it);
                }

                InventoryTaskDetailResult finalResult = result;
                mainHandler.post(() -> callback.onSuccess(finalResult));
            } catch (Exception e) {
                Log.e(TAG, "fetchInventoryTaskDetail failed", e);
                mainHandler.post(() -> callback.onFailure(e));
            }
        });
    }

    /**
     * 从返回条目中提取一个或多个 RFID。
     * 兼容字段：
     * - 单值：rfid、RFID、epc、epcCode
     * - 数组：rfidList、rfids、epcs、tags
     */
    private java.util.List<String> extractRfidsFromItem(org.json.JSONObject obj) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (obj == null) return out;
        // 单值候选
        String[] singleKeys = new String[]{"rfid", "RFID", "epc", "epcCode"};
        for (String k : singleKeys) {
            String v = obj.optString(k, null);
            if (v != null && !v.trim().isEmpty()) { out.add(v.trim()); return out; }
        }
        // 数组候选
        String[] arrayKeys = new String[]{"rfidList", "rfids", "epcs", "tags"};
        for (String k : arrayKeys) {
            org.json.JSONArray arr = obj.optJSONArray(k);
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    String v = arr.optString(i, null);
                    if (v != null && !v.trim().isEmpty()) out.add(v.trim());
                }
                if (!out.isEmpty()) return out;
            }
        }
        return out; // 可能为空，调用方会跳过
    }
    
    /**
     * 解析耗材查询API响应（严格按新结构解析，不做兼容处理）。
     *
     * 响应结构：
     * {
     *   "status":200,
     *   "message":"...",
     *   "data":{
     *     "cabinetID": number?,
     *     "notInStockList": string[],
     *     "inStockList": object[],
     *     "takenList": object[],
     *     "consumedList": object[],
     *     "usedReturnList": object[]
     *   }
     * }
     */
    private ConsumableApiResponse parseApiResponse(String response) throws Exception {
        JSONObject jsonResponse = new JSONObject(response);
        
        // 检查响应状态
        int status = jsonResponse.optInt("status", 0);
        if (status != 200) {
            String message = jsonResponse.optString("message", "未知错误");
            throw new Exception("API请求失败: " + message);
        }
        
        // 解析数据
        JSONObject data = jsonResponse.optJSONObject("data");
        if (data == null) {
            throw new Exception("API响应数据格式错误");
        }
        
        ConsumableApiResponse apiResponse = new ConsumableApiResponse();
        apiResponse.cabinetID = data.optInt("cabinetID", 0);
        
        // 未入库列表（纯 RFID 字符串）
        JSONArray notInStockArray = data.optJSONArray("notInStockList");
        if (notInStockArray != null) {
            for (int i = 0; i < notInStockArray.length(); i++) {
                apiResponse.notInStockList.add(notInStockArray.optString(i, ""));
            }
        }
        
        // 在库列表
        JSONArray inStockArray = data.optJSONArray("inStockList");
        if (inStockArray != null) {
            for (int i = 0; i < inStockArray.length(); i++) {
                JSONObject item = inStockArray.optJSONObject(i);
                if (item != null) {
                    ConsumableItem ci = parseConsumableItem(item);
                    apiResponse.inStockList.add(ci);
                }
            }
        }

        // 取用未归还列表
        JSONArray takenArray = data.optJSONArray("takenList");
        if (takenArray != null) {
            for (int i = 0; i < takenArray.length(); i++) {
                JSONObject item = takenArray.optJSONObject(i);
                if (item != null) {
                    ConsumableItem ci = parseConsumableItem(item);
                    apiResponse.takenList.add(ci);
                }
            }
        }

        // 已消耗列表
        JSONArray consumedArray = data.optJSONArray("consumedList");
        if (consumedArray != null) {
            for (int i = 0; i < consumedArray.length(); i++) {
                JSONObject item = consumedArray.optJSONObject(i);
                if (item != null) {
                    ConsumableItem ci = parseConsumableItem(item);
                    apiResponse.consumedList.add(ci);
                }
            }
        }

        // 消耗退回列表
        JSONArray usedReturnArray = data.optJSONArray("usedReturnList");
        if (usedReturnArray != null) {
            for (int i = 0; i < usedReturnArray.length(); i++) {
                JSONObject item = usedReturnArray.optJSONObject(i);
                if (item != null) {
                    ConsumableItem ci = parseConsumableItem(item);
                    apiResponse.usedReturnList.add(ci);
                }
            }
        }
        
        return apiResponse;
    }
    
    /**
     * 解析耗材项目
     * @param itemJson 耗材JSON对象
     * @return 耗材项目
     */
    private ConsumableItem parseConsumableItem(JSONObject itemJson) {
        ConsumableItem item = new ConsumableItem();
        item.setId(itemJson.optInt("id", 0));
        item.setRfid(itemJson.optString("rfid", ""));
        item.setBatch(itemJson.optString("batch", ""));
        item.setExpiryDate(itemJson.optString("expiryDate", ""));
        item.setProductionDate(itemJson.optString("productionDate", ""));
        item.setProductCode(itemJson.optString("code", ""));
        item.setCommonName(itemJson.optString("name", ""));
        item.setSpecification(itemJson.optString("spec", ""));
        item.setManufacturer(itemJson.optString("manufacturer", ""));
        item.setBrand(itemJson.optString("brand", ""));
        // 扩展解析：最后一次操作信息（若存在）
        String opName = itemJson.optString("lastOperatorName", null);
        String opTime = itemJson.optString("lastOperationTime", null);
        if (opName != null && opName.trim().isEmpty()) opName = null;
        if (opTime != null && opTime.trim().isEmpty()) opTime = null;
        item.setLastOperatorName(opName);
        item.setLastOperationTime(opTime);
        // 使用rfid作为条码号显示，但调用方可能会覆盖为原始扫描的条码
        item.setBarcode(itemJson.optString("rfid", ""));
        // 设置默认单位和数量
        item.setUnit("个");
        item.setQuantity(1);
        // 当前所在地点ID（若后端返回）
        try {
            if (!itemJson.isNull("current_location_id")) {
                int cid = itemJson.optInt("current_location_id", 0);
                if (cid > 0) item.setCurrentLocationId(cid);
            }
        } catch (Exception ignore) {}
        return item;
    }
    
    /**
     * 获取当前时间字符串
     * @return 时间字符串
     */
    private String getCurrentTimeString() {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                .format(new java.util.Date());
    }
    
    /**
     * API回调接口
     * @param <T> 响应数据类型
     */
    public interface ApiCallback<T> {
        void onSuccess(T result);
        void onFailure(Exception e);
    }

    /**
     * 盘点任务统计汇总（后端 /inventoryTask/submitScanBatch 返回的数据结构）。
     */
    public static class InventoryTaskSummary {
        public String taskId;
        public int plannedCount;
        public int scannedCount;
        public int normalCount;
        public int missingCount;
        public int surplusCount;
    }

    /**
     * 盘点任务明细条目（仅保留 PDA 需要的字段）。
     */
    public static class InventoryTaskDetailItem {
        public String rfid;
        public Integer operatingRoomID;
        public String operatingRoomName;
        public String name;
        public String spec;
        public String manufacturer;
        public String snapshotStatus;
        public String latestStatus;
        public String scanStatus;
    }

    public static class InventoryTaskDetailResult {
        public java.util.List<InventoryTaskDetailItem> items = new java.util.ArrayList<>();
    }

    /**
     * 取用/归还记录创建结果
     */
    public static class CreateTakeAndReturnResult {
        public Long recordId;
        public Integer takeCount;
        public Integer returnCount;
    }

    /**
     * 创建取用/归还记录（POST /api/stock/createTakeAndReturnLog）
     */
    public void createTakeAndReturnLog(Integer operationRoomId,
                                       java.util.List<ConsumableItem> takeItems,
                                       java.util.List<ConsumableItem> returnItems,
                                       @androidx.annotation.Nullable String fromLocationId,
                                       @androidx.annotation.Nullable String toLocationId,
                                       ApiCallback<CreateTakeAndReturnResult> callback) {
        executorService.execute(() -> {
            try {
                // 校验登录与配置
                UserInfo currentUser = SessionManager.getInstance(context).getCurrentUser();
                if (currentUser == null) {
                    mainHandler.post(() -> callback.onFailure(new Exception("用户未登录")));
                    return;
                }

                if (!networkConfig.isValidConfig()) {
                    mainHandler.post(() -> callback.onFailure(new Exception("API服务器地址未配置，请先在设置中配置API地址")));
                    return;
                }

                // 读取配置中的cabinetId
                int cabinetId = BarcodeValidator.getInstance(context).getCabinetId();
                if (cabinetId <= 0) {
                    mainHandler.post(() -> callback.onFailure(new Exception("请先在设置中配置货柜ID")));
                    return;
                }

                // 不再调用 GET_STOCK_BY_RFID 补齐ID；明细ID与服务端主键无关联，仅为行号

                // 构造请求体
                org.json.JSONObject body = new org.json.JSONObject();
                // userID 为数字
                long userIdNumeric;
                try {
                    userIdNumeric = Long.parseLong(currentUser.getUserId());
                } catch (Exception e) {
                    userIdNumeric = 0L;
                }
                body.put("userID", userIdNumeric);
                String now = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                        .format(new java.util.Date());
                body.put("openDoorTime", now);
                body.put("closeDoorTime", now);
                if (operationRoomId == null) {
                    body.put("operationroomId", org.json.JSONObject.NULL);
                } else {
                    body.put("operationroomId", operationRoomId);
                }
                body.put("cabinetID", cabinetId);
                // from 优先使用调用方传入的 current_location_id；缺失时回退柜ID
                if (fromLocationId != null && !fromLocationId.trim().isEmpty()) {
                    body.put("from_location_id", fromLocationId.trim());
                } else {
                    body.put("from_location_id", String.valueOf(cabinetId));
                }
                if (toLocationId != null && !toLocationId.trim().isEmpty()) {
                    body.put("to_location_id", toLocationId.trim());
                } else {
                    body.put("to_location_id", org.json.JSONObject.NULL);
                }

                org.json.JSONArray takeArr = new org.json.JSONArray();
                int lineId = 1;
                if (takeItems != null) {
                    for (ConsumableItem item : takeItems) {
                        // 校验基本字段
                        if (item == null) continue;
                        org.json.JSONObject it = new org.json.JSONObject();
                        // 明细ID使用本次提交内的递增序号
                        it.put("id", lineId++);
                        String rfid = item.getRfid();
                        it.put("rfid", rfid);
                        it.put("batch", item.getBatch() == null ? "" : item.getBatch());
                        it.put("expiryDate", item.getExpiryDate() == null ? "" : item.getExpiryDate());
                        it.put("productionDate", item.getProductionDate() == null ? "" : item.getProductionDate());
                        it.put("code", item.getProductCode() == null ? "" : item.getProductCode());
                        it.put("name", item.getCommonName() == null ? "" : item.getCommonName());
                        it.put("spec", item.getSpecification() == null ? "" : item.getSpecification());
                        it.put("manufacturer", item.getManufacturer() == null ? "" : item.getManufacturer());
                        it.put("brand", item.getBrand() == null ? "" : item.getBrand());
                        // 可选：取用并发校验（当前暂不强制，后端主要针对归还做校验）
                        takeArr.put(it);
                    }
                }
                body.put("takeList", takeArr);

                org.json.JSONArray returnArr = new org.json.JSONArray();
                if (returnItems != null) {
                    for (ConsumableItem item : returnItems) {
                        if (item == null) continue;
                        org.json.JSONObject it = new org.json.JSONObject();
                        it.put("id", lineId++);
                        String rfid = item.getRfid();
                        it.put("rfid", rfid);
                        it.put("batch", item.getBatch() == null ? "" : item.getBatch());
                        it.put("expiryDate", item.getExpiryDate() == null ? "" : item.getExpiryDate());
                        it.put("productionDate", item.getProductionDate() == null ? "" : item.getProductionDate());
                        it.put("code", item.getProductCode() == null ? "" : item.getProductCode());
                        it.put("name", item.getCommonName() == null ? "" : item.getCommonName());
                        it.put("spec", item.getSpecification() == null ? "" : item.getSpecification());
                        it.put("manufacturer", item.getManufacturer() == null ? "" : item.getManufacturer());
                        it.put("brand", item.getBrand() == null ? "" : item.getBrand());
                        // 并发校验：记录客户端看到的当前库存位置
                        if (item.getCurrentLocationId() != null && item.getCurrentLocationId() > 0) {
                            it.put("expected_current_location_id", String.valueOf(item.getCurrentLocationId()));
                        }
                        returnArr.put(it);
                    }
                }
                body.put("returnList", returnArr);

                String apiUrl = networkConfig.getFullUrl(ApiEndpoint.CREATE_TAKE_RETURN_LOG);
                Log.d(TAG, "提交取用记录到: " + apiUrl);
                Log.d(TAG, "请求数据: " + body.toString());

                String resp = sendHttpRequest(apiUrl, "POST", body.toString());
                org.json.JSONObject json = new org.json.JSONObject(resp);
                int status = json.optInt("status", 0);
                if (status != 200) {
                    String msg = json.optString("message", "提交失败");
                    throw new Exception(msg);
                }
                org.json.JSONObject data = json.optJSONObject("data");
                CreateTakeAndReturnResult result = new CreateTakeAndReturnResult();
                if (data != null) {
                    if (!data.isNull("recordId")) result.recordId = data.optLong("recordId");
                    if (!data.isNull("takeCount")) result.takeCount = data.optInt("takeCount");
                    if (!data.isNull("returnCount")) result.returnCount = data.optInt("returnCount");
                }
                CreateTakeAndReturnResult finalResult = result;
                mainHandler.post(() -> callback.onSuccess(finalResult));
            } catch (Exception e) {
                Log.e(TAG, "createTakeAndReturnLog failed", e);
                mainHandler.post(() -> callback.onFailure(e));
            }
        });
    }

    /**
     * 列出地点列表（仅查询，不维护）。兼容多种返回结构：
     * {items:[{id,name}]}, {data:{items:[...]}} 或 {data:[...]}
     */
    public void fetchLocations(ApiCallback<java.util.List<OperatingRoom>> callback) {
        executorService.execute(() -> {
            try {
                if (!networkConfig.isValidConfig()) {
                    mainHandler.post(() -> callback.onFailure(new Exception("API服务器地址未配置，请先在设置中配置API地址")));
                    return;
                }
                String url = networkConfig.getFullUrl(ApiEndpoint.LOCATIONS_LIST);
                String resp = sendHttpRequest(url, "GET", null);
                java.util.List<OperatingRoom> list = new java.util.ArrayList<>();
                try {
                    org.json.JSONObject root = new org.json.JSONObject(resp);
                    org.json.JSONArray arr = null;
                    if (root.has("items")) {
                        arr = root.optJSONArray("items");
                    }
                    if (arr == null && root.has("data")) {
                        Object data = root.get("data");
                        if (data instanceof org.json.JSONArray) arr = (org.json.JSONArray) data;
                        if (data instanceof org.json.JSONObject) arr = ((org.json.JSONObject) data).optJSONArray("items");
                    }
                    if (arr == null) arr = new org.json.JSONArray();
                    long now = System.currentTimeMillis();
                    for (int i = 0; i < arr.length(); i++) {
                        org.json.JSONObject o = arr.optJSONObject(i);
                        if (o == null) continue;
                        Integer sid = null;
                        if (!o.isNull("id")) sid = safeToInt(o.opt("id"));
                        String name = o.optString("name", null);
                        if (name == null || name.trim().isEmpty()) continue;
                        OperatingRoom room = new OperatingRoom(0, sid, name.trim(), now);
                        // 额外解析地点类型与启停状态（若存在）
                        try {
                            if (o.has("type") && !o.isNull("type")) {
                                room.type = o.optString("type", null);
                            }
                            if (o.has("is_active") && !o.isNull("is_active")) {
                                room.isActive = o.optBoolean("is_active", true);
                            }
                        } catch (Exception ignore) {}
                        list.add(room);
                    }
                } catch (Exception ignore) {
                    // 非标准结构，忽略
                }
                final java.util.List<OperatingRoom> finalList = list;
                mainHandler.post(() -> callback.onSuccess(finalList));
            } catch (Exception e) {
                Log.e(TAG, "fetchLocations failed", e);
                mainHandler.post(() -> callback.onFailure(e));
            }
        });
    }

    /**
     * 获取默认地点（GET /api/locations/default）
     * 返回 { id, name } 或 null
     */
    public void fetchDefaultLocation(ApiCallback<org.json.JSONObject> callback) {
        executorService.execute(() -> {
            try {
                if (!networkConfig.isValidConfig()) {
                    mainHandler.post(() -> callback.onFailure(new Exception("API服务器地址未配置，请先在设置中配置API地址")));
                    return;
                }
                String url = networkConfig.getFullUrl(ApiEndpoint.DEFAULT_LOCATION);
                String resp = sendHttpRequest(url, "GET", null);
                org.json.JSONObject root = new org.json.JSONObject(resp);
                int status = root.optInt("status", root.optInt("code", 0));
                if (status != 200) {
                    String msg = root.optString("msg", root.optString("message", "查询失败"));
                    throw new Exception(msg);
                }
                Object data = root.opt("data");
                org.json.JSONObject obj = null;
                if (data instanceof org.json.JSONObject) obj = (org.json.JSONObject) data;
                // 部分实现可能直接返回对象而非包在 data 中
                if (obj == null && root.has("id")) obj = root;
                final org.json.JSONObject result = obj; // 可能为 null
                mainHandler.post(() -> callback.onSuccess(result));
            } catch (Exception e) {
                Log.e(TAG, "fetchDefaultLocation failed", e);
                mainHandler.post(() -> callback.onFailure(e));
            }
        });
    }

    private Integer safeToInt(Object v) {
        try {
            if (v == null) return null;
            if (v instanceof Number) return ((Number) v).intValue();
            String s = String.valueOf(v);
            if (s.trim().isEmpty()) return null;
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    
    /**
     * 关闭服务
     */
    public void shutdown() {
        executorService.shutdown();
    }
}
