#!/usr/bin/env python3
"""Regression checks for exact-head branch pruning after squash/rebase merges."""
import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location(
    "cleanup", Path(__file__).resolve().parents[1] / "scripts/cleanup_merged_branches.py")
cleanup = importlib.util.module_from_spec(spec)
spec.loader.exec_module(cleanup)


def merged_pr(**overrides):
    pr = {
        "merged_at": "2026-11-13T22:20:32Z", "merge_commit_sha": "merge-sha",
        "head": {"repo": {"full_name": cleanup.REPO}, "ref": "ui/icons", "sha": "head-sha"},
    }
    pr.update(overrides)
    return pr


class BranchCleanupTest(unittest.TestCase):
    def test_direct_ancestor_needs_no_pr(self):
        with patch.object(cleanup, "api", return_value={"ahead_by": 0}):
            self.assertTrue(cleanup.merged_into("main", "ui/icons", "head-sha", []))

    def test_exact_head_squash_or_rebase_merge_in_main(self):
        with patch.object(cleanup, "api", side_effect=[{"ahead_by": 1}, {"ahead_by": 0}]) as api:
            self.assertTrue(cleanup.merged_into("main", "ui/icons", "head-sha", [merged_pr()]))
            self.assertEqual(api.call_args.args[0], "compare/main...merge-sha")

    def test_new_work_on_previously_merged_branch_is_retained(self):
        with patch.object(cleanup, "api", return_value={"ahead_by": 1}) as api:
            self.assertFalse(cleanup.merged_into("main", "ui/icons", "new-head", [merged_pr()]))
            self.assertEqual(api.call_count, 1)

    def test_merged_into_another_base_is_retained(self):
        with patch.object(cleanup, "api", side_effect=[{"ahead_by": 1}, {"ahead_by": 2}]):
            self.assertFalse(cleanup.merged_into("main", "ui/icons", "head-sha", [merged_pr()]))

    def test_closed_unmerged_or_foreign_or_deleted_head_is_retained(self):
        foreign = {"repo": {"full_name": "other/fork"}, "ref": "ui/icons", "sha": "head-sha"}
        deleted = {"repo": None, "ref": "ui/icons", "sha": "head-sha"}
        for pr in (merged_pr(merged_at=None), merged_pr(merge_commit_sha=None),
                   merged_pr(head=foreign), merged_pr(head=deleted)):
            with self.subTest(pr=pr), patch.object(cleanup, "api", return_value={"ahead_by": 1}) as api:
                self.assertFalse(cleanup.merged_into("main", "ui/icons", "head-sha", [pr]))
                self.assertEqual(api.call_count, 1)

    def test_open_pr_protects_local_head_and_base(self):
        prs = [{"base": {"ref": "main"}, "head": merged_pr()["head"]},
               {"base": {"ref": "release"}, "head": {"repo": None, "ref": "foreign"}}]
        self.assertEqual(cleanup.open_pr_refs(prs), {"main", "ui/icons", "release"})


if __name__ == "__main__":
    unittest.main()
