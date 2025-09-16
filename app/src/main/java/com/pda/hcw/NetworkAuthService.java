package com.pda.hcw;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import org.json.JSONObject;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 网络认证服务类
 * 负责处理与业务系统的接口认证
 */
public class NetworkAuthService {
    private static final String TAG = "NetworkAuthService";
    private static NetworkAuthService instance;
    
    private Context context;
    private OkHttpClient httpClient;
    private ExecutorService executorService;
    private Handler mainHandler;
    private NetworkConfig networkConfig;
    
    // API配置（路径改为从 NetworkConfig + ApiEndpoint 获取）
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    
    // 回调接口
    public interface AuthCallback {
        void onSuccess(UserInfo userInfo, String successMessage);
        void onFailure(String errorMessage);
    }
    
    private NetworkAuthService(Context context) {
        this.context = context.getApplicationContext();
        this.httpClient = new OkHttpClient();
        this.executorService = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.networkConfig = NetworkConfig.getInstance(context);
    }
    
    public static synchronized NetworkAuthService getInstance(Context context) {
        if (instance == null) {
            instance = new NetworkAuthService(context);
        }
        return instance;
    }
    
    /**
     * 用户登录
     * @param username 用户名
     * @param password 密码
     * @param callback 回调接口
     */
    public void login(String username, String password, AuthCallback callback) {
        executorService.execute(() -> {
            try {
                LoginResponse response = performLogin(username, password);
                mainHandler.post(() -> callback.onSuccess(response.getUserInfo(), response.getMessage()));
            } catch (Exception e) {
                String errorMessage = e.getMessage();
                mainHandler.post(() -> callback.onFailure(errorMessage));
            }
        });
    }
    
    /**
     * NFC登录
     * @param cardNumber NFC卡号
     * @param deviceIp 设备IP地址
     * @param callback 回调接口
     */
    public void loginByNFC(String cardNumber, String deviceIp, AuthCallback callback) {
        executorService.execute(() -> {
            try {
                LoginResponse response = performNFCLogin(cardNumber, deviceIp);
                mainHandler.post(() -> callback.onSuccess(response.getUserInfo(), response.getMessage()));
            } catch (Exception e) {
                String errorMessage = e.getMessage();
                mainHandler.post(() -> callback.onFailure(errorMessage));
            }
        });
    }
    
    /**
     * 执行登录请求
     * @param username 用户名
     * @param password 密码
     * @return 登录响应对象
     * @throws Exception 登录失败异常
     */
    private LoginResponse performLogin(String username, String password) throws Exception {
        try {
            // 构建请求体
            JSONObject requestBody = new JSONObject();
            requestBody.put("userName", username);
            requestBody.put("password", password);
            
            RequestBody body = RequestBody.create(requestBody.toString(), JSON);
            
            // 构建请求
            String loginUrl = networkConfig.getFullUrl(ApiEndpoint.LOGIN);
            Request request = new Request.Builder()
                    .url(loginUrl)
                    .post(body)
                    .addHeader("Content-Type", "application/json; charset=UTF-8")
                    .addHeader("Accept-Language", "zh-CN,zh;q=0.9")
                    .build();
            
            Log.d(TAG, "发送登录请求: " + username);
            
            // 执行请求（无论HTTP状态码如何，尽量解析Body以提取服务端message）
            Response response = httpClient.newCall(request).execute();
            String responseBody = response.body() != null ? response.body().string() : "";
            Log.d(TAG, "登录响应: " + responseBody);
            
            // 解析响应
            return parseLoginResponse(responseBody, username, response.code());
            
        } catch (IOException e) {
            Log.e(TAG, "网络请求失败", e);
            throw new Exception("网络连接失败，请检查网络设置");
        } catch (Exception e) {
            Log.e(TAG, "登录处理失败", e);
            throw e;
        }
    }
    
    /**
     * 执行NFC登录请求
     * @param cardNumber NFC卡号
     * @param deviceIp 设备IP地址
     * @return 登录响应对象
     * @throws Exception 登录失败异常
     */
    private LoginResponse performNFCLogin(String cardNumber, String deviceIp) throws Exception {
        try {
            // 构建请求体
            JSONObject requestBody = new JSONObject();
            requestBody.put("cardNumber", cardNumber);
            requestBody.put("IP", deviceIp);
            
            RequestBody body = RequestBody.create(requestBody.toString(), JSON);
            
            // 构建请求
            String nfcLoginUrl = networkConfig.getFullUrl(ApiEndpoint.NFC_LOGIN);
            Request request = new Request.Builder()
                    .url(nfcLoginUrl)
                    .post(body)
                    .addHeader("Content-Type", "application/json; charset=UTF-8")
                    .addHeader("Accept-Language", "zh-CN,zh;q=0.9")
                    .build();
            
            Log.d(TAG, "发送NFC登录请求: " + cardNumber);
            
            // 执行请求（无论HTTP状态码如何，尽量解析Body以提取服务端message）
            Response response = httpClient.newCall(request).execute();
            String responseBody = response.body() != null ? response.body().string() : "";
            Log.d(TAG, "NFC登录响应: " + responseBody);
            
            // 解析响应
            return parseLoginResponse(responseBody, cardNumber, response.code());
            
        } catch (IOException e) {
            Log.e(TAG, "网络请求失败", e);
            throw new Exception("网络连接失败，请检查网络设置");
        } catch (Exception e) {
            Log.e(TAG, "NFC登录处理失败", e);
            throw e;
        }
    }
    
    /**
     * 解析登录响应
     * @param responseBody 响应体
     * @param username 用户名
     * @return 登录响应对象，包含用户信息和成功消息
     * @throws Exception 解析失败异常
     */
    private LoginResponse parseLoginResponse(String responseBody, String username, int httpCode) throws Exception {
        try {
            JSONObject jsonResponse = new JSONObject(responseBody);

            // 读取状态与消息
            int status = jsonResponse.optInt("status", jsonResponse.optInt("code", -1));
            String message = jsonResponse.optString("message", jsonResponse.optString("msg", ""));

            // 判断成功：status为0或200，或data内包含有效token/sid
            JSONObject data = jsonResponse.optJSONObject("data");
            String tokenInData = data != null ? data.optString("token", null) : null;
            String sidInData = data != null ? data.optString("sid", null) : null;
            boolean tokenOk = tokenInData != null && !tokenInData.isEmpty();
            boolean statusOk = (status == 0 || status == 200 || status == 201);

            if (!statusOk && !tokenOk) {
                // 失败：优先展示服务端message；若为空，用HTTP码推断中文兜底
                String friendly = buildFriendlyErrorMessage(httpCode, status, message);
                throw new Exception(friendly);
            }

            // 正常解析数据
            if (data == null) {
                throw new Exception("登录失败：响应缺少data");
            }

            String token = data.optString("token", null);
            String sessionId = data.optString("sid", null);
            if (token == null || token.isEmpty() || sessionId == null || sessionId.isEmpty()) {
                throw new Exception("登录失败：响应缺少token或sid");
            }

            JSONObject user = data.optJSONObject("user");
            if (user == null) {
                throw new Exception("登录失败：响应缺少user信息");
            }

            String userId = String.valueOf(user.optInt("id", 0));
            String realName = user.optString("name", username);
            String department = user.optString("deptName", "");
            String userRole = user.optString("role", "USER");

            String successMessage = message != null && !message.isEmpty() ? message : jsonResponse.optString("message", "登录成功");

            UserInfo userInfo = UserInfo.createFromApiResponse(
                    userId, username, realName, department, userRole, token, sessionId);

            Log.d(TAG, "登录成功: " + userInfo);
            return new LoginResponse(userInfo, successMessage);

        } catch (org.json.JSONException je) {
            Log.e(TAG, "登录响应JSON解析失败", je);
            // 尝试直接从原始响应中提取message
            try {
                JSONObject err = new JSONObject(responseBody);
                int status = err.optInt("status", err.optInt("code", httpCode));
                String message = err.optString("message", err.optString("msg", ""));
                String friendly = buildFriendlyErrorMessage(httpCode, status, message);
                throw new Exception(friendly);
            } catch (Exception ignore) {
                throw new Exception(buildFriendlyErrorMessage(httpCode, httpCode, ""));
            }
        }
    }

    private String buildFriendlyErrorMessage(int httpCode, int status, String message) {
        if (message != null && !message.trim().isEmpty()) {
            String ml = message.toLowerCase();
            if (ml.contains("token") || ml.contains("jwt") || ml.contains("bearer") || ml.contains("sid")) {
                return UIUtils.friendlyErrorMessage(new Exception(message));
            }
            return message;
        }
        int code = httpCode > 0 ? httpCode : status;
        if (code == 401) return "未登录或凭证无效/用户名或密码错误";
        if (code == 403) return "没有权限";
        if (code == 404) return "资源不存在";
        if (code >= 500) return "服务器异常，请稍后重试";
        if (code == 400) return "请求有误";
        return "请求失败";
    }
    
    /**
     * 登录响应对象，包含用户信息和消息
     */
    public static class LoginResponse {
        private UserInfo userInfo;
        private String message;
        
        public LoginResponse(UserInfo userInfo, String message) {
            this.userInfo = userInfo;
            this.message = message;
        }
        
        public UserInfo getUserInfo() {
            return userInfo;
        }
        
        public String getMessage() {
            return message;
        }
    }
    
    // 移除未使用的辅助方法，避免误用
    
    /**
     * 清理资源
     */
    public void cleanup() {
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}
