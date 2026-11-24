#!/bin/sh
# 从手持机的标签解析源码生成门禁的离线依赖。使用 JDK 17。
set -eu
project=$(cd "$(dirname "$0")/.." && pwd)
destination=${1:-"$project/../AndroidPC-Unified/app/libs/hcw-rfid-format.jar"}
classes=$(mktemp -d)
trap 'rm -rf "$classes"' EXIT
javac --release 8 -encoding UTF-8 -d "$classes" "$project/app/src/main/java/com/pda/hcw/rfid/EpcFormatter.java"
jar --create --file "$destination" --date=2020-01-01T00:00:00Z --no-manifest -C "$classes" .
