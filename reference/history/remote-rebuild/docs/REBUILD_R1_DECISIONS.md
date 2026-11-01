# R1 dispositions recorded by root — 2026-10-31

**Current authoritative status — R1 DOCUMENTARY gate ACCEPTED by root on 2026-10-31**, based on reviewed candidate `13ac0bdcf45af2f0a4769991873e9bf7f8977f08` and independent re-review. Acceptance covers the specification/evidence map and ADR-R1-01, STYLE-R1-01 and EXEC-ID-R1-01 only. It is not product architecture, runtime or feature acceptance. R3–R7, Android/provider/phone evidence, source replacement and release remain open; no code, dependency, workflow, environment, license, provider or device action is authorized. No APK delivery is needed for this documentation update. Earlier candidate/pending wording below is historical preparation, superseded only for R1 documentary status.

## Historical candidate preparation and review

Root reviewed checkpoint `c94e1bb897ff56ce24b24677784b167dcec8357e` and explicitly adjudicated the three decisions below. This records that authority; it does **not** accept R1, the whole product architecture, runtime behavior or a future implementation. No source or environment action is authorized by this document.

## ADR-R1-01 — current native implementation direction

At E82 `2fac016e3b28e4d9f21390c221cad1df7a382c86:docs/PLAN.md:L50–L53`, the heading is “Kiến trúc dự kiến” (proposed architecture), followed by native Kotlin/Jetpack Compose and Room/optional DataStore. Root approves **native Java/View and SQLite as the current implementation direction replacing that historical proposed recipe**.

Preserve all native UX, accessibility, metadata durability, migration, access-control and provider obligations. Room and DataStore remain NOT IMPLEMENTED, not falsely satisfied by using another library. Existing bounded scaffold/reader approvals and compile receipts are not whole-product architecture or runtime acceptance. Future provider integration still needs its own reviewed design and acceptance evidence.

## STYLE-R1-01 — precise interpretation of broad style prose

Historical E82 `docs/phases/E19_CLONE_STYLE_PLAN.md:L11` rejects arbitrary color/CSS/URL/bitmap/path/signature input; its typed validator is at `app/src/main/java/vn/fonesi/app/storage/CloneVisualStyle.kt:L7–L29`. Root explicitly interprets the broader prose as follows, rather than claiming it was already precise:

- The style schema is closed and typed: only accent key and plain badge text. Reject unknown fields; do not ignore them or allow an extension payload. Exact external serialization field names/version, if later introduced, need a reviewed format; no import format is activated here.
- Accent allows exactly `teal`, `blue`, `purple`, `amber`, `red`, `slate`, with default `teal`. Historical per-clone red is not app branding authorization.
- Retain the existing badge rules: visible badge of 1–2 code points after presentation trimming; null/empty presents `F`; at most 16 UTF-8 bytes; reject control, FORMAT, UNASSIGNED, PRIVATE_USE, SURROGATE, line separator and paragraph separator categories. These are presentation rules, not catalog identity normalization.
- Reject resource references and CSS/URL/bitmap/signature payloads as typed style input. Badge text is never parsed, dereferenced or executed as a path, URL, HTML, signature or resource identifier. A short otherwise allowed glyph string remains plain text even if it could also be a filename. Do not introduce unreliable “looks like a path” heuristics rejecting ordinary glyphs.
- Invalid or unsupported stored input is retained unchanged and unavailable for unsafe rendering. Repair requires an explicit validated operation; do not silently normalize, rewrite or replace persisted input with a default. Presentation trim/default for valid null/empty values does not authorize persistence mutation or conceal malformed input. Original rows/opaque identity remain preserved.
- Required tests: exact unknown-field rejection; all six accent/default and badge limits/categories; no I/O, resource loading, dereference or execution for any badge; rejection preserves original stored bytes; explicit validated repair/readback; stale/busy/lifecycle races cannot apply a different row's style.
- Unicode category differences and actual rendering across Android/OEM/launcher versions require separate runtime acceptance. JDK17 category tests or Android compile success do not establish runtime Unicode/rendering behavior.

This interpretation resolves the prose ambiguity at the requirement level. The style feature, editor, codec and repair path remain NOT IMPLEMENTED in the fresh rebuild, and no implementation scope is granted here.

## EXEC-ID-R1-01 — display identity is not execution authority

`CatalogKey` remains exact display metadata `(engine_key, clone_id)`, including schema-valid opaque strings; it is never executable authority. UUIDs in the existing synthetic kernel are internal operation/instance references, not conversions of legacy opaque IDs.

Any future execution bridge must bind, through a reviewed current readback/receipt:

1. The exact catalog key, without parsing, normalization, hash remapping or replacing retained IDs.
2. Verified provider identity, revision and environment.
3. The actual provider handle and current generation.
4. The authorized operation and complete affected scope.

Access, foreground and scope checks remain independent and must be rechecked at their own boundaries; an identity match does not grant authorization. Unknown legacy bindings remain quarantined, with original data retained and no dispatch.

The exact provider adapter and mapping format can only be reviewed after provider selection. They remain **BLOCKED / NOT IMPLEMENTED**; this decision supplies constraints, not a guessed provider or permission to wire operations. Provider selection, actual runtime authority and receipt verification remain separate acceptance gates.

## Remaining gate

These three requirement-level dispositions are recorded as approved by root. Independent re-review must check consistency and coverage, then root may decide R1 documentary acceptance. R1 remains OPEN until that explicit decision. Product implementation, Android/provider/phone evidence, runtime provisioning and legacy replacement remain independently unaccepted.
