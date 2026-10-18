# Klartext Android 1.4.3 (web 1.17.0)

Previous packages: web 1.16.0 / Android 1.4.2 (versionCode 13). This release: **web 1.17.0**, **Android 1.4.3 (versionCode 14)**.

## 1.17.0: Grammar tab

- **New Grammar tab** (6th tab in the bar). 47 lessons from A1 to B2 in English: nouns and articles, plurals, present tense, word order, questions, negation, cases (nominative, accusative, dative, genitive), prepositions, modal and separable verbs, Perfekt, Präteritum, Plusquamperfekt, subordinate clauses, relative clauses, adjective endings, comparison, reflexive verbs, zu-infinitives, Konjunktiv I and II, passive, participles, nominalisation, double connectors and more.
- Each lesson: short sections, tables, example sentences you can listen to (🔊), and **10-12 interactive exercises** (multiple choice, typed answers, word-order building, matching, true/false) with instant feedback and an explanation. 557 exercises in total. Retry just the ones you missed. Best score and stars are saved per lesson and sync with your account (`klartext-grammar-progress`).
- The old Learn tile called Grammar (little-word vocabulary: pronouns, connectors...) is now **Grammar words**.
- New data file `data/grammar_v1.dat` (70 KB), loaded when the tab is first opened.
- Lessons and exercises were AI-written and validated by script (structure, answer keys, one correct option each); a German teacher should review them before public launch. Edit the source JSON and re-pack to fix an entry.

## 1.16.0 (previous release, still included)

## What is new

### Memorize screen, fully populated
- **Several example sentences per word, each in a different context or meaning.** About 3,900 words (nouns, verbs, adjectives, adverbs, connectors, prepositions, pronouns and more) now carry 3-4 labelled contexts, for example *die Nummer*: phone number, shoe size, show act, magazine issue. Words with several meanings get one sentence per meaning. Speaking phrases (Redemittel) keep their own three sentences.
- **Memory hook, mini story, synonyms, opposites and word family** where a word has them (new content file `data/wortschatz_enrich_v1.dat`, 0.6 MB).
- **Gender palace** (nouns): tap the door (der / die / das) the noun lives behind; the right door opens with a short scene and the ending rule if there is one.
- **Write it out**: type the word, get instant feedback, ask for letter hints.
- **Listen / Slowly** buttons, 🔊 on every sentence, a "Hide English" switch to test yourself, and the matched word highlighted in each sentence.
- The old "COMING SOON" block is gone.
- The answer side of a flashcard shows two of the extra contexts and a button into the full Memorize screen.

### Sound
- **Mute button (top right, on every screen).** Master switch for sound effects and automatic pronunciation. It is the same setting as Profile > Sound effects and syncs with your account. The 🔊 buttons you press yourself always work.
- **New words are pronounced automatically** when they first appear (in English to German practice, when you flip the card, so the answer is not given away). Switch off in Profile > Say new words aloud.
- Android: new `speakWithRate` in the text-to-speech bridge for the slow button (older builds fall back to normal speed).

### How-to guide
- **Interactive 3D tour** (7 steps: drag a cube, flip and tilt a flashcard, shuffle contexts, move a card through the spaced-repetition boxes, tap the speaker, answer a mini quiz). Pure CSS 3D, no extra downloads, works offline.
- Opens automatically the first time the app is used; reopen it with the ⓘ button (top right) or Profile > How to use Klartext. "Seen" is stored per account (`klartext-howto-seen`, synced) and cleared by Reset.
- The Android back button closes the guide.

### Layout
- Every screen except Home, login and the first-run level picker now has a 58 px strip at the top for the two buttons.

## Notes
- The extra sentences, hooks and stories were AI-written and checked by script (structure, counts, length, no duplicates), not word-by-word by a native speaker. Worth a skim by a German speaker before a public launch; fixing an entry means editing the JSON and re-packing.
- Android was **not compiled here**: check the first build.
