import unittest
import fake_phone as wire

class ExtensionTests(unittest.TestCase):
    def test_golden_metadata_matches_csharp_and_kotlin(self):
        self.assertEqual(wire.encode_client_hello("PC", 2).hex(), "da010702025043")
        self.assertEqual(wire.decode_status(bytes.fromhex("da0108020f")), {"pad": 2, "occupied": 15})
    def test_unicode_names_fit_without_split_codepoints(self):
        data = wire.encode_client_hello("🎮" * 100)
        self.assertLessEqual(len(data), 53)
        self.assertEqual(len(data) - 5, data[4])
        data[5:].decode("utf-8")
    def test_malformed_status_and_preferences(self):
        for raw in ("da01080400", "da01080010", "da02080000", "da0108000000", "da0108"):
            self.assertIsNone(wire.decode_status(bytes.fromhex(raw)))
        with self.assertRaises(ValueError): wire.encode_client_hello("PC", 4)
    def test_legacy_golden_vectors_still_pass(self):
        self.assertEqual(wire.run_selftest(), 0)

if __name__ == "__main__": unittest.main()
