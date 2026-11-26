# Notes — Search matches partial words as you type

Issue: #42 — Search matches partial words as you type (https://github.com/YasarMahomedAbbas/paperless-companion/issues/42)
Branch: `fix/42-search-partial-words`

## Planning

- Approach: keep the full-text `query=`, and widen each plain typed word `w` to `(w OR w*)` in `data/`, so prefixes match and exact words keep their relevance ranking on Paperless 2.x and 3.x.
- Decided:
  - **Q1, how to match partial words** (flags: api, ux): **option 1**, taken by the pipeline on the user's behalf. Widen plain words in `query` on every server. The other options were (2) Paperless 3's `text=` substring search, with option 1 as the fallback on 2.x: flat ranking, no advanced syntax, needs server-version detection, and 2.x ignores `text` and would return everything; and (3) option 1 first, then `text=` on 3.x when nothing is found: an extra call and the same version detection.
  - Only plain letter/digit words are widened. Quoted phrases, `AND`/`OR`/`NOT`, `field:` terms, wildcards and punctuated tokens are sent as typed (the pipeline's call).
  - One-character words are not widened, because `a*` matches nearly everything (the pipeline's call).
  - Data layer only. The domain's `DocumentQuery` is unchanged apart from a doc comment (the pipeline's call).
  - One checkpoint, with a library test asserting `(burger OR burger*)` that fails without the fix (the pipeline's call).
- Open risks:
  - Stemming: on a server with a stemmer for its search language, a prefix that runs past the stem finds nothing (`universit*` misses "university", which is indexed as `univers`). The exact-word half still matches once the whole word is typed. Self-verification should try a prefix on the real server.
  - Live schema unchecked: `verify.env` is absent, so the server's `/api/schema/` was not read and no real search ran. The docs site answered 403, so the plan rests on the v3.5.1 and v2.20.15 source and docs on GitHub. Self-verification must check `query=(w OR w*)` on the real server.

## Implementation

- Shipped:
  - `data/PaperlessSearch.kt:20` `partialWordQuery`: splits on whitespace (double-quoted runs are one token, an unclosed quote runs to the end), widens each plain letter/digit word of two or more characters that isn't `AND`/`OR`/`NOT` to `(w OR w*)`, passes every other token as typed, joins with single spaces.
  - `data/PaperlessLibrary.kt:78` `documents()` sends `partialWordQuery(text)` as `query`.
  - `domain/model/Documents.kt:49` KDoc on `DocumentQuery.text`; no code change in `domain/` or `ui/`.
- Tests: `PaperlessSearchTest` (11 cases, new) and `PaperlessLibraryTest` (two `query` assertions widened, plus `burger is sent as a prefix search`). With the call reverted to the raw text, 3 of 63 library tests fail; with the fix, the full check (`testDebugUnitTest koverVerify lintDebug assembleDebug`) is BUILD SUCCESSFUL, 545 tests passed / 0 failed.
- Outstanding: none in this stage. The real-server check of `query=(w OR w*)` (and the stemming risk) is self-verification's.
- Contradicted the plan: none. The new library test was named without quotes (`burger is sent as a prefix search`), because the Kotlin compiler warns that `"` in a test name can break on Windows; the checklist text was updated to match.

## Review

- Fixed: `data/PaperlessSearch.kt` widened English stop words. Paperless 2.x (Whoosh `TEXT()`, StandardAnalyzer) strips stop words from the index and from plain query words, but keeps wildcard terms. So `bill of lading` became `... (of OR of*) ...` and required a word starting with "of", which dropped documents that only hold "bill" and "lading". Stop words (Whoosh's English list, any case) are now sent as typed, which matches what 2.x did before this branch. Covered by `a stop word is sent as typed, so Paperless 2 still drops it`, which fails without the fix.
- Raised: none
- API: `GET /api/documents/?query=`. Checked against the source at v3.5.1 (`search/_query.py`, `_registry.py`, `_tokenizer.py`) and v2.20.15 (`index.py` `DelayedFullTextQuery`). On 3.x the whoosh-compat parser passes a wildcard's literal run through `pattern_normalizer` (lowercase, ascii-fold, and also the stemmed form), so `Burger*` matches lower-case index terms. Every default search field (title, content, correspondent, document_type, tag) is TEXT, so a trailing `*` raises no pattern-on-numeric 400. 3.x has no stop filter. On 2.x, MultifieldParser uses the default AND group, so words stay ANDed. The response shape is unchanged. Live schema not read, because `verify.env` is absent; that check belongs to self-verification.
- Layers: clean

## Doc drift

- Applied: `README.md` features, Documents: search "as you type" now says a partly typed word already matches (rung 3, user-facing behaviour changed).
- Applied: `README.md` architecture block, `data/`: added `PaperlessSearch` beside `PaperlessViews`, since the block names the `data/` helpers a session looks for (rung 3).
- Skipped: the widening rules (stop words, one-letter words, quoted/`field:`/operator tokens sent as typed): the KDoc in `PaperlessSearch.kt` and `PaperlessSearchTest` state and enforce them; a session touching search reads that file.
- Skipped: CLAUDE.md: no new cross-cutting rule; the change stays inside `data/` as the layer rules already require.
- Stale, fixed separately: none
