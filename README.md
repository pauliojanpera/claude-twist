# claude-twist

Talk to Claude on a walk, with the phone in your pocket.

An Android app that presses the Claude app's own buttons for you when you twist the phone. One twist
starts the Claude app's dictation; the next sends what you said; when the answer is ready it is read
aloud. You never take the phone out, and never look at the screen.

It drives the official Claude app rather than talking to an API, so whatever that conversation can
reach — connectors, a repository, your own tools — it still reaches. Dictation sends an ordinary text
message, so this is not voice mode and is not limited to what voice mode can do.

The app talks to nothing: no network permission, no account, no server. It reads the screen through
Android's accessibility service and presses buttons.

## How a turn goes

| | |
| --- | --- |
| **twist** | the Claude app starts listening — one short beep |
| *speak* | for as long as you like; pauses do not end it |
| **twist** | what you said is sent — two beeps |
| *wait* | the answer is written; the app waits for it to finish |
| | the answer is read aloud — a rising beep |
| **twist** | narration stops and the next turn starts listening |

A low alert tone means the twist did nothing — usually the Claude app is not the app in front.

The gesture is a twist about the phone's long axis, out and back twice, inside a second and a half.
It is recognised from the gyroscope, and deliberately not from a shake: walking swings a pocketed
phone about its *other* axes, so a stride never trips it.

## Setting it up

1. Build and install: `./gradlew installDebug`, or take the APK from a CI run's artifacts.
2. Open the app and work down the readiness list:
   - **Accessibility service** — the app cannot read or press the Claude app without it.
   - **Display over other apps** — keeps the screen on behind the Claude app, and shields the screen
     from pocket touches.
   - **Battery optimisation exemption** — keeps a long session from being cut short.
3. On Android 13 and later a sideloaded build is held behind *restricted settings*: if the
   accessibility toggle will not stay on, open the app's **App info**, and from the overflow menu
   choose **Allow restricted settings**.
4. Open the Claude app on the conversation you want to talk to, press **Start session**, pocket the
   phone.

The session stops from its notification, or by itself after four hours. With the touch shield on, the
screen is dimmed and takes no touches — the notification shade still works, which is how you stop it.

## When a Claude app update breaks it

The app finds the buttons by the content descriptions the Claude app publishes for accessibility
services. Those are not an API, and an update may rename one. Nothing needs rebuilding: put the
Claude app into the state that shows the control, open **Controls** in this app, press **Read the
screen in front**, and correct the field. The defaults shipped here were read off
com.anthropic.claude 1.261016.51.

## Requirements

A gyroscope, Android 8.0 or later, and the Claude app installed.

## Licence

MIT — see [LICENSE](LICENSE).
