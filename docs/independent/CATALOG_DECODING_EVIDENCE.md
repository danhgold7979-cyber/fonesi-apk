# Catalog decoding evidence — bounded original scope

Fresh Java source `CatalogKey.java` implements exact opaque composite identity
from explicitly UTF8-proven TEXT bytes, strict REPORT/roundtrip decoding and fixed
transport budgets. It does not read or write SQLite, open paths, normalize identity,
apply provider/style grammar or grant mutation/guest authority.

Focused `bash tools/test_catalog_key.sh --limited-jre`: **151 owned assertions PASS**
on local JDK21 module compiler with source17/target17. This is **LIMITED_HOST**, not
release17 API compatibility or actual Android evidence. No prior unchanged style,
update or APK tests were rerun merely for this new standalone slice. New tests are
not yet part of hosted HOST receipts; existing hosted9b760a81 result does not cover
this new class.

Covered exact composite/namespace/case/Unicode/hash-collision identity, null/type
rejection, empty/NBSP/NUL/control values, malformed UTF8 families/no replacement,
4096UTF16 and raw16384-byte processing guards, supplementary counts, raw input
retention and immutable decoded result. A malformed16384-byte fixture reaches the
strict decoder while16385bytes fails the raw budget; no false claim of a valid
16384-byte identity fitting the4096UTF16 limit.

Future adapter must prove UTF8 database transport and stable input ownership; no
atomic snapshot of concurrently modified caller arrays is promised. Invalid DB
bytes are retained by a future reader, not by a Java String conversion. Renderer,
storage/schema migration, catalog generation, access/commit linearization and
Android/OEM proof remain separate open obligations. V1 task counts and V2 proposal
status are unchanged. The accompanying writer-generation plan remains DRAFT only.
