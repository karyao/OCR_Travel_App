# Layout and spacing verification

Implemented September 30, 2026 across Home, Capture, Select Text, Map, Welcome, and Tutorial.

The activity container now applies system-bar and display-cutout padding from its original padding on every inset delivery. Headers grow with their content. Scrolling content is constrained between measured headers and action footers. Action rows stack when labels need more room.

Shared spacing resources define 16dp screen gutters and card padding, 12dp card separation, and 24dp section gaps. Home reserves the measured floating-button height and anchors snackbars above the buttons. Selection has one scrolling region. Capture uses a scrolling instruction card and 4:3 preview. Tutorial reserves the Previous slot. Map attribution wraps beside zoom controls. Saved cards hide the divider with absent location metadata.

Existing labels, emoji, text sizes, colors, view IDs, selection positions, camera actions, and navigation are preserved.

## Results

| Check | Result |
| --- | --- |
| Debug build | Passed |
| Existing unit tests | 90 passed |
| Home, Capture, Select Text behavior tests and new layout tests | 28 passed |
| Android lint | 0 errors; 123 warnings |
| Whitespace checks | Passed |

Device tests ran on the Android 16 / API 36 Medium Phone emulator. The behavior suite covers collection states and actions, capture interactions, selection restoration, saving through activity recreation, and navigation after saving.

Five new layout tests check repeated inset replacement, cutout clearance, header/footer boundaries, adaptive action labels, Home's final-card clearance, the final item among 40 selection options, metadata restoration when cards are recycled, and footer geometry for all four tutorial steps.

All six layouts were measured and rendered at 320×640dp, 360×640dp, 412×640dp, and 640×320dp, each at normal and 200% font scale: 48 renders. The fixtures include long Chinese names, translations, addresses, and many text options. Geometry assertions verify that the final Home card clears both floating controls and that selection options remain reachable above the footer.

## Before and after screenshots

These screenshots show the original build and implemented build on the same emulator.

| Screen | Before | After |
| --- | --- | --- |
| Welcome | [Original](before/welcome.png) | [Updated](after/welcome.png) |
| Tutorial | [Original](before/tutorial.png) | [Updated](after/tutorial.png) |
| Home | [Original](before/home.png) | [Updated](after/home.png) |
| Map | [Original](before/map.png) | [Updated](after/map.png) |
| Capture | [Original](before/capture.png) | [Updated](after/capture.png) |

Representative large-text renders:

- [Home: 320dp, final card above floating controls](after/rendered/home-320x640-font2.0.png)
- [Capture: 320dp, stacked actions](after/rendered/capture-320x640-font2.0.png)
- [Select Text: 320dp, final recognized options](after/rendered/selection-320x640-font2.0.png)
- [Tutorial: short landscape window](after/rendered/tutorial-640x320-font2.0.png)
- [Map: 320dp, wrapping attribution](after/rendered/map-320x640-font2.0.png)
- [Welcome: 320dp, large text](after/rendered/welcome-320x640-font2.0.png)

The full render set is in `after/rendered/`. These are Android View renders using the app theme. Camera surfaces and asynchronous image/map content are placeholders in this set; the live Capture screenshots include the actual emulator camera preview.

Additional live Capture checks enabled the simulated corner-cutout overlay and three-button navigation at 200% text:

- [320dp portrait with stacked controls](after/capture-320-font2-threebutton-cutout.png)
- [Short landscape window with horizontal controls](after/capture-landscape-font2-threebutton-cutout.png)

In these short content regions, instructions and the preview scroll while the header and controls remain outside the scrolling area. Original emulator size, font scale, cutout setting, and gesture navigation were restored after capture.

## Reproduce automated checks

```bash
./gradlew testDebugUnitTest lintDebug connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.karen_yao.chinesetravel.shared.ui.ScreenLayoutTest,com.karen_yao.chinesetravel.features.home.ui.HomeFragmentTest,com.karen_yao.chinesetravel.features.capture.ui.CaptureFragmentBehaviorTest,com.karen_yao.chinesetravel.features.textselection.ui.TextSelectionFragmentBehaviorTest
```

The instrumentation runner exports layout screenshots through `additionalTestOutputDir` when supplied by the Android Gradle plugin, with an app external-files fallback.

## Coverage limits

No physical devices or older Android releases were tested. Select Text has rendered fixtures and behavior coverage, without a live before screenshot. The width/font matrix checks the six layouts independently; repeated system-bar/cutout delivery is tested separately. Loading and error state containers share Home's measured content region, but the full width/font screenshot matrix uses populated collection fixtures.
