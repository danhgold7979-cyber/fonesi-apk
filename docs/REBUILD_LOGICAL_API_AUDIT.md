# B2 Java17 logical module API review — 2026-10-30

Scope: four files under `rebuild-logic/src/main/java/vn/fonesi/rebuild/logical`. No Android import or packaging. Audit is independent of hosted `javac --release17`; neither proves Android runtime behavior. No dependencies/core-library desugaring added.

| Actual production call surface | Primary Android reference | Availability |
|---|---|---|
| Character.isHighSurrogate/isLowSurrogate/charCount | [Character](https://developer.android.com/reference/java/lang/Character) | API1. Explicit B1b Cc/whitespace ranges avoid OS-dependent Unicode category tables. |
| String.charAt/codePointAt/length/equals/matches/hashCode, isEmpty | [String](https://developer.android.com/reference/java/lang/String) | At/below API9 (isEmpty9); no isBlank/strip/repeat in production. |
| Objects.requireNonNull/equals/hashCode(Object) | [Objects](https://developer.android.com/reference/java/util/Objects) | API19. Boxed primitive Object hashes use ordinary wrapper instances; no newer static primitive hash overload assumption. |
| Math.incrementExact(long) | [Math](https://developer.android.com/reference/java/lang/Math#incrementExact(long)) | API24, below min26. Reachable revisions already bounded to0–2. |
| HashSet/HashMap constructors/add/put, Map.Entry, collection equality/contains/keySet/values, Collections.unmodifiableSet/unmodifiableMap | [Collections](https://developer.android.com/reference/java/util/Collections) | Established API1 surface; defensive copies before wrappers, null source/elements/keys/values reject. No factories/copyOf/streams/default compute APIs. |
| UUID equals/hashCode/toString; Object/Enum equality/name/hash; standard exceptions/StringBuilder generated string concatenation | [UUID](https://developer.android.com/reference/java/util/UUID), [Enum](https://developer.android.com/reference/java/lang/Enum) | Established platform surface. Enum identity hashes are process-local, never serialized as golden values. |

Source uses final classes/enums, generic collections, loops, ordinary switch, primitive fields and classic instanceof/casts. No records, lambdas, pattern-switch, threads, file/network/device APIs or reflection. Java17 javac may emit string-concat invokedynamic; future Android import still requires actual D8/lint/package gate, not inference from this audit. Test harness uses newer host APIs outside production; it must never join an Android source set.

Three focused Python tripwires check reviewed source surface/known incompatible helpers, absent Android wiring and compare-only JDK17 CI. They are not a general API verifier. Actual minSdk26 execution and hostile guest/ownership/durability tests remain NOT RUN; no SDK install, license, device or emulator action performed.

Hosted follow-up: exact headf5a11ac7/run41130512434 passed actual installed JDK17.0.21 compile/runtime parity for this module (70 value +6,200 policy checks and17,317 frozen vectors). It remains outside Android app sources, so that run's Android compile success does **not** D8/package or execute this logical module. API26/D8/runtime acceptance stays separate.
