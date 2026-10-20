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

## Two triggers

A twist is one way to drive a step. A **Bluetooth headset's button** is the other, and the two are
interchangeable — each drives exactly the same step, decided by what is on the Claude app's screen.

The button takes a detour, because Android gives it no straight road. A Bluetooth button never
reaches the key-event pipeline at all, and the media buttons are delivered to whichever app is
playing — the Claude app, while it reads a response aloud. The one press that escapes both is
**redial**, which the headset sends as HFP's `AT+BLDN`: the Bluetooth stack turns it into an ordinary
outgoing call, and an outgoing call is something an app may answer for. So, while a session is
running, this app answers for that call, cancels it, and steps the loop instead. Outside a session
every call is placed as asked, and redial keeps working.

> **What this costs.** If the app is not there to answer — killed, role revoked, service unbound, or
> simply too slow — Telecom places the call, and the button rings the last number you dialled, from
> your pocket. Emergency numbers are never diverted, and a number the app cannot vet is not diverted
> either: every doubt resolves to placing the call.

To enable it, grant the **call redirection role** and **phone state access** in the app's Headset
button section. The twist needs neither. Without phone state access no dial attempt is ever
diverted, so the button does nothing — that is the fail-safe, not a bug.

The gesture is a brisk twist about the phone's long axis, out and back. It is recognised from the
gyroscope, and deliberately not from a shake: walking swings a pocketed phone about its *other* axes,
so a stride never trips it.

How much of a twist it takes is a setting, because only a walk settles it. **One twist** is the
default and the quickest; **one and a half** carries the return stroke on past where a pocket's own
roll would stop; **two twists** is the least mistakable and the slowest. If a pocket starts driving
the Claude app by itself, demand more.

## Setting it up

1. Install the APK from [Releases](https://github.com/pauliojanpera/claude-twist/releases), or build
   it yourself with `./gradlew installDebug`.

   A release APK and a locally built debug one are signed differently, so neither can replace the
   other in place: switching between them means uninstalling first, which also clears the app's
   settings.
2. Open the app and work down the readiness list:
   - **Accessibility service** — the app cannot read or press the Claude app without it.
   - **Display over other apps** — keeps the screen on behind the Claude app, and shields the screen
     from pocket touches.
   - **Battery optimisation exemption** — keeps a long session from being cut short.
3. Android 13 and later refuses the accessibility switch to an app that did not come from an app
   store, showing **"Restricted setting — For your security, this setting is currently unavailable"**
   in its place. The way through is **Settings → Apps → Claude Twist → ⋮ → Allow restricted
   settings**, after which the switch works. The ⋮ menu is at the top right, and the entry appears
   there only once the restriction has been met once. Installing over adb does not avoid this.

   (Confirmed on OxygenOS, Android 14.)

   Where that menu item is missing, declaring an installer on the way in is the other way out — the
   restriction keys off where the app came from:

   ```sh
   adb install -r -i com.android.vending claude-twist-v0.1.0.apk
   ```
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

A gyroscope, Android 10 or later, and the Claude app installed.

## Licence

MIT — see [LICENSE](LICENSE).
