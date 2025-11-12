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

    /**
     * 获取术间列表（GET /api/surgery/getOperatingRoomList）
     */
    public void fetchOperatingRooms(ApiCallback<List<OperatingRoom>> callback) {
        executorService.execute(() -> {
            try {
                // 登录校验
                if (SessionManager.getInstance(context).getCurrentUser() == null) {
                    mainHandler.post(() -> callback.onFailure(new Exception("用户未登录")));
                    return;
                }
                if (!networkConfig.isValidConfig()) {
                    mainHandler.post(() -> callback.onFailure(new Exception("API服务器地址未配置，请先在设置中配置API地址")));
                    return;
                }

                String apiUrl = networkConfig.getFullUrl(ApiEndpoint.GET_OPERATING_ROOM_LIST);
                String response = sendHttpRequest(apiUrl, "GET", null);

                // 解析响应
                org.json.JSONObject json = new org.json.JSONObject(response);
                int status = json.optInt("status", 0);
                if (status != 200) {
                    String msg = json.optString("message", "查询失败");
                    throw new Exception(msg);
                }

                List<OperatingRoom> rooms = new ArrayList<>();
                // data 可能是数组或对象（兼容后端示例）
                Object dataNode = json.opt("data");
                if (dataNode instanceof org.json.JSONArray) {
                    org.json.JSONArray arr = (org.json.JSONArray) dataNode;
                    for (int i = 0; i < arr.length(); i++) {
                        org.json.JSONObject item = arr.optJSONObject(i);
                        if (item != null) {
                            OperatingRoom room = new OperatingRoom();
                            room.serverId = item.optInt("id", 0);
                            room.name = item.optString("name", "");
                            room.updatedTime = System.currentTimeMillis();
                            rooms.add(room);
                        }
                    }
                } else if (dataNode instanceof org.json.JSONObject) {
                    org.json.JSONObject item = (org.json.JSONObject) dataNode;
                    OperatingRoom room = new OperatingRoom();
                    room.serverId = item.optInt("id", 0);
                    room.name = item.optString("name", "");
                    room.updatedTime = System.currentTimeMillis();
                    rooms.add(room);
                }

                List<OperatingRoom> result = rooms;
                mainHandler.post(() -> callback.onSuccess(result));
            } catch (Exception e) {
                Log.e(TAG, "fetchOperatingRooms failed", e);
                mainHandler.post(() -> callback.onFailure(e));
            }
        });
    }

    /**
     * 新增术间（POST /api/surgery/addOperatingRoom）
     */
    public void addOperatingRoom(String name, ApiCallback<OperatingRoom> callback) {
        executorService.execute(() -> {
            try {
                if (SessionManager.getInstance(context).getCurrentUser() == null) {
                    mainHandler.post(() -> callback.onFailure(new Exception("用户未登录")));
                    return;
                }
                if (!networkConfig.isValidConfig()) {
                    mainHandler.post(() -> callback.onFailure(new Exception("API服务器地址未配置，请先在设置中配置API地址")));
                    return;
                }

                String apiUrl = networkConfig.getFullUrl(ApiEndpoint.ADD_OPERATING_ROOM);
                org.json.JSONObject body = new org.json.JSONObject();
                body.put("name", name);
                String response = sendHttpRequest(apiUrl, "POST", body.toString());

                org.json.JSONObject json = new org.json.JSONObject(response);
                int status = json.optInt("status", 0);
                if (status != 200) {
                    String msg = json.optString("message", "新增失败");
                    throw new Exception(msg);
                }
                org.json.JSONObject data = json.optJSONObject("data");
                OperatingRoom room = new OperatingRoom();
                if (data != null) {
                    room.serverId = data.optInt("id", 0);
                }
                room.name = name;
                room.updatedTime = System.currentTimeMillis();

                OperatingRoom result = room;
                mainHandler.post(() -> callback.onSuccess(result));
            } catch (Exception e) {
                Log.e(TAG, "addOperatingRoom failed", e);
                mainHandler.post(() -> callback.onFailure(e));
            }
        });
    }

    /**
     * 更新术间名称（POST /api/surgery/updateOperatingRoom）
     */
    public void updateOperatingRoom(int id, String newName, ApiCallback<Boolean> callback) {
        executorService.execute(() -> {
            try {
                if (SessionManager.getInstance(context).getCurrentUser() == null) {
                    mainHandler.post(() -> callback.onFailure(new Exception("用户未登录")));
                    return;
                }
                if (!networkConfig.isValidConfig()) {
                    mainHandler.post(() -> callback.onFailure(new Exception("API服务器地址未配置，请先在设置中配置API地址")));
                    return;
                }

                String apiUrl = networkConfig.getFullUrl(ApiEndpoint.UPDATE_OPERATING_ROOM);
                org.json.JSONObject body = new org.json.JSONObject();
                body.put("id", id);
                body.put("name", newName);
                String response = sendHttpRequest(apiUrl, "POST", body.toString());

                org.json.JSONObject json = new org.json.JSONObject(response);
                int status = json.optInt("status", 0);
                if (status != 200) {
                    String msg = json.optString("message", "更新失败");
                    throw new Exception(msg);
                }
                mainHandler.post(() -> callback.onSuccess(true));
            } catch (Exception e) {
                Log.e(TAG, "updateOperatingRoom failed", e);
                mainHandler.post(() -> callback.onFailure(e));
            }
        });
    }

    /**
     * 删除术间（软删除）（POST /api/surgery/deleteOperatingRoom）
     */
    public void deleteOperatingRoom(int id, ApiCallback<Boolean> callback) {
        executorService.execute(() -> {
            try {
                if (SessionManager.getInstance(context).getCurrentUser() == null) {
                    mainHandler.post(() -> callback.onFailure(new Exception("用户未登录")));
                    return;
                }
                if (!networkConfig.isValidConfig()) {
                    mainHandler.post(() -> callback.onFailure(new Exception("API服务器地址未配置，请先在设置中配置API地址")));
                    return;
                }

                String apiUrl = networkConfig.getFullUrl(ApiEndpoint.DELETE_OPERATING_ROOM);
                org.json.JSONObject body = new org.json.JSONObject();
                body.put("id", id);
                String response = sendHttpRequest(apiUrl, "POST", body.toString());

                org.json.JSONObject json = new org.json.JSONObject(response);
                int status = json.optInt("status", 0);
                if (status != 200) {
                    String msg = json.optString("message", "删除失败");
                    throw new Exception(msg);
                }
                mainHandler.post(() -> callback.onSuccess(true));
            } catch (Exception e) {
                Log.e(TAG, "deleteOperatingRoom failed", e);
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
                // 读取地点字典用于名称映射
                java.util.Map<Integer, String> locationMap = loadLocationsMapSafe();

                for (int i = 0; i < arr.length(); i++) {
                    org.json.JSONObject obj = arr.optJSONObject(i);
                    if (obj == null) continue;
                    // 改为以 current_location_id 聚合；名称优先用 current_location_name，其次用地点字典映射
                    int roomId = 0;
                    String roomName = null;
                    if (!obj.isNull("current_location_id")) {
                        try { roomId = obj.optInt("current_location_id", 0); } catch (Exception ignore) {}
                    }
                    // 兼容旧结构
                    if (roomId <= 0) {
                        org.json.JSONObject op = obj.optJSONObject("operatingRoom");
                        if (op != null && !op.isNull("id")) roomId = op.optInt("id", 0);
                    }
                    roomName = obj.optString("current_location_name", null);
                    if ((roomName == null || roomName.isEmpty()) && roomId > 0 && locationMap != null) {
                        String m = locationMap.get(roomId);
                        if (m != null && !m.isEmpty()) roomName = m;
                    }
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
     * 同步加载地点字典，失败返回 null。
     */
    private java.util.Map<Integer, String> loadLocationsMapSafe() {
        try {
            String url = networkConfig.getFullUrl(ApiEndpoint.LOCATIONS_LIST);
            String resp = sendHttpRequest(url, "GET", null);
            java.util.Map<Integer, String> map = new java.util.HashMap<>();
            try {
                org.json.JSONObject root = new org.json.JSONObject(resp);
                org.json.JSONArray arr = null;
                if (root.has("items")) arr = root.optJSONArray("items");
                if (arr == null && root.has("data")) {
                    Object data = root.get("data");
                    if (data instanceof org.json.JSONArray) arr = (org.json.JSONArray) data;
                    if (data instanceof org.json.JSONObject) arr = ((org.json.JSONObject) data).optJSONArray("items");
                }
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        org.json.JSONObject o = arr.optJSONObject(i);
                        if (o == null) continue;
                        Integer sid = safeToInt(o.opt("id"));
                        String name = o.optString("name", null);
                        if (sid != null && sid > 0 && name != null && !name.trim().isEmpty()) {
                            map.put(sid, name.trim());
                        }
                    }
                }
            } catch (Exception ignore) {}
            return map;
        } catch (Exception e) {
            Log.w(TAG, "loadLocationsMapSafe failed: " + e.getMessage());
            return null;
        }
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
     * 解析API响应
     * @param response 响应数据
     * @return 解析后的响应对象
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
        
        // 解析未入库数据
        JSONArray notInStockArray = data.optJSONArray("notInStockData");
        if (notInStockArray != null) {
            for (int i = 0; i < notInStockArray.length(); i++) {
                apiResponse.notInStockData.add(notInStockArray.optString(i, ""));
            }
        }
        
        // 解析异常数据
        JSONArray errorArray = data.optJSONArray("errorData");
        if (errorArray != null) {
            for (int i = 0; i < errorArray.length(); i++) {
                JSONObject item = errorArray.optJSONObject(i);
                if (item != null) {
                    ConsumableItem ci = parseConsumableItem(item);
                    ci.setStatus("异常条码");
                    apiResponse.errorData.add(ci);
                }
            }
        }
        
        // 解析可取用数据
        JSONArray takeArray = data.optJSONArray("takeList");
        if (takeArray != null) {
            for (int i = 0; i < takeArray.length(); i++) {
                JSONObject item = takeArray.optJSONObject(i);
                if (item != null) {
                    ConsumableItem ci = parseConsumableItem(item);
                    ci.setStatus("可领用");
                    apiResponse.takeList.add(ci);
                }
            }
        }
        
        // 解析未归还数据
        JSONArray returnArray = data.optJSONArray("returnList");
        if (returnArray != null) {
            for (int i = 0; i < returnArray.length(); i++) {
                JSONObject item = returnArray.optJSONObject(i);
                if (item != null) {
                    ConsumableItem ci = parseConsumableItem(item);
                    ci.setStatus("未归还");
                    apiResponse.returnList.add(ci);
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
                    for (int i = 0; i < arr.length(); i++) {
                        org.json.JSONObject o = arr.optJSONObject(i);
                        if (o == null) continue;
                        Integer sid = null;
                        if (!o.isNull("id")) sid = safeToInt(o.opt("id"));
                        String name = o.optString("name", null);
                        if (name == null || name.trim().isEmpty()) continue;
                        list.add(new OperatingRoom(0, sid, name.trim(), System.currentTimeMillis()));
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
