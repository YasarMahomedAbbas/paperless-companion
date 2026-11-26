# Notes — Toggle the document list between list and cards with previews

Issue: #43 — Toggle the document list between list and cards with previews (https://github.com/YasarMahomedAbbas/paperless-companion/issues/43)
Branch: `feat/43-list-or-card-previews`

## Planning

- Approach: one `LazyVerticalGrid` on the Documents screen (1 column for rows, 2 for cards), with a new `DocumentCard` and a layout toggle in the title bar. The choice is a domain `DisplayPreferences` stored in its own SharedPreferences file. No Paperless call changes; the existing lazy, cached thumbnails feed the cards.
- Decided by the pipeline on the user's behalf (approach questions, flag: ux):
  - **Q1, card layout**: **option 1** taken. A two-column grid of portrait cards: a 3:4 thumbnail cropped from the top, then the title and "Correspondent · date". Paperless thumbnails are at most 500 px wide, so they stay sharp at about 180 dp. The other options were (2) one full-width card per row, where the thumbnail looks soft and about two fit on screen, and (3) an adaptive grid of cards at least 160 dp wide.
  - **Q2, where the toggle sits**: **option 1** taken. An icon button in the large title bar beside Settings ("Show as cards" / "Show as list"), hidden with the title row while searching or selecting. The other options were (2) a chip at the end of the chip row and (3) a Settings switch only.
  - **Q3, cards while searching**: **option 1** taken. Cards keep their shape and drop the snippet; switching to the list shows it. The other options were (2) a two-line snippet on each card and (3) always showing the list while searching.
- Decided in planning; the pipeline accepted them on the user's behalf (flag: ux):
  - One grid for both modes, with the notice, uploads, result line, states and footers spanning the full width, so there is one scroll state and one load-ahead trigger.
  - Switching keeps the first visible document in view.
  - The layout is remembered on the phone in its own `display` preferences file. It survives sign-out, and the default is list.
  - Cards get a skeleton grid. Selection is a round check over the thumbnail's corner plus the 2dp `primary` ring and `primary-tint` ground. No tags on cards.
- API: no new or changed call; `/api/documents/{id}/thumb/` is unchanged (docs only add an optional `?version=`). `~/.config/paperless-companion/verify.env` is absent, so real-server checks will be skipped.
- Open risks: smooth scrolling and thumbnail sharpness at card size can only be judged on a phone.
