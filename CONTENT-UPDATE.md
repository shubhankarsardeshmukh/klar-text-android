# Klartext v1.23.0 – Goethe/telc alignment of levels, themes and grammar

Nothing was deployed and klartext-german.web.app was not touched. This folder is a ready-to-review copy.

## What changed
- **One shared set of 22 topic names at every level** (A1 to C1), for example "Food & Restaurant", "Work & Professions", "Society, Politics & Law". A topic that is too small at a level is merged into a related one; a topic over 60 words is split into "Part 1, Part 2". Result: 111 topics (A1 18, A2 19, B1 36, B2 30, C1 8), each 30–60 words, and no topic name repeats within a level.
- **Grammar and themes no longer overlap.** Pronouns, possessives, question words, prepositions, da-/wo-words, connectors, adverbs, numbers, modal verbs and verb + preposition phrases are only in Grammar. Two new Grammar groups: "Verb + preposition" (194 cards) and "Numbers" (26). Particles such as doch, mal, eben, halt, etwa were moved out of the topics. A browser test confirms that no function-word card sits in a topic.
- **Levels follow the Goethe lists (A1, A2, B1) and the telc course lists (up to B2).** If a word is in a Goethe list, that level wins; otherwise the telc level (B2 at most) is used. About 1,000 missing A1–B1 words were written (examples, Hindi, word families), including numbers, countries and languages, and 143 B1 adjectives/adverbs.
- Words only this app had (not in any Goethe/telc list) were moved to B2, not deleted, unless they are function words, adverbs, verb + preposition phrases or set phrases (these keep their old level). 70 newly written words that turned out not to be in the lists were also put at B2.

## Coverage against the lists (automatic comparison, lemma by lemma)
| Level | Entries in the lists | In the app at the same level | Not in the app |
|---|---|---|---|
| A1 | 1,086 | 817 (75.2%) | 268 |
| A2 | 1,418 | 844 (59.5%) | 571 |
| B1 | 2,593 | 1,782 (68.7%) | 787 |
| B2 (telc) | 257 | 31 (12.1%) | 224 |

Feminine "-in" forms (Lehrerin etc.) are counted as covered when the masculine word is in the app. App words not in any list: A1 64, A2 64, B1 115 (mostly phrases and function words that kept their level), B2 1,575, C1 321 (C1 is written from general knowledge, not from a list).

## Please read: what is approximate or not verified
- The word lists were read from PDFs. Parse noise remains (some entries are fragments, headings or course-specific words such as city names, "Jobcenter", "ICE"), so the "not in the app" numbers are an upper bound.
- telc course lists are long and contain many course-specific compounds (Praktikumsbetreuer, Sonnenterrasse). Only the words I judged everyone should know were added; the rest are listed as missing above. That is why A2, B1 and B2 coverage is lower than A1.
- "Einfach los" is treated as A2 evidence because it is labelled A1 but contains A2 words.
- Words in the app at a different level than the lists say (about 30) were moved to the list's level.
- The A1–B1 level of a word comes from the list, but its topic comes from my own reading of the word, which is not an official classification.
- Hindi glosses and the new examples were written by me and have not been checked by a native speaker.

## Re-running
```
python3 content/tools/gen_specs.py          # level plan + slug_fixes.txt -> source/curation/*.txt
python3 content/tools/apply_curation.py
python3 content/tools/validate_content.py   # must report 0 errors
python3 content/build_content.py            # writes public/data/*.dat and manifest.json
```
