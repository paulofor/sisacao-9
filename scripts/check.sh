#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
bash -n scripts/check.sh
shellcheck scripts/check.sh
mvn -B verify
python3 -m unittest discover -s bridge/tests -v
npm ci --include=dev --prefix frontend
npm run build --prefix frontend
