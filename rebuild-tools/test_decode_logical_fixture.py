import base64
import gzip
import hashlib
import json
from pathlib import Path
import tempfile
import unittest

import decode_logical_fixture as transport


def packed(data, compressed=None):
    compressed = gzip.compress(data, mtime=0) if compressed is None else compressed
    encoded = base64.b64encode(compressed) + b'\n'
    spec = {'encoded_bytes': len(encoded), 'encoded_sha256': hashlib.sha256(encoded).hexdigest(),
            'compressed_bytes': len(compressed), 'decoded_bytes': len(data),
            'decoded_sha256': hashlib.sha256(data).hexdigest(), 'lines': data.count(b'\n')}
    return encoded, spec


def resign(encoded, spec):
    return dict(spec, encoded_bytes=len(encoded), encoded_sha256=hashlib.sha256(encoded).hexdigest())


class DecodeFixtureTests(unittest.TestCase):
    def test_real_fixture_decoded_identity(self):
        for name, expected in transport.EXPECTED.items():
            data = transport.load(name)
            self.assertEqual((len(data), hashlib.sha256(data).hexdigest(), data.count(b'\n')), expected)

    def test_roundtrip(self):
        encoded, spec = packed(b'a=b\nc=d\n')
        self.assertEqual(transport.decode(encoded, spec), b'a=b\nc=d\n')

    def test_transport_bit_change(self):
        encoded, spec = packed(b'a=b\n')
        with self.assertRaises(ValueError): transport.decode(b'X' + encoded[1:], spec)

    def test_unknown_metadata(self):
        encoded, spec = packed(b'a=b\n')
        with self.assertRaises(ValueError): transport.decode(encoded, dict(spec, extra=1))

    def test_metadata_must_be_object(self):
        encoded, _ = packed(b'a=b\n')
        for spec in (None, [], 17, 'text'):
            with self.subTest(spec=spec), self.assertRaises(ValueError): transport.decode(encoded, spec)

    def test_unbounded_metadata(self):
        encoded, spec = packed(b'a=b\n')
        for key, value in [('decoded_bytes', transport.MAX_DECODED + 1), ('decoded_bytes', True),
                           ('encoded_bytes', -1), ('compressed_bytes', '22'), ('lines', 0)]:
            with self.subTest(key=key, value=value), self.assertRaises(ValueError):
                transport.decode(encoded, dict(spec, **{key: value}))

    def test_noncanonical_lines(self):
        encoded, spec = packed(b'a=b\n')
        for bad in (encoded[:-1], encoded + b'\n', encoded[:-1] + b'\r\n', b'\n' + encoded):
            with self.subTest(bad=bad), self.assertRaises(ValueError):
                transport.decode(bad, resign(bad, spec))

    def test_invalid_base64(self):
        encoded, spec = packed(b'a=b\n')
        bad = b'!' + encoded[1:]
        with self.assertRaises(ValueError): transport.decode(bad, resign(bad, spec))

    def test_noncanonical_base64_pad_bits(self):
        encoded, spec = packed(b'a=b\n', b'\x00')
        bad = b'AB==\n'  # Decodes like AA== but has nonzero unused pad bits.
        with self.assertRaises(ValueError): transport.decode(bad, resign(bad, spec))

    def test_compressed_length(self):
        encoded, spec = packed(b'a=b\n')
        with self.assertRaises(ValueError): transport.decode(encoded, dict(spec, compressed_bytes=1))

    def test_truncated_gzip(self):
        compressed = gzip.compress(b'a=b\n', mtime=0)
        for missing in (1, 8, len(compressed) - 1):
            with self.subTest(missing=missing), self.assertRaises(ValueError):
                transport.decode(*packed(b'a=b\n', compressed[:-missing]))

    def test_gzip_trailer_integrity(self):
        compressed = bytearray(gzip.compress(b'a=b\n', mtime=0)); compressed[-8] ^= 1
        with self.assertRaises(ValueError): transport.decode(*packed(b'a=b\n', bytes(compressed)))

    def test_gzip_extra_bytes(self):
        compressed = gzip.compress(b'a=b\n', mtime=0)
        for tail in (b'extra', b'\x00', gzip.compress(b'a=b\n', mtime=0)):
            with self.subTest(tail=tail), self.assertRaises(ValueError):
                transport.decode(*packed(b'a=b\n', compressed + tail))

    def test_expansion_stops_at_exact_budget(self):
        compressed = gzip.compress(b'a' * 1000000, mtime=0)
        encoded, spec = packed(b'a=b\n', compressed)
        with self.assertRaises(ValueError): transport.decode(encoded, spec)

    def test_decoded_digest_and_size(self):
        encoded, spec = packed(b'a=b\n')
        for changes in ({'decoded_bytes': 5}, {'decoded_sha256': '0' * 64}, {'lines': 2}):
            with self.subTest(changes=changes), self.assertRaises(ValueError):
                transport.decode(encoded, dict(spec, **changes))

    def test_requires_final_newline(self):
        with self.assertRaises(ValueError): transport.decode(*packed(b'a=b\nc=d'))

    def test_bad_digest_metadata(self):
        encoded, spec = packed(b'a=b\n')
        for value in ('X' * 64, 'a' * 63, None):
            with self.subTest(value=value), self.assertRaises(ValueError):
                transport.decode(encoded, dict(spec, decoded_sha256=value))

    def test_unknown_fixture_name(self):
        with self.assertRaises(ValueError): transport.load('../b2')

    def test_duplicate_metadata_rejected(self):
        with self.assertRaises(ValueError):
            json.loads('{"size":1,"size":2}', object_pairs_hook=transport.metadata_object)

    def test_extra_json_rejected(self):
        with self.assertRaises(ValueError):
            json.loads('{}{}', object_pairs_hook=transport.metadata_object)

    def test_bounded_file_read(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / 'synthetic'; path.write_bytes(b'12345')
            self.assertEqual(transport.read_bounded(path, 5), b'12345')
            with self.assertRaises(ValueError): transport.read_bounded(path, 4)


if __name__ == '__main__':
    unittest.main()
