# BrowserBlocker — v1.0 Test Build

Blocks ALL browsers on Android using Device Owner API.
No app icon. Starts on boot. Auto-blocks newly installed browsers.

---

## Project Structure

```
BrowserBlocker/
├── app/src/main/
│   ├── AndroidManifest.xml
│   ├── java/com/browserlocker/app/
│   │   ├── MyDeviceAdminReceiver.java   ← Core Device Owner class
│   │   ├── BrowserBlockerService.java   ← Main logic (browser detection + suspension)
│   │   ├── BootReceiver.java            ← Auto-starts on reboot
│   │   └── PackageInstallReceiver.java  ← Auto-blocks newly installed browsers
│   └── res/
│       ├── xml/device_admin.xml         ← Admin policy declarations
│       └── values/strings.xml
└── app/build.gradle
```

---

## How It Works

### Browser Detection
Instead of hardcoding package names, the app queries Android's PackageManager
for ALL apps that can handle `https://` URLs. This catches:
- Chrome, Firefox, Brave, Opera, UC Browser, Samsung Internet
- Any sideloaded browser
- Any browser installed in the future

### Blocking Mechanism
Uses `DevicePolicyManager.setPackagesSuspended()` — Device Owner exclusive API.
Suspended apps:
- Icon is greyed out in launcher
- Tapping shows "This app is suspended"
- Cannot receive any intents

### No App Icon
The manifest has NO `<activity>` with `LAUNCHER` intent category.
The app is invisible — it cannot be opened by the user.

### Boot Persistence
`BootReceiver` fires on `BOOT_COMPLETED` → starts `BrowserBlockerService`.
Browsers are re-blocked every boot automatically.

### New Browser Auto-Block
`PackageInstallReceiver` listens for `PACKAGE_ADDED` broadcasts.
If the new app is a browser → suspended immediately.

---

## Setup Instructions

### Step 1 — Prerequisites (do this ONCE)
Your phone must have no other accounts set up as Device Owner.
Check with:
```bash
adb shell dpm list-owners
```
If output is empty, you're good. If not, the existing owner must be removed first.

### Step 2 — Enable USB Debugging
Settings → About Phone → tap "Build Number" 7 times → Developer Options → USB Debugging ON

### Step 3 — Build & Install the APK
In Android Studio:
- Open this project
- Build → Generate Signed APK (or just Run for debug build)
- Install on device:
```bash
adb install app-debug.apk
```

### Step 4 — Set as Device Owner (THE KEY STEP)
```bash
adb shell dpm set-device-owner com.browserlocker.app/.MyDeviceAdminReceiver
```

Expected output:
```
Success: Device owner set to package com.browserlocker.app
```

### Step 5 — Start the Service Manually (first time only)
After install, trigger it once via ADB (since there's no launcher icon):
```bash
adb shell am startservice com.browserlocker.app/.BrowserBlockerService
```

After this, it auto-starts on every boot. You never need to touch it again.

---

## Testing

### Test 1 — Check if Device Owner is set
```bash
adb shell dpm list-owners
```

### Test 2 — Install Chrome and watch it get blocked
```bash
adb install chrome.apk
```
Chrome icon should be greyed out within seconds.

### Test 3 — Check logcat
```bash
adb logcat -s BrowserLocker
```
You'll see lines like:
```
D/BrowserLocker: Suspended: com.android.chrome
D/BrowserLocker: Auto-blocked newly installed browser: org.mozilla.firefox
```

---

## Uninstalling (Test Phase — still possible)

Since this is the test build, you can still uninstall:

**Option A — Remove Device Owner first, then uninstall:**
```bash
adb shell dpm remove-active-admin com.browserlocker.app/.MyDeviceAdminReceiver
adb uninstall com.browserlocker.app
```

**Option B — ADB uninstall directly (if not yet Device Owner):**
```bash
adb uninstall com.browserlocker.app
```

---

## Next Phase (after testing)

When you confirm everything works, tell me and I'll:
1. Add provisioning lock (Device Owner cannot be removed without factory reset)
2. Remove the ADB removal backdoor
3. Add watchdog that re-suspends browsers if somehow un-suspended
4. Optional: Generate a QR code for zero-touch Device Owner provisioning

---

## FAQ

**Q: App appears in Settings → Apps?**
A: Yes, it appears there but cannot be opened. In the non-uninstallable version,
   the uninstall button will be greyed out.

**Q: What if user goes to Settings and clears app data?**
A: Service restarts and re-blocks on next boot. The Device Owner registration
   persists across data clears.

**Q: Does this work on all Android versions?**
A: minSdk is 26 (Android 8.0). setPackagesSuspended() works reliably from API 24+,
   but 26+ is safer for foreground service behavior.
