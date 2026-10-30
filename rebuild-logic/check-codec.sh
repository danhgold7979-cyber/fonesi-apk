#!/usr/bin/env bash
# B3c compare-only pure codec/key proof. Never refresh frozen vectors in CI.
set -euo pipefail
cd "$(dirname "$0")"
ROOT=$(cd .. && pwd)
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT HUP INT TERM
(cd "$ROOT" && sha256sum --check spec/codec-bridge/b3c/source-sha256.txt)
(cd "$ROOT" && sha256sum --check spec/codec-bridge/b3c/fixture-sha256.txt)
HOST_JAVA=${JAVA_HOME_21_X64:+$JAVA_HOME_21_X64/bin/java}
HOST_JAVA=${HOST_JAVA:-$(command -v java)}
mkdir -p "$OUT/host" "$OUT/portable" "$OUT/source"
sed 's/vn\.fonesi\.rebuild\.logical/vn.fonesi.rebuild/g' codec-oracle/shared/CodecContractTest.java > "$OUT/source/CodecContractTest.java"
HOST_SOURCE="$ROOT/rebuild-kernel/src/main/java/vn/fonesi/rebuild"
"$HOST_JAVA" -m jdk.compiler/com.sun.tools.javac.Main -source 21 -target 21 -Xlint:all -Werror -d "$OUT/host" \
    "$HOST_SOURCE/IdentityText.java" "$HOST_SOURCE/CapabilityPolicy.java" "$HOST_SOURCE/OperationProtocol.java" \
    "$HOST_SOURCE/OperationJournal.java" "$HOST_SOURCE/SharedUpdateDispatcher.java" \
    "$OUT/source/CodecContractTest.java" codec-oracle/host/CodecAdapter.java
"$HOST_JAVA" -cp "$OUT/host" vn.fonesi.rebuild.CodecContractTest
"$HOST_JAVA" -cp "$OUT/host" vn.fonesi.rebuild.CodecContractTest --emit-observations > "$OUT/host.txt"
cmp "$ROOT/spec/codec-bridge/b3c/desired.txt" "$OUT/host.txt"
mapfile -t sources < <(find src/main/java -type f -name '*.java' | sort)
if [[ -n "${JAVA_HOME_17_X64:-}" && -x "$JAVA_HOME_17_X64/bin/javac" ]]; then
    test "$("$JAVA_HOME_17_X64/bin/java" -XshowSettings:properties -version 2>&1 | awk '/java.specification.version =/ {print $3}')" = 17
    "$JAVA_HOME_17_X64/bin/java" -version
    "$JAVA_HOME_17_X64/bin/javac" --release 17 -Xlint:all -Werror -d "$OUT/portable" \
        "${sources[@]}" codec-oracle/shared/CodecContractTest.java codec-oracle/portable/CodecAdapter.java
    PORTABLE_JAVA="$JAVA_HOME_17_X64/bin/java"
elif [[ "${REQUIRE_JDK17:-0}" == 1 ]]; then
    echo 'BLOCKED: exact installed JDK17 compiler required; no automatic installation.' >&2
    exit 1
else
    echo 'Local JDK21 codec behavior fallback only; actual installed JDK17 remains required.'
    "$HOST_JAVA" -m jdk.compiler/com.sun.tools.javac.Main -source 21 -target 21 -Xlint:all -Werror -d "$OUT/portable" \
        "${sources[@]}" codec-oracle/shared/CodecContractTest.java codec-oracle/portable/CodecAdapter.java
    PORTABLE_JAVA="$HOST_JAVA"
fi
"$PORTABLE_JAVA" -cp "$OUT/portable" vn.fonesi.rebuild.logical.CodecContractTest
"$PORTABLE_JAVA" -cp "$OUT/portable" vn.fonesi.rebuild.logical.CodecContractTest --emit-observations > "$OUT/portable.txt"
cmp "$ROOT/spec/codec-bridge/b3c/desired.txt" "$OUT/portable.txt"
cmp "$OUT/host.txt" "$OUT/portable.txt"
echo "PASS $(wc -l < "$OUT/portable.txt") frozen B3c pure codec/key vectors in separate host/portable processes; no Android/storage/provider integration"
