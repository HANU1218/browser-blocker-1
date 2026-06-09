package com.browserlocker.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * BrowserBlockerService
 *
 * Runs as a persistent foreground service (invisible to user — notification
 * is low priority and can be hidden on Android 14+ with notification channels).
 *
 * Two jobs:
 *   1. On start → suspend all currently installed browsers
 *   2. Stay alive so PackageInstallReceiver can call blockSinglePackage() anytime
 */
public class BrowserBlockerService extends Service {

    private static final String TAG               = "BrowserLocker";
    private static final String CHANNEL_ID        = "blocker_channel";
    private static final int    NOTIFICATION_ID   = 1001;

    // ─────────────────────────────────────────────────────────────────────────
    // Static helper — can be called from receivers without binding the service
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Dynamically finds every browser on the device by querying which apps
     * can handle https:// URLs, then suspends them all via Device Owner API.
     */
    public static void blockAllBrowsers(Context context) {
        DevicePolicyManager dpm =
                (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName admin =
                new ComponentName(context, MyDeviceAdminReceiver.class);

        if (!dpm.isDeviceOwnerApp(context.getPackageName())) {
            Log.e(TAG, "Not Device Owner — cannot suspend packages. " +
                    "Run: adb shell dpm set-device-owner " +
                    context.getPackageName() + "/.MyDeviceAdminReceiver");
            return;
        }

        List<String> browsers = getAllBrowserPackages(context);
        browsers.remove(context.getPackageName()); // Never suspend ourselves

        if (browsers.isEmpty()) {
            Log.d(TAG, "No browsers found on device.");
            return;
        }

        String[] browserArray = browsers.toArray(new String[0]);

        try {
            String[] failed = dpm.setPackagesSuspended(admin, browserArray, true);
            Log.d(TAG, "Blocked " + (browserArray.length - failed.length) +
                    " browsers. Failed: " + failed.length);
            for (String pkg : browserArray) {
                Log.d(TAG, "  Suspended: " + pkg);
            }
        } catch (DevicePolicyManager.NameNotFoundException e) {
            Log.e(TAG, "Package not found during suspension: " + e.getMessage());
        }
    }

    /**
     * Blocks a single package if it turns out to be a browser.
     * Called from PackageInstallReceiver when a new app is installed.
     */
    public static void blockIfBrowser(Context context, String packageName) {
        if (isBrowser(context, packageName)) {
            DevicePolicyManager dpm =
                    (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName admin =
                    new ComponentName(context, MyDeviceAdminReceiver.class);

            if (!dpm.isDeviceOwnerApp(context.getPackageName())) {
                Log.e(TAG, "Not Device Owner — cannot block newly installed browser.");
                return;
            }

            try {
                dpm.setPackagesSuspended(admin, new String[]{packageName}, true);
                Log.d(TAG, "Auto-blocked newly installed browser: " + packageName);
            } catch (DevicePolicyManager.NameNotFoundException e) {
                Log.e(TAG, "Failed to block " + packageName + ": " + e.getMessage());
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Detection helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns all installed packages that can handle https:// web URLs.
     * This catches Chrome, Firefox, Brave, Opera, UC, Samsung Browser,
     * any sideloaded browser, and any future browser automatically.
     */
    public static List<String> getAllBrowserPackages(Context context) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("https://www.google.com"));

        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> resolvedList = pm.queryIntentActivities(
                intent, PackageManager.MATCH_ALL);

        List<String> packageNames = new ArrayList<>();
        for (ResolveInfo info : resolvedList) {
            String pkg = info.activityInfo.packageName;
            if (!packageNames.contains(pkg)) {
                packageNames.add(pkg);
            }
        }
        return packageNames;
    }

    /**
     * Checks if a specific package is a browser (handles https://).
     */
    private static boolean isBrowser(Context context, String packageName) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("https://www.google.com"));
        intent.setPackage(packageName);

        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> result = pm.queryIntentActivities(intent, 0);
        return !result.isEmpty();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Service lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Start as foreground so Android doesn't kill us
        startForeground(NOTIFICATION_ID, buildNotification());

        // Block all browsers immediately every time service starts
        blockAllBrowsers(this);

        Log.d(TAG, "BrowserBlockerService started");

        // START_STICKY = if killed by system, restart automatically
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; // Not a bound service
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service destroyed — restarting...");
        // Self-restart if somehow stopped
        Intent restartIntent = new Intent(this, BrowserBlockerService.class);
        startService(restartIntent);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Notification (required for foreground service, kept minimal/hidden)
    // ─────────────────────────────────────────────────────────────────────────

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "System",
                    NotificationManager.IMPORTANCE_MIN  // Lowest visibility
            );
            channel.setDescription("System background process");
            channel.setShowBadge(false);
            channel.setSound(null, null);

            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Notification.Builder builder;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        return builder
                .setContentTitle("System Service")
                .setContentText("Running")
                .setSmallIcon(android.R.drawable.ic_menu_manage)
                .setPriority(Notification.PRIORITY_MIN)
                .setOngoing(true)
                .build();
    }
}
