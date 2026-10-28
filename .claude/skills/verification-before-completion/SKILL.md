---
name: verification-before-completion
description: >-
  Use when about to claim work is complete, fixed or passing — before committing, opening a PR, merging or releasing. Requires running the verification command and reading its output before any success claim: evidence before assertions, always.
---

# Verification Before Completion

**No completion claim without fresh verification evidence.** If you haven't run the command in
this message, you can't say it passes.

## The gate

1. **Identify** the command that proves the claim.
2. **Run** it, in full, fresh.
3. **Read** the whole output: exit code, failure counts.
4. **Claim** only what the output shows, and show the evidence. If it doesn't confirm the
   claim, state the actual status.

| Claim | Requires | Not enough |
| --- | --- | --- |
| Tests pass | `./build.sh ./gradlew --console=plain testDebugUnitTest` output, 0 failures | an earlier run, "should pass" |
| CI will pass | the full CI command, green | tests alone |
| Coverage holds | `koverVerify` green | "I added tests" |
| The API call works | the live server's answer (self-verification step 3) | MockWebServer passing |
| Bug fixed | a test that failed before and passes now | code changed |
| Released | `gh release view` lists the APK | the tag pushed |
| A subagent finished | the diff and its report | its saying so |

Red flags: "should", "probably", "seems to"; satisfaction before the output; trusting a
report without checking; "just this once".
