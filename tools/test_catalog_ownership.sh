#!/bin/bash
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
out=$(mktemp -d)
trap 'rm -rf "$out"' EXIT
sources=("$root/core/src/main/java/vn/fonesi/apk/foundation/CatalogOwnership.java"
         "$root/core/src/test/java/vn/fonesi/apk/foundation/CatalogOwnershipTest.java")
if [[ "${1:-}" == "--limited-jre" && $# -eq 1 ]]; then
  echo 'LIMITED HOST: JDK21 module compiler source17/target17; no release17 API or Android verification'
  java -m jdk.compiler/com.sun.tools.javac.Main -source 17 -target 17 -Xlint:all,-options -Werror -d "$out" "${sources[@]}"
elif [[ $# -eq 0 ]]; then
  javac --release 17 -Xlint:all -Werror -d "$out" "${sources[@]}"
else
  echo 'Unsupported argument' >&2
  exit 2
fi
java -cp "$out" vn.fonesi.apk.foundation.CatalogOwnershipTest
