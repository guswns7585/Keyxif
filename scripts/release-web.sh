#!/usr/bin/env bash
set -euo pipefail

VERSION_NAME="${1:-}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WEB_ROOT="${ROOT}/web"

if [ -n "$VERSION_NAME" ]; then
  node - "$WEB_ROOT/package.json" "$VERSION_NAME" <<'JS'
const fs = require("fs");
const [packageFile, versionName] = process.argv.slice(2);
const pkg = JSON.parse(fs.readFileSync(packageFile, "utf8"));
pkg.version = versionName;
fs.writeFileSync(packageFile, `${JSON.stringify(pkg, null, 2)}\n`);
JS
fi

(cd "$WEB_ROOT" && npm install && npm run build)

echo "Web build complete. Deploy web/dist with Cloudflare Pages."
