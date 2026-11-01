# Original-scope style value core — bounded implementation contract

Scope: SUP-S01/S02/S03, original APK-P6-T25 with T13/T15/T28 integration still unimplemented. Independent of V1 embedded engine versus proposed V2 work profile. No task/requirement acceptance, architecture choice, UI/DB/shortcut/auth/permission/manifest/library change implied. Fresh Java source only; historical implementation is not copied.

Authority: exact immutable `docs/requirements.json` rows SUP-S01/S02/S03 and `reference/history/local-rebuild/docs/REBUILD_R1_DECISIONS.md` STYLE-R1-01 lines19–25. Six accents and inert bounded badge are already required. This is a small real validation component, not a fake engine or a claim the whole style feature is finished.

## Input/output API (internal Java only, no external format)

`StyleValue.validateFields(Map<String, ?>)` accepts exactly two internal typed fields `accentKey` and `badgeText`; both keys required, no others. Accent must be a nonnull String matching exactly teal/blue/purple/amber/red/slate. Badge may be null or String, never a bitmap/resource/URL/signature object. These Java member labels do **not** define a config/storage serialization format; no JSON/file reader is added. `StyleValue.newDefault()` constructs explicit valid teal/null input, not an error fallback.

Return immutable result with either precise error enum or validated presentation plus original raw badge and exact accent; no logging/input echo on error. Error result has no renderable presentation. Preserve null versus empty versus padded valid badge raw value: trim/default only the display string. Never mutate caller map, catalog identity or persisted bytes. An explicit repair/storage adapter is future separate work; this core cannot rewrite invalid stored data.

## Badge validation order

1. Iterate Unicode code points without lossy UTF-8 conversion. Reject unpaired surrogates and any CONTROL, FORMAT, UNASSIGNED, PRIVATE_USE, SURROGATE, LINE_SEPARATOR or PARAGRAPH_SEPARATOR category **anywhere in the raw text**, before trim. Thus newline/tab/control at an edge cannot be silently normalized away.
2. Presentation trim removes leading/trailing code points satisfying `Character.isWhitespace` or `Character.isSpaceChar` only after prohibited-category rejection. This retains legal Unicode text unchanged inside the visible portion. Null or empty visible portion displays `F`; stored/raw value is not replaced.
3. Otherwise require1–2 visible code points and at most16 UTF-8 bytes. Reject rather than truncate. Supplementary characters count as one code point. Host Unicode tables prove host behavior only; Android/OEM category/rendering parity remains separate.
4. Never parse/dereference glyphs as path/resource/HTML/URL/signature. Short strings such as `..`, `./`, `@x`, `C:` are inert text if they satisfy the character/count rules. Unknown `path`, `url`, `resource`, `bitmap` or `signature` fields and non-String badge payloads are rejected structurally, not by unreliable path-looking heuristics.

## Test and evidence boundary

Affected tests only: six exact accents and explicit default; unknown/case/whitespace/null accents; exact fieldset/missing/unknown/nonstrings; null/empty/spaces/NBSP presentation;1/2/3 code-point boundaries including supplementary; each forbidden Unicode category and lone-surrogate variants before trim;16-byte rule preserved (the2-code-point rule is stricter for validUnicode); inert filename-like glyphs; input map/raw-value unchanged on success/failure; invalid result cannot supply presentation; later caller map edits cannot change an earlier immutable result.

No file/network/resource/classloading/reflection/Android references in this core. No renderer, persistence, repair transaction, stale-row lifecycle concurrency or device proof claimed. Tests use owned in-memory values; no account/private-data inputs. A focused script compiles only this new core/test and does not rerun unchanged update-preflight suites. Local stripped JDK21 can produce LIMITED_HOST results only; JDK17/Android evidence needs its separate actual environment. Existing CI receipt counts are not modified until a reviewed integration decision.
