#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p dist
cp app/build/outputs/apk/debug/app-debug.apk dist/vmq-2.0.0-debug.apk
cp app/build/outputs/apk/release/app-release-unsigned.apk dist/vmq-2.0.0-release-unsigned.apk
cp app/build/outputs/bundle/release/app-release.aab dist/vmq-2.0.0-release-unsigned.aab
cp docs/BUILD.md dist/BUILD.md
(cd dist && sha256sum ./*.apk ./*.aab > SHA256SUMS.txt)
