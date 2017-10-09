# iNaturalistAndroid

iNaturalistAndroid is an Android app for [iNaturalist.org](http://www.inaturalist.org).

## Setup

1. Make sure you have tha latest Android Studio
1. Download and install [Crashlytics](https://www.crashlytics.com/downloads/android-studio)
1. Download the iNaturalist source code and extract to a directory of your choosing
1. Go to the `iNaturalist/src/main/res/values` and rename `config.xml.example` to `config.xml` (and change its values to match your GMaps, Flurry, FB, etc. keys)
1. From Android Studio: File -> Open -> Choose the root directory of the downloaded source code
1. Download and install [Android NDK](https://developer.android.com/ndk/downloads/index.html)
1. Make sure ANDROID_NDK_HOME environment variable points to the NDK root path.
1. If on Mac: Make sure the above env variable is passed to Android Studio's gradle system: https://stackoverflow.com/a/30128305/1233767
1. Clean & build

