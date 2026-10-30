#!/usr/bin/env python3
"""Bounded transport decoder; byte identity verification, never fixture regeneration."""
import base64
import binascii
import hashlib
import json
from pathlib import Path
import sys
import zlib

MAX_ENCODED = 128 * 1024
MAX_COMPRESSED = 96 * 1024
MAX_DECODED = 2 * 1024 * 1024
ROOT = Path(__file__).resolve().parents[1]
EXPECTED = {
    'b1b-observed': (1129640, '330acb1152da6f329792ea36628731f270183f341c4d6f37185da6c7ae679942', 17317),
    'b2': (1141217, '5524e281c11e827f6e22dfe62c21c78b1a51f2ef79e66a46248dd4ae62cca78a', 17317),
}
FIELDS = {'encoded_bytes', 'encoded_sha256', 'compressed_bytes', 'decoded_bytes', 'decoded_sha256', 'lines'}


def read_bounded(path, limit):
    with path.open('rb') as source:
        data = source.read(limit + 1)
    if len(data) > limit:
        raise ValueError('Input exceeds transport budget')
    return data


def decode(encoded, spec):
    if not isinstance(spec, dict) or set(spec) != FIELDS:
        raise ValueError('Unknown transport metadata')
    for key, limit in (('encoded_bytes', MAX_ENCODED), ('compressed_bytes', MAX_COMPRESSED),
                       ('decoded_bytes', MAX_DECODED), ('lines', MAX_DECODED)):
        if type(spec[key]) is not int or not 0 < spec[key] <= limit:
            raise ValueError('Invalid bounded metadata')
    for key in ('encoded_sha256', 'decoded_sha256'):
        value = spec[key]
        if not isinstance(value, str) or len(value) != 64 or any(c not in '0123456789abcdef' for c in value):
            raise ValueError('Invalid digest metadata')
    if len(encoded) > MAX_ENCODED or len(encoded) != spec['encoded_bytes']:
        raise ValueError('Encoded size mismatch')
    if hashlib.sha256(encoded).hexdigest() != spec['encoded_sha256']:
        raise ValueError('Encoded digest mismatch')
    if not encoded.endswith(b'\n') or b'\n' in encoded[:-1]:
        raise ValueError('Noncanonical transport lines')
    try:
        compressed = base64.b64decode(encoded[:-1], validate=True)
    except binascii.Error as error:
        raise ValueError('Invalid base64') from error
    if base64.b64encode(compressed) + b'\n' != encoded:
        raise ValueError('Noncanonical base64')
    if len(compressed) != spec['compressed_bytes'] or len(compressed) > MAX_COMPRESSED:
        raise ValueError('Compressed size mismatch')
    stream = zlib.decompressobj(16 + zlib.MAX_WBITS)
    try:
        data = stream.decompress(compressed, spec['decoded_bytes'] + 1)
    except zlib.error as error:
        raise ValueError('Invalid gzip integrity') from error
    if not stream.eof or stream.unused_data or stream.unconsumed_tail:
        raise ValueError('Incomplete, oversized or extra gzip data')
    if len(data) != spec['decoded_bytes'] or hashlib.sha256(data).hexdigest() != spec['decoded_sha256']:
        raise ValueError('Decoded identity mismatch')
    if not data.endswith(b'\n') or data.count(b'\n') != spec['lines']:
        raise ValueError('Decoded line count mismatch')
    return data


def metadata_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError('Duplicate transport metadata key')
        result[key] = value
    return result


def load(name):
    if name not in EXPECTED:
        raise ValueError('Unknown fixture name')
    base = ROOT / 'spec/logical-bridge'
    metadata = json.loads(read_bounded(base / 'transport-manifest.json', 4096), object_pairs_hook=metadata_object)
    if not isinstance(metadata, dict) or set(metadata) != set(EXPECTED):
        raise ValueError('Unknown fixture manifest')
    spec = metadata[name]
    if not isinstance(spec, dict) or set(spec) != FIELDS:
        raise ValueError('Invalid fixture metadata object')
    if (spec['decoded_bytes'], spec['decoded_sha256'], spec['lines']) != EXPECTED[name]:
        raise ValueError('Historical decoded provenance drift')
    return decode(read_bounded(base / name / 'observations.txt.gz.b64', MAX_ENCODED), spec)


def main():
    if len(sys.argv) != 2:
        raise ValueError('Expected one fixture name')
    # Output only after complete bounded decode + identity checks; caller owns temporary destination.
    sys.stdout.buffer.write(load(sys.argv[1]))


if __name__ == '__main__':
    main()
