package com.browserlocker.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

/**
 * PackageInstallReceiver
 *
 * Listens for PACKAGE_ADDED broadcast (fires whenever user installs any app).
 *
 * When a new app is installed:
 *   1. Extract its package name
 *   2. Check if it can handle https:// URLs (i.e., is a browser)
 *   3. If yes → suspend it immediately via Device Owner API
 *
 * This means even if the user somehow installs Chrome or any new browser
 * after BrowserBlocker is set up, it gets blocked within seconds of install.
 */
public class PackageInstallReceiver extends BroadcastReceiver {

    private static final String TAG = "BrowserLocker";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getData() == null) return;

        String action = intent.getAction();
        if (!Intent.ACTION_PACKAGE_ADDED.equals(action) &&
            !Intent.ACTION_PACKAGE_REPLACED.equals(action)) {
            return;
        }

        Uri data = intent.getData();
        String newPackage = data.getSchemeSpecificPart();

        if (newPackage == null || newPackage.isEmpty()) return;

        // Don't react to our own updates
        if (newPackage.equals(context.getPackageName())) return;

        Log.d(TAG, "New package installed: " + newPackage + " — checking if browser...");

        // Check if it's a browser and block it
        BrowserBlockerService.blockIfBrowser(context, newPackage);
    }
}
