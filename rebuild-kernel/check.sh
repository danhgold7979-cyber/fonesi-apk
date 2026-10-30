#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT HUP INT TERM
mapfile -t sources < <(find src/main/java src/test/java -type f -name '*.java' | sort)
((${#sources[@]} > 0))
java -m jdk.compiler/com.sun.tools.javac.Main -source 21 -target 21 -Xlint:all -Werror -d "$OUT" "${sources[@]}"
java -cp "$OUT" vn.fonesi.rebuild.KernelTest
java -cp "$OUT" vn.fonesi.rebuild.JournalTest
java -cp "$OUT" vn.fonesi.rebuild.DispatcherTest
java -cp "$OUT" vn.fonesi.rebuild.BridgeOracleTest
