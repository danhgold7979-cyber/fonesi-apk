#!/bin/bash
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
out=$(mktemp -d)
trap 'rm -rf "$out"' EXIT
mapfile -t sources < <(find "$root/core/src" -name '*.java' -type f | sort)
test "${#sources[@]}" -ge 2
if [[ "${1:-}" == "--limited-jre" ]]; then
  echo 'LIMITED HOST: JDK21 module compiler source17/target17; no release17 API verification'
  java -m jdk.compiler/com.sun.tools.javac.Main -source 17 -target 17 -Xlint:all,-options -Werror -d "$out" "${sources[@]}"
else
  javac --release 17 -Xlint:all -Werror -d "$out" "${sources[@]}"
fi
java -cp "$out" vn.fonesi.apk.foundation.UpdatePreflightTest
