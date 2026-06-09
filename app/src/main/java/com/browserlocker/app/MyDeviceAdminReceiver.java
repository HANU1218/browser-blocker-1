package com.browserlocker.app;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * DeviceAdminReceiver — The core of Device Owner functionality.
 *
 * After provisioning via ADB:
 *   adb shell dpm set-device-owner com.browserlocker.app/.MyDeviceAdminReceiver
 *
 * This class gains full Device Owner privileges:
 *   - setPackagesSuspended()  → freeze/grey out any app
 *   - The app CANNOT be uninstalled without factory reset (once Device Owner)
 */
public class MyDeviceAdminReceiver extends DeviceAdminReceiver {

    private static final String TAG = "BrowserLocker";

    @Override
    public void onEnabled(Context context, Intent intent) {
        super.onEnabled(context, intent);
        Log.d(TAG, "Device Admin enabled");
        // Immediately block all currently installed browsers
        BrowserBlockerService.blockAllBrowsers(context);
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        super.onDisabled(context, intent);
        Log.d(TAG, "Device Admin disabled — browsers will be unblocked");
    }

    @Override
    public CharSequence onDisableRequested(Context context, Intent intent) {
        return "Disabling this will allow browser access.";
    }
}
