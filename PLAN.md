# PLAN — MCQ Tracker for Android

Derived line by line from the repository's handoff spec (`# Handoff build the MCQ Tracker And.txt`, 460 lines). This file is the working checklist: it is updated after every implementation pass, and every item carries a status.

Sources of truth, in order:
1. The handoff contract (note format, grading, scheduling, write-back, file map, tests).
2. This plan's checkboxes.
3. The user's iterative loop instructions (implement → update plan → test → push, then MIT-plugin research and feature expansion).

---

## 0. Product definition

An **unofficial** native Android companion to the "MCQ Tracker" Obsidian plugin: it opens an Obsidian vault folder over the Storage Access Framework, finds MCQ Tracker question notes, lets the user practise them offline, schedules reviews on the plugin's 1/3/7/21/60-day ladder, and optionally writes compatible review state back into the notes.

- **App name:** "MCQ Tracker" / "MCQ Tracker for Android" — unofficial companion, stated in-app and in the README.
- **Package:** `com.mcqtracker.android`. **Version:** 0.1.0 (versionCode 1).
- **License:** MIT (this app). The Obsidian plugin is credited as the format reference only.

## 1. Non-negotiable constraints

- [ ] Work only in this repository; never commit, push, or edit anything in `mcq-tracker`.
- [ ] Offline and local-first: no `INTERNET` permission, no analytics, no accounts, no network calls.
- [ ] No `.md` file anywhere in this repo that contains a `question_id` property; bundled samples use `.qnote`.
- [ ] No prompt/README file at the repo root with a frontmatter `question_id`.
- [ ] Project lives at the repo root, not nested, and not inside the plugin repo or a vault.
- [ ] Dependencies limited to AndroidX, Compose Material 3, DocumentFile, JUnit. No Room, Hilt, Retrofit, Markdown renderer.
- [ ] MIT `LICENSE` present; unofficial disclaimer visible in-app and in README.

## 2. Stack and project shape

- [ ] Kotlin 2.0.21, AGP 8.7.3, Gradle 8.9, JDK 17.
- [ ] `compileSdk 35`, `targetSdk 35`, `minSdk 26`.
- [ ] Compose BOM 2024.10.01, Material 3, `activity-compose`, lifecycle ViewModel, `androidx.documentfile`, JUnit 4.
- [ ] Root layout: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `app/build.gradle.kts`, `app/src/main/...`.
- [ ] `.gitignore` covering `.gradle/`, `build/`, `local.properties`, `.idea/`, `*.iml`, `dist/`.
- [ ] `README.md`, `LICENSE` (MIT), optional `NOTICE`.
- [ ] Gradle wrapper generated and committed (wrapper JAR, `gradlew`, `gradlew.bat`, properties).
- [ ] No `android.permission.INTERNET` in the manifest.

## 3. Note-format contract

Question note = Markdown + YAML frontmatter; `question_id` required.

- [ ] Parse properties: `question_id, course, test, section, domain, skill, difficulty, unit, question_type, answer, choices, target_seconds, stimulus_id, scheduler, study_status, known_until, attempts, correct_count, streak, next_review, result, last_confidence, last_error_type, time_spent_total, last_time_spent, flagged, history`.
- [ ] Section aliases (case-insensitive, stripping `* _ `` `):
  - stimulus: `stimulus, passage, source, excerpt, reading, text, figure`
  - question: `question, stem, prompt`
  - hint: `hint, clue`
  - choices: `answer choices, choices, options, answers choices`
  - answer: `answer, answer key, correct answer, answer and rationale, solution`
  - notes: `my notes, notes`
- [ ] Choices: `- A. text`, `- A) text`, `- A: text`, optionally bolded; wrapped continuation lines; letters A–H.
- [ ] Answer-key precedence: frontmatter (`answer`, `correct_answer`, `correct`, `answer_key`, `key`) → choice sets (`A, C`, `A/C`, `A and C`, `AC`) → single letter → `## Answer` text patterns (`Correct Answer: B`, `**Answer:** B`, `The correct answer is C`, `Correct Answers: A, C`, `Correct Answer: False`) → else self-graded.
- [ ] Mode inference: `spr | student-produced | grid-in | free-response | short-answer | numeric | fill` → self-graded; `select_all | sata | multi-select | multi-answer | checkbox` → select-all; `true_false | boolean | tf` → true/false; `mcq | multiple | choice | select` → MCQ or select-all if key is a set; set key → select-all; choices exactly True/False → true/false; full-word True/False key with no choices → true/false; single letter → MCQ; no key → self-graded.
- [ ] < 2 choices on MCQ/select-all → downgrade to self-graded.
- [ ] Normalization: MCQ uppercase; select-all sorted `A, C`; true/false canonical `True`/`False` mapping letters through the note's own choices; self-graded keeps the raw key.
- [ ] Borrow stimulus by `stimulus_id` when `## Stimulus` is absent.
- [ ] Respect `study_status`: `excluded`/`suspended` never queued; `known` only when `known_until` is today or earlier; never rewrite these statuses.
- [ ] Strip Markdown to readable plain text; no LaTeX/MathJax rendering in v1.

## 4. Grading contract

- [ ] MCQ: chosen letter equals key, case-insensitive.
- [ ] Select-all: exact set equality (missing or extra = wrong).
- [ ] True/false: compare canonical values, letter keys mapped through choices.
- [ ] Self-graded: "I got it right" / "I got it wrong"; history answer field empty.
- [ ] Confidence labels 1 = Guessing, 2 = Unsure, 3 = Confident; default 2.

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

- [ ] First correct → 1 day, second → 3, third → 7, fourth → 21, fifth+ → 60.
- [ ] Correct guess (confidence 1) → 2 days, streak unchanged.
- [ ] Miss → 1 day, streak 0.
- [ ] Error tags use exact plugin values: `careless`, `content gap`, `misread`, `ran out of time`, `guessed`.

## 6. Write-back contract

Write only when **all** hold:

- [ ] User enabled write-back.
- [ ] Note came from an SAF file (not the bundled sample).
- [ ] `scheduler` is not `fsrs`/`sm2`; treat existing `srs`/`sm2` state as scheduler-managed.

Fields written (top level, body and line endings preserved): `result`, `date attempted` (`yyyy-MM-dd'T'HH:mm`), `first_attempted` (only if missing/empty), `attempts`, `correct_count`, `streak`, `time_spent_total`, `last_time_spent`, `last_answer` (empty for self-graded), `last_confidence`, `last_error_type`, `next_review` (`yyyy-MM-dd`), `last_attempt_id` (UUID), `history`.

- [ ] Never modify `srs`, `sm2`, `scheduler`, `flagged`, `study_status`, or the note body.
- [ ] Never write FSRS/SM-2 notes.
- [ ] History row `yyyy-MM-dd'T'HH:mm:ss|correct|seconds|chosen|confidence|error_type|attempt_id`; sanitize `|`/newlines in `chosen`; append only if attempt ID absent; preserve optional 8th `variant` field; parse/preserve quoted YAML list entries.
- [ ] On write, replace the whole `history` list with the local list only after verifying the note is unchanged.
- [ ] Write safety: re-read, compare signature/mtime, abort on external change (message + keep local progress), single `openOutputStream(uri,"wt")` write, keep local progress on failure.
- [ ] Write-back defaults **off**; Settings explains what is written.
- [ ] Storage status surfaced after each answer: "Saved to note" / "Saved in app only" / "Note changed elsewhere — saved in app only".

## 7. Local progress

- [ ] SharedPreferences JSON mirror keyed by `sourceKey::question_id` (`sourceKey` = `sample` or SAF tree URI).
- [ ] Store attempts, correct count, streak, next review, last result, last confidence, last error type, seconds, history, flagged.
- [ ] Merge rule: more attempts wins; tie → more history rows; tie → non-empty local error tag over empty note tag; else note.
- [ ] FSRS/SM-2 notes read-only locally but still due in-app.

## 8. Architecture and file map

```
app/src/main/java/com/mcqtracker/android/
  MainActivity.kt              // Compose host + SAF picker + persisted permission
  AppViewModel.kt              // UiState, screens, queues, submit, error tags, write-back
  data/  Model.kt, NoteParser.kt, NoteWriter.kt, Scheduler.kt,
         MarkdownLite.kt, ProgressStore.kt, VaultRepository.kt
  domain/ AnswerTypes.kt, AnswerGrader.kt, Stats.kt
  ui/     Theme.kt, Components.kt, MCQApp.kt, PracticeScreen.kt, LibraryScreens.kt
```

- [ ] `MainActivity`: `ActivityResultContracts.OpenDocumentTree`, persisted read/write with read-only fallback.
- [ ] `VaultRepository`: recursive `.md` scan, skip dot dirs, cap 4,000 files / 2 MB per note, require non-empty `question_id`.
- [ ] File work on `Dispatchers.IO`; never block main thread.
- [ ] `Question.key = "$sourceKey::$questionId"`; keep raw text until write-back; refresh raw text + progress after successful write.

## 9. Screens and visual direction

Theme: warm paper background, deep indigo primary, amber secondary, green success/tertiary, red error; serif display/headline, clean body; light + dark.

- [ ] **Today** — title + vault pill; sample notice; hero due+new with "Start practice"; streak/accuracy/attempts tiles; Practice all + Missed actions; first three questions + Browse all; read-only FSRS/SM-2 count note.
- [ ] **Practice** — close, x of n, topic, elapsed timer, target-time indicator, progress bar; stimulus, question, hint toggle, choices; confidence chips before submit; self-graded buttons; submit disabled until pick; result card (correct/incorrect, correct answer, explanation, next review + interval, storage status, error-tag chips); Next / See summary.
- [ ] **Summary** — accuracy ring, correct count, total time, average time; missed list (tap to practise one); Back to Today / Review misses.
- [ ] **Questions** — search across prompt/ID/skill/course/domain/section/test; filters All/Due/New/Missed; rows with topic, title, status pill, accuracy/attempts, FSRS marker; tap to practise.
- [ ] **Progress** — accuracy hero, study days, logged time, streak; last-7-days bar chart; per-skill accuracy bars; empty state and local-only privacy note.
- [ ] **Settings** — vault source, choose folder, rescan, use sample; write-back switch with explanation/warning; compatibility, scheduling, privacy sections; about/version/unofficial disclaimer.

## 10. Sample vault and plugin isolation

- [ ] Four original samples in `app/src/main/assets/sample-vault/Questions/` with `.qnote` (reading meaning, linear equation, cell organelle, pH calculation).
- [ ] Loader reads `.qnote`.
- [ ] Isolation check: `find . -name '*.md'` has no `question_id`; `grep -rl '^question_id:' app/src/main/assets` returns nothing.

## 11. Tests (write alongside logic)

- [ ] **Parser** — documented sample note (id, mode, key C, 4 choices, explanation, target seconds); aliases + bold/list choices; answer only in `## Answer`; select-all `A, C` and `A/C`; true/false as word and as letter via choices; no-choice true/false; SPR/numeric self-graded; progress + history parsing; borrowed stimulus; malformed/missing frontmatter → null; Markdown cleanup (callouts, links, images, bold).
- [ ] **Grader** — MCQ; select-all exact set; true/false custom labels; self-grade; correct choice letters and labels.
- [ ] **Scheduler** — 1/3/7/21/60; guess → 2 days, streak unchanged; miss → 1 day, streak 0; confidence clamping; exact history row + date formats; error tag updates only matching attempt.
- [ ] **Note writer** — body preserved; scalar updates; `first_attempted` preserved; history appended once, no duplicate attempt ID, quoted entries; error tag into matching row + `last_error_type`; FSRS/SM-2/unknown properties untouched; CRLF + trailing newline behavior; note without frontmatter gains a valid one.
- [ ] **Stats** — due/new/missed counts, accuracy, streak across today/yesterday, last-7-days counts, per-skill accuracy.
- [ ] **Repository/sample** — `.qnote` assets yield four questions; merge logic tested as a pure function.
- [ ] `./gradlew testDebugUnitTest` passes.

## 12. Build and verify

- [ ] JDK 17, Android SDK platform 35, build-tools 35.0.0, Gradle 8.9 available in this sandbox.
- [ ] `local.properties` with SDK path (gitignored).
- [ ] `./gradlew assembleDebug` produces `app/build/outputs/apk/debug/app-debug.apk`.
- [ ] Device/emulator run: if unavailable, state it explicitly and do not claim runtime testing.

## 13. Deliverables

- [ ] Buildable debug APK.
- [ ] Passing unit tests.
- [ ] README covering features, note format, vault choice, write-back behavior + risks, FSRS/SM-2 read-only policy, privacy/no network, build steps, test command, limitations, unofficial disclaimer.
- [ ] CI workflow: `./gradlew testDebugUnitTest assembleDebug` on push/PR, Temurin 17, `android-actions/setup-android`, upload debug APK artifact.
- [ ] No changes to `mcq-tracker`.

## 14. Phase B — related MIT plugins (user loop steps 8–12)

Not part of the handoff's v1 scope; executed after v1 is green.

- [ ] Search related Obsidian study plugins; for each **MIT-licensed** one, record in this plan: plugin name, link, GitHub repo link, and the feature it has that MCQ Tracker lacks.
- [ ] Add those features to this app top-to-bottom, preserving MCQ Tracker's identity and the handoff's hard constraints (offline, no network, minimal deps).
- [ ] Where MIT code is reused or merged, keep attribution and license notices.
- [ ] Re-test and re-push after each feature batch.

> Conflict to resolve with the user before Phase B: the handoff explicitly lists flashcards/cloze, calendar, AI, imports, Anki/CSV export, sync and FSRS as **out of scope for v1**. Phase B deliberately revisits that list; v1 constraints (no network, no INTERNET permission) still hold unless the user overrides them.

---

## Status log

| Date | Pass | Result |
| --- | --- | --- |
| 2026-10-31 | Plan created from handoff | Sections 0–13 written from the 460-line spec; implementation not started |
