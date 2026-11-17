# UPI Display — phone listener (v0.1)

Reads payment notifications from the UPI apps you choose and shows the last 10
payments on screen. It can also send each payment over the shop Wi-Fi to the
ESP32 display (or to a second phone in display mode).

What it does NOT do: read SMS, use accessibility, draw over other apps, or
contact any server. Notifications from apps you did not tick are ignored.

## Build (GitHub, no Android Studio needed)
1. Create a new repo on GitHub, e.g. `upi-display`.
2. Upload everything in this folder, including the `.github` folder and
   `app/debug.keystore`.
3. Actions tab -> latest run -> Artifacts -> `UpiDisplay-APK`.

## Install
Best: from a Windows PC with `adb install app-debug.apk` (USB debugging on).
From a file manager, Android may block it or hide "Notification access".
If so: Settings -> Apps -> UPI Display -> menu (top right) -> "Allow restricted settings".

## Network message (for the ESP32 firmware)
UDP broadcast to port 4210:
{"t":"pay","id":"...","amt":250.0,"name":"Ravi","app":"GPay Biz","ts":1731830400000,"v":false}
