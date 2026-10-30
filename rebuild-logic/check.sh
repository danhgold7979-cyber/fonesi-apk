#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
ROOT=$(cd .. && pwd)
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT HUP INT TERM
(cd "$ROOT" && sha256sum --check spec/logical-bridge/b2/host-source-sha256.txt)
(cd "$ROOT" && sha256sum --check spec/logical-bridge/b2/portable-source-sha256.txt)
HOST_JAVA=${JAVA_HOME_21_X64:+$JAVA_HOME_21_X64/bin/java}
HOST_JAVA=${HOST_JAVA:-$(command -v java)}
mkdir -p "$OUT/host" "$OUT/portable" "$OUT/source" "$OUT/portable-source"
for file in oracle/LogicalVectors.java src/test/java/vn/fonesi/rebuild/logical/LogicalContractTest.java; do
    sed 's/^package vn.fonesi.rebuild.logical;/package vn.fonesi.rebuild;/' "$file" > "$OUT/source/$(basename "$file")"
done
HOST_SOURCE="$ROOT/rebuild-kernel/src/main/java/vn/fonesi/rebuild"
POLICY_TEST="$ROOT/rebuild-kernel/src/test/java/vn/fonesi/rebuild/PolicyModeTest.java"
sed 's/^package vn.fonesi.rebuild;/package vn.fonesi.rebuild.logical;/' "$POLICY_TEST" > "$OUT/portable-source/PolicyModeTest.java"
"$HOST_JAVA" -m jdk.compiler/com.sun.tools.javac.Main -source 21 -target 21 -Xlint:all -Werror -d "$OUT/host" \
    "$HOST_SOURCE/IdentityText.java" "$HOST_SOURCE/CapabilityPolicy.java" "$HOST_SOURCE/OperationProtocol.java" "$OUT/source/LogicalVectors.java" "$OUT/source/LogicalContractTest.java" "$POLICY_TEST"
"$HOST_JAVA" -cp "$OUT/host" vn.fonesi.rebuild.LogicalContractTest
"$HOST_JAVA" -cp "$OUT/host" vn.fonesi.rebuild.PolicyModeTest
"$HOST_JAVA" -cp "$OUT/host" vn.fonesi.rebuild.LogicalVectors > "$OUT/host.txt"
python3 "$ROOT/rebuild-tools/decode_logical_fixture.py" b2 > "$OUT/frozen.txt"
cmp "$OUT/frozen.txt" "$OUT/host.txt"
mapfile -t sources < <(find src/main/java src/test/java oracle -type f -name '*.java' | sort)
if [[ -n "${JAVA_HOME_17_X64:-}" && -x "$JAVA_HOME_17_X64/bin/javac" ]]; then
    test "$("$JAVA_HOME_17_X64/bin/java" -XshowSettings:properties -version 2>&1 | awk '/java.specification.version =/ {print $3}')" = 17
    "$JAVA_HOME_17_X64/bin/java" -version
    "$JAVA_HOME_17_X64/bin/javac" --release 17 -Xlint:all -Werror -d "$OUT/portable" "${sources[@]}" "$OUT/portable-source/PolicyModeTest.java"
    PORTABLE_JAVA="$JAVA_HOME_17_X64/bin/java"
elif [[ "${REQUIRE_JDK17:-0}" == 1 ]]; then
    echo 'BLOCKED: exact installed JDK17 compiler required; no automatic installation.' >&2
    exit 1
else
    echo 'Local JDK21 behavior fallback only; hosted installed JDK17 remains required.'
    "$HOST_JAVA" -m jdk.compiler/com.sun.tools.javac.Main -source 21 -target 21 -Xlint:all -Werror -d "$OUT/portable" "${sources[@]}" "$OUT/portable-source/PolicyModeTest.java"
    PORTABLE_JAVA="$HOST_JAVA"
fi
"$PORTABLE_JAVA" -cp "$OUT/portable" vn.fonesi.rebuild.logical.LogicalContractTest
"$PORTABLE_JAVA" -cp "$OUT/portable" vn.fonesi.rebuild.logical.PolicyModeTest
"$PORTABLE_JAVA" -cp "$OUT/portable" vn.fonesi.rebuild.logical.LogicalVectors > "$OUT/portable.txt"
cmp "$OUT/frozen.txt" "$OUT/portable.txt"
cmp "$OUT/host.txt" "$OUT/portable.txt"
echo "PASS $(wc -l < "$OUT/portable.txt") frozen B2 logical vectors in separate host/portable processes; no Android/provider/storage integration"
