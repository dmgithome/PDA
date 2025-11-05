package com.pda.hcw;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;


/**
 * 启动登录界面
 * 作为应用启动入口，检查登录状态并决定跳转目标
 */
public class SplashLoginActivity extends AppCompatActivity {
    private static final String TAG = "SplashLoginActivity";
    private static final int SPLASH_DELAY = 1000; // 1秒延迟，显示启动画面
    
    private SessionManager sessionManager;
    private Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
            Log.d(TAG, "SplashLoginActivity启动");
            
            // 设置简单的启动画面布局
            setContentView(R.layout.activity_splash_login);
            
            // 初始化会话管理器
            sessionManager = SessionManager.getInstance(this);
            
                        
            // 延迟执行登录状态检查
            handler.postDelayed(this::checkLoginStatus, SPLASH_DELAY);
            
        } catch (Exception e) {
            Log.e(TAG, "SplashLoginActivity创建失败", e);
            // 发生异常时直接跳转到登录界面
            navigateToLogin();
        }
    }
    
    /**
     * 检查用户登录状态
     */
    private void checkLoginStatus() {
        try {
            Log.d(TAG, "检查用户登录状态");
            
            // 新架构：不再支持会话恢复，总是跳转到登录界面
            Log.d(TAG, "新架构：不支持会话恢复，跳转到登录界面");
            
            // 跳转到登录界面（自动切换到NFC模式）
            navigateToLogin();
            
        } catch (Exception e) {
            Log.e(TAG, "检查登录状态失败", e);
            // 发生异常时跳转到登录界面
            navigateToLogin();
        }
    }
    
    /**
     * 跳转到主界面
     */
    private void navigateToMain() {
        try {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
            Log.d(TAG, "已跳转到主界面");
        } catch (Exception e) {
            Log.e(TAG, "跳转到主界面失败", e);
            // 跳转失败时尝试跳转到登录界面
            navigateToLogin();
        }
    }
    
    /**
     * 跳转到登录界面（自动切换到NFC模式）
     */
    private void navigateToLogin() {
        try {
            Intent intent = new Intent(this, LoginActivity.class);
            intent.putExtra("auto_switch_to_nfc", true); // 自动切换到NFC模式
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
            Log.d(TAG, "已跳转到NFC登录界面");
        } catch (Exception e) {
            Log.e(TAG, "跳转到登录界面失败", e);
            // 如果连登录界面都跳转失败，显示错误信息并结束应用
            finish();
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 清理Handler引用
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
        Log.d(TAG, "SplashLoginActivity已销毁");
    }
}
