# 📋 Changelog

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
