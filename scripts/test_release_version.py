import unittest
from release_version import release_version


class ReleaseVersionTest(unittest.TestCase):
    def test_values_and_order(self):
        self.assertEqual(('0.1.0', 1000), release_version('v0.1.0'))
        self.assertEqual(('1.2.3', 1002003), release_version('v1.2.3'))
        self.assertGreater(release_version('v1.0.0')[1], release_version('v0.999.999')[1])
        self.assertGreater(release_version('v1.1.0')[1], release_version('v1.0.999')[1])

    def test_rejects_invalid_colliding_and_out_of_range_tags(self):
        for tag in ['v0.0.0', 'v01.0.0', 'v1.1000.0', 'v1.0.1000', 'v2101.0.0',
                    'v1.2.3-beta', '1.2.3', 'v1.2', 'v1.2.3.4', 'v-1.0.0', 'v1.2.3\n']:
            with self.subTest(tag=tag), self.assertRaises(ValueError):
                release_version(tag)


if __name__ == '__main__':
    unittest.main()
