package com.pda.hcw;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.material.textfield.TextInputEditText;

/**
 * API 设置界面：支持配置 BaseURL 与常用端点路径。
 */
public class ApiSettingsActivity extends BaseActivity {
    private static final String TAG = "ApiSettingsActivity";

    private NetworkConfig networkConfig;

    // 基础 URL
    private TextInputEditText apiBaseUrlInput;
    private TextView networkStatusText;

    // 端点输入框
    private TextInputEditText epLoginInput;
    private TextInputEditText epNfcLoginInput;
    private TextInputEditText epConsumeByBarcodeInput;
    private TextInputEditText epGetAllConsumablesInput;
    private TextInputEditText epCreateTakeReturnInput;
    private TextInputEditText epGetOperatingRoomListInput;
    private TextInputEditText epAddOperatingRoomInput;
    private TextInputEditText epOvertimeUnreturnNumberInput;

    private Button saveButton;
    private Button resetButton;

    @Override
    protected int getLayoutResourceId() { return R.layout.activity_api_settings; }

    @Override
    protected String getActivityName() { return "API设置"; }

    @Override
    protected String getActivityTitle() { return "API设置"; }

    @Override
    protected boolean checkPermissions() {
        // 复用系统设置权限
        if (!permissionManager.hasPermission(Permissions.SYSTEM_SETTINGS)) {
            showError("您没有系统设置权限");
            return false;
        }
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            networkConfig = NetworkConfig.getInstance(this);
            initFields();
            bindValues();
            bindActions();
        } catch (Exception e) {
            Log.e(TAG, "onCreate failed", e);
            showError("界面初始化失败");
            finish();
        }
    }

    private void initFields() {
        apiBaseUrlInput = findViewById(R.id.api_base_url_input);
        networkStatusText = findViewById(R.id.api_network_status_text);

        epLoginInput = findViewById(R.id.endpoint_login_input);
        epNfcLoginInput = findViewById(R.id.endpoint_nfc_login_input);
        epConsumeByBarcodeInput = findViewById(R.id.endpoint_consume_by_barcode_input);
        epGetAllConsumablesInput = findViewById(R.id.endpoint_get_all_consumables_input);
        epCreateTakeReturnInput = findViewById(R.id.endpoint_create_take_return_input);
        epGetOperatingRoomListInput = findViewById(R.id.endpoint_get_operating_room_list_input);
        epAddOperatingRoomInput = findViewById(R.id.endpoint_add_operating_room_input);
        epOvertimeUnreturnNumberInput = findViewById(R.id.endpoint_overtime_unreturn_number_input);

        saveButton = findViewById(R.id.api_save_button);
        resetButton = findViewById(R.id.api_reset_button);
    }

    private void bindValues() {
        apiBaseUrlInput.setText(networkConfig.getApiBaseUrl());
        updateNetworkStatus();

        epLoginInput.setText(networkConfig.getEndpointPath(ApiEndpoint.LOGIN));
        epNfcLoginInput.setText(networkConfig.getEndpointPath(ApiEndpoint.NFC_LOGIN));
        epConsumeByBarcodeInput.setText(networkConfig.getEndpointPath(ApiEndpoint.CONSUME_BY_BARCODE));
        epGetAllConsumablesInput.setText(networkConfig.getEndpointPath(ApiEndpoint.GET_ALL_CONSUMABLES));
        epCreateTakeReturnInput.setText(networkConfig.getEndpointPath(ApiEndpoint.CREATE_TAKE_RETURN_LOG));
        epGetOperatingRoomListInput.setText(networkConfig.getEndpointPath(ApiEndpoint.GET_OPERATING_ROOM_LIST));
        epAddOperatingRoomInput.setText(networkConfig.getEndpointPath(ApiEndpoint.ADD_OPERATING_ROOM));
        epOvertimeUnreturnNumberInput.setText(networkConfig.getEndpointPath(ApiEndpoint.OVERTIME_UNRETURN_NUMBER));
    }

    private void bindActions() {
        saveButton.setOnClickListener(v -> {
            try {
                // 基础URL
                String baseUrl = safeText(apiBaseUrlInput);
                networkConfig.setApiBaseUrl(baseUrl);
                // 端点
                networkConfig.setEndpointPath(ApiEndpoint.LOGIN, safeText(epLoginInput));
                networkConfig.setEndpointPath(ApiEndpoint.NFC_LOGIN, safeText(epNfcLoginInput));
                networkConfig.setEndpointPath(ApiEndpoint.CONSUME_BY_BARCODE, safeText(epConsumeByBarcodeInput));
                networkConfig.setEndpointPath(ApiEndpoint.GET_ALL_CONSUMABLES, safeText(epGetAllConsumablesInput));
                networkConfig.setEndpointPath(ApiEndpoint.CREATE_TAKE_RETURN_LOG, safeText(epCreateTakeReturnInput));
                networkConfig.setEndpointPath(ApiEndpoint.GET_OPERATING_ROOM_LIST, safeText(epGetOperatingRoomListInput));
                networkConfig.setEndpointPath(ApiEndpoint.ADD_OPERATING_ROOM, safeText(epAddOperatingRoomInput));
                networkConfig.setEndpointPath(ApiEndpoint.OVERTIME_UNRETURN_NUMBER, safeText(epOvertimeUnreturnNumberInput));

                updateNetworkStatus();
                showSuccess("保存成功");
            } catch (Exception e) {
                Log.e(TAG, "save failed", e);
                showError("保存失败");
            }
        });

        resetButton.setOnClickListener(v -> {
            try {
                networkConfig.resetEndpointsToDefault();
                bindValues();
                showSuccess("已重置为默认端点");
            } catch (Exception e) {
                Log.e(TAG, "reset failed", e);
                showError("重置失败");
            }
        });
    }

    private void updateNetworkStatus() {
        String status = networkConfig.getConfigStatus();
        networkStatusText.setText(status);
        if (networkConfig.isValidConfig()) {
            networkStatusText.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
        } else {
            networkStatusText.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
        }
    }

    private static String safeText(TextInputEditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }
}
