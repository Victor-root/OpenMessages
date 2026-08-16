# 📋 Changelog

## 🚀 v1.0.1 (2026-08-16)

A bug-fix release. Every issue below is marked with where it comes from. All but one are pre-existing bugs from the original QUIK/QKSMS codebase rather than regressions introduced during the Open Messages rebrand: they were already present in the code this project forked from, and only ever showed up on certain phones or with certain settings, which is why they went unnoticed until now.

### 🔄 Changed
- 📏 **The maximum MMS size now defaults to "Automatic"**, meaning whatever limit the carrier itself reports, instead of a fixed 300 KB guessed on its behalf. Where a carrier reports no limit, 300 KB is still what's used, so nothing is lost by asking first. Anyone who has already picked a size keeps it.

### 🐛 Fixed
- 📥 **(inherited from upstream) Conversations imported from another SMS app** no longer land in the inbox if they were archived there before switching to Open Messages.
- 📷 **(inherited from upstream) MMS (photo/video) sends failing instantly** on some phones, where the app mistook a missing SIM preference for subscription "0" and tried to send over a SIM that doesn't exist.
- 📇 **(inherited from upstream) Recipients possibly resolving to the wrong number** on phones whose system address book doesn't lay out its columns in the same order Android's reference implementation does.
- 📏 **(inherited from upstream) MMS sends failing when the "Automatic" size setting was used** and the carrier reported no known limit, which the app mistook for a limit of zero.
- 🗂️ **(inherited from upstream) The navigation drawer getting stuck reopening itself** right after closing it, during the first sync after installing.
- 💬 **(inherited from upstream) A conversation started with a photo staying empty** and missing from the conversation list until the app was left and reopened, because the message was filed under a different conversation from the one on screen.
- 🔁 **(inherited from upstream) Retrying a failed MMS** resending it untouched instead of rebuilding it, so a message that failed for its size failed again at the same size.
- 🐢 **(inherited from upstream) Several photos in one message taking a long time to appear**, each having been encoded at full size only to be discarded, then shrunk by fixed steps that overshot.
- 🖼️ **(inherited from upstream) Photos in MMS coming out far smaller than they needed to be.** The app asked for a sensible image quality and the request was ignored, so every photo was saved at maximum quality and had to give up most of its pixels to fit the size limit.
- 👤 **(inherited from upstream) Conversation shortcuts and share targets showing a coloured initial** in place of the contact's photo.
- 🔤 **(introduced in v1.0.0, not inherited) A message holding several photos reading "Picture" untranslated** in the conversation list. Search results, the home-screen widget and notifications never translated it at all.

## 🚀 v1.0.0 (2026-08-13)

First public release of Open Messages, a rebrand and continued development of QUIK (itself a fork of QKSMS). Everything below is relative to the original QUIK/QKSMS this project started from.

### ➕ Added
- 💾 **Multi-category backup and restore:** back up SMS, MMS with attachments, app settings, blocking rules, conversation states (archived, pinned, blocked, custom name, flagged) and scheduled messages. Saved to Documents/OpenMessages by default, a custom folder, or a single .zip, with configurable automatic backups and an in-app manager to rename or delete existing backups.
- 📅 **Scheduled messages shown in the conversation:** a message you've scheduled now appears at the bottom of its conversation, faded and captioned with its send time, instead of only being reachable from a separate screen.
- 🛡️ **Integrated anti-spam blocking:** a phishing-link blocklist, an allowlist, and content filters, built into the app rather than left to a third party.
- 📝 **Saved message templates:** write a message once, reuse it from any conversation.
- 🎨 **Themed launcher icon:** pick the app icon's colour from an in-app picker instead of a single fixed icon.
- 🔊 **Optional sound on send:** off by default, with a choice of tones and a volume slider, independent from the notification sound for incoming messages.
- ⚡ **Configurable quick-action button** in the conversation header.
- 🖼️ **Setting to show or hide the contact photo** in conversations.
- 📋 **"Copy code" action on verification-code notifications**, and more reliable code detection (letter-prefixed codes, multi-group codes, and codes preceded by a shorter number).
- 📱 **Edge-to-edge display setting.**
- 🌐 **A landing page for the project**, under `website/`.

### 🔄 Changed
- 🏷️ **Rebranded from QUIK/QKSMS to Open Messages**, name, icons, package and branding, with no functional link to the app it was forked from.
- 🖌️ **UI, theme system and dependency injection overhaul**, including a modernized send button, mic button, and a Material bottom-sheet attachment menu in place of the old speed-dial.
- 🗂️ **Backup manager UI redesigned** and moved into the settings list.

### 🐛 Fixed
- 🐛 **A number of bugs inherited from upstream QUIK/QKSMS.**
