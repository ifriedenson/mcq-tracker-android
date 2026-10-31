# MCQ Tracker for Android

An **unofficial** native Android companion for the [MCQ Tracker](https://github.com/ifriedenson/mcq-tracker) Obsidian plugin.

> Not affiliated with or endorsed by Obsidian, or by the MCQ Tracker plugin. It reads and writes the plugin's note format; the notes remain ordinary Markdown files in your vault.

The app opens an Obsidian vault folder, finds question notes, lets you practise them offline, schedules reviews on the plugin's ladder, and can write compatible review state back into the notes.

## Features

- **Vault access over the Storage Access Framework** — pick any folder, with persisted read/write permission and a read-only fallback.
- **Bundled sample vault** so the app is usable the moment it launches.
- **Four question modes** — multiple choice, select-all-that-apply, true/false, and self-graded (SPR, numeric, free response).
- **Queues** — due, new and missed, plus single-question practice from the bank.
- **Grading and feedback** — automatic grading, explanations, hint toggle, confidence (Guessing / Unsure / Confident), and error tagging.
- **Plugin-compatible scheduling** — the 1 / 3 / 7 / 21 / 60-day ladder.
- **Optional write-back** of review properties into note frontmatter.
- **Local progress mirror** for sample notes, read-only notes and failed writes.
- **Screens** — Today, Questions, Progress, Settings, plus a practice screen and session summary.
- **Stats** — streak, accuracy, attempts, last-7-days chart, per-skill accuracy.
- **Flags and leeches** — flag questions locally (never written to the note) and filter by them; a question missed four or more times in a row is surfaced as a leech.

## Note format

A question note is Markdown with YAML frontmatter, and the `question_id` property is required. Indented below is a complete example (kept indented so this file itself never looks like a question note to the Obsidian plugin):

    ---
    question_id: SAMPLE-001
    course: Sample Course
    skill: Reading
    difficulty: easy
    choices: 4
    answer: C
    target_seconds: 60
    ---

    ## Question

    In the sentence "The experiment was inconclusive, so the team repeated it," what does "inconclusive" most nearly mean?

    ## Answer Choices

    - A. Dangerous
    - B. Expensive
    - C. Not decisive
    - D. Carefully planned

    ## Answer

    > [!answer]- Reveal answer
    > **Correct Answer: C**
    >
    > Inconclusive means the results did not settle the question.

Recognised sections (case-insensitive, aliases in brackets):

| Section | Aliases |
| --- | --- |
| Stimulus | stimulus, passage, source, excerpt, reading, text, figure |
| Question | question, stem, prompt |
| Hint | hint, clue |
| Choices | answer choices, choices, options, answers choices |
| Answer | answer, answer key, correct answer, answer and rationale, solution |
| Notes | my notes, notes |

Choices may be written `- A. text`, `- A) text` or `- A: text`, optionally bolded, and may wrap onto continuation lines.

The answer key is read from frontmatter (`answer`, `correct_answer`, `correct`, `answer_key`, `key`), or from choice-set notation (`A, C`, `A/C`, `A and C`, `AC`), or from the `## Answer` section (`Correct Answer: B`, `**Answer:** B`, `The correct answer is C`, `Correct Answer: False`). With no key the note becomes self-graded.

## Choosing a vault

Tap **Settings → Choose folder** and select the folder that contains your question notes. The app scans recursively for `.md` files, skipping dot-directories, stopping at 4,000 files or 2 MB per note, and only notes with a non-empty `question_id` become questions. The choice is remembered across restarts.

## Write-back and its risks

Write-back is **off on first launch**. Enable it in **Settings → Write progress into notes**.

When enabled, reviewing a note from your chosen folder updates these frontmatter properties: `result`, `date attempted`, `first_attempted`, `attempts`, `correct_count`, `streak`, `time_spent_total`, `last_time_spent`, `last_answer`, `last_confidence`, `last_error_type`, `next_review`, `last_attempt_id`, `history`.

Safety rules:

- The note **body is never modified**, and line endings are preserved.
- Before writing, the app re-reads the note and compares a content signature. If the note changed in Obsidian since it was loaded, the app **does not overwrite** it: it keeps your progress locally and reports "Note changed elsewhere — saved in app only".
- `srs`, `sm2`, `scheduler`, `flagged` and `study_status` are never written.
- Each attempt appears in `history` once; existing rows (and their optional 8th `variant` field) are preserved.

Writes go through the same note you selected, so keep a backup or a version-controlled vault if you care about the history.

## FSRS / SM-2 notes are read-only

Notes whose `scheduler` is `fsrs`, `sm2` or `srs`, or which contain `srs`/`sm2` state, are treated as scheduler-managed. The app shows their queue and keeps your progress locally so reviews still work, but **never writes to those notes**.

## Privacy

- No `INTERNET` permission in the manifest.
- No accounts, no analytics, no network calls.
- Notes are read only from the folder you pick; progress lives in this app's private storage.

## Build

Requirements: JDK 17, Android SDK platform 35 and build-tools 35.0.0.

```bash
# point Gradle at your SDK
echo "sdk.dir=$ANDROID_HOME" > local.properties

./gradlew testDebugUnitTest
./gradlew assembleDebug
```

The debug APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

CI runs the same two tasks on every push and pull request and uploads the debug APK as an artifact (`.github/workflows/android.yml`).

## Tests

`./gradlew testDebugUnitTest` covers the parser, grader, scheduler, writer, stats and progress store as pure JVM tests. The suite asserts the exact contracts: ladder intervals, guess and miss behaviour, history row format, write-back preservation, and merge precedence.

## Limitations (v1)

- No flashcards, cloze, image occlusion, calendar, AI, imports, Anki/CSV export or sync.
- No native FSRS or SM-2 scheduling — those notes are read-only.
- No LaTeX/MathJax rendering; Markdown is reduced to readable plain text.
- No creating or editing question notes inside the app.
- No process-death recovery for an in-progress timer.
- Verified with unit tests and a debug build; it has not been exercised on a physical device in this environment.

## License

MIT — see [LICENSE](LICENSE). The MCQ Tracker Obsidian plugin is the format reference and is MIT-licensed as well.
