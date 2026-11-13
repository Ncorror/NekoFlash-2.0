#!/usr/bin/env python3
"""Safely prune branches merged into main. Requires authenticated gh CLI.

Dry-run by default. Protects default branch, security work and open PR refs.
Recognizes exact-head merged PRs after squash/rebase; preserves newer work.
No force-push and no deletion of divergent work.
"""
from __future__ import annotations

import argparse
import json
import subprocess
import sys
from urllib.parse import quote

REPO = "Ncorror/NekoFlash-2.0"


def api(path: str, method: str = "GET"):
    endpoint = f"repos/{REPO}" + (f"/{path}" if path else "")
    command = ["gh", "api", "-X", method, endpoint]
    output = subprocess.check_output(command, text=True)
    return json.loads(output) if output.strip() else None


def paginate(endpoint: str):
    result = []
    for page in range(1, 100):
        suffix = "&" if "?" in endpoint else "?"
        items = api(f"{endpoint}{suffix}per_page=100&page={page}")
        if not isinstance(items, list):
            raise RuntimeError(f"Unexpected response for {endpoint}")
        result.extend(items)
        if len(items) < 100:
            return result
    raise RuntimeError(f"Too many pages for {endpoint}; stopped before deletion")


def open_pr_refs(prs):
    refs = set()
    for pr in prs:
        refs.add(pr["base"]["ref"])
        head_repo = pr["head"].get("repo")
        if head_repo and head_repo["full_name"] == REPO:
            refs.add(pr["head"]["ref"])
    return refs


def merged_into(default: str, name: str, head_sha: str, closed_prs) -> bool:
    """Require direct ancestry or an exact-head merged PR contained in default."""
    target = quote(default, safe="")
    compare = api(f"compare/{target}...{quote(head_sha, safe='')}")
    if compare.get("ahead_by") == 0:
        return True
    for pr in closed_prs:
        head = pr["head"]
        head_repo = head.get("repo")
        if not (pr.get("merged_at") and pr.get("merge_commit_sha")
                and head_repo and head_repo["full_name"] == REPO
                and head["ref"] == name and head["sha"] == head_sha):
            continue
        merge = api(f"compare/{target}...{quote(pr['merge_commit_sha'], safe='')}")
        if merge.get("ahead_by") == 0:
            return True
    return False


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--execute", action="store_true", help="Actually delete eligible remote branches")
    parser.add_argument("--yes", action="store_true", help="Confirm permanent deletion (requires --execute)")
    args = parser.parse_args()
    if args.execute != args.yes:
        parser.error("Deletion needs BOTH --execute and --yes; otherwise use a dry-run")

    repo = api("")
    default = repo["default_branch"]
    branches = paginate("branches")
    prs = paginate("pulls?state=open")
    closed_prs = paginate("pulls?state=closed")
    active = open_pr_refs(prs)

    candidates = []
    skipped = []
    for entry in branches:
        name = entry["name"]
        if name == default or entry.get("protected") or name.startswith("security/"):
            skipped.append((name, "protected/default/security"))
            continue
        if name in active:
            skipped.append((name, "open PR head/base"))
            continue
        if not merged_into(default, name, entry["commit"]["sha"], closed_prs):
            skipped.append((name, "unmerged commits or head changed after merged PR"))
            continue
        candidates.append(name)

    print(f"Repo: {REPO}; default: {default}; open PRs: {len(prs)}")
    print(f"Eligible merged branches: {len(candidates)}")
    for name in candidates:
        print(f"  DELETE-ELIGIBLE {name}")
    print(f"Protected/unmerged/in-use branches: {len(skipped)}")
    for name, reason in skipped:
        print(f"  KEEP {name}: {reason}")

    if not args.execute:
        print("DRY RUN: no remote refs changed. Pass --execute --yes to delete listed eligible refs.")
        return 0

    for name in candidates:
        # Revalidate on the server immediately before every destructive call.
        current = api(f"git/ref/heads/{quote(name, safe='')}")
        if current["object"]["sha"] != next(x["commit"]["sha"] for x in branches if x["name"] == name):
            print(f"SKIP {name}: branch head moved", file=sys.stderr)
            continue
        if name in open_pr_refs(paginate("pulls?state=open")):
            print(f"SKIP {name}: branch is now used by an open PR", file=sys.stderr)
            continue
        if not merged_into(default, name, current["object"]["sha"], paginate("pulls?state=closed")):
            print(f"SKIP {name}: branch no longer merged", file=sys.stderr)
            continue
        api(f"git/refs/heads/{quote(name, safe='')}", "DELETE")
        print(f"DELETED {name}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
