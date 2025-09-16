package com.pda.hcw.rfid;

import android.content.Context;
import android.util.Log;
import java.util.List;

// 导入UHF库相关类 - 统一使用ModuleAPI_J.jar中的类
import com.handheld.uhfr.UHFRManager;
import com.uhf.api.cls.Reader;
import com.uhf.api.cls.Reader.READER_ERR;
import com.uhf.api.cls.Reader.Region_Conf;
import com.uhf.api.cls.Reader.TAGINFO;

/**
 * UHF RFID管理器
 * 封装UHF库的调用，提供统一的接口
 * 基于UHFRManager API v1.0
 */
public class UHFManager {
    private static final String TAG = "UHFManager";

    // UHF库实例 - 使用真实的UHFRManager
    private UHFRManager uhfReader = null;
    private boolean isInitialized = false;
    private boolean isReading = false;

    // 参数管理（按照厂家demo）
    private SharedUtil sharedUtil;
    private Context context;

    // 单例模式
    private static UHFManager instance;
    
    private UHFManager() {
    }
    
    public static synchronized UHFManager getInstance() {
        if (instance == null) {
            instance = new UHFManager();
        }
        return instance;
    }
    
    /**
     * 初始化UHF模块（完全按照厂家demo实现）
     * @param context 上下文
     * @return 是否初始化成功
     */
    public boolean initialize(Context context) {
        try {
            Log.d(TAG, "开始初始化UHF模块");

            this.context = context;
            this.sharedUtil = new SharedUtil(context);

            // 不再强制应用默认参数；保持用户设置

            // 调用UHFRManager.getInstance()获取实例（按照厂家demo）
            uhfReader = UHFRManager.getInstance();
            if (uhfReader == null) {
                Log.e(TAG, "获取UHFRManager实例失败");
                return false;
            }

            // 按照厂家demo的初始化流程
            boolean initResult = initializeWithFactoryLogic();
            if (initResult) {
                isInitialized = true;
                Log.d(TAG, "UHF模块初始化成功");
                return true;
            } else {
                Log.e(TAG, "UHF模块初始化失败");
                return false;
            }

        } catch (Exception e) {
            Log.e(TAG, "初始化UHF模块失败", e);
            return false;
        }
    }

    /**
     * 应用默认参数设置
     * 确保每次初始化都使用标准化的默认参数
     */
    // 取消强制默认：保留占位（兼容），不做任何操作
    private void applyDefaultSettings() { }

    /**
     * 按照厂家demo的初始化逻辑
     * @return 是否初始化成功
     */
    private boolean initializeWithFactoryLogic() {
        try {
            // 按照厂家demo：先尝试设置高功率
            READER_ERR err = uhfReader.setPower(sharedUtil.getPower(), sharedUtil.getPower());

            if (err == Reader.READER_ERR.MT_OK_ERR) {
                // 高功率设置成功
                Log.d(TAG, "高功率设置成功: " + sharedUtil.getPower() + "dBm");

                // 设置频段
                READER_ERR err1 = uhfReader.setRegion(Region_Conf.valueOf(sharedUtil.getWorkFreq()));
                if (err1 != Reader.READER_ERR.MT_OK_ERR) {
                    Log.w(TAG, "频段设置失败: " + err1);
                }

                // 设置其他参数
                setParam();
                return true;

            } else {
                // 降级到30dBm（按照厂家demo）
                Log.w(TAG, "高功率设置失败，降级到30dBm");
                READER_ERR err1 = uhfReader.setPower(30, 30);

                if (err1 == Reader.READER_ERR.MT_OK_ERR) {
                    Log.d(TAG, "降级功率设置成功: 30dBm");

                    // 设置频段
                    uhfReader.setRegion(Region_Conf.valueOf(sharedUtil.getWorkFreq()));

                    // 设置其他参数
                    setParam();
                    return true;
                } else {
                    Log.e(TAG, "降级功率设置也失败: " + err1);
                    return false;
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "厂家demo初始化逻辑失败", e);
            return false;
        }
    }

    /**
     * 设置参数（按照厂家demo的setParam方法）
     */
    private void setParam() {
        try {
            // Session参数
            uhfReader.setGen2session(sharedUtil.getSession());
            Log.d(TAG, "Session参数设置: " + sharedUtil.getSession());

            // Target参数
            uhfReader.setTarget(sharedUtil.getTarget());
            Log.d(TAG, "Target参数设置: " + sharedUtil.getTarget());

            // Q值参数
            uhfReader.setQvaule(sharedUtil.getQvalue());
            Log.d(TAG, "Q值参数设置: " + sharedUtil.getQvalue());

            // FastID 按用户设置
            try {
                boolean fast = sharedUtil.getFastId();
                uhfReader.setFastID(fast);
                Log.d(TAG, "FastID功能设置: " + fast);
            } catch (Exception e) {
                Log.w(TAG, "设置FastID失败", e);
            }

        } catch (Exception e) {
            Log.w(TAG, "设置参数失败", e);
        }
    }

    /**
     * 设置频段
     * @param region 频段代码 (1=中国, 2=美国, 3=欧洲)
     * @return 是否设置成功
     */
    public boolean setRegion(int region) {
        if (!isInitialized) {
            Log.e(TAG, "UHF模块未初始化");
            return false;
        }

        try {
            Log.d(TAG, "设置频段: " + region);

            // 调用UHFRManager.setRegion()方法
            Region_Conf regionConf = getRegionConf(region);
            READER_ERR result = uhfReader.setRegion(regionConf);
            if (result != Reader.READER_ERR.MT_OK_ERR) {
                Log.e(TAG, "设置频段失败: " + result);
                return false;
            }

            Log.d(TAG, "频段设置成功: " + getRegionName(region));
            return true;

        } catch (Exception e) {
            Log.e(TAG, "设置频段失败", e);
            return false;
        }
    }
    
    /**
     * 设置发射功率（参考厂家demo的降级处理）
     * @param power 功率值 (dBm, 范围5-30)
     * @return 是否设置成功
     */
    public boolean setPower(int power) {
        if (!isInitialized) {
            Log.e(TAG, "UHF模块未初始化");
            return false;
        }

        try {
            Log.d(TAG, "设置功率: " + power + "dBm");

            // 先尝试设置指定功率
            READER_ERR result = uhfReader.setPower(power, power);
            if (result != Reader.READER_ERR.MT_OK_ERR) {
                Log.w(TAG, "设置功率" + power + "dBm失败，尝试降级到30dBm");
                // 降级到30dBm（参考厂家demo）
                result = uhfReader.setPower(30, 30);
                if (result != Reader.READER_ERR.MT_OK_ERR) {
                    Log.e(TAG, "设置功率失败: " + result);
                    return false;
                } else {
                    Log.d(TAG, "功率降级设置成功: 30dBm");
                    return true;
                }
            }

            Log.d(TAG, "功率设置成功: " + power + "dBm");

            // 设置完功率后，设置其他重要参数（参考厂家demo）
            setAdditionalParams();

            return true;

        } catch (Exception e) {
            Log.e(TAG, "设置功率失败", e);
            return false;
        }
    }

    /**
     * 设置其他重要参数（参考厂家demo的setParam方法）
     */
    private void setAdditionalParams() {
        try {
            // 设置Session参数（初始化时使用单标签模式）
            uhfReader.setGen2session(0);
            Log.d(TAG, "Session参数设置成功: 0");

            // 设置Target参数（默认0）
            uhfReader.setTarget(0);
            Log.d(TAG, "Target参数设置成功: 0");

            // 设置Q值参数（默认0）
            uhfReader.setQvaule(0);
            Log.d(TAG, "Q值参数设置成功: 0");

            // 设置FastID功能（按用户设置，默认关闭）
            boolean fast = sharedUtil != null && sharedUtil.getFastId();
            uhfReader.setFastID(fast);
            Log.d(TAG, "FastID功能设置成功: " + fast);

        } catch (Exception e) {
            Log.w(TAG, "设置附加参数失败", e);
        }
    }
    
    /**
     * 开始高性能读取模式（使用优化的定时读取）
     * @return 是否启动成功
     */
    public boolean startHighPerformanceReading() {
        if (!isInitialized) {
            Log.e(TAG, "UHF模块未初始化");
            return false;
        }

        if (isReading) {
            Log.w(TAG, "已经在读取中");
            return false;
        }

        try {
            Log.d(TAG, "启动高性能读取模式（优化定时读取）");

            // 1. 确保完全停止之前的操作
            forceStopAllOperations();

            // 2. 等待一段时间确保资源释放
            Thread.sleep(100);

            // 3. 设置高性能参数（按用户设置）
            uhfReader.setGen2session(sharedUtil.getSession());    // 多标签模式
            uhfReader.setTarget(sharedUtil.getTarget());          // 优化Target
            uhfReader.setQvaule(sharedUtil.getQvalue());          // 优化Q值
            uhfReader.setFastID(sharedUtil.getFastId());          // FastID功能
            Log.d(TAG, "高性能参数设置完成: Session=" + sharedUtil.getSession() + 
                      ", Target=" + sharedUtil.getTarget() + 
                      ", Q=" + sharedUtil.getQvalue() + 
                      ", FastID=" + sharedUtil.getFastId());

            isReading = true;
            Log.d(TAG, "高性能读取模式启动成功");
            return true;

        } catch (Exception e) {
            Log.e(TAG, "启动高性能读取失败", e);
            return false;
        }
    }
    
    /**
     * 开始普通读取模式
     * @return 是否启动成功
     */
    public boolean startNormalReading() {
        if (!isInitialized) {
            Log.e(TAG, "UHF模块未初始化");
            return false;
        }

        if (isReading) {
            Log.w(TAG, "已经在读取中");
            return false;
        }

        try {
            Log.d(TAG, "启动普通读取模式");

            // 设置读取参数（按用户设置）
            uhfReader.setGen2session(sharedUtil.getSession());    // 多标签模式
            uhfReader.setTarget(sharedUtil.getTarget());          // 优化Target
            uhfReader.setQvaule(sharedUtil.getQvalue());          // 优化Q值
            uhfReader.setFastID(sharedUtil.getFastId());          // FastID功能
            
            Log.d(TAG, "普通读取模式参数设置完成: Session=" + sharedUtil.getSession() + 
                      ", Target=" + sharedUtil.getTarget() + 
                      ", Q=" + sharedUtil.getQvalue() + 
                      ", FastID=" + sharedUtil.getFastId());

            isReading = true;
            Log.d(TAG, "普通读取模式启动成功");
            return true;

        } catch (Exception e) {
            Log.e(TAG, "启动普通读取失败", e);
            return false;
        }
    }
    

    
    /**
     * 定时标签读取（用于普通模式）
     * @param timeout 超时时间(ms)
     * @return 读取到的标签信息列表
     */
    public List<TAGINFO> tagInventoryByTimer(short timeout) {
        if (!isInitialized) {
            Log.e(TAG, "UHF模块未初始化");
            return null;
        }

        try {
            // 调用UHFRManager.tagInventoryByTimer()方法（按照厂家demo，不需要预先启动）
            List<TAGINFO> tagList = uhfReader.tagInventoryByTimer(timeout);
            if (tagList != null && !tagList.isEmpty()) {
                Log.d(TAG, "tagInventoryByTimer()读取到 " + tagList.size() + " 个标签");
            } else {
                Log.d(TAG, "tagInventoryByTimer()返回空列表");
            }
            return tagList;

        } catch (Exception e) {
            Log.e(TAG, "定时读取标签失败", e);
            return null;
        }
    }

    /**
     * 高性能定时标签读取（优化版本）
     * @param timeout 超时时间(ms)
     * @return 读取到的标签数据数组
     */
    public String[] tagInventoryByTimerHighPerformance(short timeout) {
        if (!isInitialized || !isReading) {
            return null;
        }

        try {
            // 使用更短的超时时间，提高读取频率
            List<TAGINFO> tagList = uhfReader.tagInventoryByTimer(timeout);
            if (tagList != null && !tagList.isEmpty()) {
                String[] tags = new String[tagList.size()];
                for (int i = 0; i < tagList.size(); i++) {
                    // EpcId是byte[]类型，需要转换为十六进制字符串
                    tags[i] = bytesToHexString(tagList.get(i).EpcId);
                }
                return tags;
            }
            return null;

        } catch (Exception e) {
            Log.e(TAG, "高性能定时读取标签失败", e);
            return null;
        }
    }

    /**
     * 定时标签读取（带TID数据）
     * @param timeout 超时时间(ms)
     * @return 读取到的标签信息列表
     */
    public List<TAGINFO> tagEpcTidInventoryByTimer(short timeout) {
        if (!isInitialized) {
            Log.e(TAG, "UHF模块未初始化");
            return null;
        }

        try {
            // 调用UHFRManager.tagEpcTidInventoryByTimer()方法（按照厂家demo，不需要预先启动）
            List<TAGINFO> tagList = uhfReader.tagEpcTidInventoryByTimer(timeout);
            if (tagList != null && !tagList.isEmpty()) {
                Log.d(TAG, "tagEpcTidInventoryByTimer()读取到 " + tagList.size() + " 个标签");
            } else {
                Log.d(TAG, "tagEpcTidInventoryByTimer()返回空列表");
            }
            return tagList;

        } catch (Exception e) {
            Log.e(TAG, "定时读取标签(带TID)失败", e);
            return null;
        }
    }
    
    /**
     * 停止读取
     * @return 是否停止成功
     */
    public boolean stopReading() {
        if (!isReading) {
            Log.w(TAG, "当前未在读取");
            return true;
        }

        try {
            Log.d(TAG, "停止RFID读取");

            // 使用强制停止方法确保完全停止
            forceStopAllOperations();

            // 等待一段时间确保停止完成
            Thread.sleep(100);

            Log.d(TAG, "RFID读取已停止");
            return true;

        } catch (Exception e) {
            Log.e(TAG, "停止读取失败", e);
            return false;
        }
    }

    /**
     * 异步停止读取（用于性能优化）
     * @return 是否停止成功
     */
    public boolean asyncStopReading() {
        if (!isInitialized) {
            Log.e(TAG, "UHF模块未初始化");
            return false;
        }

        try {
            READER_ERR result = uhfReader.asyncStopReading();
            if (result != Reader.READER_ERR.MT_OK_ERR) {
                Log.e(TAG, "异步停止读取失败: " + result);
                return false;
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "异步停止读取失败", e);
            return false;
        }
    }

    /**
     * 异步开始读取（用于性能优化）
     * @return 是否启动成功
     */
    public boolean asyncStartReading() {
        if (!isInitialized) {
            Log.e(TAG, "UHF模块未初始化");
            return false;
        }

        try {
            READER_ERR result = uhfReader.asyncStartReading();
            if (result != Reader.READER_ERR.MT_OK_ERR) {
                Log.e(TAG, "异步启动读取失败: " + result);
                return false;
            }
            isReading = true; // 设置读取状态
            Log.d(TAG, "异步读取启动成功");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "异步启动读取失败", e);
            return false;
        }
    }

    /**
     * 强制停止所有操作（用于解决MT_OP_EXECING错误）
     */
    private void forceStopAllOperations() {
        try {
            Log.d(TAG, "强制停止所有UHF操作");

            // 尝试停止异步读取
            try {
                uhfReader.asyncStopReading();
            } catch (Exception e) {
                Log.w(TAG, "停止异步读取时出错", e);
            }

            // 尝试停止定时读取
            try {
                uhfReader.stopTagInventory();
            } catch (Exception e) {
                Log.w(TAG, "停止定时读取时出错", e);
            }

            // 重置读取状态
            isReading = false;

            Log.d(TAG, "所有UHF操作已强制停止");

        } catch (Exception e) {
            Log.e(TAG, "强制停止操作失败", e);
        }
    }

    /**
     * 对外暴露的安全停止方法：停止一切读取/定时盘存/异步读取。
     * 供上层在切换任务、停止扫描、页面离开时调用，确保硬件真正停止工作。
     */
    public void stopAllOperations() {
        forceStopAllOperations();
    }
    
    /**
     * 关闭UHF模块
     */
    public void close() {
        try {
            Log.d(TAG, "关闭UHF模块");

            // 先停止读取
            if (isReading) {
                stopReading();
            }

            // 调用UHFRManager.close()方法
            if (uhfReader != null) {
                boolean closeResult = uhfReader.close();
                if (!closeResult) {
                    Log.w(TAG, "UHF模块关闭可能失败");
                }
            }

            uhfReader = null;
            isInitialized = false;
            isReading = false;

            Log.d(TAG, "UHF模块已关闭");

        } catch (Exception e) {
            Log.e(TAG, "关闭UHF模块失败", e);
        }
    }
    
    /**
     * 获取频段名称
     */
    private String getRegionName(int region) {
        switch (region) {
            case 1: return "中国 (920-925MHz)";
            case 2: return "美国 (902-928MHz)";
            case 3: return "欧洲 (865-868MHz)";
            default: return "未知频段";
        }
    }

    /**
     * 将整数频段代码转换为Region_Conf枚举
     * @param region 频段代码 (1=中国, 2=美国, 3=欧洲)
     * @return Region_Conf枚举值
     */
    private Region_Conf getRegionConf(int region) {
        // 根据厂家demo，使用valueOf()方法
        // 0=CA(加拿大), 1=FCC(美国), 2=EU(欧洲)
        switch (region) {
            case 1: return Region_Conf.valueOf(2); // 中国使用EU频段
            case 2: return Region_Conf.valueOf(1); // 美国使用FCC频段
            case 3: return Region_Conf.valueOf(2); // 欧洲使用EU频段
            default: return Region_Conf.valueOf(1); // 默认FCC频段
        }
    }
    
    /**
     * 检查是否已初始化
     */
    public boolean isInitialized() {
        return isInitialized;
    }
    
    /**
     * 检查是否正在读取
     */
    public boolean isReading() {
        return isReading;
    }

    /**
     * 将byte数组转换为十六进制字符串
     * @param bytes byte数组
     * @return 十六进制字符串
     */
    private String bytesToHexString(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b & 0xFF));
        }
        return sb.toString();
    }

    /**
     * 获取当前Session设置
     * @return Session值
     */
    public int getGen2session() {
        if (!isInitialized) {
            return -1;
        }
        try {
            return uhfReader.getGen2session();
        } catch (Exception e) {
            Log.e(TAG, "获取Session失败", e);
            return -1;
        }
    }

    /**
     * 重置所有参数到默认值
     * @return 是否重置成功
     */
    public boolean resetToDefaultSettings() {
        if (!isInitialized) {
            Log.e(TAG, "UHF模块未初始化");
            return false;
        }

        try {
            Log.d(TAG, "重置所有RFID参数到默认值");
            // 应用默认参数设置（显式重置）
            if (sharedUtil != null) {
                sharedUtil.applyDefaultSettings();
            }

            // 设置功率
            READER_ERR err = uhfReader.setPower(sharedUtil.getPower(), sharedUtil.getPower());
            if (err != Reader.READER_ERR.MT_OK_ERR) {
                Log.w(TAG, "功率设置失败，尝试降级到30dBm");
                uhfReader.setPower(30, 30);
            }

            // 设置频段
            uhfReader.setRegion(Region_Conf.valueOf(sharedUtil.getWorkFreq()));

            // 设置其他参数
            uhfReader.setGen2session(sharedUtil.getSession());
            uhfReader.setTarget(sharedUtil.getTarget());
            uhfReader.setQvaule(sharedUtil.getQvalue());
            uhfReader.setFastID(sharedUtil.getFastId());

            Log.d(TAG, "参数重置完成: 功率=" + sharedUtil.getPower() + "dBm" + 
                      ", 频段=" + getRegionName(sharedUtil.getWorkFreq()) +
                      ", Session=" + sharedUtil.getSession() + 
                      ", Target=" + sharedUtil.getTarget() + 
                      ", Q=" + sharedUtil.getQvalue() + 
                      ", FastID=" + sharedUtil.getFastId());

            return true;

        } catch (Exception e) {
            Log.e(TAG, "重置参数失败", e);
            return false;
        }
    }

    /**
     * 从 SharedUtil 读取用户设置并应用到硬件。
     * 在设置页保存后或初始化完成后调用。
     */
    public boolean applyUserSettings() {
        if (!isInitialized) {
            Log.e(TAG, "UHF模块未初始化");
            return false;
        }
        try {
            Log.d(TAG, "应用用户RFID设置");
            // 顺序：功率 -> 频段 -> 其他参数
            READER_ERR p = uhfReader.setPower(sharedUtil.getPower(), sharedUtil.getPower());
            if (p != Reader.READER_ERR.MT_OK_ERR) {
                Log.w(TAG, "按用户设置应用功率失败，降级到30dBm");
                uhfReader.setPower(30, 30);
            }
            READER_ERR r = uhfReader.setRegion(Region_Conf.valueOf(sharedUtil.getWorkFreq()));
            if (r != Reader.READER_ERR.MT_OK_ERR) {
                Log.w(TAG, "按用户设置应用频段失败: " + r);
            }
            uhfReader.setGen2session(sharedUtil.getSession());
            uhfReader.setTarget(sharedUtil.getTarget());
            uhfReader.setQvaule(sharedUtil.getQvalue());
            uhfReader.setFastID(sharedUtil.getFastId());
            Log.d(TAG, "应用完成: P=" + sharedUtil.getPower() + ", FreqIdx=" + sharedUtil.getWorkFreq() +
                    ", S=" + sharedUtil.getSession() + ", T=" + sharedUtil.getTarget() +
                    ", Q=" + sharedUtil.getQvalue() + ", FastID=" + sharedUtil.getFastId());
            return true;
        } catch (Exception e) {
            Log.e(TAG, "应用用户RFID设置失败", e);
            return false;
        }
    }

    /**
     * 设置Session（支持boolean参数，按照厂家demo）
     * @param isMulti 是否多标签模式
     * @return 是否设置成功
     */
    public boolean setGen2session(boolean isMulti) {
        if (!isInitialized) {
            Log.e(TAG, "UHF模块未初始化");
            return false;
        }
        try {
            // 按照厂家demo：true=多标签模式，false=单标签模式
            int sessionValue = isMulti ? 3 : 0;
            uhfReader.setGen2session(sessionValue);
            Log.d(TAG, "Session设置成功: " + sessionValue + " (多标签模式: " + isMulti + ")");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "设置Session失败", e);
            return false;
        }
    }

    /**
     * 返回标签列表版本的tagInventoryRealTime（按照厂家demo）
     * @return 标签列表
     */
    public List<TAGINFO> tagInventoryRealTime() {
        if (!isInitialized) {
            Log.e(TAG, "UHF模块未初始化");
            return null;
        }

        try {
            // 调用UHFRManager.tagInventoryRealTime()方法
            List<TAGINFO> tagList = uhfReader.tagInventoryRealTime();
            if (tagList != null && !tagList.isEmpty()) {
                Log.d(TAG, "tagInventoryRealTime()读取到 " + tagList.size() + " 个标签");
            } else {
                Log.d(TAG, "tagInventoryRealTime()返回空列表");
            }
            return tagList;

        } catch (Exception e) {
            Log.e(TAG, "实时读取标签失败", e);
            return null;
        }
    }
}
