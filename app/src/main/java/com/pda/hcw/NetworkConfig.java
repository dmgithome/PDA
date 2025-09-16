package com.pda.hcw;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * 网络配置类
 * 用于管理API服务器地址等网络配置
 */
public class NetworkConfig {
    private static final String TAG = "NetworkConfig";
    private static final String PREFS_NAME = "network_prefs";
    private static final String KEY_API_BASE_URL = "api_base_url";
    private static final String DEFAULT_API_BASE_URL = "http://your-api-server.com";
    private static final String KEY_ENDPOINT_PREFIX = "endpoint_";
    
    private final Context context;
    private final SharedPreferences prefs;
    
    // 单例模式
    private static NetworkConfig instance;
    
    public static NetworkConfig getInstance(Context context) {
        if (instance == null) {
            instance = new NetworkConfig(context.getApplicationContext());
        }
        return instance;
    }
    
    private NetworkConfig(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    /**
     * 获取API基础URL
     * @return API基础URL
     */
    public String getApiBaseUrl() {
        return prefs.getString(KEY_API_BASE_URL, DEFAULT_API_BASE_URL);
    }
    
    /**
     * 设置API基础URL
     * @param baseUrl API基础URL
     */
    public void setApiBaseUrl(String baseUrl) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_API_BASE_URL, baseUrl);
        editor.apply();
        
        Log.d(TAG, "API基础URL已设置为: " + baseUrl);
    }
    
    /**
     * 重置为默认配置
     */
    public void resetToDefault() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove(KEY_API_BASE_URL);
        editor.apply();
        
        Log.d(TAG, "网络配置已重置为默认值");
    }

    /**
     * 重置所有端点为默认路径
     */
    public void resetEndpointsToDefault() {
        SharedPreferences.Editor editor = prefs.edit();
        for (ApiEndpoint ep : ApiEndpoint.values()) {
            editor.remove(KEY_ENDPOINT_PREFIX + ep.name().toLowerCase());
        }
        editor.apply();
        Log.d(TAG, "所有API端点已重置为默认");
    }

    /**
     * 获取完整的API URL
     * @param endpoint API端点
     * @return 完整的API URL
     */
    public String getFullApiUrl(String endpoint) {
        String baseUrl = getApiBaseUrl();
        
        // 确保baseUrl不以/结尾，endpoint不以/开头
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        
        if (endpoint.startsWith("/")) {
            endpoint = endpoint.substring(1);
        }
        
        return baseUrl + "/" + endpoint;
    }

    /**
     * 获取端点路径（带覆盖，默认来自Apifox定义）。
     */
    public String getEndpointPath(ApiEndpoint endpoint) {
        String key = KEY_ENDPOINT_PREFIX + endpoint.name().toLowerCase();
        String val = prefs.getString(key, null);
        if (val == null || val.trim().isEmpty()) {
            return endpoint.getDefaultPath();
        }
        return sanitizeEndpoint(val);
    }

    /**
     * 设置端点路径（仅保存相对路径）。
     */
    public void setEndpointPath(ApiEndpoint endpoint, String path) {
        String key = KEY_ENDPOINT_PREFIX + endpoint.name().toLowerCase();
        String sanitized = sanitizeEndpoint(path);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(key, sanitized);
        editor.apply();
        Log.d(TAG, "端点更新 " + endpoint.name() + ": " + sanitized);
    }

    /**
     * 根据端点枚举获取完整URL。
     */
    public String getFullUrl(ApiEndpoint endpoint) {
        return getFullApiUrl(getEndpointPath(endpoint));
    }

    private String sanitizeEndpoint(String endpoint) {
        if (endpoint == null) return "/";
        String p = endpoint.trim();
        // 去掉scheme和host（如误填入完整URL）
        try {
            if (p.startsWith("http://") || p.startsWith("https://")) {
                int idx = p.indexOf('/', p.indexOf("//") + 2);
                if (idx >= 0) p = p.substring(idx);
                else p = "/"; // 只有域名时
            }
        } catch (Exception ignore) {}
        if (!p.startsWith("/")) p = "/" + p;
        // 去除多余的尾部斜杠（保留根路径"/")
        if (p.length() > 1 && p.endsWith("/")) p = p.substring(0, p.length() - 1);
        return p;
    }
    
    /**
     * 检查网络配置是否有效
     * @return 是否有效
     */
    public boolean isValidConfig() {
        String baseUrl = getApiBaseUrl();
        return baseUrl != null && !baseUrl.trim().isEmpty() && 
               !baseUrl.equals(DEFAULT_API_BASE_URL);
    }
    
    /**
     * 获取配置状态描述
     * @return 状态描述
     */
    public String getConfigStatus() {
        if (isValidConfig()) {
            return "已配置: " + getApiBaseUrl();
        } else {
            return "未配置 (使用默认地址)";
        }
    }
}
