#!/usr/bin/env bash
set -euo pipefail

# 离线构建脚本：复用本机Gradle缓存与JDK17，避免联网下载
# 使用方式：bash scripts/build_offline.sh

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

export GRADLE_USER_HOME="${ROOT_DIR}/.gradle"

# 复用全局缓存以离线解析依赖（只读），写入发生在项目内 .gradle
mkdir -p .gradle
if [ ! -e .gradle/caches ] && [ -d "$HOME/.gradle/caches" ]; then ln -s "$HOME/.gradle/caches" .gradle/caches; fi
if [ -L .gradle/daemon ]; then rm .gradle/daemon; fi
mkdir -p .gradle/daemon .gradle/native .gradle/wrapper

# 使用系统JDK17运行Gradle（AGP 8.0 需要）
JAVA17="/usr/lib/jvm/java-17-openjdk-amd64"
if [ -d "$JAVA17" ]; then
  export ORG_GRADLE_JAVA_HOME="$JAVA17"
else
  echo "未找到JDK17，当前JDK: $(java -version 2>&1 | head -n1)" >&2
fi

# 关闭守护进程与配置缓存，避免在受限环境产生额外Socket/文件占用
./gradlew \
  -g "$GRADLE_USER_HOME" \
  --no-daemon \
  -Dorg.gradle.daemon=false \
  -Dorg.gradle.jvmargs= \
  --offline \
  -x lint \
  assembleDebug

echo "\n构建完成：app/build/outputs/apk/debug/app-debug.apk"

