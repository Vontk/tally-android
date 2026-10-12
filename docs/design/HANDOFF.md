# Tally — final phone design handoff

Based on the supplied native Android 1.0.1 bundle. The final design keeps the core experience: a circular tally button, two main destinations, separate colored subjects, and day/hour/month history. This is a design/source handoff, not an APK release. The hosted website is unchanged.

## Open the design

Open `design/review.html` for the offline phone-shaped viewer. It contains 20 selectable mockup states. They illustrate interaction outcomes; controls inside the phone are not live.

- `core-screens.png` / `.svg`: Counter, Days, Hours, Month.
- `supporting-screens.png` / `.svg`: Subject switcher, editor, Settings, light Counter.
- `deletion-states.png` / `.svg`: Subject deletion, moment detail, individual deletion, no subjects.
- `usability-states.png` / `.svg`: Timestamp list, date picker, feedback, restore confirmation.
- `completion-states.png` / `.svg`: Example mode, New subject, Custom RGB, Backup error.
- `UX_RESEARCH.md`: primary-source research, audit findings, design decisions and user-test plan.
- `reference/`: original uploaded source, screenshots and documentation.

Mockup records: today 7, seven-day total 38, streak 5, month total 63, all-time 148. They are example content and never inserted into the native database.

## Final screen hierarchy

One compact header shows brand and **Settings**. Appearance is inside Settings rather than an unexplained icon. Beneath it, a 52 dp subject selector and labelled **Edit** action remain visible. The bottom tabs always read **Counter** and **History**.

Counter prioritizes today's count, the circular button and **Tap to add** instruction. **Undo last tally**, seven-day/all-time/streak statistics and recent timestamps follow. Only the circle adds records. The count, optional haptic response and recent time acknowledge the action; brief status appears above navigation when useful.

History keeps **Days / Hours / Month** visible. Date controls are individually sized: previous, **Choose date**, next, **Today**. Days put earlier dates above today as originally requested. A visible hint and Today shortcut explain that direction. Dates retain weekday, month/year, day and count. Each row/cell opens the selected day's timeline.

Hours retain all 24 hours and flat exact-accent timestamp dots at their recorded minutes. **View list** exposes separate, labelled timestamp rows when the timeline is dense or difficult to use. Tap a dot or row for subject/date/time and **Delete tally**.

Month retains rounded, intensity-colored cells with explicit numeric counts. On the illustrated 390 dp layout, cells are 48 dp wide. Responsive calendar gutters/gaps shrink before targets do. For usable widths below 336 dp or large text, the full implementation must provide an accessible day-list alternative instead of overlapping touch targets. Numeric counts and a Today marker convey meaning beyond color.

## Feature and state checklist

| Feature | Entry and behavior |
| --- | --- |
| Add tally | Circle; one accepted tap, one timestamp; visual/haptic feedback |
| Undo latest | Below circle; latest inserted real record for selected subject; disabled when empty |
| Statistics/recent records | Below primary action; recent rows open detail; View all opens History |
| Days | History tab; earlier days above Today; row opens day |
| Hours | 24-hour timeline; initial first-record vicinity; exact timestamp list alternative |
| Month | Rounded heatmap; numeric counts; cell opens day; relative intensity legend |
| Date jump | Choose date opens native picker; arrows and Today remain |
| Create subject | Subject selector → New subject → name/color → Create |
| Switch subject | Selector sheet with names, accents, totals and check for selection |
| Edit name/color | Labelled Edit → validated name, presets, custom hex/RGB → Save |
| Delete subject | Edit → separated Delete subject → named/count confirmation |
| Delete individual tally | Moment detail → Delete tally → exact-record confirmation |
| Last-subject deletion | No-subject state offers Create and backup restore |
| Dark/light | Settings → Appearance; explicit Dark and Light selection |
| Example data | Settings → Try example data; persistent badge + Exit preview; isolated records |
| Export | Settings → Export backup; native file picker, busy/success/error/cancel states |
| Restore | Settings → Restore backup; validate first, show replacement scope, export-first option, confirm/cancel |
| Empty counter/day/month | Zero counts, useful instructions, disabled undo; calendar/time context remains |
| Errors | Inline field validation; storage failure/retry; invalid backup leaves data unchanged |
| Exit/cancel | Done/Cancel controls and Android Back; no swipe-only exits |

Delete subject means deleting that subject and its associated records. Other subjects remain. After deleting a subject, select the next existing one; after deleting the last, show the no-subject state. Never silently recreate a subject. Individual deletion affects only one record and recomputes all derived counts. Cancel never changes data.

## Icon, typography and accessibility rules

Minimal vector icons: 24-unit grid, 1.7-unit rounded strokes, 20–24 dp rendered size inside at least 48 dp touch regions. Settings, Edit, navigation and destructive actions have visible labels. No emoji/font-symbol UI icons, hover-only hints, long-press-only actions or color-only states.

20–24 dp normal gutters; calendar may use narrower gutters for seven columns; 12–14 dp card radii; 24 sp titles; 14–15 sp primary labels; 12–13 sp supporting labels. Heights grow with text. Normal text targets 4.5:1 contrast; large text/meaningful non-text indicators target 3:1. Exact custom accent fills retain their chosen color, with contrasting text or outlines. Heatmap labels choose black or white for each fill.

Dark: background #171719, panel #202023, main #ECECEF, secondary #A2A2AA, dividers #343438. Light: #F8F7FA, #FFFFFF, #27252D, #706B79, #DDDCE3. Default accent #A69AEE; light action labels use a darker contrast-safe violet, not the raw lavender. Custom colors are evaluated at runtime.

## Source integration status

`source/TallyUi.kt` updates the provided Compose layer with compact headers, labelled Settings/Edit, subject sheet with Done, compact titles, timestamp list fallback, contextual totals, exact-minute positioning, theme/settings access, original backup machinery and original color validation. `source/TallyUi.patch` shows changes against the original. `Theme.kt` and `Moments.kt` are retained. `DeletionUi.kt` supplies confirmation components with caller-provided callbacks.

**Pending in the complete Android project:** wire subject/record deletion transactions and final-subject behavior; connect the labelled date picker; restore originating history context on Back; make recent rows open detail; coalesce persisted-write feedback; implement preview exit/banner in every final state; provide calendar list fallback at constrained sizes; connect export-first restore action. These require absent ViewModel/repository/state/navigation files. The editor mockup includes Delete subject; that action is not connected in the supplied native editor. Do not confuse illustrated completeness with compiled implementation.

No Android build, emulator or user-study result is claimed. Lightweight source delimiter/feature checks, vector rendering/inspection, archive validation, and default-palette contrast calculations were performed. Before shipping, integrate with the full project and run its build, lint, unit/device tests, plus the usability tasks in `UX_RESEARCH.md`. Test at 320/360/390/412 dp, 200% text, both themes, custom black/white accents, dense timestamp hours, rotation/recreation, keyboard-open editor, midnight/timezones, deletion/undo and all backup outcomes.
