### Bug Fixes (runtime - `html` / `@safe`)

#### `Html.clean` keeps a relative link: `<img src="/api/files/3">` keeps its `src` under `@safe(relaxed)` (issue 217)
- Why: `Html.clean` passed `""` as jsoup's base URI, so a relative URL could not be resolved, never passed the
  http/https check, and was removed - a site's own uploaded image lost its `src` under `@safe(relaxed)`, a link to
  `/posts/7` or `#top` its `href` under both safelists.
- Now: the markup is read against a placeholder base (`https://site.invalid/`) with `preserveRelativeLinks(true)` on a
  copy of the safelist (the one given is not changed), so a relative link passes the protocol check and is kept as
  written. What changes, for `basic` and `relaxed` alike:
  - kept now: a relative link (`/api/files/3`, `files/3`, `../x`, `#top`, `?q=1`) and a protocol-relative one
    (`//other.host/x.png`). The latter reaches a host that `https://other.host/x.png` reaches too, which both
    safelists already keep, so no new external host is allowed.
  - `rel="nofollow"` (basic): put on a link to another host, `//other.host/x` included, and no longer on a link to the
    site itself (`/posts/7`) - jsoup's rule once a link is relative.
  - an absolute link is kept as written rather than as jsoup normalised it (`HTTPS://Host/x` stays so, not
    `https://Host/x`).
  - still removed: every protocol the safelist refuses (`javascript:`, with any case, leading space, tab, newline or
    entity; `vbscript:`, `data:`), and a link a browser reads as another host's while jsoup reads it as a path of the
    base (`/\host`, `\\host`, a tab inside a relative link, `https:host`, `https:\\host`) - these are taken off before
    the clean, so their output is exactly what it was.
- Checked: `HtmlTest`, run in `elasticore-springboot3` and `elasticore-springboot4` (shared source): a relative image
  and links survive `relaxed` and `basic` (fail without the change); `nofollow` only for another host; the refused
  protocols and the browser-read forms are removed, and which links count as such; the given safelist is unchanged. A probe of 32 URLs through
  `<a href>` and `<img src>` under both safelists, old against new, changed only the forms listed above.
- Guides: the annotation guide (en, ko), `html / @safe / @plain` - relative links, `rel`, and that which images a writer
  may embed is the application's to check in `beforeSave`.
- Files: `Html.java`, `HtmlTest.java`, `elasticore-annotation-guide.md` (en, ko).
