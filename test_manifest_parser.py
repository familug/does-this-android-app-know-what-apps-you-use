import manifest_parser
import pytest


@pytest.fixture
def leak_manifest():
    f = open("manifests/Swiggy_AndroidManifest.xmldump")
    return f.read()


@pytest.fixture
def no_query_manifest():
    f = open("manifests/KISS Launcher_AndroidManifest.xmldump")
    return f.read()


@pytest.fixture
def okx_manifest():
    f = open("manifests/OKX_AndroidManifest.xmldump")
    return f.read()


@pytest.fixture
def fb_manifest():
    f = open("manifests/Facebook_AndroidManifest.xmldump")
    return f.read()


@pytest.fixture
def chrome_manifest():
    f = open("manifests/Google Chrome_AndroidManifest.xmldump")
    return f.read()


def test_extract_query_section(leak_manifest):
    assert manifest_parser.check_leak_query_packages(leak_manifest) is True


def test_extract_package_name():
    line = (
        """A: android:name(0x01010003)="com.okinc.okex.gp" (Raw: "com.okinc.okex.gp")"""
    )
    assert manifest_parser._extract_package_name(line) == "com.okinc.okex.gp"


def test_query_packages(leak_manifest):
    assert (
        len(
            manifest_parser.query_packages(
                manifest_parser.extract_query_section(leak_manifest)
            )
        )
        == 15
    )


def test_no_query_packages(no_query_manifest):
    assert (
        len(
            manifest_parser.query_packages(
                manifest_parser.extract_query_section(no_query_manifest)
            )
        )
        == 0
    )


def test_okx_query_packages(okx_manifest):
    assert "com.facebook.katana" in manifest_parser.query_packages(
        manifest_parser.extract_query_section(okx_manifest)
    )


def test_facebook_query_packages(fb_manifest):
    assert "org.telegram.messenger" in manifest_parser.query_packages(
        manifest_parser.extract_query_section(fb_manifest)
    )


def test_remove_dup_sub_packages(leak_manifest):
    assert (
        len(
            manifest_parser.remove_dup_sub_packages(
                manifest_parser.query_packages(
                    manifest_parser.extract_query_section(leak_manifest)
                )
            )
        )
        == 14
    )
