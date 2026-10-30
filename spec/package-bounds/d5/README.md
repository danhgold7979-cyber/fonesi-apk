# D5 bounded package validation revision

The three existing package validators now reject lengths above255 before evaluating the unchanged regex. Null/grammar/valid boundary semantics and wirev2 are unchanged. This is a defensive validation-work ordering correction, not a new identity grammar or provider capability.

`historical-source/` preserves byte-identical five B3b host and four B2 portable classes. Existing `spec/bridge-oracle/b3b/source-sha256.txt` and both B2 source manifests are unchanged and now verified against this archive. New current source pins are in this directory. Historical B3a, v1 and D3 observations remain separate and unchanged. The B2 desired fixture remains byte-identical and compares current host/portable behavior.

Executed scope: ordinary <=257-character constructor regressions plus source-order proof at exactly three checks. The separately requested malformed-envelope reproduction was denied by platform screening and was not retried or substituted. No long malformed-envelope outcome is claimed. See `docs/REBUILD_PACKAGE_BOUNDS_DEFECT.md` for approval and limits. B3c pure-port files are a separate unpublished draft, not this checkpoint's acceptance claim.
