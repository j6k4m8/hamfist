import unittest

from release import version


class ReleaseVersionTest(unittest.TestCase):
    def test_tag_becomes_matching_android_version(self):
        self.assertEqual(("0.1.0", 1000), version("v0.1.0"))
        self.assertEqual(("2.12.345", 2_012_345), version("v2.12.345"))

    def test_versions_remain_ordered_across_component_rollovers(self):
        tags = ["v0.0.1", "v0.0.999", "v0.1.0", "v0.999.999", "v1.0.0", "v2099.999.999"]
        codes = [version(tag)[1] for tag in tags]
        self.assertEqual(codes, sorted(set(codes)))
        self.assertLessEqual(codes[-1], 2_100_000_000)

    def test_rejects_ambiguous_unsupported_and_unsafe_tags(self):
        for tag in ["0.1.0", "v01.1.0", "v1.01.0", "v1.0.00", "v1.0.0-beta.1",
                    "v1.0.0+build", "v0.0.0", "v2100.0.0", "v1.1000.0", "v1.0.1000",
                    "v1.0.0\n", "v1.0.0/../../file", "v1.0.0;echo secret"]:
            with self.subTest(tag=tag), self.assertRaises(ValueError):
                version(tag)


if __name__ == "__main__":
    unittest.main()
