#!/usr/bin/env python3
"""Verify an intentional offline root refusal, not an arbitrary failed Gradle command."""
from pathlib import Path
import sys

MARKER = 'FONESI_LEGACY_ROOT_BUILD_DISABLED: use ./gradlew -p rebuild-android; legacy source is reference-only.'
MAX_LOG_BYTES = 1024 * 1024


def verify(status, log):
    if type(status) is not int or not 1 <= status <= 255:
        raise ValueError('Root invocation did not fail as required')
    if len(log) > MAX_LOG_BYTES:
        raise ValueError('Root refusal log exceeds budget')
    text = log.decode('utf-8')
    lines = text.splitlines()
    if MARKER not in lines or '* What went wrong:' not in lines or 'settings.gradle.kts' not in text:
        raise ValueError('Missing exact root-settings refusal evidence')
    if not any(line.startswith('BUILD FAILED') for line in lines):
        raise ValueError('Missing Gradle failure completion')
    if any(line.startswith('> Task ') or line.startswith('Downloading ') for line in lines):
        raise ValueError('Root invocation executed tasks or attempted download')


def main():
    if len(sys.argv) != 3:
        raise ValueError('Expected exit status and temporary log path')
    with Path(sys.argv[2]).open('rb') as source:
        log = source.read(MAX_LOG_BYTES + 1)
    verify(int(sys.argv[1]), log)
    print('PASS exact offline legacy-root refusal; no legacy project tasks executed')


if __name__ == '__main__':
    main()
