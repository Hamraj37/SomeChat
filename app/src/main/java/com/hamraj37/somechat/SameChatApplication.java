package com.hamraj37.somechat;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.color.DynamicColors;

import java.lang.ref.WeakReference;

public class SameChatApplication extends Application {
    
    private static WeakReference<Activity> currentActivity;

    @Override
    public void onCreate() {
        super.onCreate();
        // This applies dynamic colors (Monet) to all activities in the app.
        DynamicColors.applyToActivitiesIfAvailable(this);
        
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
                currentActivity = new WeakReference<>(activity);
            }

            @Override
            public void onActivityStarted(@NonNull Activity activity) {
                currentActivity = new WeakReference<>(activity);
            }

            @Override
            public void onActivityResumed(@NonNull Activity activity) {
                currentActivity = new WeakReference<>(activity);
            }

            @Override
            public void onActivityPaused(@NonNull Activity activity) {}

            @Override
            public void onActivityStopped(@NonNull Activity activity) {}

            @Override
            public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}

            @Override
            public void onActivityDestroyed(@NonNull Activity activity) {
                if (currentActivity != null && currentActivity.get() == activity) {
                    currentActivity = null;
                }
            }
        });
    }
    
    public static Activity getCurrentActivity() {
        return currentActivity != null ? currentActivity.get() : null;
    }
}