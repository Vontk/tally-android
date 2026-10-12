# Tally 1.1.0 — implemented design update

Source of truth: user-supplied `tally-design-updated.zip`, preserved in `docs/design/`. Native Kotlin/Compose APK; offline Room/DataStore persistence; no WebView.

- Apply compact headers, labelled subject/settings controls, revised palette, circular counter, and phone/tablet navigation.
- Integrate subject editor, separate RGB sheet, selection sheet, real record details and confirmed deletion.
- Preserve intentional empty databases after final-subject deletion; retain existing schema, signing identity, subjects and tallies on update.
- Connect native date picker, chronological Days, exact-minute Hours, timestamp list and responsive Month/day-list alternative.
- Preserve history context on Back; show an exit-able preview banner; coalesce persisted-write feedback and undo the exact acknowledged record.
- Connect document-picker backup/restore with replacement scope, export-first action, cancellation and error states.
- Compile optimized APK and app bundle; sign locally; publish source, signed APK and checksums to the existing private repository.
