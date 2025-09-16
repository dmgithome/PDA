package com.pda.hcw;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * 条码校验工具类
 * 用于校验条码格式和提供配置管理
 */
public class BarcodeValidator {
    private static final String TAG = "BarcodeValidator";
    private static final String PREFS_NAME = "settings_prefs";
    
    // 配置键名
    private static final String KEY_PREFIX = "barcode_prefix";
    private static final String KEY_LENGTH = "barcode_length";
    private static final String KEY_CABINET_ID = "cabinet_id";
    
    private final Context context;
    private final SharedPreferences prefs;
    
    // 单例模式
    private static BarcodeValidator instance;
    
    public static BarcodeValidator getInstance(Context context) {
        if (instance == null) {
            instance = new BarcodeValidator(context.getApplicationContext());
        }
        return instance;
    }
    
    private BarcodeValidator(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    /**
     * 校验条码格式
     * @param barcode 条码
     * @return 是否符合校验规则
     */
    public boolean validateBarcode(String barcode) {
        if (barcode == null || barcode.trim().isEmpty()) {
            Log.w(TAG, "条码为空");
            return false;
        }
        
        String trimmedBarcode = barcode.trim();
        
        // 获取校验规则
        String prefix = getBarcodePrefix();
        int length = getBarcodeLength();
        
        Log.d(TAG, "校验条码: '" + trimmedBarcode + "', 前缀规则: '" + prefix + "', 长度规则: " + length);
        
        // 校验前缀
        if (prefix != null && !prefix.isEmpty()) {
            if (!trimmedBarcode.startsWith(prefix)) {
                Log.w(TAG, "条码前缀不匹配: 期望 '" + prefix + "', 实际 '" + trimmedBarcode + "'");
                return false;
            }
        }
        
        // 校验长度
        if (length > 0) {
            if (trimmedBarcode.length() != length) {
                Log.w(TAG, "条码长度不匹配: 期望 " + length + ", 实际 " + trimmedBarcode.length());
                return false;
            }
        }
        
        Log.d(TAG, "条码校验通过: " + trimmedBarcode);
        return true;
    }
    
    /**
     * 获取条码前缀配置
     * @return 条码前缀
     */
    public String getBarcodePrefix() {
        return prefs.getString(KEY_PREFIX, "");
    }
    
    /**
     * 获取条码长度配置
     * @return 条码长度（0表示不校验长度）
     */
    public int getBarcodeLength() {
        return prefs.getInt(KEY_LENGTH, 0);
    }
    
    /**
     * 获取货柜ID配置
     * @return 货柜ID
     */
    public int getCabinetId() {
        return prefs.getInt(KEY_CABINET_ID, 0);
    }
    
    /**
     * 获取校验规则描述
     * @return 规则描述文本
     */
    public String getValidationRulesDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append("条码校验规则：");
        
        String prefix = getBarcodePrefix();
        int length = getBarcodeLength();
        
        if (prefix.isEmpty() && length == 0) {
            sb.append("无限制");
        } else {
            if (!prefix.isEmpty()) {
                sb.append("前缀[").append(prefix).append("]");
            }
            if (length > 0) {
                if (!prefix.isEmpty()) {
                    sb.append("且");
                }
                sb.append("长度[").append(length).append("]");
            }
        }
        
        return sb.toString();
    }
    
    /**
     * 检查是否配置了校验规则
     * @return 是否有配置的校验规则
     */
    public boolean hasValidationRules() {
        String prefix = getBarcodePrefix();
        int length = getBarcodeLength();
        return !prefix.isEmpty() || length > 0;
    }
    
    /**
     * 获取无效条码的提示信息
     * @return 提示信息
     */
    public String getInvalidBarcodeMessage() {
        String prefix = getBarcodePrefix();
        int length = getBarcodeLength();
        
        StringBuilder sb = new StringBuilder();
        sb.append("无效的条码号");
        
        if (!prefix.isEmpty() && length > 0) {
            sb.append("，必须以").append(prefix).append("开头且长度为").append(length).append("位");
        } else if (!prefix.isEmpty()) {
            sb.append("，必须以").append(prefix).append("开头");
        } else if (length > 0) {
            sb.append("，长度必须为").append(length).append("位");
        }
        
        return sb.toString();
    }
    
    /**
     * 保存条码校验配置
     * @param prefix 条码前缀
     * @param length 条码长度
     * @param cabinetId 货柜ID
     */
    public void saveBarcodeSettings(String prefix, int length, int cabinetId) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_PREFIX, prefix != null ? prefix : "");
        editor.putInt(KEY_LENGTH, length);
        editor.putInt(KEY_CABINET_ID, cabinetId);
        editor.apply();
        
        Log.d(TAG, "条码设置已保存: prefix=" + prefix + ", length=" + length + ", cabinetId=" + cabinetId);
    }
    
    /**
     * 清除条码校验配置
     */
    public void clearBarcodeSettings() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove(KEY_PREFIX);
        editor.remove(KEY_LENGTH);
        editor.remove(KEY_CABINET_ID);
        editor.apply();
        
        Log.d(TAG, "条码设置已清除");
    }
    
    /**
     * 刷新配置缓存
     * 在配置被修改后调用此方法确保读取最新配置
     */
    public void refreshConfig() {
        // 由于prefs是final字段，我们通过强制重新加载单例实例来刷新配置
        Log.d(TAG, "配置已刷新，建议使用forceReload方法确保配置更新");
    }
    
    /**
     * 强制重新加载单例实例
     * 在配置发生重大变化时调用此方法
     */
    public static void forceReload(Context context) {
        instance = null;
        getInstance(context);
        
        Log.d(TAG, "单例实例已强制重新加载");
    }
    
    /**
     * 获取当前配置的调试信息
     */
    public String getDebugInfo() {
        return String.format("BarcodeValidator配置: prefix='%s', length=%d, cabinetId=%d, hasRules=%s",
            getBarcodePrefix(), getBarcodeLength(), getCabinetId(), hasValidationRules());
    }
}