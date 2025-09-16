package com.pda.hcw;

import android.content.Context;
import android.util.Log;
import java.util.List;
import java.util.ArrayList;

/**
 * 盘点数据库包装类（精简版）
 * 仅保留当前使用到的任务级别操作，底层委托 CheckoutRecordDatabase。
 */
public class InventoryDatabase {
    private static final String TAG = "InventoryDatabase";

    private final CheckoutRecordDatabase database;
    private static InventoryDatabase instance;

    public static synchronized InventoryDatabase getInstance(Context context) {
        if (instance == null) {
            instance = new InventoryDatabase(context.getApplicationContext());
        }
        return instance;
    }

    private InventoryDatabase(Context context) {
        this.database = CheckoutRecordDatabase.getInstance(context);
    }

    public boolean saveInventoryTask(InventoryTask task) {
        try {
            long result = database.insertInventoryTask(task);
            return result != -1;
        } catch (Exception e) {
            Log.e(TAG, "Error saving inventory task", e);
            return false;
        }
    }

    public boolean updateTaskStatus(String taskId, String status) {
        try {
            return database.updateInventoryTaskStatus(taskId, status);
        } catch (Exception e) {
            Log.e(TAG, "Error updating task status", e);
            return false;
        }
    }

    public List<InventoryTask> getLocalTasks() {
        try {
            return database.getLocalInventoryTasks();
        } catch (Exception e) {
            Log.e(TAG, "Error getting local tasks", e);
            return new ArrayList<>();
        }
    }

    public InventoryTask getTaskById(String taskId) {
        List<InventoryTask> allTasks = getLocalTasks();
        for (InventoryTask task : allTasks) {
            if (taskId.equals(task.getTaskId())) {
                return task;
            }
        }
        return null;
    }
}
