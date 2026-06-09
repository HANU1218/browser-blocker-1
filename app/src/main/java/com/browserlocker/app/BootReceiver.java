package com.browserlocker.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/**
 * BootReceiver
 *
 * Fires on BOOT_COMPLETED → starts BrowserBlockerService automatically.
 * This ensures browsers are blocked even after a reboot, without
 * the user needing to open the app (which they can't anyway — no icon).
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BrowserLocker";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (Intent.ACTION_BOOT_COMPLETED.equals(action) ||
            "android.intent.action.QUICKBOOT_POWERON".equals(action)) {

            Log.d(TAG, "Boot detected — starting BrowserBlockerService");

            Intent serviceIntent = new Intent(context, BrowserBlockerService.class);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        }
    }
}
