# Sample App
This is a sample android app that uses the `analytics-kotlin` library and the new `Plugins` concepts. It is meant to be simplistic, and easy to understand all the while showcasing the power of the analytics-kotlin library

## Plugins
- Android AdvertisingId Plugin
Using the `play-services-ads` library this plugin adds the `advertisingId` to all payloads (under the `context` key) going through the analytics timeline

- Android Record Screen Plugin
Using the application lifecycle, this plugin automatically sends `Screen` events through the analytics timeline, on Activity start

- Consent Tracking
Presents user with a dialog to consent to tracking. If consent is given, any queued events will be sent out to the analytics timeline. If consent is not given, all queued events and future events will be dropped
** Note: You will have to switch to the `ConsentActivity` inside of AndroidManifest.xml to view this feature **

- Webhook Plugin
A destination plugin that allows you to send the event from the analytics timeline to a webhook of your choice. Ideal for debugging payloads in an internal network.

## Tracking Deep Links
The sample app is configured to open links with the schema and hostname `https://segment-sample.com`

Here is how you can do it via adb
```bash
adb shell am start -W -a android.intent.action.VIEW -d "https://segment-sample.com?utm_source=cli\&utm_click=2" com.segment.analytics.next
```