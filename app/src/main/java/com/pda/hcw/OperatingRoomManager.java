package com.pda.hcw;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * 术间管理：封装本地持久化与接口交互
 */
public class OperatingRoomManager {
    private static final String TAG = "OperatingRoomManager";

    private final Context context;
    private final CheckoutRecordDatabase db;
    private final ConsumableApiService api;

    private static OperatingRoomManager instance;

    public static synchronized OperatingRoomManager getInstance(Context context) {
        if (instance == null) {
            instance = new OperatingRoomManager(context.getApplicationContext());
        }
        return instance;
    }

    private OperatingRoomManager(Context context) {
        this.context = context;
        this.db = CheckoutRecordDatabase.getInstance(context);
        this.api = ConsumableApiService.getInstance(context);
    }

    // 本地读取
    public List<OperatingRoom> getAllRooms() {
        return db.getOperatingRooms();
    }

    public List<String> getAllRoomNames() {
        return db.getOperatingRoomNames();
    }

    // 从接口手动更新并落库
    public void refreshFromServer(ConsumableApiService.ApiCallback<List<OperatingRoom>> callback) {
        api.fetchOperatingRooms(new ConsumableApiService.ApiCallback<List<OperatingRoom>>() {
            @Override
            public void onSuccess(List<OperatingRoom> result) {
                // 兼容空返回
                if (result == null) result = new ArrayList<>();
                boolean ok = db.replaceOperatingRooms(result);
                if (!ok) {
                    Log.w(TAG, "replaceOperatingRooms failed, but returning fetched data");
                }
                callback.onSuccess(result);
            }

            @Override
            public void onFailure(Exception e) {
                callback.onFailure(e);
            }
        });
    }

    // 新增术间：调用接口成功后落库
    public void addRoom(String name, ConsumableApiService.ApiCallback<OperatingRoom> callback) {
        if (name == null || name.trim().isEmpty()) {
            callback.onFailure(new IllegalArgumentException("术间名称不能为空"));
            return;
        }
        if (db.existsOperatingRoomByName(name.trim())) {
            callback.onFailure(new IllegalStateException("本地已存在同名术间"));
            return;
        }

        api.addOperatingRoom(name.trim(), new ConsumableApiService.ApiCallback<OperatingRoom>() {
            @Override
            public void onSuccess(OperatingRoom result) {
                // 落库
                db.insertOperatingRoom(result.name, result.serverId);
                callback.onSuccess(result);
            }

            @Override
            public void onFailure(Exception e) {
                callback.onFailure(e);
            }
        });
    }
}

