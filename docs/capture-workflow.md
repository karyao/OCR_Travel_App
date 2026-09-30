# Capture and text-selection workflows

## Boundaries

CaptureViewModel owns decisions, the active job, and the managed-image lease.
CaptureFragment owns Android contracts, CameraX preview binding, rendering, dialogs,
Toasts, and Fragment transactions. CaptureWorkflowDependencies exposes application-scoped
services without Activity, Fragment, LifecycleOwner, or Android Location/Uri arguments.
The dependency-owner factory creates these services once per ViewModel; OCR closes in
onCleared. CapturedPlaceSaver shares preparation and persistence with text selection.
TextSelectionViewModel owns selection, saving, and cleanup after capture hands off the image.

## Event and effect rules

- All events enter on the main thread; camera results use the main executor. Reduction
  and effect acknowledgement are synchronous. Do not launch acknowledgement into another
  coroutine: a resumed collector must remove a command before executing the next one.
- CaptureUiState is the only source of control availability. Idle and Error accept new
  operations. An initial tap before camera readiness only displays the existing message.
- Operation IDs correlate permission, picker, capture, and dialog results. View generations
  reject callbacks from replaced views. Preview binding has a separate generation because
  a pause can invalidate a binding without destroying the view.
- Effects are ordered and acknowledged only at the head of the queue. The Fragment consumes
  them while resumed and defers navigation when FragmentManager has saved its state.
  Destruction invalidates the queue. Re-entering Capture never replays selection navigation.

## Lifetime and image ownership

Permission and picker requests survive their temporary pauses. Location lookup,
camera-readiness waits, and camera capture cancel on ordinary pauses. Import, recognition,
and saving continue while the view exists; their UI commands wait for resume.
View destruction, including rotation, cancels all owned work and returns to Idle.
There is intentionally no process-death operation recovery.

A managed image remains leased through recognition and the no-text dialog. Cancellation
or failure releases it before saving. Once persistence starts, retain it even on failure
or cancellation: the database may already have committed. External images are never deleted.
CameraX may finish an abandoned write after cancellation, so its late completion releases
that destination again without touching a newer operation.

Accepted text-selection navigation transfers ownership before the capture view is destroyed.
Rejected navigation or destruction before acceptance releases the image. Dismissing the
no-text dialog releases its image, just like its explicit choices.

## Verification

Run from the repository root with an emulator connected:

```sh
./gradlew testDebugUnitTest connectedDebugAndroidTest lintDebug
git diff --check
```

CaptureViewModelTest exercises transitions, stale results, ordered acknowledgement,
cancellation, and ownership using a controlled coroutine scheduler. CaptureFragmentBehaviorTest
uses the debug host, fake services, latches, and real Android permission/picker contracts.
It uses isolated temporary directories and never runs real camera, GPS, OCR, or translation.
Existing OCR and EXIF instrumentation tests remain separate.

Production smoke checklist: deny location on a real CameraX capture; import a Chinese image
through the gallery, choose an OCR line, save, force-stop/relaunch, and confirm the saved text
and original image persist. Translation may use its existing fallback when a model is unavailable.

## Text selection

TextSelectionFragment renders StateFlow state and owns only preview display, Android back
callbacks, Toasts, and Fragment transactions. The Fragment-scoped TextSelectionViewModel
uses a SavedState-aware factory and saves the selected index. Its one save attempt runs in
viewModelScope and survives rotation and temporary backgrounding. Selection, Confirm, and
all three exit paths are locked during saving. Repeated confirmation and cancellation taps
cannot start another save or exit.

Only deleteImageIfUnsaved=true creates a disposable image lease. A managed path alone is
not ownership. Cancelling or clearing the ViewModel before saving deletes an owned managed
image; neither rotation nor cancellation deletes unowned/external images. Once the saver
is invoked, retain the image even on exceptions or cancellation because a commit may have
occurred. Failures allow leaving but disable another save attempt from this screen.

SavedStateHandle is a checkpoint, not a transaction log. A restored recorded in-progress
save becomes OutcomeUnknown and is never restarted automatically; a restored recorded
success returns Home without another save. Checkpoint timing cannot guarantee duplicate
prevention after every process-kill/commit race. Durable idempotency is outside this change.

Navigation commands wait for resume and an unsaved FragmentManager state and are
acknowledged synchronously after acceptance. Home names its capture back-stack entry;
success pops capture and selection together to the existing Home. Standalone or older
unnamed stacks are cleared before opening Home. Header Back, Cancel, and system Back all
request one guarded exit; the callback disables itself before delegating to avoid recursion.

TextSelectionViewModelTest covers selection, restored checkpoints, duplicate taps, save
outcomes, acknowledgement, and owned/unowned/external image cleanup. The device-side
TextSelectionFragmentBehaviorTest uses a compatible debug host with a fake saver and tests
rotation during selection and saving, deferred navigation, exit paths, and back-stack cleanup.
No camera, GPS, OCR, translation, or model downloads are needed for these tests.
