package mobile;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

/**
 * Runs once when the app's process starts: creates the notification channels and keeps
 * track of whether one of our screens is visible (no notifications while the user is in the app).
 * <p>
 * Declared in AndroidManifest.xml with {@code android:name="mobile.ChatApp"}. Android creates it
 * before any screen. Every Activity start/stop is reported to the callbacks below, so we simply
 * count the visible screens: more than 0 means the user is looking at the app.
 */
public class ChatApp extends Application {

    /** Our activities currently started (visible). Main thread only. */
    private static int startedActivities;

    public static boolean isInForeground() {
        return startedActivities > 0;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Notifications.createChannels(this);
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityStarted(Activity activity) {
                startedActivities++;
            }

            @Override
            public void onActivityStopped(Activity activity) {
                startedActivities--;
            }

            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
            }

            @Override
            public void onActivityResumed(Activity activity) {
            }

            @Override
            public void onActivityPaused(Activity activity) {
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
            }

            @Override
            public void onActivityDestroyed(Activity activity) {
            }
        });
    }
}
