# Artboard snippets

Building blocks for `__MAIN__` in `artboard.dc.html`. **The copy is sample text**: replace all
of it, and never add a block because it's here. Every colour and radius is a `var(--…)` from
the template's `:root`. These are the first pass; when `docs/design/components.md` settles a
recipe, update its snippet to match.

## Top bar

Screen title with an action on the right:

```html
<header style="display:flex;align-items:center;justify-content:space-between;padding:12px 20px 8px"><h1 style="margin:0;font-size:28px;font-weight:500;letter-spacing:-0.01em">Documents</h1><button aria-label="Settings" style="width:44px;height:44px;border-radius:999px;border:0;background:var(--surface);color:var(--mfg);display:flex;align-items:center;justify-content:center"><svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3h0a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8v0a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z"/></svg></button></header>
```

## Search field

```html
<div style="padding:4px 20px 12px"><label style="display:flex;align-items:center;gap:10px;height:52px;padding:0 16px;border-radius:999px;background:var(--field);color:var(--mfg)"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" aria-hidden="true"><circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/></svg><input placeholder="Search your documents" style="flex:1;border:0;background:transparent;color:var(--fg);font:inherit;font-size:16px;outline:none"></label></div>
```

## Filter chips

Selected chip carries the green tint; the rest are quiet:

```html
<div style="display:flex;gap:8px;padding:0 20px 12px;overflow:hidden"><span style="height:34px;padding:0 14px;border-radius:999px;background:var(--ptint);color:var(--primary);display:inline-flex;align-items:center;font-size:14px;font-weight:500">All</span><span style="height:34px;padding:0 14px;border-radius:999px;border:1px solid var(--border);color:var(--mfg);display:inline-flex;align-items:center;font-size:14px">Bills</span><span style="height:34px;padding:0 14px;border-radius:999px;border:1px solid var(--border);color:var(--mfg);display:inline-flex;align-items:center;font-size:14px">Payslips</span></div>
```

## Document row

Thumbnail, title, correspondent · date, tags:

```html
<div style="display:flex;gap:14px;padding:12px;margin:0 12px 8px;border-radius:var(--r-card);background:var(--surface)"><div style="width:56px;height:72px;border-radius:10px;background:#e9ece8;flex-shrink:0"></div><div style="display:flex;flex-direction:column;gap:4px;min-width:0"><span style="font-size:16px;font-weight:500;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">Electricity bill — October</span><span style="font-size:14px;color:var(--mfg)">City Power · 3 Oct 2026</span><span style="display:flex;gap:6px;margin-top:2px"><span style="font-size:12px;padding:2px 8px;border-radius:999px;background:var(--raised);color:var(--mfg)">Bills</span><span style="font-size:12px;padding:2px 8px;border-radius:999px;background:var(--raised);color:var(--mfg)">House</span></span></div></div>
```

## Floating action

The one filled green action on the screen:

```html
<button style="position:absolute;right:20px;bottom:28px;height:56px;padding:0 22px;border:0;border-radius:18px;background:var(--primary);color:var(--pfg);font-size:16px;font-weight:600;display:flex;align-items:center;gap:10px;box-shadow:0 6px 20px rgba(0,0,0,.35)"><svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><path d="M12 5v14M5 12h14"/></svg>Add</button>
```

## Bottom sheet

Over a scrim; the screen behind stays drawn:

```html
<div style="position:absolute;inset:0;background:rgba(8,12,15,.55)"></div><div style="position:absolute;left:0;right:0;bottom:0;background:var(--surface);border-radius:var(--r-sheet) var(--r-sheet) 0 0;padding:12px 20px 28px"><div style="width:36px;height:4px;border-radius:2px;background:var(--border);margin:0 auto 16px"></div><h2 style="margin:0 0 12px;font-size:20px;font-weight:500">Add a document</h2><!-- rows --></div>
```

## Empty / failed card

```html
<div style="margin:40px 20px;padding:28px 24px;border-radius:var(--r-card);background:var(--surface);text-align:center;display:flex;flex-direction:column;gap:8px;align-items:center"><span style="font-size:17px;font-weight:500">Couldn't load your documents</span><span style="font-size:14px;color:var(--mfg);line-height:1.5">Check you're on the home network or the VPN, then try again.</span><button style="margin-top:12px;height:44px;padding:0 20px;border-radius:999px;border:1px solid var(--border);background:transparent;color:var(--fg);font-size:15px;font-weight:500">Try again</button></div>
```

## Skeleton row

```html
<div style="display:flex;gap:14px;padding:12px;margin:0 12px 8px;border-radius:var(--r-card);background:var(--surface)"><div class="shim" style="width:56px;height:72px"></div><div style="flex:1;display:flex;flex-direction:column;gap:8px;padding-top:4px"><div class="shim" style="height:14px;width:70%"></div><div class="shim" style="height:12px;width:45%"></div></div></div>
```
