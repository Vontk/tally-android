# Implementation decisions

Tally is a native Android APK using Kotlin and Compose, matching the counter, subject editor, themes, preview, and history of https://tally.aptmoss8.chatgpt.site.

The implementation follows the approved plan: single activity, ViewModel state, serialized mutations, Room records, DataStore preferences, native document-picker backup/restore, phone/tablet layouts, and release shrinking. Start fresh with an empty Little moments subject; no website migration or server is included.

Source and APK releases are published privately to Vontk/tally-android. See README.md for builds, signing, and storage behavior; VERIFICATION.md records measured release results.
