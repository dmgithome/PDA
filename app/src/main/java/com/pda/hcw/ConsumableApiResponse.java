package com.pda.hcw;

/**
 * 耗材API响应数据模型
 * 对应 /api/stock/getConsumeOrReturnConsumablesByBarcode 接口的返回数据结构
 */
public class ConsumableApiResponse {
    public int cabinetID;
    public java.util.List<String> notInStockData;
    public java.util.List<ConsumableItem> errorData;
    public java.util.List<ConsumableItem> takeList;
    public java.util.List<ConsumableItem> returnList;
    
    public ConsumableApiResponse() {
        this.notInStockData = new java.util.ArrayList<>();
        this.errorData = new java.util.ArrayList<>();
        this.takeList = new java.util.ArrayList<>();
        this.returnList = new java.util.ArrayList<>();
    }
    
    /**
     * 检查是否有未入库的耗材
     * @return 是否有未入库耗材
     */
    public boolean hasNotInStockData() {
        return notInStockData != null && !notInStockData.isEmpty();
    }
    
    /**
     * 检查是否有异常数据（已消耗或已退库）
     * @return 是否有异常数据
     */
    public boolean hasErrorData() {
        return errorData != null && !errorData.isEmpty();
    }
    
    /**
     * 检查是否有可正常取用的耗材
     * @return 是否有可取用耗材
     */
    public boolean hasTakeList() {
        return takeList != null && !takeList.isEmpty();
    }
    
    /**
     * 检查是否有已领用未归还的耗材
     * @return 是否有未归还耗材
     */
    public boolean hasReturnList() {
        return returnList != null && !returnList.isEmpty();
    }
    
    /**
     * 获取处理结果的总结信息
     * @return 总结信息
     */
    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("处理结果：");
        
        if (hasNotInStockData()) {
            sb.append("未入库[").append(notInStockData.size()).append("] ");
        }
        if (hasErrorData()) {
            sb.append("异常[").append(errorData.size()).append("] ");
        }
        if (hasTakeList()) {
            sb.append("可取用[").append(takeList.size()).append("] ");
        }
        if (hasReturnList()) {
            sb.append("未归还[").append(returnList.size()).append("]");
        }
        
        return sb.toString();
    }
}
