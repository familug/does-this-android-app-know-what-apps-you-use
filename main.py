"""
Detects if given android app knows what apps you use.

https://peabee.substack.com/p/everyone-knows-what-apps-you-use

"""

import csv
import datetime
import subprocess
import shutil
import tempfile
import os
from typing import Any
from playwright.sync_api import sync_playwright

import argparse
import urllib.parse as up

import manifest_parser as amparser


def parse_id_from_play_url(url: str) -> str:
    q = up.urlparse(url).query

    return up.parse_qs(q)["id"][0]


KISS_GPLAY_URL = "https://play.google.com/store/apps/details?id=fr.neamar.kiss"
assert parse_id_from_play_url(KISS_GPLAY_URL) == "fr.neamar.kiss"
# one of the smallest useful package ~1MB, use for quick test
KISS_APK = "https://d.apkpure.com/b/XAPK/fr.neamar.kiss?version=latest"
argp = argparse.ArgumentParser()
argp.add_argument("--url", "-u", required=False, default=KISS_GPLAY_URL)
argp.add_argument("--cleanup", "-c", required=False, action="store_true")
argp.add_argument("--check", help="Check if leak base on given manifest dump")
args = argp.parse_args()

playwright = sync_playwright().start()
# Use playwright.chromium, playwright.firefox or playwright.webkit
# Pass headless=False to launch() to see the browser UI
# browser = playwright.chromium.launch(headless=False, slow_mo=50)


def gplay_to_download_url(gplay_url: str) -> str:
    package_id = parse_id_from_play_url(gplay_url)

    return f"https://d.apkpure.com/b/XAPK/{package_id}?version=latest"


assert (
    gplay_to_download_url(
        "https://play.google.com/store/apps/details?id=fr.neamar.kiss"
    )
    == KISS_APK
)

url = gplay_to_download_url(args.url)


# TODO: make it work with headless=True to able run on docker/GHA
# TODO:  Try to download from other sources/directly from GPlay
def download(url: str):
    # https://github.com/microsoft/playwright-python/issues/1557
    browser = playwright.chromium.launch(headless=False)
    page = browser.new_page()
    with page.expect_download() as download_info:
        try:
            page.goto(url)
            print(page)
        except Exception:
            pass

    download = download_info.value
    return download


manifest_dir = os.path.join(os.path.abspath(os.path.dirname(".")), "manifests")
os.makedirs(manifest_dir, exist_ok=True)
cur_dir = os.path.abspath(os.path.dirname("."))

d = tempfile.mkdtemp()


def extract_appname(suggested_filename: str) -> str:
    return suggested_filename.removesuffix(".apk").split("_")[0]


assert extract_appname("KISS Launcher_3.21.3_APKPure.apk") == "KISS Launcher"


def extract(download: Any) -> dict[str, str]:
    download_filepath = download.path()
    print(download_filepath)
    os.chdir(d)
    os.system(f"cp {download_filepath} file.zip")
    result: dict[str, str] = {}
    if download.suggested_filename.endswith(".xapk"):
        os.system("unzip " + str(download_filepath))
        for fn in os.listdir():
            if fn.endswith(".apk") and not fn.startswith("config."):
                output = subprocess.run(
                    [
                        "aapt",
                        "dump",
                        "xmltree",
                        fn,
                        "AndroidManifest.xml",
                    ],
                    capture_output=True,
                ).stdout.decode("utf-8")
                package_id = parse_package_name(output)
                if package_id in fn:
                    result[extract_appname(download.suggested_filename)] = output

    else:
        output = subprocess.run(
            ["aapt", "dump", "xmltree", download_filepath, "AndroidManifest.xml"],
            capture_output=True,
        ).stdout.decode("utf-8")
        result[extract_appname(download.suggested_filename)] = output

    return result


def parse_version(manifest_dump: str) -> str:
    """
    A: android:versionName(0x0101021c)="3.49.41" (Raw: "3.49.41")
    """
    for line in manifest_dump.splitlines():
        if "android:versionName" in line:
            return line.split('"')[1]
    return "unknown"


assert (
    parse_version("""A: android:versionName(0x0101021c)="3.49.41" (Raw: "3.49.41")""")
    == "3.49.41"
)


def play_store_url(package_id: str) -> str:
    return f"https://play.google.com/store/apps/details?id={package_id}"


def parse_package_name(manifest_dump: str) -> str:
    for line in manifest_dump.splitlines():
        if "package=" in line:
            return line.split('"')[1]
    return "unknown"


assert (
    parse_package_name(""" A: package="com.shopee.vn" (Raw: "com.shopee.vn") """)
    == "com.shopee.vn"
)


def get_file_last_modified(fn: str) -> str:
    return datetime.datetime.fromtimestamp(os.stat(fn).st_mtime).strftime("%Y%m%d")


def summary() -> None:
    headers = [
        "checked_at",
        "app_name",
        "app_id",
        "store URL",
        "version",
        "knows all via query action.MAIN",
        "knows specific packages",
    ]
    with open(os.path.join(cur_dir, "apps.csv"), "wt") as csvf:
        writer = csv.writer(csvf)
        writer.writerow(headers)

        for fn in sorted(os.listdir(manifest_dir)):
            file_path = os.path.join(manifest_dir, fn)
            print("Checking", file_path)
            with open(file_path) as f:
                content = f.read()
            leak = amparser.check_leak_query_packages(content)
            # app_id = "_".join(fn.split("_")[:-1])
            app_id = parse_package_name(content)
            app_name = fn.split("_")[0]
            version = parse_version(content)
            store_url = play_store_url(app_id)
            modified_time = get_file_last_modified(file_path)
            queried_packages = " ".join(
                amparser.remove_dup_sub_packages(
                    amparser.query_packages(amparser.extract_query_section(content))
                )
            )

            writer.writerow(
                [
                    modified_time,
                    app_name,
                    app_id,
                    store_url,
                    version,
                    leak,
                    queried_packages,
                ]
            )


def run() -> None:
    if args.check:
        with open(args.check) as f:
            print(amparser.check_leak_query_packages(f.read()))
        exit(0)

    download_res = download(url)
    res = extract(download_res)
    for fn, value in res.items():
        output_fn = fn.removesuffix(".apk") + "_AndroidManifest.xmldump"

        output_path = os.path.join(manifest_dir, output_fn)
        with open(output_path, "wt") as f:
            f.write(value)

    summary()

    if args.cleanup:
        shutil.rmtree(d)


def main() -> None:
    run()


if __name__ == "__main__":
    main()
# os.system("unzip " + str(download_filepath))
# browser.close()
# playwright.stop()
