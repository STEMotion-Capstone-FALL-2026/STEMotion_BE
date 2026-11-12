#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SOURCES_DIR="${1:-D:/stem-sources}"

python "${SCRIPT_DIR}/verify-quotes.py" --sources-dir "${SOURCES_DIR}" "$@"
