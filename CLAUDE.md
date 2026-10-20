# claude-twist

An Android app that drives the Claude Android app's own dictation and narration by a twist of the
wrist, so a walk can be spent talking to Claude with the phone in a pocket. Hold a high,
professional bar: a small tree is no excuse for a careless one — no sloppy shortcuts, no leaving the
tree in a half-working state.

Aim for elegance in code: concision and extensibility. Don't settle for the mediocre. Name
identifiers after exactly what they stand for — a long name over a meaningless one. Name functions,
as a rule, with imperative, self-explanatory phrases. Fix foolishness wherever you come across it in
the code.

## Model check

**Do this before anything else — before reading files, before writing code, before any tool call.**
Check which model you are. If it is not some version of Opus, and the model was not explicitly
switched to within the session, tell the user immediately and wait for explicit confirmation before
continuing. Do not proceed until the user replies.

## No AI attribution

Commits are the user's own. Do not add `Co-Authored-By` or any AI attribution to git commits, and do
not use or refer to the claude account.

**Never invent an explanation.** A commit message says what the change does and what in the code
required it — nothing else. Do not write that something was missing, awkward, repetitive or hard for
users, and do not describe a need, a workflow or a shortcoming of the old behaviour, unless the code
or the user stated it. A plain description of the change is a complete message.

**No fix-narration in comments.** When fixing a bug, never leave a comment explaining the fixed
mistake or warning "don't do X". The fix is visible in the code and in history; the rationale belongs
in the commit message.

## Git / branches

- **Never commit or push directly to `main` on your own.** All work goes on a feature branch.
- Sync before branching: `git fetch origin && git checkout main && git merge --ff-only origin/main`,
  then `git checkout -b <type>/<name>`. On a stale branch: `git fetch origin && git rebase
  origin/main`.
- Name branches with a conventional type prefix (`feat/`, `fix/`, `docs/`, `chore/`). Do not use a
  `claude/` prefix; if a session starts on a harness-assigned branch, create a conventionally named
  one and work there.
- Do not suggest deleting branches. Avoid force pushes.
- The remote is reached through the `github-personal` SSH host alias.

## What this app rests on, and how it breaks

Everything hinges on the **content descriptions the Claude app publishes for accessibility
services**. They are not an API and carry no compatibility promise: a Claude app release may rename
one at any time, and the app then drives nothing.

That is why the vocabulary is data, not code paths. `ClaudeAppControl` holds the shipped defaults,
`SettingsRepository` stores a per-control override, and the Controls screen lists the descriptions of
whatever window is in front so a renamed control can be relearned on the phone without a new release.
Keep it that way: never branch on a description inline.

The descriptions below were read off **com.anthropic.claude 1.261016.51** on a OnePlus 8T running
Android 14. Two shapes occur, and `AccessibilityTreeSearch` handles both by climbing to the nearest
clickable self-or-ancestor:

| Control | Description | Where the description sits |
| --- | --- | --- |
| `StartSpeechInput` | `Start speech input` | a presentational child; press the ancestor |
| `StopDictation` | `Stop dictation` | the clickable `Button` itself |
| `SendDictation` | `Send dictation` | the clickable `Button` itself |
| `CancelDictation` | `Cancel` | the clickable `Button` itself |
| `NarrateResponse` | `Narrate` | a presentational child; press the ancestor |
| `PauseNarration` | `Pause narration` | replaces `Narrate` on the newest row while it plays |
| `RetryMessage` | `Retry message` | never pressed — the landmark marking the newest message |

Two facts of the Claude app's composer that the design leans on:

- `Send dictation` **stops and sends in one press**, so a turn needs no stop-then-send chaining.
- The slot holding `Voice Mode` on an empty composer holds `Send` once there is text in it. The
  states are therefore told apart by which controls exist, which is why this app keeps no state
  machine of its own — see `ClaudeAppReading`.

### Rediscovering the vocabulary from the workstation

With the phone on USB:

```sh
adb shell uiautomator dump /sdcard/ct.xml && adb pull /sdcard/ct.xml
```

then read the `content-desc` of every node, and for each one find the nearest ancestor with
`clickable="true"` — that ancestor's bounds are what a tap would need. Put the Claude app into each
state in turn (idle composer, dictating, narrating) and dump again; a state shows only its own
controls.

## Known tuning points

Two things are deliberate guesses, flagged so they are not mistaken for settled:

- **A cue may land in the dictation.** `Cue.ListeningStarted` plays right after the microphone opens,
  because a cue before the press would lie about a press that then failed. If the beep turns up in
  transcripts, move it ahead of the press in `ClaudeAppDriverService.pressPlan`.
- **When a response counts as finished.** The action row carrying `Narrate` appears when a message is
  complete, but it was never observed whether the Claude app withholds it while text is still
  streaming. `narrateResponseOnceFinished` therefore also waits for the text on screen to stop
  changing. If the row is proven to appear only at completion, the settling wait can go.

## Verification

- `./gradlew test` — the pure layer: the gesture recogniser (`TwistDetector`) and the gesture
  semantics (`ClaudeAppReading`, `planPressesForTwist`). Both are free of Android types on purpose;
  keep new logic that can be tested this way out of the Android classes.
- `./gradlew lintDebug` — **kept at zero findings.** Do not add a suppression without a comment
  saying why it is allowed here.
- `./gradlew installDebug` — onto the phone. Enabling the accessibility service needs
  "Allow restricted settings" from the app's App info on Android 13+, because the build is sideloaded.
- The accessibility driving, the overlay and the cues cannot be unit-tested. Exercise them on the
  phone: start a session, and drive a full turn — twist, speak, twist, hear the answer read back.

Run no formatter of your own; the build's own tasks are the whole toolchain.

## Cutting a release

A tag does it: pushing `v<major>.<minor>.<patch>` runs `.github/workflows/release.yml`, which tests,
lints, assembles a signed release APK, verifies the signature and publishes a GitHub release with the
APK attached. The tag is the only trigger, and the version is read from it — nothing in the tree
carries the version of a release.

The signing key is **not** in the repository and must never be put there. It lives under
`~/.android-keystores/` on the author's machine, and CI restores it from repository secrets
(`ANDROID_KEYSTORE`, base64, plus its password, alias and key password). Losing the key means no
installed copy can ever be upgraded again, only uninstalled and replaced.

A signed APK builds locally the same way, passing `keystoreFile`, `keystorePassword`, `keyAlias`,
`keyPassword`, `versionName` and `versionCode` as `-P` properties. Without `keystoreFile` the release
APK comes out unsigned and installs nowhere; the workflow's verify step is what keeps such an APK out
of a release.
