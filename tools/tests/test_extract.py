#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Extraction must reject a wrong dump before modifying known-good blobs."""
import hashlib
import importlib.util
import tempfile
import unittest
from pathlib import Path

SPEC = importlib.util.spec_from_file_location('extract_files', Path(__file__).parents[2] / 'extract-files.py')
extract = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(extract)


class ExtractionTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        root = Path(self.temp.name)
        extract.LIST = root / 'list'
        extract.SUMS = root / 'sums'
        extract.DEST = root / 'output'
        self.dump = root / 'dump'
        self.dump.mkdir()
        extract.DEST.mkdir()
        extract.LIST.write_text('a\nb\n')
        self.sums = {name: hashlib.sha256(name.encode()).hexdigest() for name in ('a', 'b')}
        extract.SUMS.write_text(''.join(f'{digest}  {name}\n' for name, digest in self.sums.items()))
        for name in self.sums:
            (self.dump / name).write_text(name)
            (extract.DEST / name).write_text('known-good')

    def test_wrong_hash_rejected_without_writes(self):
        (self.dump / 'b').write_text('wrong-firmware')
        self.assertEqual(extract.extract(self.dump, False), 1)
        self.assertEqual((extract.DEST / 'a').read_text(), 'known-good')
        self.assertEqual((extract.DEST / 'b').read_text(), 'known-good')

    def test_missing_input_rejected_without_writes(self):
        (self.dump / 'b').unlink()
        self.assertEqual(extract.extract(self.dump, False), 1)
        self.assertEqual((extract.DEST / 'a').read_text(), 'known-good')

    def test_matching_dump_copies_and_verifies(self):
        self.assertEqual(extract.extract(self.dump, False), 0)
        self.assertEqual(extract.verify(), 0)

    def test_write_sums_does_not_accept_changed_pin(self):
        (self.dump / 'b').write_text('wrong-firmware')
        self.assertEqual(extract.extract(self.dump, True), 1)
        self.assertEqual(extract.load_sums(), self.sums)


if __name__ == '__main__':
    unittest.main()
