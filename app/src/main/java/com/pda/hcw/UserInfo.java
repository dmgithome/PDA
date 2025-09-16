package com.pda.hcw;

/**
 * 用户信息类
 * 从接口获取的用户信息结构
 */
public class UserInfo {
    private String userId;           // 用户唯一标识
    private String username;         // 登录账号
    private String realName;         // 真实姓名
    private String department;       // 所属部门
    private UserRole role;           // 用户角色
    private String token;            // 认证令牌
    private String sessionId;        // 会话ID
    private String nfcCardId;        // NFC卡ID
    private boolean isActive;        // 是否启用
    private long lastLoginTime;      // 最后登录时间
    private boolean isBackdoorAccount; // 是否为后门账户
    private String email;            // 邮箱
    private String phone;            // 电话
    private int status;              // 状态

    // 构造函数
    public UserInfo() {
        this.isActive = true;
        this.lastLoginTime = System.currentTimeMillis();
    }

    public UserInfo(String userId, String username, String realName, UserRole role) {
        this();
        this.userId = userId;
        this.username = username;
        this.realName = realName;
        this.role = role;
    }

    public UserInfo(String userId, String username, String realName, UserRole role, String token, String sessionId) {
        this(userId, username, realName, role);
        this.token = token;
        this.sessionId = sessionId;
    }

    // Getter和Setter方法
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

  
    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public long getLastLoginTime() {
        return lastLoginTime;
    }

    public void setLastLoginTime(long lastLoginTime) {
        this.lastLoginTime = lastLoginTime;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public void setNfcCardId(String nfcCardId) {
        this.nfcCardId = nfcCardId;
    }

    public String getNfcCardId() {
        return nfcCardId;
    }

    public boolean isBackdoorAccount() {
        return isBackdoorAccount;
    }

    public void setBackdoorAccount(boolean backdoorAccount) {
        isBackdoorAccount = backdoorAccount;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    /**
     * 检查用户是否有指定权限
     * @param permission 权限标识
     * @return 是否有权限
     */
    public boolean hasPermission(String permission) {
        return role != null && role.hasPermission(permission);
    }

    /**
     * 检查用户是否有效
     * @return 是否有效
     */
    public boolean isValid() {
        return userId != null && !userId.isEmpty() && 
               username != null && !username.isEmpty() && 
               role != null && isActive;
    }

    /**
     * 获取用户显示名称
     * @return 显示名称（真实姓名或用户名）
     */
    public String getDisplayName() {
        return realName != null && !realName.isEmpty() ? realName : username;
    }

    /**
     * 获取用户完整信息字符串
     * @return 完整信息
     */
    @Override
    public String toString() {
        return "UserInfo{" +
                "userId='" + userId + '\'' +
                ", username='" + username + '\'' +
                ", realName='" + realName + '\'' +
                ", department='" + department + '\'' +
                ", role=" + role +
                ", token='" + maskSecret(token) + '\'' +
                ", sessionId='" + maskSecret(sessionId) + '\'' +
                ", isActive=" + isActive +
                ", lastLoginTime=" + lastLoginTime +
                '}';
    }

    // 避免在日志中输出完整敏感信息
    private static String maskSecret(String secret) {
        if (secret == null || secret.isEmpty()) return "";
        int keep = Math.min(4, secret.length());
        String tail = secret.substring(secret.length() - keep);
        return "****" + tail;
    }

    /**
     * 创建用户信息（基于接口返回数据）
     * @param userId 用户ID
     * @param username 用户名
     * @param realName 真实姓名
     * @param department 部门
     * @param userRole 用户角色（从接口获取）
     * @param token 认证令牌
     * @param sessionId 会话ID
     * @return 用户信息对象
     */
    public static UserInfo createFromApiResponse(String userId, String username, String realName, String department, String userRole, String token, String sessionId) {
        // 根据接口返回的角色信息创建角色对象
        UserRole role = parseUserRole(userRole);
        
        UserInfo user = new UserInfo();
        user.setUserId(userId);
        user.setUsername(username);
        user.setRealName(realName);
        user.setDepartment(department);
        user.setRole(role);
        user.setToken(token);
        user.setSessionId(sessionId);
        user.setActive(true);
        return user;
    }
    
    /**
     * 保持原有方法兼容性
     */
    public static UserInfo createFromApiResponse(String userId, String username, String realName, String department, String token, String sessionId) {
        return createFromApiResponse(userId, username, realName, department, "USER", token, sessionId);
    }
    
    /**
     * 解析用户角色
     * @param userRole 角色字符串
     * @return 用户角色枚举
     */
    private static UserRole parseUserRole(String userRole) {
        if (userRole == null || userRole.isEmpty()) {
            return UserRole.USER;
        }
        
        // 转换为大写进行比较
        String roleUpper = userRole.toUpperCase();
        
        if (roleUpper.contains("ADMIN") || roleUpper.contains("管理员") || roleUpper.contains("SUPER")) {
            return UserRole.ADMIN;
        } else {
            return UserRole.USER;
        }
    }

    /**
     * 检查是否为管理员
     * @return 是否为管理员
     */
    public boolean isAdmin() {
        return role != null && role.isAdmin();
    }

    /**
     * 检查是否为普通用户
     * @return 是否为普通用户
     */
    public boolean isRegularUser() {
        return role != null && role.isRegularUser();
    }
}
