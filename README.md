<p align="center"><strong>📱 Screenshots</strong></p>
<p align="center">
  <img src="https://github.com/user-attachments/assets/94f53cd8-d569-4cec-b7a0-c38e97471f28" width="250" alt="OpenMessages mobile home screen"/>
  <img src="https://github.com/user-attachments/assets/65b431b9-7fef-42a4-b139-50ef798c25cf" width="250" alt="OpenMessages mobile home screen"/>
  <img src="https://github.com/user-attachments/assets/70d8da96-81df-4ea2-8b5c-dc7e3a40324b" width="250" alt="OpenMessages mobile home screen"/>
</p>

## ✨ What's different from upstream

Open Messages is a rebrand of QUIK (itself a fork of QKSMS), with the package `io.openmessages` and
a number of UX, theming and privacy improvements layered on top. The highlights:

### 🛡️ Privacy-first spam & phishing blocking
* The blocking settings are split into two clear sections: **Integrated** (Open Messages blocks
  on your own device, with no third-party app) and **External apps** (optionally delegate to
  Call Control, Should I Answer? or Call Blocker). The integrated sources are **additive**, so you
  can combine several at once instead of picking only one.
* **Allowlist**: approve a sender and the automatic sources will never flag or block them again.
* **"Suspected spam"**: borderline senders stay in your inbox but get a discreet label in the
  conversation list and a banner inside the conversation with one-tap **Approve** or **Block**. An
  option lets you send suspected spam straight to the blocked list instead of just labelling it.
* Opt-in integrated sources: **telemarketing numbers** (French ARCEP "démarchage" ranges, with
  matching logic ported from the open-source
  [Saracroche](https://codeberg.org/cbouvat/saracroche-android)) and **anti-phishing** (catches
  messages whose links point to known phishing sites).
* **Offline-first & transparent**: Open Messages makes no network connection on its own. When you
  turn a source on, it downloads that list once over the internet (from
  [Saracroche](https://codeberg.org/cbouvat/saracroche-android) or
  [The Block List Project](https://github.com/blocklistproject/Lists)); everything after that, all
  the matching, happens offline on your device.

### 🎨 Theming & appearance
* Reworked, tabbed theme picker with rounded corners and **live in-app theme updates** (no restart,
  no flash). Switching theme, dark mode or text size now crossfades instead of flashing.
* **Per-conversation colors** use the same modern picker, opening on the conversation's real color.
* **Pure-black (AMOLED) mode** that turns the dark backgrounds true black to save power on OLED
  screens.
* A dedicated **App icon** tab: color the launcher icon independently of the app theme, or have it
  follow the theme. The icon swaps instantly, and the splash and recent-apps colors stay in sync
  across launchers and OEMs.
* New Tabler-style message launcher icon.
* **Material 3 switches** throughout, themed dialog buttons and rounded corners on dialogs app-wide,
  a themed unread badge, and the brand gradient restored for the default violet.
* Icons added to the notification-settings and blocking rows, refreshed drawer icons, and system-bar
  icons kept legible on every theme color.

### 📋 Main screen
* Refreshed main screen: a gradient header, a pill-shaped unread badge, a redesigned drawer, a
  scroll-to-top button and a collapsible search.

### 💬 Messaging
* Saved message **Templates**, with optional titles, distinct cards, and insertion from inside a
  conversation.
* **Expandable message input** that grows for long texts, with a full-screen editor.
* A new **read/unread toggle** swipe action, with an envelope icon that opens or closes to match
  what the swipe will do. Default swipe actions are **swipe right = mark read** and **swipe left =
  archive**.
* Redesigned signature settings, and the target conversation flashes when you jump to the next
  unread.

### 🔔 Notifications
* Notifications show **"Messages"** as the app name in the notification panel.

### 🔒 Privacy
* The voice-input (speech-to-text) button is **off by default**.

---

## 🎯 OpenMessages will simply be a place where I can

* 🔧 implement changes upstream does not want
* 🐛 fix issues I personally encounter
* 📱 improve the Android user experience
* 🤖 experiment freely with modern development workflows
* 🌱 maintain a version aligned with my own vision of open source

---

## 🚀 Why this fork exists

OpenMessages started after I wanted to make a few small improvements to Open Messages while using it as my daily SMS app.

Like many developers today, I use AI tools as part of my workflow, *not to blindly generate code*, but to understand codebases faster, test ideas, and work more efficiently while still **reviewing everything myself**.

My first instinct was to contribute those changes upstream.

However, after submitting a small contribution, I quickly realized that some projects are still more focused on ***how*** code is written than on the **actual quality of the contribution itself**.

I personally disagree with that approach.

Good code should be judged on the final result, maintainability, and whether the contributor understands what they are shipping, not simply on whether modern development tools were involved during development.

So instead of stopping there, I decided to maintain my own fork.

---

## 💭 Philosophy

**Open source is freedom.**

Sometimes that means contributing upstream.
Sometimes that means building your own path.
