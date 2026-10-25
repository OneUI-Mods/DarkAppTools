import unittest

from migrate_settings import preferences, serialize


class PreferencesMigrationTest(unittest.TestCase):
    def test_copies_user_choices_without_runtime_status(self):
        values = preferences(b'''<map>
            <boolean name="enabled" value="false"/>
            <string name="mode">light</string>
            <long name="reset" value="123456789"/>
            <string name="state">blocked</string>
            <long name="checked" value="9"/>
        </map>''')
        self.assertEqual(values, {"enabled": False, "mode": "light", "reset": 123456789})
        encoded = serialize(values)
        self.assertNotIn(b"blocked", encoded)
        self.assertEqual(preferences(encoded), values)

    def test_missing_settings_keep_original_defaults(self):
        self.assertEqual(preferences(b"<map/>"), {"enabled": True, "mode": "system", "reset": 0})

    def test_corrupt_settings_are_rejected_instead_of_silently_reset(self):
        for body in [
            '<string name="mode">unknown</string>',
            '<boolean name="enabled" value="1"/>',
            '<long name="reset" value="9223372036854775808"/>',
            '<long name="reset" value="-1"/>',
        ]:
            with self.subTest(body=body), self.assertRaises(ValueError):
                preferences(("<map>" + body + "</map>").encode())

    def test_duplicate_settings_are_rejected(self):
        with self.assertRaises(ValueError):
            preferences(b'<map><string name="mode">dark</string><string name="mode">light</string></map>')


if __name__ == "__main__":
    unittest.main()
