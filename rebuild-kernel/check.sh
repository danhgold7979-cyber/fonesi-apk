#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p build/classes
mapfile -t sources < <(find src/main/java src/test/java -type f -name '*.java' | sort)
((${#sources[@]} > 0))
java -m jdk.compiler/com.sun.tools.javac.Main -source 21 -target 21 -Xlint:all -Werror -d build/classes "${sources[@]}"
java -cp build/classes vn.fonesi.rebuild.KernelTest
java -cp build/classes vn.fonesi.rebuild.JournalTest
