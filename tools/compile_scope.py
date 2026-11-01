"""Conservative APK-input delta gate; reused proof never becomes new COMPILE evidence."""
import json
import os
from pathlib import Path
import subprocess

ACCEPTED_SOURCE = 'a3372fa6be3b6872bd5f4c402ebd72c233701127'
HOST_ONLY = {
    'README.md',
    'core/src/main/java/vn/fonesi/apk/foundation/CatalogKey.java',
    'core/src/test/java/vn/fonesi/apk/foundation/CatalogKeyTest.java',
    'tools/test_catalog_key.sh', 'tools/test_style_migration.py',
    'storage/sql/style_columns_3_to_4.sql',
    'core/src/main/java/vn/fonesi/apk/foundation/StyleValue.java',
    'core/src/test/java/vn/fonesi/apk/foundation/StyleValueTest.java',
    'tools/test_style_core.sh', 'tools/run_host_checks.py',
    'tools/check_plan.py', 'tools/test_plan_integrity.py',
    'tools/record_baseline.py', 'tools/test_baseline_receipt.py',
    'tools/compile_scope.py', 'tools/test_host_integration.py',
    '.github/workflows/phase-foundation.yml',
}


def requires_compile(paths, event):
    # Manual verification explicitly requests a fresh compile. Unknown paths fail closed.
    return event == 'workflow_dispatch' or any(
        path not in HOST_ONLY and not path.startswith('docs/') for path in paths)


def main():
    root = Path(__file__).resolve().parents[1]
    sha = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip()
    assert sha == os.environ['EXPECTED_SOURCE_SHA']
    # No empty/missing-base fallback: Git failure prevents the downstream job decision.
    subprocess.run(['git', 'merge-base', '--is-ancestor', ACCEPTED_SOURCE, sha], cwd=root, check=True)
    paths = subprocess.check_output(['git', 'diff', '--no-renames', '--name-only', '-z',
                                     ACCEPTED_SOURCE, sha], cwd=root).decode().split('\0')
    paths = [path for path in paths if path]
    needed = requires_compile(paths, os.environ['GITHUB_EVENT_NAME'])
    receipt = {'source_sha': sha, 'comparison_source': ACCEPTED_SOURCE,
               'changed_paths': paths, 'compile_required': needed,
               'scope': 'INPUT_COMPARISON_NOT_COMPILE_ACCEPTANCE',
               'prior_run': '41290554652', 'new_COMPILE': 'NOT_RUN',
               'ANDROID': 'NOT_RUN', 'PROVIDER': 'NOT_RUN', 'PHONE': 'NOT_RUN'}
    (root / 'compile-scope.json').write_text(json.dumps(receipt, indent=2) + '\n')
    with open(os.environ['GITHUB_OUTPUT'], 'a') as output:
        output.write('compile_required=' + str(needed).lower() + '\n')
    print(json.dumps(receipt))


if __name__ == '__main__':
    main()
