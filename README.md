# iNaturalistAndroid

iNaturalistAndroid is an Android app for [iNaturalist.org](http://www.inaturalist.org). If you'd like to contribute code, please check out [Contributing Code to iNaturalist](https://github.com/inaturalist/inaturalist/blob/master/CONTRIBUTING.md) for general guidelines. If you'd like to contribute translations, please provide them through [our Crowdin project](https://crowdin.com/project/inaturalistios) (look for the `strings.xml` file to work on the Android app).

## Setup

1. Make sure you have the latest [Android Studio](https://developer.android.com/studio)
1. Download and install [Crashlytics](https://www.crashlytics.com/downloads/android-studio)
1. Download the iNaturalist source code and extract to a directory of your choosing
1. Go to `iNaturalist/src/main/res/values` and copy `config.example.xml` to `config.xml` (and change its values to match your GMaps, FB, etc. keys)
1. Go to `iNaturalist/` and copy `google-services.example.json` to `google-services.json`. This contains stub values that will allow the app to build but won't connect to Firebase or other Google Services.
1. From Android Studio: File -> Open -> Choose the root directory of the downloaded source code
1. Download and install [Android NDK](https://developer.android.com/ndk/downloads/index.html)
1. Make sure ANDROID_NDK_HOME environment variable points to the NDK root path.
1. If on Mac: Make sure the above env variable is passed to Android Studio's gradle system: https://stackoverflow.com/a/30128305/1233767
1. Clean & build
