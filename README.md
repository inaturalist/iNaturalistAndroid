# iNaturalistAndroid

iNaturalistAndroid is an Android app for [iNaturalist.org](http://www.inaturalist.org).

## Eclipse Setup

### From a terminal

```bash
cd path/to/your/workspace
git clone git@github.com:inaturalist/iNaturalistAndroid.git

# Get the JAR deps
mkdir iNaturalistAndroid/libs/
cd iNaturalistAndroid/libs/
wget https://github.com/loopj/android-async-http/raw/master/releases/android-async-http-1.3.1.jar
wget http://psg.mtu.edu/pub/apache//commons/collections/binaries/commons-collections-3.2.1-bin.tar.gz
tar xzvf commons-collections-3.2.1-bin.tar.gz
wget http://apache.cs.utah.edu//commons/lang/binaries/commons-lang3-3.1-bin.tar.gz
tar xzvf commons-lang3-3.1-bin.tar.gz
wget http://archive.apache.org/dist/httpcomponents/httpclient/binary/httpcomponents-client-4.1.2-bin.tar.gz
tar xzvf httpcomponents-client-4.1.2-bin.tar.gz
cd ../../

# Get the FacebookSDK
git clone git://github.com/facebook/facebook-android-sdk.git
./facebook-android-sdk/scripts/build_and_test.sh

# Get the Android-PullToRefresh library
git clone git://github.com/budowski/Android-PullToRefresh.git

# Get the Android-ActionBar library
git clone git://github.com/johannilsson/android-actionbar.git

# Copy the example config file and add your own API keys etc
cp iNaturalistAndroid/res/values/config.xml.example iNaturalistAndroid/res/values/config.xml
```

### From Eclipse

1. Open menu `File / Import...`
1. Choose `General / Existing Projects into Workspace`
1. `Select root directory` as `path/to/your/workspace/facebook-android-sdk/facebook`
1. Check the `FacebookSDK` project and click `Finish`
1. Open menu `File / Import...`
1. Choose `General / Existing Projects into Workspace`
1. `Select root directory` as `path/to/your/workspace/INaturalistAndroid`
1. Check the `INaturalistAndroid` project and click `Finish`
1. Open menu `File / Import...`
1. Choose `General / Existing Projects into Workspace`
1. `Select root directory` as `path/to/your/workspace/Android-PullToRefresh/library`
1. Check the `library` project and click `Finish`
1. Choose `General / Existing Projects into Workspace`
1. `Select root directory` as `path/to/your/workspace/android-actionbar/actionbar`
1. Check the `android-actionbar-library` project and click `Finish`
1. Right-click the `iNaturalistAndroid` project and select `Properties`
1. Go to `Android` tab
1. Scroll down to the `Library` box and click `Add`
1. Select the `actionbar-library` and `PullToRefresh-library` project and press OK
1. Press OK to close the dialog


In theory it should build now!
