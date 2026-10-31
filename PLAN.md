# PLAN — MCQ Tracker for Android

Derived line by line from the repository's handoff spec (`# Handoff build the MCQ Tracker And.txt`, 460 lines). This file is the working checklist: it is updated after every implementation pass, and every item carries a status.

Sources of truth, in order:
1. The handoff contract (note format, grading, scheduling, write-back, file map, tests).
2. This plan's checkboxes.
3. The user's iterative loop (implement → update plan → test → push; then MIT-plugin research and feature expansion).

**Legend:** `[x]` implemented and verified · `[~]` partially done or not verifiable in this environment · `[ ]` not started.

---

## 0. Product definition

An **unofficial** native Android companion to the "MCQ Tracker" Obsidian plugin: it opens an Obsidian vault folder over the Storage Access Framework, finds MCQ Tracker question notes, lets the user practise them offline, schedules reviews on the plugin's 1/3/7/21/60-day ladder, and optionally writes compatible review state back into the notes.

- [x] **App name:** "MCQ Tracker" / "MCQ Tracker for Android", unofficial companion stated in-app and in the README.
- [x] **Package:** `com.mcqtracker.android`. **Version:** 0.1.0 (versionCode 1).
- [x] **License:** MIT (this app), plugin credited as format reference only.

## 1. Non-negotiable constraints

- [x] Work confined to this repository; nothing in `mcq-tracker` touched.
- [x] Offline and local-first: no `INTERNET` permission, no analytics, no accounts, no network calls.
- [x] No `.md` file in this repo is a question note; bundled samples are `.qnote`.
- [x] No prompt/README file with frontmatter `question_id` (the README example is an indented code block, so no line starts with `question_id:`).
- [x] Project at the repo root, not nested, not inside the plugin repo or a vault.
- [x] Dependencies limited to AndroidX, Compose Material 3, DocumentFile, JUnit, plus `kotlinx-coroutines-android` (required for `Dispatchers.IO` file work). No Room, Hilt, Retrofit, Markdown renderer.
- [x] MIT `LICENSE` present; unofficial disclaimer in-app and in README.

## 2. Stack and project shape

- [x] Kotlin 2.0.21, AGP 8.7.3, Gradle 8.9, JDK 17.
- [x] `compileSdk 35`, `targetSdk 35`, `minSdk 26`.
- [x] Compose BOM 2024.10.01, Material 3, `activity-compose`, lifecycle ViewModel, `androidx.documentfile`, JUnit 4.
- [x] Root layout: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `app/build.gradle.kts`, `app/src/main/...`.
- [x] `.gitignore` covering `.gradle/`, `build/`, `local.properties`, `.idea/`, `*.iml`, `dist/`, plus secrets.
- [x] `README.md`, `LICENSE` (MIT), optional `NOTICE` (not needed).
- [x] Gradle wrapper generated and committed (wrapper JAR, `gradlew`, `gradlew.bat`, properties).
- [x] No `android.permission.INTERNET` in the manifest (verified in the built APK with `aapt2 dump permissions`).

## 3. Note-format contract

- [x] All listed properties parsed: `question_id, course, test, section, domain, skill, difficulty, unit, question_type, answer, choices, target_seconds, stimulus_id, scheduler, study_status, known_until, attempts, correct_count, streak, next_review, result, last_confidence, last_error_type, time_spent_total, last_time_spent, flagged, history`.
- [x] Section aliases (case-insensitive, stripping `* _ `` `): stimulus/passage/source/excerpt/reading/text/figure; question/stem/prompt; hint/clue; answer choices/choices/options/answers choices; answer/answer key/correct answer/answer and rationale/solution; my notes/notes.
- [x] Choices: `- A. text`, `- A) text`, `- A: text`, optionally bolded (`- **A)** text`), wrapped continuation lines, letters A–H.
- [x] Answer-key precedence: frontmatter → choice sets (`A, C`, `A/C`, `A and C`, `AC`) → single letter → `## Answer` patterns → self-graded.
- [x] Mode inference: SPR/numeric family → self-graded; select-all/checkbox family → select-all; true-false/boolean family → true/false; mcq/multiple/choice/select → MCQ or select-all when the key is a set; set key → select-all; True/False choice pair → true/false; word key without choices → true/false; single letter → MCQ; no key → self-graded.
- [x] MCQ/select-all with fewer than two choices downgrades to self-graded.
- [x] Normalization: MCQ uppercase; select-all sorted `A, C`; true/false canonical `True`/`False` mapping letters through the note's own choices; self-graded keeps the raw key.
- [x] Stimulus borrowed by `stimulus_id` when `## Stimulus` is absent.
- [x] `study_status` respected: `excluded`/`suspended` never queued; `known` only when `known_until` is today or earlier; statuses never rewritten.
- [x] Markdown stripped to plain text; callouts, links, images and emphasis removed; no LaTeX rendering.

## 4. Grading contract

- [x] MCQ: chosen letter equals key, case-insensitive.
- [x] Select-all: exact set equality (missing or extra = wrong).
- [x] True/false: canonical comparison, letter keys mapped through choices.
- [x] Self-graded: "I got it right" / "I got it wrong"; history answer empty.
- [x] Confidence 1/2/3 = Guessing/Unsure/Confident, default 2.

## 5. Scheduling contract (exact)

Intervals `[1, 3, 7, 21, 60]` days.

```
conf = clamp(confidence, 1, 3)
if correct:
    streak = (conf == 1) ? previousStreak : previousStreak + 1
    intervalDays = (conf == 1) ? 2 : intervals[min(max(streak,1)-1, 4)]
else:
    streak = 0
    intervalDays = 1
next_review = localToday + intervalDays
attempts += 1; correct_count += correct?1:0
time_spent_total += seconds; last_time_spent = seconds
last_result = correct ? "correct" : "incorrect"
last_confidence = conf; last_error_type = ""
```

- [x] First correct → 1 day, second → 3, third → 7, fourth → 21, fifth+ → 60.
- [x] Correct guess (confidence 1) → 2 days, streak unchanged.
- [x] Miss → 1 day, streak 0.
- [x] Error tags use the plugin's exact values: `careless`, `content gap`, `misread`, `ran out of time`, `guessed`.

## 6. Write-back contract

- [x] Writes only when write-back is enabled, the note came from SAF, and `scheduler` is not `fsrs`/`sm2`/`srs` (and no `srs`/`sm2` state exists).
- [x] Fields written: `result`, `date attempted` (`yyyy-MM-dd'T'HH:mm`), `first_attempted` (only if missing/empty), `attempts`, `correct_count`, `streak`, `time_spent_total`, `last_time_spent`, `last_answer`, `last_confidence`, `last_error_type`, `next_review` (`yyyy-MM-dd`), `last_attempt_id`, `history`.
- [x] `srs`, `sm2`, `scheduler`, `flagged`, `study_status` and the note body are never modified.
- [x] History row `yyyy-MM-dd'T'HH:mm:ss|correct|seconds|chosen|confidence|error_type|attempt_id`; `|`/newlines sanitized; no duplicate attempt id; optional 8th `variant` preserved; quoted YAML entries parsed.
- [x] Whole `history` list replaced from local state only after the unchanged-note check.
- [x] Write safety: re-read, signature compare, abort on external change, single `openOutputStream(uri,"wt")` write, keep local progress on failure.
- [x] Write-back defaults **off**; Settings explains what is written.
- [x] Storage status surfaced after each answer ("Saved to note" / "Saved in app only" / "Note changed elsewhere — saved in app only").

## 7. Local progress

- [x] SharedPreferences JSON mirror keyed by `sourceKey::question_id` (`sample` or SAF tree URI).
- [x] Stores attempts, correct count, streak, next review, last result, last confidence, last error type, seconds, history, flagged.
- [x] Merge rule: more attempts wins → more history rows → non-empty local error tag → else note.
- [x] FSRS/SM-2 notes read-only on disk but still due in-app.

## 8. Architecture and file map

```
app/src/main/java/com/mcqtracker/android/
  MainActivity.kt, AppViewModel.kt
  data/  Model.kt, NoteParser.kt, NoteWriter.kt, Scheduler.kt,
         MarkdownLite.kt, ProgressStore.kt, VaultRepository.kt, Json.kt
  domain/ AnswerTypes.kt, AnswerGrader.kt, Stats.kt
  ui/     Theme.kt, Components.kt, MCQApp.kt, PracticeScreen.kt, LibraryScreens.kt
```

- [x] `MainActivity`: `OpenDocumentTree`, persisted read/write with read-only fallback.
- [x] `VaultRepository`: recursive `.md` scan, dot dirs skipped, 4,000-file / 2 MB caps, non-empty `question_id` required.
- [x] File work on `Dispatchers.IO`.
- [x] `Question.key = "$sourceKey::$questionId"`; raw text kept until write-back; refreshed after a successful write.
- [x] `Json.kt` added: a dependency-free encoder/decoder so the progress store is unit-testable without `org.json` stubs.

## 9. Screens and visual direction

- [x] Theme: warm paper background, deep indigo primary, amber secondary, green success, red error, serif headings, light + dark.
- [x] **Today** — title, vault pill, sample notice, hero due+new with Start practice, streak/accuracy/attempts tiles, Practice all + Missed, first three questions + Browse all, read-only FSRS count note.
- [x] **Practice** — close, x of n, topic, elapsed timer with target indicator, progress bar, stimulus, question, hint toggle, choices, confidence chips, self-graded buttons, submit gating, result card (answer, explanation, next review + interval, storage status, error tags), Next/See summary.
- [x] **Summary** — accuracy ring, correct count, total/average time, missed list (tap to practise), Back to Today / Review misses.
- [x] **Questions** — search across prompt/ID/skill/course/domain/section/test; All/Due/New/Missed filters; rows with topic, title, status pill, accuracy/attempts, FSRS marker.
- [x] **Progress** — accuracy hero, study days, logged time, streak, last-7-days bar chart, per-skill accuracy bars, empty state, local-only privacy note.
- [x] **Settings** — vault source, choose folder, rescan, use sample; write-back switch with explanation/warning; compatibility, scheduling, privacy sections; about/version/unofficial disclaimer.

## 10. Sample vault and plugin isolation

- [x] Four original samples in `app/src/main/assets/sample-vault/Questions/` as `.qnote` (reading meaning, linear equation, cell organelle true/false, pH calculation).
- [x] Loader reads `.qnote`.
- [x] Isolation check that matters: **no `.md` file anywhere in this repository contains a frontmatter `question_id`** (verified by `grep -rln --include=*.md '^question_id:' .`). The four samples are `.qnote`, and the plugin only discovers `.md`, so it cannot see them.
- [~] The handoff's literal check `grep -rl '^question_id:' app/src/main/assets` returns nothing is impossible to satisfy while bundling samples that carry a `question_id`; that grep matches the `.qnote` files by content. The `.qnote` extension, not the grep, is what provides the isolation, and the plugin never scans it.

## 11. Tests

- [x] **Parser** — documented sample note; aliases + bold/list choices; answer only in `## Answer`; select-all `A, C` / `A/C` / `A and C` / `AC`; true/false as word and as letter via choices; SPR/numeric self-graded; progress + history parsing; borrowed stimulus; malformed/missing frontmatter → null; Markdown cleanup; FSRS detection; excluded/suspended never queued.
- [x] **Grader** — MCQ; select-all exact set; true/false custom labels; self-grade; correct letters and labels.
- [x] **Scheduler** — 1/3/7/21/60; guess → 2 days, streak unchanged; miss → 1 day, streak 0; clamping; exact history row and timestamp formats; error tag only on the matching attempt.
- [x] **Note writer** — body preserved; scalar updates; `first_attempted` preserved; history appended once; quoted rows survive; error tag into matching row + `last_error_type`; unknown properties untouched; CRLF + trailing newline preserved; no-frontmatter note gains a valid one.
- [x] **Stats** — due/new/missed counts, accuracy, streak across today/yesterday, last-7-days window, per-skill accuracy, search/filter.
- [x] **Repository/sample** — four `.qnote` assets parse; no `.md` fixtures; merge tested as a pure function.
- [x] `./gradlew testDebugUnitTest` passes: **56 tests, 0 failures, 0 errors**.

## 12. Build and verify

- [x] JDK 17, Android SDK platform 35, build-tools 35.0.0, Gradle 8.9 installed in the sandbox.
- [x] `local.properties` with the SDK path (gitignored).
- [x] `./gradlew assembleDebug` produces `app/build/outputs/apk/debug/app-debug.apk` (9.3 MB).
- [ ] Device/emulator run — **not possible here**: no emulator or physical device in this environment. Runtime behaviour (SAF picker, write-back against a real Obsidian vault) is therefore untested on hardware and must not be claimed as verified.

## 13. Deliverables

- [x] Buildable debug APK.
- [x] Passing unit tests.
- [x] README: features, note format, vault choice, write-back behaviour + risks, FSRS/SM-2 read-only policy, privacy/no network, build steps, test command, limitations, unofficial disclaimer.
- [x] CI workflow `.github/workflows/android.yml`: Temurin 17, `android-actions/setup-android`, `testDebugUnitTest` + `assembleDebug` on push/PR, debug APK artifact.
- [x] No changes to `mcq-tracker`.

## 14. Phase B — related MIT plugins (user loop steps 8–12)

Not part of the handoff's v1 scope; executes after v1 is green.

- [ ] Search related Obsidian study plugins; for each **MIT-licensed** one, record: plugin name, plugin link, GitHub repo link, and the feature it has that MCQ Tracker lacks.
- [ ] Add those features top-to-bottom while preserving MCQ Tracker's identity and the hard constraints (offline, no network, minimal deps).
- [ ] Keep attribution and license notices where MIT code is reused or merged.
- [ ] Re-test and re-push after each feature batch.

> Open conflict to resolve before Phase B: the handoff lists flashcards/cloze, calendar, AI, imports, Anki/CSV export, sync and FSRS as **out of scope for v1**. Phase B deliberately revisits that list; v1 constraints (no network, no INTERNET permission) still hold unless the user overrides them.

---

## Status log

| Date | Pass | Result |
| --- | --- | --- |
| 2026-10-31 | 1 | Plan created from the 460-line handoff; sections 0–13 written, implementation not started |
| 2026-10-31 | 2 | Toolchain installed (JDK 17.0.21, Gradle 8.9, SDK 35 + build-tools 35.0.0); Gradle project, manifest, .gitignore and LICENSE added |
| 2026-10-31 | 3 | Data/domain layer implemented: model, parser, writer, scheduler, markdown, JSON, progress store, repository, stats, grader |
| 2026-10-31 | 4 | Compose UI implemented: theme, components, Today, Practice, Summary, Questions, Progress, Settings, ViewModel, SAF activity |
| 2026-10-31 | 5 | First build: 2 Kotlin errors fixed (wrong argument type; non-suspend recursive scan). Then 3 test failures: 1 real parser bug (`- **A)** text` leaked `**` into choice text) fixed, 2 wrong test expectations corrected |
| 2026-10-31 | 6 | Green build: 56 tests, 0 failures, debug APK produced; README + CI added |
