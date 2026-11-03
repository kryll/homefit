"""Validate numeric release tags before any signing step. No secret access."""
import re
import sys


def release_version(tag):
    match = re.fullmatch(r'v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)', tag)
    if not match:
        raise ValueError('Use vX.Y.Z with no suffix or leading zeroes')
    major, minor, patch = map(int, match.groups())
    if minor >= 1000 or patch >= 1000:
        raise ValueError('Minor and patch must be below 1000')
    code = major * 1_000_000 + minor * 1_000 + patch
    if not 1 <= code <= 2_100_000_000:
        raise ValueError('versionCode outside Android range')
    return tag[1:], code


if __name__ == '__main__':
    try:
        name, code = release_version(sys.argv[1])
    except (ValueError, IndexError) as error:
        sys.exit(str(error))
    print(f'HOMEFIT_VERSION_NAME={name}')
    print(f'HOMEFIT_VERSION_CODE={code}')
