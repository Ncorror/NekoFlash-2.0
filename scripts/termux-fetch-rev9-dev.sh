#!/data/data/com.termux/files/usr/bin/bash
# REV9: fetch ONLY the latest successful APK matching the current feature branch HEAD.
# Does not install, delete, or overwrite any signed/original NekoFlash build.
set -euo pipefail
REPO="Ncorror/NekoFlash-2.0"
BRANCH="feature/rev6-shell"
for tool in gh git sha256sum; do
    command -v "$tool" >/dev/null || { echo "Missing $tool. Run: pkg install -y git gh coreutils"; exit 1; }
done
gh auth status >/dev/null
HEAD_SHA="$(git ls-remote "https://github.com/$REPO.git" "refs/heads/$BRANCH" | awk 'NR==1 {print $1}')"
test -n "$HEAD_SHA" || { echo "Cannot resolve $BRANCH"; exit 1; }
RUN_ID="$(gh run list -R "$REPO" -w build.yml -b "$BRANCH" --limit 30 --json databaseId,headSha,conclusion --jq ".[] | select(.headSha == \"$HEAD_SHA\" and .conclusion == \"success\") | .databaseId" | head -n 1)"
test -n "$RUN_ID" || { echo "No SUCCESS CI for current SHA $HEAD_SHA. Never use an older build."; exit 1; }
ARTIFACT="$(gh api "repos/$REPO/actions/runs/$RUN_ID/artifacts" --jq '.artifacts[].name | select(endswith("-debug"))' | head -n 1)"
test -n "$ARTIFACT" || { echo "No debug artifact for run $RUN_ID"; exit 1; }
DEST="$HOME/storage/downloads/NekoFlash-2.0-REV9/$HEAD_SHA"
mkdir -p "$DEST"
gh run download "$RUN_ID" -R "$REPO" -n "$ARTIFACT" -D "$DEST"
(
  cd "$DEST"
  sha256sum -c checksums-sha256.txt
)
echo "VERIFIED SHA=$HEAD_SHA"
echo "RUN=$RUN_ID"
find "$DEST" -maxdepth 1 -type f -name '*.apk' -print
echo "The DEV APK is for manual installation only, beside the original signed NekoFlash."
