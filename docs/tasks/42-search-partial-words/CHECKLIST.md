# Checklist — Search matches partial words as you type

Issue: #42 — Search matches partial words as you type (https://github.com/YasarMahomedAbbas/paperless-companion/issues/42)
Branch: `fix/42-search-partial-words`
Problem: searching documents only matches whole words, so typing "burger" doesn't find a document that contains "Burgerservicenummer" until the whole word has been typed.

## Done when

- Typing "burger" finds documents that contain "Burgerservicenummer" (or any word starting with "burger"), on Paperless 2.x and 3.x.
- Results are still in full-text relevance order (no `ordering`), with highlights, and a document that has the exact word still scores above prefix-only matches.
- Advanced query syntax the user types (quoted phrases, `AND`/`OR`/`NOT`, `field:value`, existing wildcards, parentheses) is sent unchanged.
- The Paperless query syntax stays in `data/`. `domain/` and `ui/` don't change, apart from a doc comment on `DocumentQuery.text`.
- The fix comes with a test that fails without it.

## References

- Paperless API: `GET /api/documents/?query=` (docs `api.md` § Searching for documents; query syntax in `usage.md` § Wildcards and § Combining terms). Checked against the source at v3.5.1, the latest release (`src/documents/search/_query.py` `parse_user_query`, `views.py` `_get_tantivy_query_and_mode`), and at v2.20.15 (Whoosh, `views.py` `query` / `more_like_id`).
  - 3.x (Tantivy, API v10): `query` is parsed by whoosh-compat. `*`/`?` wildcards work, terms are ANDed by default, `OR` and parentheses group, and a wildcard leaf scores flat (no BM25). With a stemmer configured, a prefix that runs past the stem can find nothing (`universit*` misses "university").
  - 2.x (Whoosh, API v9): the same `query` grammar, `*` prefix wildcards included.
  - Rejected for this ticket: `text=` (3.x only; regex substring `.*w.*` scored flat apart from a 2x title boost; 2.x ignores the parameter and would return every document). `title_content` (deprecated in v10, ORM `icontains`, unranked).
  - The shape of the response is unchanged (`__search_hit__` on v9 and v10).
- The docs site (docs.paperless-ngx.com) answered 403 to the fetch, so the docs were read from the repository's `docs/` at the same tags.

## Context

`PaperlessLibrary.documents` sends trimmed typed text as `query=` (whole words only). The pipeline took option 1 on the user's behalf: each plain word `w` (letters and digits only, two characters or more) becomes `(w OR w*)`, so the exact word keeps its BM25 score and anything starting with `w` also matches. Words stay ANDed, as before. Any token carrying syntax (quotes, `:`, `*`, `?`, brackets, parentheses, `-`, `/` or other punctuation, or the operators `AND`/`OR`/`NOT`) is passed through as typed, so advanced search and numbers like `A-1312/B` behave as today. Once a double quote opens, everything up to the closing quote is left alone. The rewrite is a pure function in a new `data/PaperlessSearch.kt`, tested on its own (mirroring `PaperlessViews.kt` + `PaperlessViewsTest.kt`), and called from `documents()` in place of the raw text. More like this and saved views' own `text`/`title_search` parameters are untouched. Substring matching ("servicenummer" → "Burgerservicenummer") is out of scope.

## Test plan

- Unit (`PaperlessSearchTest`, pure): `burger` → `(burger OR burger*)`; `power bill` → `(power OR power*) (bill OR bill*)`; one-character word unchanged (`a`); umlauts/accents count as letters (`müll` widened); digits (`1312` widened); `A-1312/B` unchanged; `burger*` unchanged; `tag:unpaid` unchanged; `"quick brown"` unchanged inside the quotes, with words outside widened; `invoice NOT draft` keeps `NOT` and widens both words; an unbalanced quote leaves the rest unchanged; `(a OR b)` unchanged; extra whitespace collapsed to one space.
- MockWebServer (`PaperlessLibraryTest`): `a text search sends the query and no ordering, and reads the hits` now expects `query=(power OR power*)` (fails without the fix); `typed text replaces the view's own search` expects the widened query; more like this still sends no `query`. One shape for v9 and v10.
- Real server (self-verification, read-only): the live `/api/schema/` for `query` on `/api/documents/`; `GET /api/documents/?query=(w OR w*)` for a prefix of a word in a known document returns it, with `__search_hit__`, and no 400; a quoted phrase and a `tag:` query still work.
- Device only: none (typing in the search field already debounces into the same call).

---

## Checkpoint 1 — Partial-word search · commit: `fix(documents): search matches partial words as you type`

Goal: typed search text reaches Paperless with each plain word widened to its prefixes, and the tests prove it.

### [ ] 1.1 — The query rewriter

File: `app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/data/PaperlessSearch.kt` (new)
`internal fun partialWordQuery(text: String): String`. It splits the text on whitespace while keeping double-quoted runs as one token (an unclosed quote runs to the end), widens each token matching `^[\p{L}\p{N}]{2,}$` that isn't `AND`/`OR`/`NOT` to `(w OR w*)`, keeps every other token as typed, and joins with single spaces. A KDoc names the Paperless behaviour it relies on (wildcards score flat, so the exact word is kept beside the prefix) and the stemming caveat.

### [ ] 1.2 — Use it in the list call

File: `app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/data/PaperlessLibrary.kt`
In `documents()`, the `text != null` branch puts `partialWordQuery(text)` instead of `text`. Update the comment there.

### [ ] 1.3 — Domain doc

File: `app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/domain/model/Documents.kt`
A KDoc on `DocumentQuery.text`: "Words to search for. A word also matches longer words that start with it." No code change.

### [ ] 1.4 — Tests

Files: `app/src/test/java/io/github/yasarmahomedabbas/paperlesscompanion/data/PaperlessSearchTest.kt` (new, the cases in the test plan) and `app/src/test/java/io/github/yasarmahomedabbas/paperlesscompanion/data/PaperlessLibraryTest.kt` (update the two `query` assertions to the widened form and add `"burger" is sent as a prefix search` asserting `(burger OR burger*)`).

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*PaperlessSearchTest' --tests '*PaperlessLibraryTest'`
- The library test asserting `(burger OR burger*)` fails on `origin/main` and passes here.
- Then the full check: `./build.sh ./gradlew --console=plain testDebugUnitTest koverVerify lintDebug assembleDebug`

---

## Out of scope

- Substring matching inside words ("servicenummer" finding "Burgerservicenummer"), and Paperless 3's `text=` simple search.
- Detecting the server version.
- Autocomplete suggestions (`/api/search/autocomplete/`) or any UI change.
- Saved views' own `text` / `title_search` / `query` parameters, and more like this.
