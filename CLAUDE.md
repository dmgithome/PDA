# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 构建命令

```bash
# 需要 JDK 17

# 标准构建
./gradlew assembleDebug             # 构建 Debug APK
./gradlew assembleRelease           # 构建 Release APK
./gradlew build                     # 完整构建 (含测试)
./gradlew clean build               # 清理后重新构建

# 安装到设备
./gradlew installDebug

# 测试
./gradlew test                      # 单元测试
./gradlew connectedAndroidTest      # 设备测试

# 离线构建 (使用缓存依赖)
bash scripts/build_offline.sh
```

## 项目架构

**技术栈**: Android/Java, Gradle 8.11.1, AGP 8.10.0, SDK 34 (Min 28)

**核心组件** (`app/src/main/java/com/pda/hcw/`):
- `MyApplication` - 全局会话管理和 Activity 追踪
- `SplashLoginActivity` - 启动入口
- `LoginActivity` - 登录 (支持 NFC)
- `MainActivity` - 主仪表盘
- `ConsumableCheckoutNewActivity` - 条码/RFID 耗材出库
- `InventoryActivity` - 盘点管理
- `InboundAcceptanceActivity` - 入库验收 (批量 RFID)

**硬件集成** (`rfid/`):
- `UHFManager` - UHF RFID 读卡器封装 (单例)
- `EpcFormatter` - EPC 标签编码/解码
- `RfidBeepManager` - RFID 读取音频反馈
- 本地 JAR 库: `app/libs/` (UHF67_v3.6.jar, reader.jar 等)

**数据管理**:
- `SessionManager` - 会话超时 (默认 15 分钟，可配置)
- `NetworkConfig` - API 端点管理 (单例)
- `CheckoutRecordDatabase` - 本地 SQLite 出库记录
- `InventoryDatabase` - 盘点任务持久化

**API 服务**:
- `ConsumableApiService` - REST API 客户端 (OkHttp 4.12)
- `InboundApiService` - 入库操作接口

## 配置

**支持架构**: arm64-v8a, armeabi-v7a, armeabi

**NFC**: 必需功能，用于标签发现和登录

**网络**: 允许明文流量 (network_security_config)

**会话管理**:
- 默认超时: 15 分钟
- 超时前 60 秒预警
- 超时后自动跳转登录页

**APK 输出**:
- Debug: `app/build/outputs/apk/debug/app-debug.apk`
- Release: `app/build/outputs/apk/release/app-release.apk`
