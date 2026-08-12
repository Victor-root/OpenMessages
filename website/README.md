# OpenMessages website

The static source for the OpenMessages landing page. Plain HTML, CSS and
vanilla JS, hand-built, no framework or build step.

## Preview locally

```
python3 -m http.server 8000 --directory website
```

then open http://localhost:8000.

## Deploying

This folder is meant to be published as its own site root. With GitHub
Pages, point the deployment (branch folder or Actions workflow) at
`website/` so `index.html` ends up at the domain root.

## Structure

* `index.html`, the whole one-page site.
* `styles.css`, all styling (colors, layout, the phone/chat mockup, the
  reveal-on-scroll animation).
* `script.js`, the topbar scroll state and the reveal-on-scroll observer.
* `images/favicon-*.png`, rendered from the app's own `icon_source.png` and
  launcher background gradient.

The brand gradient (`#7B2DDC` to `#5D72FB`) and every icon on the page are
defined directly in `index.html`/`styles.css`, no external icon font or
image dependency beyond the Google-hosted Manrope/Outfit fonts.
