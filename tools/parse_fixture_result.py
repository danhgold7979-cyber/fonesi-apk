"""Pure bounded text decoding. Never executes commands or grants Android acceptance."""
from dataclasses import dataclass
import re

CASES = ('missing', 'fresh_read', 'commit_readback', 'stale', 'cancelled')
TARGET = 'vn.fonesi.fixture.storage'
SCHEMA = 'owned-storage-fixture-text-v2'

@dataclass(frozen=True)
class ParsedFixtureResult:
    source_sha: str
    counts: tuple[int, ...]


def parse_result(raw: bytes, expected_source: str) -> ParsedFixtureResult:
    """Caller separately proves execution identity, freshness, exit and timeout."""
    if type(raw) is not bytes or not 0 < len(raw) <= 8192:
        raise ValueError('Invalid fixture text')
    if type(expected_source) is not str or not re.fullmatch('[0-9a-f]{40}', expected_source):
        raise ValueError('Invalid expected source')
    try:
        text = raw.decode('utf-8', errors='strict')
    except UnicodeError:
        raise ValueError('Invalid fixture text') from None
    text = text.replace('\r\n', '\n')
    if '\r' in text:
        raise ValueError('Invalid fixture text')
    if text.endswith('\n'):
        text = text[:-1]
    lines = text.split('\n')
    expected = {'schema': SCHEMA, 'target': TARGET, 'source_sha': expected_source,
                'cases': ','.join(CASES), 'result': 'PASS'}
    keys = set(expected) | {'assertions_' + case for case in CASES}
    if len(lines) != len(keys) + 1 or lines[-1] != 'INSTRUMENTATION_CODE: -1':
        raise ValueError('Invalid fixture text')
    values = {}
    for line in lines[:-1]:
        if not line.startswith('INSTRUMENTATION_RESULT: '):
            raise ValueError('Invalid fixture text')
        key, sep, value = line[len('INSTRUMENTATION_RESULT: '):].partition('=')
        if not sep or key not in keys or key in values:
            raise ValueError('Invalid fixture text')
        values[key] = value
    if set(values) != keys or any(values[k] != v for k, v in expected.items()):
        raise ValueError('Invalid fixture text')
    counts = []
    for case in CASES:
        value = values['assertions_' + case]
        if not re.fullmatch('[1-9][0-9]{0,9}', value) or int(value) > 2147483647:
            raise ValueError('Invalid fixture text')
        counts.append(int(value))
    return ParsedFixtureResult(expected_source, tuple(counts))
