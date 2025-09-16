package com.pda.hcw.rfid;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 参数管理类（完全按照厂家demo的SharedUtil实现）
 */
public class SharedUtil {
    private static final String PREF_NAME = "uhf_settings";
    
    private SharedPreferences sharedPreferences;
    private SharedPreferences.Editor editor;
    
    public SharedUtil(Context context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = sharedPreferences.edit();
    }
    
    /**
     * 获取功率设置
     * @return 功率值 (dBm)
     */
    public int getPower() {
        return sharedPreferences.getInt("power", 30); // 修改默认功率为30dBm
    }
    
    /**
     * 设置功率
     * @param power 功率值
     */
    public void setPower(int power) {
        editor.putInt("power", power);
        editor.apply();
    }
    
    /**
     * 获取工作频段
     * @return 频段索引 (0=CA, 1=FCC, 2=EU)
     */
    public int getWorkFreq() {
        return sharedPreferences.getInt("freRegion", 1); // 默认FCC
    }
    
    /**
     * 设置工作频段
     * @param freq 频段索引
     */
    public void setWorkFreq(int freq) {
        editor.putInt("freRegion", freq);
        editor.apply();
    }
    
    /**
     * 获取Session参数
     * @return Session值 (0-3)
     */
    public int getSession() {
        return sharedPreferences.getInt("session", 3); // 修改默认Session为3（多标签模式）
    }
    
    /**
     * 设置Session参数
     * @param session Session值
     */
    public void setSession(int session) {
        editor.putInt("session", session);
        editor.apply();
    }
    
    /**
     * 获取Target参数
     * @return Target值 (0-1)
     */
    public int getTarget() {
        return sharedPreferences.getInt("target", 1); // 修改默认Target为1（优化多标签性能）
    }
    
    /**
     * 设置Target参数
     * @param target Target值
     */
    public void setTarget(int target) {
        editor.putInt("target", target);
        editor.apply();
    }
    
    /**
     * 获取Q值参数
     * @return Q值 (0-15)
     */
    public int getQvalue() {
        return sharedPreferences.getInt("qvalue", 1); // 修改默认Q值为1（优化多标签性能）
    }
    
    /**
     * 设置Q值参数
     * @param qvalue Q值
     */
    public void setQvalue(int qvalue) {
        editor.putInt("qvalue", qvalue);
        editor.apply();
    }
    
    /**
     * 获取FastID设置
     * @return 是否启用FastID
     */
    public boolean getFastId() {
        return sharedPreferences.getBoolean("fastid", false); // 默认关闭FastID
    }
    
    /**
     * 设置FastID
     * @param fastId 是否启用
     */
    public void setFastId(boolean fastId) {
        editor.putBoolean("fastid", fastId);
        editor.apply();
    }
    
    /**
     * 应用默认参数设置
     * 确保每次调用RFID盘存时都使用标准化的默认参数
     */
    public void applyDefaultSettings() {
        editor.putInt("power", 30);          // 默认功率30dBm
        editor.putInt("freRegion", 1);        // 默认频段：美国(FCC)
        editor.putInt("session", 3);         // 默认Session：多标签模式
        editor.putInt("target", 1);          // 默认Target：优化多标签性能
        editor.putInt("qvalue", 1);          // 默认Q值：优化多标签性能
        editor.putBoolean("fastid", false);   // 默认关闭FastID
        editor.apply();
    }
    
    /**
     * 检查是否需要应用默认设置
     * @return 是否需要应用默认设置
     */
    public boolean needsDefaultSettings() {
        // 是否与默认值不一致（默认功率30、FCC、S3、T1、Q1、FastID=false）
        return getPower() != 30 ||
               getWorkFreq() != 1 ||
               getSession() != 3 ||
               getTarget() != 1 ||
               getQvalue() != 1 ||
               getFastId(); // true 表示与默认(false)不一致
    }
}
