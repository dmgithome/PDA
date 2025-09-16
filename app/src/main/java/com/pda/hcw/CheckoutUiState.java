package com.pda.hcw;

/**
 * 取用界面UI状态机
 * NEW_CHECKOUT: 新建取用模式
 * VIEW_RECORD: 查看记录模式
 * SUBMITTING: 提交中（预留，不强制改变现有业务逻辑）
 * DIALOG_OPEN: 弹窗打开（预留）
 */
public enum CheckoutUiState {
    NEW_CHECKOUT,
    VIEW_RECORD,
    SUBMITTING,
    DIALOG_OPEN
}

