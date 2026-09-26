# Chinese Travel

I started this Android app after a trip to China. I had taken photos of restaurant signs and places I visited, but later I often couldn't remember what they were called or where the photos were taken.

The app lets you photograph Chinese text, recognize it with ML Kit, translate it, and save it together with the photo and location.

Still a work in progress! wanting to adjust the text recognition since it works for reasonably well and clear close-up signs, but busy backgrounds can still make mistakes.

## What it does

- Take a photo or choose one from the gallery.
- Recognize Chinese text with ML Kit.
- Select the relevant line when an image contains multiple results.
- Show the pinyin and English translation.
- Save the original photo and optional location information.
- View saved places on a map.

## How it works

The camera is built with CameraX, and Chinese text recognition uses ML Kit's on-device Chinese model. When an image contains multiple lines, the app lets you choose which result you want to save.

Saved photos, recognized text, translations, and locations are stored locally with Room. Pinyin is generated on the device, and ML Kit handles Chinese-to-English translation. The translation model may need to be downloaded the first time it is used.

Location is optional. When permission and a recent location are available, the app stores the coordinates with the photo and shows saved locations on an OpenStreetMap map.

## Running the project

1. Clone the repository.
2. Open it in Android Studio.
3. Allow Gradle to sync the project and download its dependencies.
4. Run the `app` configuration on an emulator or Android device.

The app supports Android 10 and newer. Camera permission is needed to take photos, while location permission is optional.

## Current limitations

- OCR results depend heavily on focus, lighting, text size, and the amount of background in the image.
- Busy scenes and distant signs can produce incorrect or duplicated characters.
- The current image preprocessing does not consistently improve recognition accuracy.
- Translation might not be available until its on-device model finishes downloading.
- Reverse geocoding depends on the services available on the device.

## Testing

The project contains unit tests for database, ViewModel, image-sampling, and OCR decision logic. It also contains Android instrumentation tests for the database, UI, EXIF metadata, and image preprocessing.

There is a device-side OCR comparison test that measures the original and preprocessed versions of sample images using confidence, character error rate, and processing time. This is useful for checking whether an image-processing change actually helps instead of relying only on ML Kit confidence.

To run the local unit tests and lint checks:

```bash
./gradlew testDebugUnitTest lintDebug
```

To run the Android tests with an emulator or device connected:

```bash
./gradlew connectedDebugAndroidTest
```

## Things I want to improve

- Add a crop or scan region so the target text fills more of the image.
- Improve camera focus and low-light controls.
- Build a larger labeled image set for measuring OCR accuracy.
- Keep experimenting with resizing and image adjustments, but only use them when testing shows an improvement.
- Continue cleaning up the capture flow and UI.

## Demo

[View the demo slides](https://www.canva.com/design/DAG2qomCLng/9fsaEYu4gz9h0B8irsffUw/view?utm_content=DAG2qomCLng&utm_campaign=designshare&utm_medium=link2&utm_source=uniquelinks&utlId=hda107d5dc7)
