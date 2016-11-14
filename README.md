# GmsWear

GMS Library Wrapper for Android Wear

[ ![Download](https://api.bintray.com/packages/csarron/libs/gmswear/images/download.svg) ](https://bintray.com/csarron/libs/gmswear/_latestVersion)

## Introduction

This library  demonstrates how to use Android Wearable GMS API for messaging and syncing data between mobile and wearable apps.

Features:

- Support latest Google Play Services (Wearable) 9.8.0
- Send and receive messages from both wear and mobile devices
- Syncing data between wearable and handhelds

## Installation

- gradele:

````
dependencies {
    compile 'com.cscao.libs:gmswear:0.97.4'
}
````

## Usage

1. Configure capabilities for your Application(or Activities)
  see sample at `mobile/src/main/res/values/wear.xml`

  or you can add capabilities during runtime, see the commented `MSG_CAPABILITY` in `mobile/src/main/java/com/cscao/apps/gmswear/PhoneActivity.java`

2. Configure GMS service in `AndroidManifest.xml`
  put below info inside `application` tag
  ```
  <service android:name="com.cscao.libs.gmswear.GmsWearService">
      <intent-filter>
          <!-- listeners receive events that match the action and data filters -->
          <action android:name="com.google.android.gms.wearable.DATA_CHANGED" />
          <action android:name="com.google.android.gms.wearable.MESSAGE_RECEIVED" />
          <action android:name="com.google.android.gms.wearable.CAPABILITY_CHANGED" />
          <action android:name="com.google.android.gms.wearable.CHANNEL_EVENT" />
          <data
              android:host="*"
              android:pathPrefix="/com.cscao.libs.gmswear/"
              android:scheme="wear" />
      </intent-filter>
  </service>
  ```
  note that you can customize pathPrefix and `android:pathPrefix` is optional.

3. Initialize the GmsWear in your application by calling `GmsWear.initialize(getApplicationContext());` in `onCreate()`.
  see sample at `mobile/src/main/java/com/cscao/apps/gmswear/PhoneApplication.java`

4. Call `GmsWear.getInstance()` where you want to send msg/data

5. Set a `DataConsumer` where you want to receive msg/data, note that you need to register/unregister(often in `onResume` and `onPause()`) your data consumer to the GmsWear instance.

You can also skip step 1 and dynamically add capabilities(either during initialization or right before you send data) to your app

More usage see the demo(either `PhoneActivity.java` or `WearActivity.java`) in this repo, and see the googlesamples (link below) although there are small api changes

## Note

This library is heavily inspired by the [WCL library](https://github.com/googlesamples/android-WclDemoSample)
