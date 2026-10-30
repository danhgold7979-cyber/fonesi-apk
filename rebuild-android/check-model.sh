#!/bin/sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT HUP INT TERM
SOURCE="$ROOT/app/src/main/java/vn/fonesi/rebuild/model/ManagerModel.java"
TEST="$ROOT/contract-tests/ManagerModelTest.java"
if command -v javac >/dev/null 2>&1; then
    javac --release 17 -Xlint:all -Werror -d "$OUT" "$SOURCE" "$TEST"
else
    echo 'Local limited JDK21 behavior test only; Java17 compatibility requires hosted JDK17/Android build.'
    java -m jdk.compiler/com.sun.tools.javac.Main -source 21 -target 21 -Xlint:all -Werror -d "$OUT" "$SOURCE" "$TEST"
fi
java -cp "$OUT" ManagerModelTest
