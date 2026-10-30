#!/bin/sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT HUP INT TERM
PROD="$ROOT/app/src/main/java/vn/fonesi/rebuild"
# Explicit files: test Android/reader stand-ins are confined to this separate temporary classpath.
find "$ROOT/host-loader-tests" -name '*.java' -print > "$OUT/sources"
printf '%s\n' "$PROD/catalog/CatalogLoader.java" "$PROD/catalog/CatalogRequestGate.java" \
    "$PROD/catalog/CatalogPage.java" "$PROD/model/ManagerModel.java" "$PROD/model/ManagerController.java" >> "$OUT/sources"
if command -v javac >/dev/null 2>&1; then
    javac --release 17 -Xlint:all -Werror -d "$OUT" @"$OUT/sources"
else
    echo 'Local limited JDK21 loader-host test; actual Java17 compatibility requires hosted CI.'
    java -m jdk.compiler/com.sun.tools.javac.Main -source 21 -target 21 -Xlint:all -Werror -d "$OUT" @"$OUT/sources"
fi
java -cp "$OUT" vn.fonesi.rebuild.catalog.CatalogLoaderHostTest
