package com.pda.hcw;

/**
 * 耗材项数据类
 */
public class ConsumableItem {
    // 原有字段
    public String productCode;
    public String barcode;
    public String commonName;
    public String specification;
    public String manufacturer;
    public int quantity;
    public String unit;
    
    // API相关字段
    private int id;
    private String rfid;
    private String batch;
    private String expiryDate;
    private String productionDate;
    private String brand;
    // 新增：当前所在地点ID（来自扫码/库存接口的 current_location_id）
    private Integer currentLocationId;
    // 新增：最后一次操作信息（仅 takeList/returnList 返回）
    private String lastOperatorName;
    private String lastOperationTime; // 服务器返回格式：yyyy-MM-dd HH:mm:ss
    // 新增：状态（不在库/可领用/未归还/异常条码），由解析阶段或UI层赋值
    private String status;
    
    public ConsumableItem() {
        // 默认构造函数
    }
    
    public ConsumableItem(String productCode, String barcode, String commonName, 
                        String specification, String manufacturer, int quantity, String unit) {
        this.productCode = productCode;
        this.barcode = barcode;
        this.commonName = commonName;
        this.specification = specification;
        this.manufacturer = manufacturer;
        this.quantity = quantity;
        this.unit = unit;
    }
    
    // Getter方法
    public String getProductCode() {
        return productCode;
    }
    
    public String getBarcode() {
        return barcode;
    }
    
    public String getCommonName() {
        return commonName;
    }
    
    public String getSpecification() {
        return specification;
    }
    
    public String getManufacturer() {
        return manufacturer;
    }
    
    public int getQuantity() {
        return quantity;
    }
    
    public String getUnit() {
        return unit;
    }
    
    // Setter方法
    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }
    
    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }
    
    public void setCommonName(String commonName) {
        this.commonName = commonName;
    }
    
    public void setSpecification(String specification) {
        this.specification = specification;
    }
    
    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }
    
    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
    
    public void setUnit(String unit) {
        this.unit = unit;
    }
    
    // 新增字段的Getter和Setter方法
    public int getId() {
        return id;
    }
    
    public void setId(int id) {
        this.id = id;
    }
    
    public String getRfid() {
        return rfid;
    }
    
    public void setRfid(String rfid) {
        this.rfid = rfid;
    }
    
    public String getBatch() {
        return batch;
    }
    
    public void setBatch(String batch) {
        this.batch = batch;
    }
    
    public String getExpiryDate() {
        return expiryDate;
    }
    
    public void setExpiryDate(String expiryDate) {
        this.expiryDate = expiryDate;
    }
    
    public String getProductionDate() {
        return productionDate;
    }
    
    public void setProductionDate(String productionDate) {
        this.productionDate = productionDate;
    }
    
    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public Integer getCurrentLocationId() { return currentLocationId; }
    public void setCurrentLocationId(Integer currentLocationId) { this.currentLocationId = currentLocationId; }
    
    public String getLastOperatorName() {
        return lastOperatorName;
    }

    public void setLastOperatorName(String lastOperatorName) {
        this.lastOperatorName = lastOperatorName;
    }

    public String getLastOperationTime() {
        return lastOperationTime;
    }

    public void setLastOperationTime(String lastOperationTime) {
        this.lastOperationTime = lastOperationTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
    
    @Override
    public String toString() {
        return "ConsumableItem{" +
                "productCode='" + productCode + '\'' +
                ", barcode='" + barcode + '\'' +
                ", commonName='" + commonName + '\'' +
                ", specification='" + specification + '\'' +
                ", manufacturer='" + manufacturer + '\'' +
                ", quantity=" + quantity +
                ", unit='" + unit + '\'' +
                ", id=" + id +
                ", rfid='" + rfid + '\'' +
                ", batch='" + batch + '\'' +
                ", expiryDate='" + expiryDate + '\'' +
                ", productionDate='" + productionDate + '\'' +
                ", brand='" + brand + '\'' +
                ", lastOperatorName='" + lastOperatorName + '\'' +
                ", lastOperationTime='" + lastOperationTime + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}
