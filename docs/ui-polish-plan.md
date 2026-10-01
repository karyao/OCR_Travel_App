# Chinese Travel UI refinement plan

Prepared September 30, 2026.

The design direction is a calm travel journal: warm cream surfaces, restrained coral accents, readable Chinese text, and clear actions. Keep the existing identity while making alignment, spacing, and screen boundaries predictable.

This is a review of the current layout resources, theme, and screen code. Layout risks below are supported by the source; their exact appearance on individual devices still needs visual verification. A live device preview was unavailable during this review. This document proposes changes; it does not change the app UI.

## 1. Fix the layout foundations first — P0

### Replace guessed header and footer offsets

The shared header wraps its content and adds 16–20dp top margins to individual children. Home starts its list at 100dp, while Capture and Select Text start content at 80dp. These independent measurements can diverge when a title wraps or text size increases. In the layered layouts, later content can also draw over the header.

Use one shared screen structure:

1. A top app bar with its content below the status bar and cutout.
2. A content region positioned below the measured app bar.
3. Where needed, a bottom action region positioned above the navigation area.

Constrain content to those regions instead of reserving arbitrary 80, 96, 100, or 120dp spaces. Use a 56dp minimum app-bar content height at normal text size, with room to grow. Remove the individual top nudges. Use a 24dp back icon within a 48dp touch target; align title and actions vertically. Move the collection count beneath the title if the header becomes crowded.

The app targets SDK 36, and no explicit window-inset handling was found in the reviewed app code. Android displays apps targeting SDK 35 or later edge-to-edge on Android 15 or later, so headers and controls need actual system-bar and cutout measurements. Give each edge one owner for inset handling to avoid doubled padding. [Android edge-to-edge guidance](https://developer.android.com/develop/ui/views/layout/edge-to-edge).

**Acceptance:** Content never covers the app bar; back, capture, save, and map controls remain usable with gesture navigation, three-button navigation, cutouts, and enlarged text.

### Make the end of every screen intentional

For screens with a fixed footer, let the footer grow with its labels and constrain the scrolling area above it. Put 16dp of breathing room after the last content item. Apply the navigation inset to the footer once.

For Home, keep the list viewport below the app bar and reserve scrolling clearance for the actual floating capture control plus 16dp. Avoid shortening the entire list by a guessed bottom margin. The final card and its actions must scroll completely above that control.

For Welcome, use an ordinary scrolling page with a consistent 24dp ending plus the bottom safe area. For Tutorial, keep navigation in a measured footer. For Map, the map may extend behind system UI, but controls and attribution must remain in the safe area.

**Acceptance:** The last item and every action are fully reachable; there is no unexplained blank strip, clipped button, or abrupt content ending.

## 2. Establish a small shared component system — P1

These values are proposed design defaults, not universal platform requirements. Components must adapt to content and accessibility settings.

| Element | Proposed rule |
|---|---|
| Spacing scale | 4, 8, 12, 16, 24, 32dp; named resources instead of one-off values |
| Screen gutter | 16dp on each side, owned by the screen container |
| Card padding | 16dp; no additional horizontal item margin inside a padded list |
| Text grouping | 4–8dp between related text; 16dp between groups; 24dp between sections |
| List separation | 12dp between cards, applied once |
| Shapes | 16dp card radius; 12dp button radius |
| Depth | Quiet cards with 0–2dp elevation; stronger separation only for overlapping controls |
| Page title | 20sp, medium/bold; brief enough to fit normal phone widths |
| Chinese name | 20–24sp, medium/bold; multiline when needed |
| Body and translation | 16sp, regular; use scalable line height |
| Pinyin and supporting text | 14sp, regular; consistent system font |
| Primary action | Filled brand color, 16sp label, 56dp minimum height; grow for larger text |
| Secondary action | Outlined or text style with a readable dark/brand label |
| Icons | One vector family, normally 24dp, with consistent weight |
| Interactive targets | At least 48 × 48dp |

Home currently combines a 15dp list margin, 16dp list padding, and 8dp item margin: card edges sit approximately 39dp from the screen edge before internal padding. Select Text adds 16dp screen padding, 20dp card padding, 8dp option margins, and 16dp option padding. Flatten these layers so text has room and card edges align across screens.

Standardize on Material button variants. The custom outlined style inherits from a filled button, while several views explicitly request white labels over pale backgrounds. Theme tinting can affect the rendered result, so inspect actual enabled, pressed, disabled, and loading states after consolidating styles.

Keep coral as the brand accent; use the existing darker coral where small white labels need stronger contrast. White on the current primary coral calculates to about 4.32:1; white on the darker coral is about 5.62:1. Make primary and secondary text roles distinct while retaining readable contrast. The current primary and secondary text tokens have identical values.

Android recommends 48dp touch targets and contrast thresholds based on text size and weight. [Android accessibility guidance](https://developer.android.com/guide/topics/ui/accessibility/apps).

## 3. Remove functional emoji and simplify copy — P1

Remove emoji from app bars, button labels, dialogs, location metadata, and selection instructions. Use vector icons only when they add recognition or communicate an action. Decorative illustration can remain in onboarding or empty states, but should use one coherent style.

| Current UI | Proposed UI |
|---|---|
| Capture Chinese Text with camera emoji | Capture |
| Select Text with note emoji | Select text |
| Travel Map with map emoji | Travel map |
| How to Use Chinese Travel with books emoji | How it works |
| Gallery with folder emoji | Gallery; optional image icon |
| Camera-switch emoji as button text | Camera-switch vector icon; accessible label “Switch camera” |
| Capture with camera emoji | Centered shutter control; accessible label “Take photo” |
| Confirm Selection | Save place |
| Get Started! with rocket emoji | Get started |
| No Text Detected with magnifier emoji | No text found |
| Saved. Total rows: … | Place saved |
| Location pin emoji | Small vector pin alongside the address |

“Save place” makes the selection action's result explicit: this step currently persists the chosen text and photo. Keep “Saving…” as the progress label.

Shorten the capture instruction to “Keep the Chinese text clear and well lit.” Remove the 10sp gesture tip once the camera-switch control is clearly discoverable. For a no-text result, use “Move closer and keep the text in focus,” with “Try again” and “Choose photo” actions; the regular back navigation already provides an exit.

Search both resources and screen code during implementation: several headers, dialog labels, and map snippets are assigned in Kotlin. Move user-facing copy into string resources. Log decoration is outside this UI cleanup.

## 4. Apply the system to each screen — P1/P2

### Home and saved-place cards

- Shorten the header to “Your places,” with the saved count as supporting information.
- Keep one prominent Capture action; move Travel map into the app bar to remove the stacked floating buttons.
- Simplify cards to Chinese name → pinyin → translation → optional location. Remove the nested gold header panel and italic translation; use typography to establish hierarchy.
- Use a labelled overflow action for Delete instead of a prominent cross beside every name. Preserve the existing confirmation before deletion.
- Make “Open in Google Maps” a secondary action. Allow long names, translations, and addresses to wrap.
- Hide the location divider and its surrounding spacing when there is no location or map action. The current adapter hides those views but leaves the unconditional divider.
- Use one simple empty-state illustration and “Save your first place,” with an obvious capture action. Give load failures a “Try again” action if reload support is added.

### Capture

- Give the camera preview the remaining space between app bar and measured controls.
- Replace the large instruction card and strong shadow with a compact readable hint, clear of the main framing area.
- Use a centered shutter, Gallery at one side, and Switch camera at the other. Avoid squeezing two emoji-labelled buttons and a switch button into the same narrow row.
- Keep the hint and all controls outside system overlays. In short landscape windows, use an alternate control arrangement and reduce optional instructional content.
- Show recognition progress and failures in a stable area; preserve the existing rules that prevent repeated capture while work is underway.

### Select text

- Replace the outer ScrollView plus fixed 400dp nested list with a single scrolling list containing instruction, image preview, and selectable rows.
- Remove the enclosing instruction card. Use “Choose the text to save” and a left-aligned supporting sentence that works for any place, not only restaurants.
- Fit the full image within an aspect-aware preview so the sign can be compared with recognized text; the current center crop may omit useful context.
- Reserve a consistent trailing slot for a radio/check indicator. The current “Selected” label appears only after selection and takes width away from the text, potentially changing wrapping.
- Keep Cancel and Save place in a measured footer. Stack actions when enlarged labels no longer fit side by side. Use a border and check indicator for selection, with an accessible selected state.
- Keep save-disabled, saving, and uncertain-save states consistent with the existing workflow. A visual retry must not allow accidental duplicate saves.

### Welcome and tutorial

- Reduce Welcome's oversized top gap and repeated 48dp section gaps. Use a compact logo, one headline, one supporting sentence, and a clear Get started action.
- Keep Learn more visually secondary. Replace the three feature emoji with consistent small icons, or omit the icons if the copy is sufficient.
- Show one tutorial step at a time with a visible “1 of 4” indicator; remove the repeated feature-card stack from every step.
- Align tutorial content to the shared 16dp gutter instead of 10dp outer margins. Put Previous and Next/Get started in the same footer positions across steps, reserving the Previous slot when unavailable.
- Rewrite tutorial claims to reflect optional location and possible first-use translation setup. Avoid implying that every photo receives an instant translation or current location.
- On step changes, reset the content scroll to the top and announce the new step for accessibility.

### Map

- Retain the current structural placement below the header; bring its header into the shared system.
- Position zoom controls and attribution using actual safe-area insets. Allow attribution to wrap and rearrange controls on narrow screens so they cannot collide.
- Replace the empty-state emoji with a quiet illustration; explain that places appear when location was available when saving.
- Keep attribution visible and tappable whenever the map is displayed, including an empty collection. Simplify popup typography and remove location emoji from snippets.

## 5. Implementation order and completion checks

| Pass | Deliverable | Completion condition |
|---|---|---|
| 1 — Layout stability | Shared app bar, inset handling, measured footers; Home/Capture/Select Text positioning; one Select Text scroll region | No header, footer, system-bar, or scroll-boundary collisions |
| 2 — Components and language | Spacing resources, button/text/card styles, vector icons, copy updates | Shared gutters and action hierarchy; no functional emoji; readable states |
| 3 — Screen refinement | Simpler place cards, compact capture hint, lighter onboarding/tutorial, map control positioning | Each screen has clear hierarchy and an intentional ending |
| 4 — Visual verification | Before/after screenshots and issue checklist for representative states | No clipping, unreachable controls, orphan dividers, or inconsistent state layouts |

Start verification at 320dp, 360dp, and 412dp phone widths in portrait, plus a short landscape window. Check Android 10, 14, 15, and 16 where available, gesture and three-button navigation, and a display cutout. Test default, intermediate, and 200% font sizes; Android supports nonlinear scaling up to 200%. [Android font-scaling guidance](https://developer.android.com/about/versions/14/features#font-scaling).

Include an empty collection; loading and failure states; long Chinese names, pinyin and translations; missing addresses; long addresses; one and many OCR options; no text found; camera permission denied; location unavailable; saving and save failure; each tutorial step; and map popups. Verify both light and dark system settings because the current theme uses DayNight with fixed light surfaces.

For every screenshot, check: aligned outer edges, readable labels, visible final content, safe bottom controls, and no spacing left by hidden content. Use TalkBack to verify traversal, selection state, and icon labels. During implementation, run the existing lint and relevant screen behavior tests, then add targeted layout checks for any reproduced overlap or reachability defect. A build alone does not verify spacing.

## Implementation map

- **Screen boundaries:** [MainActivity.kt](/Users/karenyao/AndroidStudioProjects/ChineseTravel/app/src/main/java/com/karen_yao/chinesetravel/MainActivity.kt), [header_common.xml](/Users/karenyao/AndroidStudioProjects/ChineseTravel/app/src/main/res/layout/header_common.xml), and the screen layouts below.
- **Collection:** [fragment_home.xml](/Users/karenyao/AndroidStudioProjects/ChineseTravel/app/src/main/res/layout/fragment_home.xml), [item_snap.xml](/Users/karenyao/AndroidStudioProjects/ChineseTravel/app/src/main/res/layout/item_snap.xml), [SnapsAdapter.kt](/Users/karenyao/AndroidStudioProjects/ChineseTravel/app/src/main/java/com/karen_yao/chinesetravel/features/home/ui/SnapsAdapter.kt).
- **Capture:** [fragment_capture.xml](/Users/karenyao/AndroidStudioProjects/ChineseTravel/app/src/main/res/layout/fragment_capture.xml), [CaptureFragment.kt](/Users/karenyao/AndroidStudioProjects/ChineseTravel/app/src/main/java/com/karen_yao/chinesetravel/features/capture/ui/CaptureFragment.kt).
- **Selection:** [fragment_text_selection.xml](/Users/karenyao/AndroidStudioProjects/ChineseTravel/app/src/main/res/layout/fragment_text_selection.xml), [item_text_option.xml](/Users/karenyao/AndroidStudioProjects/ChineseTravel/app/src/main/res/layout/item_text_option.xml), [TextOptionAdapter.kt](/Users/karenyao/AndroidStudioProjects/ChineseTravel/app/src/main/java/com/karen_yao/chinesetravel/features/textselection/ui/TextOptionAdapter.kt), [TextSelectionFragment.kt](/Users/karenyao/AndroidStudioProjects/ChineseTravel/app/src/main/java/com/karen_yao/chinesetravel/features/textselection/ui/TextSelectionFragment.kt).
- **Onboarding:** [fragment_welcome.xml](/Users/karenyao/AndroidStudioProjects/ChineseTravel/app/src/main/res/layout/fragment_welcome.xml), [fragment_tutorial.xml](/Users/karenyao/AndroidStudioProjects/ChineseTravel/app/src/main/res/layout/fragment_tutorial.xml), [TutorialFragment.kt](/Users/karenyao/AndroidStudioProjects/ChineseTravel/app/src/main/java/com/karen_yao/chinesetravel/features/tutorial/ui/TutorialFragment.kt).
- **Map:** [fragment_map.xml](/Users/karenyao/AndroidStudioProjects/ChineseTravel/app/src/main/res/layout/fragment_map.xml), [MapFragment.kt](/Users/karenyao/AndroidStudioProjects/ChineseTravel/app/src/main/java/com/karen_yao/chinesetravel/features/map/ui/MapFragment.kt).
- **Shared visual rules:** [themes.xml](/Users/karenyao/AndroidStudioProjects/ChineseTravel/app/src/main/res/values/themes.xml), [night theme](/Users/karenyao/AndroidStudioProjects/ChineseTravel/app/src/main/res/values-night/themes.xml), [colors.xml](/Users/karenyao/AndroidStudioProjects/ChineseTravel/app/src/main/res/values/colors.xml), [strings.xml](/Users/karenyao/AndroidStudioProjects/ChineseTravel/app/src/main/res/values/strings.xml); introduce shared dimension and text-style resources.
