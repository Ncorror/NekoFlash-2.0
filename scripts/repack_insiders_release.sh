#!/usr/bin/env bash
# Migrate an existing full-layout Insiders asset while preserving its tagged APK.
set -euo pipefail
tag=$1
[[ "$tag" =~ ^insiders-rev([1-9][0-9]*)$ ]]
archive="NekoFlash-Insiders-REV${BASH_REMATCH[1]}.zip"
source_asset=filesystem-tools-source.tar.gz
scratch=$(mktemp -d)
trap 'rm -rf -- "$scratch"' EXIT
gh release download "$tag" --pattern "$archive" --dir "$scratch/original"
old="$scratch/original/$archive"
mkdir "$scratch/kit"
unzip -qj "$old" '*.apk' testbook.html -d "$scratch/kit"
commit=$(git rev-list -n 1 "$tag")
python3 scripts/verify_insiders_kit.py "$scratch/kit" --commit "$commit"
if [[ $(unzip -Z1 "$old" | wc -l) -gt 2 ]]; then
  # Check the replacement book before touching either published asset.
  cp docs/insiders/testbook.html "$scratch/kit/"
  python3 scripts/verify_insiders_kit.py "$scratch/kit" --commit "$commit"
  unzip -p "$old" "$source_asset" > "$scratch/$source_asset"
  tar -tzf "$scratch/$source_asset" > /dev/null
  if gh release view "$tag" --json assets --jq '.assets[].name' | grep -Fx "$source_asset" > /dev/null; then
    gh release download "$tag" --pattern "$source_asset" --dir "$scratch/existing"
    cmp "$scratch/$source_asset" "$scratch/existing/$source_asset"
  else
    gh release upload "$tag" "$scratch/$source_asset"
  fi
  (cd "$scratch/kit" && zip -Xq "$scratch/$archive" *.apk testbook.html)
  if ! gh release upload "$tag" "$scratch/$archive" --clobber ||
     ! gh release download "$tag" --pattern "$archive" --dir "$scratch/verified" ||
     ! cmp "$scratch/$archive" "$scratch/verified/$archive"; then
    gh release upload "$tag" "$old" --clobber
    exit 1
  fi
  published="$scratch/$archive"
else
  published="$old"
fi
digest=$(sha256sum "$published" | cut -d' ' -f1)
gh release edit "$tag" --notes "Extract the ZIP, install the APK on the host phone and open testbook.html. / Распакуй ZIP, установи APK на хост и открой testbook.html. [Telegram · NekoFlash Insiders](https://t.me/+NR_FiUmvXlViOTUy). ZIP SHA-256: \`$digest\`."
