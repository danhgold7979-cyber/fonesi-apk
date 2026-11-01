# Fonesi APK — fresh rebuild

A native Android no-root multi-instance manager being rebuilt from preserved requirements, not old implementation code. **No provider/runtime accepted; no working clone claim.**

- [Current master plan](docs/MASTER_PLAN.md) — 8 phases, 32 original steps, 4 phone gates.
- [Current status](docs/status.json) — no inherited historical acceptance.
- [362 exact requirements](docs/requirements.json) and [ownership rationale](docs/MAPPING_RATIONALE.md).
- [Detailed tasks](docs/TASKS.md), [archive/reset manifest](docs/clean-tree-manifest.json).
- [Historical references](reference/README.md) — immutable requirements/UI/notices/schema, not active source.

Run `python3 tools/check_plan.py` for documentary integrity only. Documentary checkpoint independently reviewed; bounded T03 foundation source is present, not product accepted. Run `python3 tools/test_shell_contract.py` and `bash tools/test_core.sh` for independent static/host checks. This workspace supports only the explicitly limited `--limited-jre` host option; Android compile/lifecycle/provider/phone evidence remains separate. Java/View + SQLite; six fixed accents; original green branding retained. APK chat updates use yellow only. Android/provider/phone tests are not run.

Phase-0 documentary tasks T01/T02 are accepted (2/4 phase tasks, not functionality). T03/T04 local source preparation includes a [bounded target36 lint policy](docs/FOUNDATION_LINT_POLICY.md) and [baseline receipt/reproduction contract](docs/P0_BASELINE_REPRODUCTION.md). Actual exact-batch integrated APK proof is still pending; no verified downloadable APK is asserted.
