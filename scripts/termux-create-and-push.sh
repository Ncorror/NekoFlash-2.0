#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
TARGET="Ncorror/NekoFlash-2.0"
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$DIR"
command -v git >/dev/null || { echo 'Missing git: pkg install git'; exit 1; }
command -v gh >/dev/null || { echo 'Missing gh: pkg install gh'; exit 1; }
if ! gh auth status >/dev/null 2>&1; then
  echo 'Log in with the Ncorror GitHub account in the browser:'
  gh auth login --hostname github.com --git-protocol https --web
fi
USER="$(gh api user --jq .login)"
if [ "$USER" != 'Ncorror' ]; then
  echo "STOP: authenticated as $USER, required Ncorror. No upload performed." >&2
  exit 1
fi
if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo 'STOP: .git missing from archive. Use the complete Git-ready ZIP.' >&2
  exit 1
fi
if [ "$(git branch --show-current)" != 'main' ]; then
  echo 'STOP: expected main branch. No upload performed.' >&2
  exit 1
fi
if [ -n "$(git status --porcelain)" ]; then
  echo 'STOP: local Git worktree has modifications; no automatic commit.' >&2
  exit 1
fi
# git credential integration for https remotes.
gh auth setup-git
if gh repo view "$TARGET" >/dev/null 2>&1; then
  echo "Repository already exists: https://github.com/$TARGET"
  if git remote get-url origin >/dev/null 2>&1; then
    EXISTING="$(git remote get-url origin)"
    case "$EXISTING" in
      "https://github.com/$TARGET"|"https://github.com/$TARGET.git"|"git@github.com:$TARGET.git") ;;
      *) echo "STOP: origin points to $EXISTING" >&2; exit 1 ;;
    esac
  else
    git remote add origin "https://github.com/$TARGET.git"
  fi
  echo 'Pushing main without force (will stop on conflicts)...'
  git push --set-upstream origin main
else
  echo "Creating PUBLIC GitHub repository $TARGET and pushing main..."
  gh repo create "$TARGET" --public --description 'NekoFlash 2.0 — Android development checkpoint REV5' --source=. --remote=origin --push
fi
echo "PUBLISHED: https://github.com/$TARGET"
