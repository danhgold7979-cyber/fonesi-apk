"""Conservative APK-input delta gate; reused proof never becomes new COMPILE evidence."""
import json
import os
from pathlib import Path
import subprocess

# Independently verified HOST/COMPILE snapshot; not product/runtime acceptance.
ACCEPTED_SOURCE = '7367a445d83e602aba1fcf9b526b3f7ae35bfc92'
ACCEPTED_TREE = '3212d99077cf7f3148a924cafb4e8c4f3657e133'
PRIOR_RUN = '41323172540'
HOST_ONLY = {
    'README.md',
    'core/src/test/java/vn/fonesi/apk/foundation/CatalogKeyTest.java',
    'tools/test_catalog_key.sh', 'tools/test_style_migration.py',
    'core/src/test/java/vn/fonesi/apk/foundation/StyleValueTest.java',
    'tools/test_style_core.sh', 'tools/run_host_checks.py',
    'tools/check_plan.py', 'tools/test_plan_integrity.py',
    'tools/record_baseline.py', 'tools/test_baseline_receipt.py',
    'tools/compile_scope.py', 'tools/test_host_integration.py',
}


def requires_compile(paths, event):
    # Manual verification explicitly requests a fresh compile. Unknown paths fail closed.
    return event == 'workflow_dispatch' or any(
        path not in HOST_ONLY and not path.startswith('docs/') for path in paths)


def comparison_paths(root, sha):
    # Exact source/tree identity and ancestry are mandatory. Missing Git objects or
    # any Git error propagate; there is no empty-diff fallback or ref mutation.
    tree = subprocess.check_output(
        ['git', 'rev-parse', ACCEPTED_SOURCE + '^{tree}'], cwd=root, text=True).strip()
    assert tree == ACCEPTED_TREE, 'Retained comparison tree mismatch'
    subprocess.run(['git', 'merge-base', '--is-ancestor', ACCEPTED_SOURCE, sha],
                   cwd=root, check=True)
    raw = subprocess.check_output(['git', 'diff', '--no-renames', '--name-only', '-z',
                                   ACCEPTED_SOURCE, sha], cwd=root)
    return [path for path in raw.decode('utf-8', errors='strict').split('\0') if path]


def main():
    root = Path(__file__).resolve().parents[1]
    sha = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip()
    assert sha == os.environ['EXPECTED_SOURCE_SHA']
    paths = comparison_paths(root, sha)
    needed = requires_compile(paths, os.environ['GITHUB_EVENT_NAME'])
    receipt = {'source_sha': sha, 'comparison_source': ACCEPTED_SOURCE,
               'changed_paths': paths, 'compile_required': needed,
               'scope': 'INPUT_COMPARISON_NOT_COMPILE_ACCEPTANCE',
               'comparison_tree': ACCEPTED_TREE,
               'prior_run': PRIOR_RUN, 'new_COMPILE': 'NOT_RUN',
               'ANDROID': 'NOT_RUN', 'PROVIDER': 'NOT_RUN', 'PHONE': 'NOT_RUN'}
    (root / 'compile-scope.json').write_text(json.dumps(receipt, indent=2) + '\n')
    with open(os.environ['GITHUB_OUTPUT'], 'a') as output:
        output.write('compile_required=' + str(needed).lower() + '\n')
    print(json.dumps(receipt))


if __name__ == '__main__':
    main()
