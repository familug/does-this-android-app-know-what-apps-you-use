# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This project investigates which Android apps can see what other apps are installed on a user's device. It downloads APKs from APKPure, extracts their `AndroidManifest.xml` via `aapt dump xmltree`, parses the `<queries>` section, and reports which apps enumerate installed packages. Results are compiled into `apps.csv`.

## Common Commands

- **Run tests**: `uv run pytest`
- **Lint**: `ruff check .`
- **Check a single manifest**: `uv run main.py --check manifests/Facebook_AndroidManifest.xmldump`
- **Download and analyze a single app**: `uv run main.py --url 'https://play.google.com/store/apps/details?id=com.example.app'`
- **Analyze all packages in pkgs.txt**: `bash runall.sh`
- **Container build**: `podman build . -t androidleak`

## Architecture

Two Python modules form the core:

- **`main.py`** — CLI entry point. Converts Google Play URLs to APKPure download URLs, uses Playwright (Firefox, headless) to download APK/XAPK files, runs `aapt` to dump manifests, saves dumps to `manifests/`, and generates `apps.csv`. Contains inline assert-based tests for its URL parsing helpers that run on import.

- **`manifest_parser.py`** — Library for parsing aapt XML dump output. Key functions: `extract_query_section()` extracts the `<queries>` block, `query_packages()` lists queried package IDs, `check_leak_query_packages()` detects if an app queries `android.intent.action.MAIN` (meaning it can list ALL installed apps), `remove_dup_sub_packages()` filters out Android system packages.

## Data Flow

`pkgs.txt` (Play Store URLs) → `main.py` downloads APKs → `aapt` dumps manifests → saved to `manifests/*.xmldump` → parsed by `manifest_parser.py` → results written to `apps.csv`

## Testing

Tests are in `test_manifest_parser.py` using pytest. Fixtures load real manifest dumps from the `manifests/` directory (Swiggy, KISS Launcher, OKX, Facebook, Chrome). The manifests directory is test data — don't remove files that fixtures depend on.

## CI

GitHub Actions (`.github/workflows/python-app.yml`) runs on push/PR to main: `uv run main.py --check` on all manifests, `ruff check .`, and `uv run pytest`. A separate workflow (`.github/workflows/run.yml`) auto-analyzes newly added packages when `pkgs.txt` is modified in a PR.

## System Dependencies

Requires `aapt` (Android Asset Packaging Tool), `unzip`, and Playwright Firefox (`uvx playwright install firefox --with-deps`).
