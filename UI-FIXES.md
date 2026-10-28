# Klartext UI fixes (on top of web 1.21.1)

Only public/index.html (web) / app/src/main/assets/index.html (Android bundle) changed. Nothing deployed.

1. Bottom navigation partly hidden: the tab buttons used an invalid `font:` shorthand (6 places), so they
   rendered at the browser's default size, which made the bar wider than a phone and let the page zoom out.
   Fixed the shorthand, let tabs shrink, and locked the viewport (minimum-scale=1, viewport-fit=cover).
2. Flashcard content cut off: the card was forced to the free space above the answer bar and its back
   (examples, word family, tips) was clipped or scrolled inside. The card now grows to fit the face being
   shown; the page scrolls and the answer bar stays pinned flush to the bottom with a fade cue.
3. Answer bar: icon-over-label tool buttons (no more overlapping at 360px), tool row hidden while the
   feedback + Continue are showing, tighter spacing on short phones, "Loading voices" noise removed.
4. Screens use 100dvh (real visible height) instead of 100vh.
5. Grammar lesson tables wrap instead of cutting words off ("ein Buc").
6. Centered content that overflowed now uses `safe center` so the top is never unreachable.
