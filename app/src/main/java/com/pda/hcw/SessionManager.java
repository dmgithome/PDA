package com.pda.hcw;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 会话管理器
 * 负责管理用户会话生命周期，实现自动登出功能
 */
public class SessionManager {
    private static final String TAG = "SessionManager";
    private static SessionManager instance;
    
    private SharedPreferences prefs;
    private Context context;
    private Handler timeoutHandler;
    private Runnable timeoutRunnable;
    private Runnable warningRunnable;
    private CheckoutRecordDatabase database;
    
    // 会话状态
    private boolean isActive = false;
    private UserInfo currentUser;
    private long lastActivityTime;
    private long timeoutDuration = 15 * 60 * 1000; // 默认15分钟
    private static final long WARNING_OFFSET_MS = 60 * 1000; // 超时前60秒预警
    private String deviceId;
    private SimpleDateFormat dateFormat;
    
    // 回调接口
    public interface SessionListener {
        void onSessionTimeout();
        void onSessionWarning(long remainingTime);
        void onSessionDestroyed();
    }
    
    private SessionListener listener;

    private SessionManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences("session_prefs", Context.MODE_PRIVATE);
        this.timeoutHandler = new Handler(Looper.getMainLooper());
        this.database = CheckoutRecordDatabase.getInstance(context);
        this.deviceId = android.provider.Settings.Secure.getString(
            context.getContentResolver(), 
            android.provider.Settings.Secure.ANDROID_ID
        );
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        loadSettings();
    }

    public static synchronized SessionManager getInstance(Context context) {
        if (instance == null) {
            instance = new SessionManager(context.getApplicationContext());
        }
        return instance;
    }

    /**
     * 加载设置
     */
    private void loadSettings() {
        // 初始化读取一次设置，避免频繁 reload
        SharedPreferences settingsPrefs = context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE);
        timeoutDuration = settingsPrefs.getLong("session_timeout_ms", 15 * 60 * 1000);
        Log.d(TAG, "加载会话超时设置: " + (timeoutDuration / 1000 / 60) + "分钟");
    }

    /**
     * 开始会话
     * @param user 用户信息
     */
    public void startSession(UserInfo user) {
        Log.d(TAG, "开始会话: " + user.getUsername());
        
        this.currentUser = user;
        this.isActive = true;
        this.lastActivityTime = System.currentTimeMillis();
        
        // 保存会话信息
        saveSessionInfo();
        
        // 启动超时检测
        startTimeoutTimer();
        
        // 记录登录日志到数据库
        recordUserLogin(user.getUserId(), "password");
    }

    /**
     * 结束会话
     * @param reason 结束原因
     */
    public void endSession(String reason) {
        Log.d(TAG, "结束会话: " + reason);
        
        if (currentUser != null) {
            // 记录登出日志到数据库
            recordUserLogout(currentUser.getUserId(), reason);
        }
        
        this.isActive = false;
        this.currentUser = null;
        
        // 停止超时检测
        stopTimeoutTimer();
        
        // 清除会话信息
        clearSessionInfo();
        
        // 通知监听器
        if (listener != null) {
            listener.onSessionDestroyed();
        }
    }

    /**
     * 更新用户活动时间
     */
    public void updateActivity() {
        if (isActive) {
            lastActivityTime = System.currentTimeMillis();
            Log.d(TAG, "更新用户活动时间");
            
            // 重启超时检测
            startTimeoutTimer();
        }
    }

    /**
     * 检查会话是否活跃
     * @return 是否活跃
     */
    public boolean isSessionActive() {
        return isActive && currentUser != null;
    }

    /**
     * 获取当前用户
     * @return 当前用户信息
     */
    public UserInfo getCurrentUser() {
        return currentUser;
    }

    /**
     * 获取会话超时时间
     * @return 超时时间（毫秒）
     */
    public long getTimeoutDuration() {
        return timeoutDuration;
    }

    /**
     * 设置会话超时时间
     * @param duration 超时时间（毫秒）
     */
    public void setTimeoutDuration(long duration) {
        this.timeoutDuration = duration;
        
        // 保存到 settings_prefs
        SharedPreferences settingsPrefs = context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor settingsEditor = settingsPrefs.edit();
        settingsEditor.putLong("session_timeout_ms", duration);
        settingsEditor.putInt("session_timeout_minutes", (int) (duration / (60 * 1000)));
        settingsEditor.apply();
        
        Log.d(TAG, "设置会话超时时间: " + (duration / 1000 / 60) + "分钟");
        
        // 重启超时检测
        if (isActive) {
            startTimeoutTimer();
        }
    }

    /**
     * 获取剩余时间
     * @return 剩余时间（毫秒）
     */
    public long getRemainingTime() {
        if (!isActive) return 0;
        long elapsed = System.currentTimeMillis() - lastActivityTime;
        return Math.max(0, timeoutDuration - elapsed);
    }

    /**
     * 启动超时检测
     */
    private void startTimeoutTimer() {
        stopTimeoutTimer();

        long remaining = getRemainingTime();
        if (remaining <= 0) {
            // 到点先判断是否命中前台白名单：命中则续期并重启计时
            if (isIdleExemptForeground()) {
                lastActivityTime = System.currentTimeMillis();
                Log.d(TAG, "会话超时命中白名单，已自动续期(立即)");
                startTimeoutTimer();
                return;
            }
            Log.d(TAG, "会话超时，自动登出");
            endSession("会话超时");
            if (listener != null) listener.onSessionTimeout();
            return;
        }

        // 预警回调（提前 WARNING_OFFSET_MS）
        long warnDelay = remaining - WARNING_OFFSET_MS;
        if (warnDelay > 0 && listener != null) {
            warningRunnable = new Runnable() {
                @Override public void run() {
                    long rem = getRemainingTime();
                    if (isActive && rem > 0) listener.onSessionWarning(rem);
                }
            };
            timeoutHandler.postDelayed(warningRunnable, warnDelay);
        }

        // 超时回调
        timeoutRunnable = new Runnable() {
            @Override public void run() {
                if (!isActive) return;
                // 到点先判断是否命中前台白名单：命中则续期并重启计时
                if (isIdleExemptForeground()) {
                    lastActivityTime = System.currentTimeMillis();
                    Log.d(TAG, "会话超时命中白名单，已自动续期");
                    startTimeoutTimer();
                    return;
                }
                Log.d(TAG, "会话超时，自动登出");
                endSession("会话超时");
                if (listener != null) listener.onSessionTimeout();
            }
        };
        timeoutHandler.postDelayed(timeoutRunnable, remaining);
        Log.d(TAG, "启动/重置会话超时检测，remaining=" + remaining + "ms");
    }

    /**
     * 是否当前处于前台且命中会话空闲豁免白名单
     */
    private boolean isIdleExemptForeground() {
        try {
            Context appCtx = context.getApplicationContext();
            if (!(appCtx instanceof MyApplication)) return false;
            MyApplication app = (MyApplication) appCtx;
            android.app.Activity a = app.getCurrentActivity();
            if (a == null) return false;

            android.content.SharedPreferences sp = context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE);
            boolean enabled = sp.getBoolean("idle_exempt_enabled", false);
            if (!enabled) return false;

            String csv = sp.getString("idle_exempt_modules", "");
            if (csv == null || csv.trim().isEmpty()) return false;

            String simple = a.getClass().getSimpleName();
            for (String s : csv.split(",")) {
                if (s != null && simple.equals(s.trim())) {
                    return true;
                }
            }
            return false;
        } catch (Throwable t) {
            Log.w(TAG, "isIdleExemptForeground check failed", t);
            return false;
        }
    }

    /**
     * 停止超时检测
     */
    private void stopTimeoutTimer() {
        if (timeoutRunnable != null) {
            timeoutHandler.removeCallbacks(timeoutRunnable);
            timeoutRunnable = null;
        }
        if (warningRunnable != null) {
            timeoutHandler.removeCallbacks(warningRunnable);
            warningRunnable = null;
        }
    }

    /**
     * 保存会话信息（简化版）
     */
    private void saveSessionInfo() {
        if (currentUser != null) {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean("session_active", true);
            editor.putString("current_user_id", currentUser.getUserId());
            editor.putString("current_username", currentUser.getUsername());
            editor.putString("current_user_role", currentUser.getRole().name());
            // 出于安全考虑，不持久化 token/session 到本地
            editor.putLong("session_start_time", System.currentTimeMillis());
            editor.putLong("last_activity_time", lastActivityTime);
            editor.apply();
        }
    }

    /**
     * 清除会话信息
     */
    private void clearSessionInfo() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove("session_active");
        editor.remove("current_user_id");
        editor.remove("current_username");
        editor.remove("current_user_role");
        editor.remove("current_user_token");
        editor.remove("current_user_session");
        editor.remove("session_start_time");
        editor.remove("last_activity_time");
        editor.apply();
    }

    /**
     * 恢复会话（简化版，不再恢复本地用户信息）
     * @return 是否恢复成功
     */
    public boolean restoreSession() {
        // 新架构：不再恢复本地用户信息，需要重新登录
        Log.d(TAG, "新架构：不支持会话恢复，需要重新登录");
        return false;
    }

    /**
     * 设置会话监听器
     * @param listener 监听器
     */
    public void setSessionListener(SessionListener listener) {
        this.listener = listener;
    }

    /**
     * 获取设备ID
     * @return 设备ID
     */
    public String getDeviceId() {
        return deviceId;
    }

    /**
     * 强制结束会话（用于安全检测）
     * @param reason 结束原因
     */
    public void forceEndSession(String reason) {
        Log.d(TAG, "强制结束会话: " + reason);
        endSession(reason);
    }

    /**
     * 记录用户登录操作
     * @param userId 用户ID
     * @param loginType 登录类型
     */
    private void recordUserLogin(String userId, String loginType) {
        try {
            long operationTime = System.currentTimeMillis();
            String userName = currentUser != null ? currentUser.getUsername() : userId;
            String deviceInfo = "Device ID: " + deviceId;
            
            database.recordUserOperation(
                userId,
                userName,
                "登录",
                "用户登录",
                loginType,
                deviceInfo,
                operationTime
            );
            
            Log.d(TAG, "已记录用户登录: " + userName + ", 类型: " + loginType);
        } catch (Exception e) {
            Log.e(TAG, "记录用户登录失败", e);
        }
    }

    /**
     * 记录用户登出操作
     * @param userId 用户ID
     * @param logoutReason 登出原因
     */
    private void recordUserLogout(String userId, String logoutReason) {
        try {
            long operationTime = System.currentTimeMillis();
            String userName = currentUser != null ? currentUser.getUsername() : userId;
            String deviceInfo = "Device ID: " + deviceId;
            
            database.recordUserOperation(
                userId,
                userName,
                "登出",
                logoutReason,
                "登出方式: " + logoutReason,
                deviceInfo,
                operationTime
            );
            
            Log.d(TAG, "已记录用户登出: " + userName + ", 原因: " + logoutReason);
        } catch (Exception e) {
            Log.e(TAG, "记录用户登出失败", e);
        }
    }

    /**
     * 记录用户功能使用
     * @param operationType 操作类型
     * @param moduleName 功能模块名称
     * @param operationDetails 操作详情
     */
    public void recordFunctionUsage(String operationType, String moduleName, String operationDetails) {
        if (currentUser == null || !isActive) {
            return;
        }

        try {
            long operationTime = System.currentTimeMillis();
            String deviceInfo = "Device ID: " + deviceId;
            
            database.recordUserOperation(
                currentUser.getUserId(),
                currentUser.getUsername(),
                operationType,
                moduleName,
                operationDetails,
                deviceInfo,
                operationTime
            );
            
            Log.d(TAG, "已记录功能使用: " + moduleName + ", 操作: " + operationType);
        } catch (Exception e) {
            Log.e(TAG, "记录功能使用失败", e);
        }
    }

    /**
     * 记录用户简单操作
     * @param operationType 操作类型
     * @param operationDetails 操作详情
     */
    public void recordSimpleOperation(String operationType, String operationDetails) {
        if (currentUser == null || !isActive) {
            return;
        }

        try {
            long operationTime = System.currentTimeMillis();
            String deviceInfo = "Device ID: " + deviceId;
            
            database.recordUserOperation(
                currentUser.getUserId(),
                currentUser.getUsername(),
                operationType,
                "简单操作",
                operationDetails,
                deviceInfo,
                operationTime
            );
            
            Log.d(TAG, "已记录简单操作: " + operationType + ", 详情: " + operationDetails);
        } catch (Exception e) {
            Log.e(TAG, "记录简单操作失败", e);
        }
    }
}
