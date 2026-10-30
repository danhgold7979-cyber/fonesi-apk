#!/bin/sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT HUP INT TERM
SOURCE="$ROOT/app/src/main/java/vn/fonesi/rebuild/model/ManagerModel.java"
TEST="$ROOT/contract-tests/ManagerModelTest.java"
CONTROLLER="$ROOT/app/src/main/java/vn/fonesi/rebuild/model/ManagerController.java"
CONTROLLER_TEST="$ROOT/contract-tests/ManagerControllerTest.java"
GATE="$ROOT/app/src/main/java/vn/fonesi/rebuild/catalog/CatalogRequestGate.java"
GATE_TEST="$ROOT/contract-tests/CatalogRequestGateTest.java"
PAGE="$ROOT/app/src/main/java/vn/fonesi/rebuild/catalog/CatalogPage.java"
PAGE_TEST="$ROOT/contract-tests/CatalogPageTest.java"
OWNERSHIP="$ROOT/app/src/main/java/vn/fonesi/rebuild/catalog/CatalogOwnership.java"
OWNERSHIP_TEST="$ROOT/contract-tests/CatalogOwnershipTest.java"
if command -v javac >/dev/null 2>&1; then
    javac --release 17 -Xlint:all -Werror -d "$OUT" "$SOURCE" "$TEST" "$CONTROLLER" "$CONTROLLER_TEST" "$GATE" "$GATE_TEST" "$PAGE" "$PAGE_TEST" "$OWNERSHIP" "$OWNERSHIP_TEST"
else
    echo 'Local limited JDK21 behavior test only; Java17 compatibility requires hosted JDK17/Android build.'
    java -m jdk.compiler/com.sun.tools.javac.Main -source 21 -target 21 -Xlint:all -Werror -d "$OUT" "$SOURCE" "$TEST" "$CONTROLLER" "$CONTROLLER_TEST" "$GATE" "$GATE_TEST" "$PAGE" "$PAGE_TEST" "$OWNERSHIP" "$OWNERSHIP_TEST"
fi
java -cp "$OUT" ManagerModelTest
java -cp "$OUT" ManagerControllerTest
java -cp "$OUT" CatalogRequestGateTest
java -cp "$OUT" vn.fonesi.rebuild.catalog.CatalogPageTest
java -cp "$OUT" CatalogOwnershipTest

"$ROOT/check-loader-host.sh"
