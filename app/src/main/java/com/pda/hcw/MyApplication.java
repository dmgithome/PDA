package com.pda.hcw;

import android.app.Activity;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
// import android.widget.Toast;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 应用程序类，用于处理全局会话管理和输入事件分发
 */
public class MyApplication extends Application implements Application.ActivityLifecycleCallbacks {
    private static final String TAG = "MyApplication";
    private static MyApplication instance;
    
    private SessionManager sessionManager;
    private Activity currentActivity; // 当前正在显示的Activity
    
    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        // 不再初始化 Toast；统一使用 Snackbar + 页面横幅
        
        // 注册Activity生命周期监听
        registerActivityLifecycleCallbacks(this);
        
        // 初始化SessionManager
        sessionManager = SessionManager.getInstance(this);
        
        // 设置全局会话监听器
        sessionManager.setSessionListener(new SessionManager.SessionListener() {
            @Override
            public void onSessionTimeout() {
                handleSessionTimeout();
            }
            
            @Override
            public void onSessionWarning(long remainingTime) {
                // 可以在这里添加会话即将超时的提醒
                Log.w(TAG, "会话即将超时，剩余: " + (remainingTime / 1000) + "秒");
            }
            
            @Override
            public void onSessionDestroyed() {
                // 会话销毁时的处理
                Log.d(TAG, "会话已销毁");
            }
        });
        
        try {
            // 盘点任务清理逻辑：仅当显式启用开关且未执行过才执行，默认关闭
            android.content.SharedPreferences sp = getSharedPreferences("maintenance", MODE_PRIVATE);
            boolean enabled = sp.getBoolean("enable_inventory_clear_on_first_run", false);
            boolean done = sp.getBoolean("inventory_cleared_once", false);
            if (enabled && !done) {
                int n = CheckoutRecordDatabase.getInstance(this).clearAllInventoryTasks();
                sp.edit().putBoolean("inventory_cleared_once", true).apply();
                Log.d(TAG, "按维护开关执行：已清空历史盘点任务及明细，受影响记录数=" + n);
            }
        } catch (Throwable t) {
            Log.w(TAG, "清空历史盘点任务维护操作失败", t);
        }

        Log.d(TAG, "MyApplication初始化完成");
    }
    
    /**
     * 处理会话超时
     */
    private void handleSessionTimeout() {
        Log.w(TAG, "会话超时，跳转到登录界面");
        
        // 获取当前Activity的上下文
        Context context = getCurrentActivityContext();
        if (context != null) {
            // 不弹出Toast，交由目标界面展示明文提示
            
            // 跳转到登录界面
            try {
                Intent intent = new Intent(context, Class.forName("com.pda.hcw.LoginActivity"));
                // 清空任务栈并启动登录页，返回键即退出应用
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                context.startActivity(intent);
            } catch (ClassNotFoundException e) {
                Log.e(TAG, "找不到LoginActivity类", e);
            }
        }
    }
    
    // ========= ActivityLifecycleCallbacks 方法 =========
    
    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        Log.d(TAG, "Activity created: " + activity.getClass().getSimpleName());
    }
    
    @Override
    public void onActivityStarted(Activity activity) {
        Log.d(TAG, "Activity started: " + activity.getClass().getSimpleName());
    }
    
    @Override
    public void onActivityResumed(Activity activity) {
        currentActivity = activity;
        Log.d(TAG, "Activity resumed: " + activity.getClass().getSimpleName() + " (set as current)");
    }
    
    @Override
    public void onActivityPaused(Activity activity) {
        Log.d(TAG, "Activity paused: " + activity.getClass().getSimpleName());
        if (currentActivity == activity) {
            currentActivity = null;
        }
    }
    
    @Override
    public void onActivityStopped(Activity activity) {
        Log.d(TAG, "Activity stopped: " + activity.getClass().getSimpleName());
    }
    
    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
        Log.d(TAG, "Activity save instance state: " + activity.getClass().getSimpleName());
    }
    
    @Override
    public void onActivityDestroyed(Activity activity) {
        Log.d(TAG, "Activity destroyed: " + activity.getClass().getSimpleName());
        if (currentActivity == activity) {
            currentActivity = null;
        }
    }
    
    
    /**
     * 获取当前Activity
     */
    public Activity getCurrentActivity() {
        return currentActivity;
    }
    
    /**
     * 获取当前Activity的上下文
     * @return 当前Activity的上下文，如果没有则返回Application上下文
     */
    private Context getCurrentActivityContext() {
        return currentActivity != null ? currentActivity : getApplicationContext();
    }
    
    /**
     * 获取Application实例
     * @return MyApplication实例
     */
    public static MyApplication getInstance() {
        return instance;
    }
    
    /**
     * 获取SessionManager实例
     * @return SessionManager实例
     */
    public SessionManager getSessionManager() {
        return sessionManager;
    }
}
