#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -lt 2 ]; then
  echo "Usage: scripts/release.sh <versionName> <versionCode> [owner/repo]" >&2
  exit 1
fi

VERSION_NAME="$1"
VERSION_CODE="$2"
OWNER_REPO="${3:-username/keyxif}"
TAG="v${VERSION_NAME}"
APK_NAME="keyxif-${VERSION_NAME}.apk"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "Updating Gradle version to ${VERSION_NAME} (${VERSION_CODE})"
python - "$ROOT/app/build.gradle.kts" "$VERSION_NAME" "$VERSION_CODE" <<'PY'
import re
import sys
from pathlib import Path

path = Path(sys.argv[1])
version_name = sys.argv[2]
version_code = sys.argv[3]
text = path.read_text(encoding="utf-8")
text = re.sub(r'versionCode\s*=\s*\d+', f'versionCode = {version_code}', text)
text = re.sub(r'versionName\s*=\s*"[^"]+"', f'versionName = "{version_name}"', text)
path.write_text(text, encoding="utf-8")
PY

echo "Updating docs/update.json"
cat > "$ROOT/docs/update.json" <<EOF
{
  "latestVersionCode": ${VERSION_CODE},
  "latestVersionName": "${VERSION_NAME}",
  "minRequiredVersionCode": 1,
  "title": "새 버전이 있습니다",
  "message": "Keyxif의 새 APK가 준비되었습니다.",
  "apkUrl": "https://github.com/${OWNER_REPO}/releases/download/${TAG}/${APK_NAME}",
  "releaseNoteUrl": "https://github.com/${OWNER_REPO}/releases/tag/${TAG}",
  "forceUpdate": false
}
EOF

echo "Running release sanity build"
"$ROOT/gradlew" :app:compileReleaseKotlin

echo "Creating release commit and tag"
git add app/build.gradle.kts docs/update.json
git commit -m "Release ${TAG}"
git tag "${TAG}"

echo "Pushing branch and tag"
git push
git push origin "${TAG}"

echo "Release tag pushed: ${TAG}"
