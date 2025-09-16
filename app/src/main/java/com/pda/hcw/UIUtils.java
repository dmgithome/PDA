package com.pda.hcw;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
// import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * UI工具类，统一UI操作方法
 * 提供常用的UI操作封装，减少重复代码
 */
public class UIUtils {
    private static final String TAG = "UIUtils";
    
    /**
     * 显示Toast消息
     * @param context 上下文
     * @param message 消息内容
     */
    public static void showToast(Context context, String message) {
        // Deprecated: 改用 showSuccess/showError/showInfo
        showSuccess(context, message);
    }
    
    /**
     * 显示长Toast消息
     * @param context 上下文
     * @param message 消息内容
     */
    public static void showLongToast(Context context, String message) {
        showSuccess(context, message);
    }
    
    /**
     * 显示成功消息
     * @param context 上下文
     * @param message 消息内容
     */
    public static void showSuccess(Context context, String message) {
        try {
            if (context instanceof BaseActivity) {
                ((BaseActivity) context).showSuccess(message);
                return;
            }
            if (context instanceof android.app.Activity) {
                android.app.Activity act = (android.app.Activity) context;
                android.view.View anchor = act.findViewById(android.R.id.content);
                if (anchor != null) {
                    com.google.android.material.snackbar.Snackbar.make(anchor, message, com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
                            .setDuration(1000)
                            .show();
                    return;
                }
            }
        } catch (Throwable ignore) {}
        Log.d(TAG, "Success: " + message);
    }
    
    /**
     * 显示错误消息
     * @param context 上下文
     * @param message 消息内容
     */
    public static void showError(Context context, String message) {
        if (context instanceof BaseActivity) {
            ((BaseActivity) context).showError(message);
        } else {
            Log.e(TAG, "Error: " + message);
        }
    }
    
        
    /**
     * 显示信息消息
     * @param context 上下文
     * @param message 消息内容
     */
    public static void showInfo(Context context, String message) {
        if (context instanceof BaseActivity) {
            ((BaseActivity) context).showInfo(message);
        } else {
            Log.i(TAG, "Info: " + message);
        }
    }

    // —— 错误信息友好化 ——
    public static String friendlyErrorMessage(Throwable t) {
        String raw = t != null && t.getMessage() != null ? t.getMessage() : "";
        String s = raw.toLowerCase();
        if (s.contains("unable to resolve host") || s.contains("no address associated with hostname")) {
            return "无法连接服务器，请检查网络或服务器地址";
        }
        if (s.contains("failed to connect") || s.contains("failed to connect to") || s.contains("connection refused")) {
            return "无法连接服务器，请检查网络";
        }
        if (s.contains("timeout") || s.contains("timed out") || s.contains("sockettimeout")) {
            return "连接超时，请检查网络";
        }
        if (s.contains("connection reset") || s.contains("reset by peer")) {
            return "网络连接被重置，请重试";
        }
        if (s.contains("software caused connection abort") || s.contains("broken pipe")) {
            return "网络中断，请重试";
        }
        if (s.contains("ssl") || s.contains("certificate") || s.contains("cert") || s.contains("handshake")) {
            return "SSL/证书错误，请联系管理员";
        }
        if (s.contains("cleartext") && s.contains("not permitted")) {
            return "已禁用明文HTTP，请开启明文或使用HTTPS";
        }
        if (s.contains("unexpected end of stream") || s.contains("eof")) {
            return "网络中断，请重试";
        }
        if (s.contains("canceled") || s.contains("cancelled")) {
            return "请求已取消";
        }
        // 认证/令牌相关
        if (s.contains("token") || s.contains("jwt") || s.contains("bearer")) {
            if (s.contains("invalid") || s.contains("expire") || s.contains("expired") || s.contains("mismatch") || s.contains("wrong") || s.contains("not")) {
                return "登录凭证无效或已过期，请重新登录";
            }
            return "登录凭证错误，请重新登录";
        }
        // 默认兜底
        return raw != null && !raw.isEmpty() ? raw : "网络连接失败，请检查网络设置";
    }

    public static String friendlyErrorMessage(String prefix, Throwable t) {
        String base = friendlyErrorMessage(t);
        if (prefix == null || prefix.isEmpty()) return base;
        return prefix + "：" + base;
    }
    
    // 统一提示：成功使用 Snackbar；错误/提示使用 BaseActivity 横幅。
    
    /**
     * 显示确认对话框
     * @param context 上下文
     * @param title 标题
     * @param message 消息内容
     * @param onConfirm 确认回调
     */
    public static void showConfirmDialog(Context context, String title, String message, 
                                       DialogInterface.OnClickListener onConfirm) {
        showConfirmDialog(context, title, message, onConfirm, null);
    }
    
    /**
     * 显示确认对话框
     * @param context 上下文
     * @param title 标题
     * @param message 消息内容
     * @param onConfirm 确认回调
     * @param onCancel 取消回调
     */
    public static void showConfirmDialog(Context context, String title, String message, 
                                       DialogInterface.OnClickListener onConfirm,
                                       DialogInterface.OnClickListener onCancel) {
        if (context == null) return;
        
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title)
               .setMessage(message)
               .setPositiveButton("确定", onConfirm)
               .setNegativeButton("取消", onCancel != null ? onCancel : (dialog, which) -> dialog.dismiss())
               .setCancelable(true)
               .show();
    }
    
    /**
     * 显示警告对话框
     * @param context 上下文
     * @param title 标题
     * @param message 消息内容
     */
    public static void showWarningDialog(Context context, String title, String message) {
        if (context == null) return;
        
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title)
               .setMessage(message)
               .setPositiveButton("确定", (dialog, which) -> dialog.dismiss())
               .setCancelable(true)
               .show();
    }
    
    /**
     * 显示输入对话框
     * @param context 上下文
     * @param title 标题
     * @param message 消息内容
     * @param defaultValue 默认值
     * @param onInput 输入回调
     */
    public static void showInputDialog(Context context, String title, String message, 
                                      String defaultValue, DialogInterface.OnClickListener onInput) {
        if (context == null) return;
        
        EditText input = new EditText(context);
        input.setText(defaultValue);
        input.setSingleLine();
        
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title)
               .setMessage(message)
               .setView(input)
               .setPositiveButton("确定", onInput)
               .setNegativeButton("取消", (dialog, which) -> dialog.dismiss())
               .setCancelable(true)
               .show();
    }
    
    /**
     * 设置TextView文本为空时显示占位符
     * @param textView TextView
     * @param text 文本内容
     * @param placeholder 占位符
     */
    public static void setTextWithPlaceholder(TextView textView, String text, String placeholder) {
        if (textView == null) return;
        
        if (TextUtils.isEmpty(text)) {
            textView.setText(placeholder);
            textView.setTextColor(Color.GRAY);
        } else {
            textView.setText(text);
            textView.setTextColor(Color.BLACK);
        }
    }
    
    /**
     * 验证EditText是否为空
     * @param editText EditText
     * @param errorMessage 错误消息
     * @return 是否为空
     */
    public static boolean isEmpty(EditText editText, String errorMessage) {
        if (editText == null) return true;
        
        if (TextUtils.isEmpty(editText.getText().toString().trim())) {
            editText.setError(errorMessage);
            editText.requestFocus();
            return true;
        }
        return false;
    }
    
    /**
     * 验证邮箱格式
     * @param email 邮箱地址
     * @return 是否有效
     */
    public static boolean isValidEmail(String email) {
        return !TextUtils.isEmpty(email) && Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }
    
    /**
     * 验证手机号格式
     * @param phone 手机号
     * @return 是否有效
     */
    public static boolean isValidPhone(String phone) {
        if (TextUtils.isEmpty(phone)) return false;
        // 简单的手机号验证：11位数字
        return Pattern.matches("^1[3-9]\\d{9}$", phone);
    }
    
    /**
     * 格式化时间戳
     * @param timestamp 时间戳
     * @return 格式化的时间字符串
     */
    public static String formatTimestamp(long timestamp) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(new Date(timestamp));
    }
    
    /**
     * 格式化日期
     * @param timestamp 时间戳
     * @return 格式化的日期字符串
     */
    public static String formatDate(long timestamp) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            .format(new Date(timestamp));
    }
    
    /**
     * 格式化时间
     * @param timestamp 时间戳
     * @return 格式化的时间字符串
     */
    public static String formatTime(long timestamp) {
        return new SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            .format(new Date(timestamp));
    }
    
    /**
     * 隐藏软键盘
     * @param activity Activity
     */
    public static void hideSoftKeyboard(Activity activity) {
        if (activity == null) return;
        
        try {
            InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(activity.getWindow().getDecorView().getWindowToken(), 0);
            }
        } catch (Exception e) {
            Log.e(TAG, "隐藏软键盘失败", e);
        }
    }
    
    /**
     * 显示软键盘
     * @param activity Activity
     * @param view View
     */
    public static void showSoftKeyboard(Activity activity, View view) {
        if (activity == null || view == null) return;
        
        try {
            InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
            }
        } catch (Exception e) {
            Log.e(TAG, "显示软键盘失败", e);
        }
    }
    
    /**
     * 设置View可见性
     * @param view View
     * @param visible 是否可见
     */
    public static void setVisibility(View view, boolean visible) {
        if (view == null) return;
        
        view.setVisibility(visible ? View.VISIBLE : View.GONE);
    }
    
    /**
     * 设置View启用状态
     * @param view View
     * @param enabled 是否启用
     */
    public static void setEnabled(View view, boolean enabled) {
        if (view == null) return;
        
        view.setEnabled(enabled);
        view.setAlpha(enabled ? 1.0f : 0.5f);
    }
    
    /**
     * 获取EditText文本内容
     * @param editText EditText
     * @return 文本内容
     */
    public static String getText(EditText editText) {
        if (editText == null) return "";
        
        return editText.getText().toString().trim();
    }
    
    /**
     * 设置EditText文本内容
     * @param editText EditText
     * @param text 文本内容
     */
    public static void setText(EditText editText, String text) {
        if (editText == null) return;
        
        editText.setText(text != null ? text : "");
    }
    
    /**
     * 清空EditText文本内容
     * @param editText EditText
     */
    public static void clearText(EditText editText) {
        if (editText == null) return;
        
        editText.setText("");
    }
    
    /**
     * 设置View的Margin
     * @param view View
     * @param left 左边距
     * @param top 上边距
     * @param right 右边距
     * @param bottom 下边距
     */
    public static void setMargins(View view, int left, int top, int right, int bottom) {
        if (view == null) return;
        
        if (view.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
            params.setMargins(left, top, right, bottom);
            view.setLayoutParams(params);
        }
    }
    
    /**
     * 检查是否有网络连接
     * @param context 上下文
     * @return 是否有网络
     */
    public static boolean isNetworkAvailable(Context context) {
        if (context == null) return false;
        
        android.net.ConnectivityManager cm = (android.net.ConnectivityManager) 
            context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            android.net.NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
        }
        return false;
    }
    
    /**
     * 获取屏幕宽度
     * @param context 上下文
     * @return 屏幕宽度
     */
    public static int getScreenWidth(Context context) {
        if (context == null) return 0;
        
        android.view.WindowManager wm = (android.view.WindowManager) 
            context.getSystemService(Context.WINDOW_SERVICE);
        if (wm != null) {
            android.view.Display display = wm.getDefaultDisplay();
            android.graphics.Point point = new android.graphics.Point();
            display.getSize(point);
            return point.x;
        }
        return 0;
    }
    
    /**
     * 获取屏幕高度
     * @param context 上下文
     * @return 屏幕高度
     */
    public static int getScreenHeight(Context context) {
        if (context == null) return 0;
        
        android.view.WindowManager wm = (android.view.WindowManager) 
            context.getSystemService(Context.WINDOW_SERVICE);
        if (wm != null) {
            android.view.Display display = wm.getDefaultDisplay();
            android.graphics.Point point = new android.graphics.Point();
            display.getSize(point);
            return point.y;
        }
        return 0;
    }
}
