#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
(cd .. && sha256sum --check spec/bridge-oracle/b1c/source-sha256.txt)
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT HUP INT TERM
mapfile -t sources < <(find src/main/java src/test/java -type f -name '*.java' ! -name 'BridgeOracleTest.java' | sort)
((${#sources[@]} > 0))
java -m jdk.compiler/com.sun.tools.javac.Main -source 21 -target 21 -Xlint:all -Werror -d "$OUT" "${sources[@]}"
java -cp "$OUT" vn.fonesi.rebuild.KernelTest
java -cp "$OUT" vn.fonesi.rebuild.PolicyModeTest
java -cp "$OUT" vn.fonesi.rebuild.JournalTest
java -cp "$OUT" vn.fonesi.rebuild.DispatcherTest
java -cp "$OUT" vn.fonesi.rebuild.IdentityTextTest
mkdir "$OUT/legacy"
mapfile -t pinned < <(sed 's/^[0-9a-f]*  //' ../spec/bridge-oracle/v1/source-sha256.txt | sort)
mapfile -t found < <(cd ../spec/bridge-oracle/v1/source && find . -type f -name '*.java' -printf '%P\n' | sort)
[[ "${pinned[*]}" == "${found[*]}" ]] || { echo 'Unexpected historical oracle source set' >&2; exit 1; }
legacy=()
for source in "${pinned[@]}"; do legacy+=("../spec/bridge-oracle/v1/source/$source"); done
java -m jdk.compiler/com.sun.tools.javac.Main -source 21 -target 21 -Xlint:all -Werror -d "$OUT/legacy" "${legacy[@]}" src/test/java/vn/fonesi/rebuild/BridgeOracleTest.java
java -cp "$OUT/legacy" vn.fonesi.rebuild.BridgeOracleTest

# Historical D3 evidence is isolated and never a desired production acceptance target.
(cd ../spec/bridge-oracle/b1b/source && sha256sum --check ../source-sha256.txt)
mkdir "$OUT/b1b-observed" "$OUT/b1b-test-source"
sed 's/^package vn.fonesi.rebuild.logical;/package vn.fonesi.rebuild;/' ../spec/logical-bridge/b1b-observed/LogicalVectors.java > "$OUT/b1b-test-source/LogicalVectors.java"
OLD=../spec/bridge-oracle/b1b/source/rebuild-kernel/src/main/java/vn/fonesi/rebuild
java -m jdk.compiler/com.sun.tools.javac.Main -source 21 -target 21 -Xlint:all -Werror -d "$OUT/b1b-observed" "$OLD/IdentityText.java" "$OLD/CapabilityPolicy.java" "$OLD/OperationProtocol.java" "$OUT/b1b-test-source/LogicalVectors.java"
java -cp "$OUT/b1b-observed" vn.fonesi.rebuild.LogicalVectors > "$OUT/b1b.txt"
python3 ../rebuild-tools/decode_logical_fixture.py b1b-observed > "$OUT/b1b-frozen.txt"
cmp "$OUT/b1b-frozen.txt" "$OUT/b1b.txt"
echo 'PASS historical D3 observations retained unchanged; unsafe permits NOT desired acceptance'
