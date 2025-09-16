package com.pda.hcw;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 安全管理器
 * 负责异常登录检测和安全监控
 */
public class SecurityManager {
    private static final String TAG = "SecurityManager";
    private static SecurityManager instance;
    
    private Context context;
    private SharedPreferences prefs;
    private AuditLogger auditLogger;
    
    // 异常检测阈值
    private static final int MAX_FAILED_ATTEMPTS = 3;          // 5分钟内最大失败次数
    private static final int MAX_USER_SWITCHING = 10;         // 1小时内最大用户切换数
    private static final int MAX_DAILY_USER_SWITCHING = 20;   // 24小时内最大用户切换数
    
    // 时间窗口（毫秒）
    private static final long FAILED_ATTEMPT_WINDOW = 5 * 60 * 1000;        // 5分钟
    private static final long USER_SWITCHING_WINDOW = 60 * 60 * 1000;      // 1小时
    private static final long DAILY_WINDOW = 24 * 60 * 60 * 1000;          // 24小时

    private SecurityManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE);
        this.auditLogger = AuditLogger.getInstance(context);
    }

    public static synchronized SecurityManager getInstance(Context context) {
        if (instance == null) {
            instance = new SecurityManager(context.getApplicationContext());
        }
        return instance;
    }

    /**
     * 安全检测结果
     */
    public static class SecurityResult {
        private boolean isAbnormal;
        private String reason;
        private int riskLevel; // 1-低风险, 2-中风险, 3-高风险
        
        public SecurityResult(boolean isAbnormal, String reason, int riskLevel) {
            this.isAbnormal = isAbnormal;
            this.reason = reason;
            this.riskLevel = riskLevel;
        }
        
        public boolean isAbnormal() { return isAbnormal; }
        public String getReason() { return reason; }
        public int getRiskLevel() { return riskLevel; }
    }

    /**
     * 检查登录安全性
     * @param userId 用户ID
     * @param deviceId 设备ID
     * @return 安全检测结果
     */
    public SecurityResult checkLoginSecurity(String userId, String deviceId) {
        Log.d(TAG, "检查登录安全性: " + userId);
        
        // 检查频率异常
        SecurityResult frequencyResult = checkLoginFrequency(deviceId);
        if (frequencyResult.isAbnormal()) {
            return frequencyResult;
        }
        
        // 检查用户切换异常
        SecurityResult userSwitchingResult = checkUserSwitching(deviceId);
        if (userSwitchingResult.isAbnormal()) {
            return userSwitchingResult;
        }
        
        // 检查用户状态
        SecurityResult userStatusResult = checkUserStatus(userId);
        if (userStatusResult.isAbnormal()) {
            return userStatusResult;
        }
        
        return new SecurityResult(false, "登录正常", 1);
    }

    /**
     * 检查登录频率异常
     * @param deviceId 设备ID
     * @return 安全检测结果
     */
    private SecurityResult checkLoginFrequency(String deviceId) {
        try {
            // 获取最近的失败登录记录
            long cutoffTime = System.currentTimeMillis() - FAILED_ATTEMPT_WINDOW;
            List<LoginRecord> failedLogins = getRecentFailedLogins(deviceId, cutoffTime);
            
            if (failedLogins.size() >= MAX_FAILED_ATTEMPTS) {
                String reason = String.format("登录失败次数过多（%d分钟内：%d次，阈值：%d）",
                    FAILED_ATTEMPT_WINDOW / 1000 / 60, failedLogins.size(), MAX_FAILED_ATTEMPTS);
                Log.w(TAG, "登录频率异常");
                AuditLogger.logSecurityEvent("system", "FREQUENCY_ANOMALY", reason);
                
                return new SecurityResult(true, reason, 3);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "检查登录频率异常失败", e);
        }
        
        return new SecurityResult(false, "频率正常", 1);
    }

    /**
     * 检查用户切换异常
     * @param deviceId 设备ID
     * @return 安全检测结果
     */
    private SecurityResult checkUserSwitching(String deviceId) {
        try {
            // 检查1小时内的用户切换
            long hourCutoff = System.currentTimeMillis() - USER_SWITCHING_WINDOW;
            List<String> hourlyUsers = getRecentUniqueUsers(deviceId, hourCutoff);
            
            if (hourlyUsers.size() >= MAX_USER_SWITCHING) {
                String reason = String.format("1小时内用户切换次数过多（%d个，阈值：%d）",
                    hourlyUsers.size(), MAX_USER_SWITCHING);
                Log.w(TAG, "用户切换异常（1小时内）");
                AuditLogger.logSecurityEvent("system", "USER_SWITCHING_ANOMALY", reason);
                
                return new SecurityResult(true, reason, 2);
            }
            
            // 检查24小时内的用户切换
            long dailyCutoff = System.currentTimeMillis() - DAILY_WINDOW;
            List<String> dailyUsers = getRecentUniqueUsers(deviceId, dailyCutoff);
            
            if (dailyUsers.size() >= MAX_DAILY_USER_SWITCHING) {
                String reason = String.format("24小时内用户切换次数过多（%d个，阈值：%d）",
                    dailyUsers.size(), MAX_DAILY_USER_SWITCHING);
                Log.w(TAG, "用户切换异常（24小时内）");
                AuditLogger.logSecurityEvent("system", "EXCESSIVE_USER_SWITCHING", reason);
                
                return new SecurityResult(true, reason, 3);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "检查用户切换异常失败", e);
        }
        
        return new SecurityResult(false, "用户切换正常", 1);
    }

    /**
     * 检查用户状态
     * @param userId 用户ID
     * @return 安全检测结果
     */
    private SecurityResult checkUserStatus(String userId) {
        try {
            // 检查用户是否被锁定
            if (isUserLocked(userId)) {
                String reason = "用户已被锁定";
                Log.w(TAG, "用户状态异常（锁定）");
                return new SecurityResult(true, reason, 3);
            }
            
            // 检查用户是否活跃
            if (!isUserActive(userId)) {
                String reason = "用户已被禁用";
                Log.w(TAG, "用户状态异常（禁用）");
                return new SecurityResult(true, reason, 2);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "检查用户状态失败", e);
        }
        
        return new SecurityResult(false, "用户状态正常", 1);
    }

    /**
     * 记录登录成功
     * @param userId 用户ID
     * @param deviceId 设备ID
     */
    public void recordLoginSuccess(String userId, String deviceId) {
        try {
            // 清除失败记录
            clearFailedLoginAttempts(deviceId);
            
            // 记录成功登录
            recordSuccessfulLogin(userId, deviceId);
            
            Log.d(TAG, "记录登录成功");
            
        } catch (Exception e) {
            Log.e(TAG, "记录登录成功失败", e);
        }
    }

    /**
     * 记录登录失败
     * @param userId 用户ID
     * @param deviceId 设备ID
     */
    public void recordLoginFailure(String userId, String deviceId) {
        try {
            // 记录失败登录
            recordFailedLogin(userId, deviceId);
            
            // 检查是否需要锁定设备
            if (shouldLockDevice(deviceId)) {
                lockDevice(deviceId);
                AuditLogger.logSecurityEvent("system", "DEVICE_LOCKED", 
                    "设备 " + deviceId + " 因多次登录失败被锁定");
            }
            
            Log.d(TAG, "记录登录失败");
            
        } catch (Exception e) {
            Log.e(TAG, "记录登录失败失败", e);
        }
    }

    /**
     * 记录成功登录
     * @param userId 用户ID
     * @param deviceId 设备ID
     */
    private void recordSuccessfulLogin(String userId, String deviceId) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("last_success_user_" + deviceId, userId);
        editor.putLong("last_success_time_" + deviceId, System.currentTimeMillis());
        // 记录成功登录历史，用于用户切换检测
        try {
            String key = "success_attempts_" + deviceId;
            long now = System.currentTimeMillis();
            String existing = prefs.getString(key, "");
            String newRec = userId + "," + now;
            if (existing == null || existing.isEmpty()) existing = newRec; else existing += ";" + newRec;
            // 可选：简单限长，避免无限增长（仅保留最近100条）
            String[] parts = existing.split(";");
            if (parts.length > 100) {
                StringBuilder sb = new StringBuilder();
                for (int i = parts.length - 100; i < parts.length; i++) {
                    if (sb.length() > 0) sb.append(';');
                    sb.append(parts[i]);
                }
                existing = sb.toString();
            }
            editor.putString(key, existing);
        } catch (Exception ignore) {}
        editor.apply();
    }

    /**
     * 记录失败登录
     * @param userId 用户ID
     * @param deviceId 设备ID
     */
    private void recordFailedLogin(String userId, String deviceId) {
        String key = "failed_attempts_" + deviceId;
        long currentTime = System.currentTimeMillis();
        
        // 获取现有失败记录
        String existingData = prefs.getString(key, "");
        String newRecord = userId + "," + currentTime;
        
        if (existingData.isEmpty()) {
            existingData = newRecord;
        } else {
            existingData += ";" + newRecord;
        }
        
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(key, existingData);
        editor.apply();
    }

    /**
     * 清除失败登录记录
     * @param deviceId 设备ID
     */
    private void clearFailedLoginAttempts(String deviceId) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove("failed_attempts_" + deviceId);
        editor.apply();
    }

    /**
     * 检查是否应该锁定设备
     * @param deviceId 设备ID
     * @return 是否应该锁定
     */
    private boolean shouldLockDevice(String deviceId) {
        List<LoginRecord> failedLogins = getRecentFailedLogins(deviceId, 
            System.currentTimeMillis() - FAILED_ATTEMPT_WINDOW);
        return failedLogins.size() >= MAX_FAILED_ATTEMPTS;
    }

    /**
     * 锁定设备
     * @param deviceId 设备ID
     */
    private void lockDevice(String deviceId) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("device_locked_" + deviceId, true);
        editor.putLong("lock_time_" + deviceId, System.currentTimeMillis());
        editor.apply();
        
        Log.w(TAG, "设备已锁定: " + deviceId);
    }

    /**
     * 解锁设备
     * @param deviceId 设备ID
     */
    public void unlockDevice(String deviceId) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove("device_locked_" + deviceId);
        editor.remove("lock_time_" + deviceId);
        editor.apply();
        
        Log.d(TAG, "设备已解锁: " + deviceId);
    }

    /**
     * 检查设备是否被锁定
     * @param deviceId 设备ID
     * @return 是否被锁定
     */
    public boolean isDeviceLocked(String deviceId) {
        return prefs.getBoolean("device_locked_" + deviceId, false);
    }

    /**
     * 检查用户是否被锁定
     * @param userId 用户ID
     * @return 是否被锁定
     */
    private boolean isUserLocked(String userId) {
        return prefs.getBoolean("user_locked_" + userId, false);
    }

    /**
     * 检查用户是否活跃
     * @param userId 用户ID
     * @return 是否活跃
     */
    private boolean isUserActive(String userId) {
        // 这里应该从主业务系统获取用户状态
        // 暂时返回true，实际项目中需要调用API
        return true;
    }

    /**
     * 获取最近的失败登录记录
     * @param deviceId 设备ID
     * @param cutoffTime 截止时间
     * @return 失败登录记录列表
     */
    private List<LoginRecord> getRecentFailedLogins(String deviceId, long cutoffTime) {
        List<LoginRecord> records = new ArrayList<>();
        String key = "failed_attempts_" + deviceId;
        String data = prefs.getString(key, "");
        
        if (!data.isEmpty()) {
            String[] entries = data.split(";");
            for (String entry : entries) {
                String[] parts = entry.split(",");
                if (parts.length == 2) {
                    long timestamp = Long.parseLong(parts[1]);
                    if (timestamp >= cutoffTime) {
                        records.add(new LoginRecord(parts[0], timestamp));
                    }
                }
            }
        }
        
        return records;
    }

    /**
     * 获取最近的唯一用户列表
     * @param deviceId 设备ID
     * @param cutoffTime 截止时间
     * @return 唯一用户列表
     */
    private List<String> getRecentUniqueUsers(String deviceId, long cutoffTime) {
        List<String> users = new ArrayList<>();
        // 合并成功与失败的尝试，以更真实反映切换
        String[] keys = new String[]{"success_attempts_" + deviceId, "failed_attempts_" + deviceId};
        for (String key : keys) {
            String data = prefs.getString(key, "");
            if (data != null && !data.isEmpty()) {
                String[] entries = data.split(";");
                for (String entry : entries) {
                    String[] parts = entry.split(",");
                    if (parts.length == 2) {
                        try {
                            long timestamp = Long.parseLong(parts[1]);
                            if (timestamp >= cutoffTime) {
                                String u = parts[0];
                                if (!users.contains(u)) users.add(u);
                            }
                        } catch (Exception ignore) {}
                    }
                }
            }
        }
        return users;
    }

    /**
     * 登录记录类
     */
    private static class LoginRecord {
        String userId;
        long timestamp;
        
        public LoginRecord(String userId, long timestamp) {
            this.userId = userId;
            this.timestamp = timestamp;
        }
    }

    /**
     * 获取安全统计信息
     * @return 统计信息
     */
    public String getSecurityStatistics() {
        try {
            StringBuilder stats = new StringBuilder();
            stats.append("=== 安全统计 ===\n");
            
            // 获取当前设备状态
            String deviceId = SessionManager.getInstance(context).getDeviceId();
            boolean isLocked = isDeviceLocked(deviceId);
            stats.append("设备状态: ").append(isLocked ? "已锁定" : "正常").append("\n");
            
            // 获取最近的失败登录次数
            List<LoginRecord> recentFailures = getRecentFailedLogins(deviceId, 
                System.currentTimeMillis() - FAILED_ATTEMPT_WINDOW);
            stats.append("最近失败登录: ").append(recentFailures.size()).append("次\n");
            
            // 获取用户切换次数
            List<String> recentUsers = getRecentUniqueUsers(deviceId, 
                System.currentTimeMillis() - USER_SWITCHING_WINDOW);
            stats.append("最近用户切换: ").append(recentUsers.size()).append("个\n");
            
            return stats.toString();
            
        } catch (Exception e) {
            Log.e(TAG, "获取安全统计失败", e);
            return "获取安全统计失败";
        }
    }

    /**
     * 重置安全状态（用于测试或管理员操作）
     * @param deviceId 设备ID
     */
    public void resetSecurityStatus(String deviceId) {
        try {
            // 解锁设备
            unlockDevice(deviceId);
            
            // 清除失败记录
            clearFailedLoginAttempts(deviceId);
            
            Log.d(TAG, "重置安全状态: " + deviceId);
            
        } catch (Exception e) {
            Log.e(TAG, "重置安全状态失败", e);
        }
    }
}
