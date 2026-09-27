# Capture workflow (PR 2)

## Boundaries

CaptureViewModel owns decisions, the active job, and the managed-image lease.
CaptureFragment owns Android contracts, CameraX preview binding, rendering, dialogs,
Toasts, and Fragment transactions. CaptureWorkflowDependencies exposes application-scoped
services without Activity, Fragment, LifecycleOwner, or Android Location/Uri arguments.
The dependency-owner factory creates these services once per ViewModel; OCR closes in
onCleared. CapturePersistence is shared with text selection, which still owns its own
preparation, saving, and cleanup until PR 3.

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
