# NekoFlash 2.0 — CI Intermittent Issues · 10 November 2026

## Issue: API 34 Emulator IME Timing Flakiness

### Observation

**Run:** #42834675621  
**Date:** 10 November 2026, 14:29 UTC  
**Branch:** `audit/claude-docs-20261110`  
**Commit:** `639f94c`

**Result:**
- Build: ✅ SUCCESS
- API 26: ✅ SUCCESS (28/28 tests)
- API 34: ❌ FAILURE (flaky IME timing)

### Failed Tests

```
java.lang.IllegalStateException: terminal expanded with visible IME did not settle within 15s
java.lang.IllegalStateException: command keyboard visible did not settle within 15s
```

### Root Cause Analysis

**This is NOT a code regression.**

1. **Same code passed in previous run #657:**
   - Run #42830107144 (earlier today): ✅ ALL PASS
   - Same commit baseline `e3d1c3b`: API 34 passed 28/28

2. **Documentation-only changes:**
   - Commits `02236f5` and `639f94c` added only `.md` files
   - Zero Kotlin/Java/XML changes
   - Zero build configuration changes

3. **Known Issue #16:**
   - Intermittent CI emulator instability
   - Documented in project backlog
   - Affects terminal/IME timing-sensitive tests

### Evidence This is Infrastructure, Not Code

**Previous Successful Runs on Same Baseline:**

| Run | Commit | API 26 | API 34 | Result |
|-----|--------|--------|--------|--------|
| #42804598658 (#653) | `6a13d8ed2ff8` | 28/28 | 28/28 | ✅ PASS |
| #42830107144 (#657) | `e3d1c3b` (docs only) | 28/28 | 28/28 | ✅ PASS |
| #42834675621 | `639f94c` (docs only) | 28/28 | FLAKY | ❌ IME timeout |

**Pattern:** Same code passes/fails randomly on API 34 emulator → infrastructure flakiness.

### Why This is NOT Blocking

1. **Build Success:** Code compiles cleanly
2. **API 26 Success:** All tests pass on stable emulator
3. **Previous API 34 Success:** Same code passed before
4. **Zero Code Changes:** Only documentation commits
5. **Known Issue:** #16 tracks emulator instability

### Action Taken

- ✅ Rerun failed job: `gh run rerun 42834675621 --failed`
- ⏳ Awaiting retry completion

### Recommendation

**If retry passes:** Proceed with PR merge (evidence: code is correct, emulator was flaky)

**If retry fails again:**
1. Check emulator infrastructure status
2. Consider isolated API 34 rerun
3. Evaluate if #16 needs priority escalation

**Do NOT:** Block release for intermittent emulator issues when code is verified correct.

### Historical Context

From `DEVELOPMENT_STATUS.md`:
> Issue #16: intermittent CI emulator Android API 26/34 UI wait timeouts. REV55 [CI #501] passed 336 JVM + 28/28 on each Android API 26/34, independent same-SHA API26 repeat PASS.

Multiple instances of emulator flakiness documented. This is a **known limitation of CI infrastructure**, not code defects.

---

**Status:** ⏳ RETRY IN PROGRESS  
**Expected:** ✅ PASS on retry (historical pattern)  
**Blocker:** ❌ NO (infrastructure issue, not code defect)
