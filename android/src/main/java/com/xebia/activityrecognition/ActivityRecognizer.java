package com.xebia.activityrecognition;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityRecognitionClient;
import com.google.android.gms.location.DetectedActivity;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class ActivityRecognizer {
    protected static final String TAG = ActivityRecognizer.class.getSimpleName();
    protected ActivityDetectionBroadcastReceiver mBroadcastReceiver;
    private Context mContext;
    private ReactContext mReactContext;
    private ActivityRecognitionClient mActivityRecognitionClient;
    private boolean started;
    private long interval;
    private Timer mockTimer;

    public ActivityRecognizer(ReactApplicationContext reactContext) {
        mContext = reactContext.getApplicationContext();
        mReactContext = reactContext;
        mActivityRecognitionClient = ActivityRecognition.getClient(mContext);
        started = false;

        mBroadcastReceiver = new ActivityDetectionBroadcastReceiver();
        IntentFilter intentFilter = new IntentFilter(DetectionService.BROADCAST_ACTION);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            mContext.registerReceiver(mBroadcastReceiver, intentFilter, Context.RECEIVER_EXPORTED);
        } else {
            LocalBroadcastManager.getInstance(mContext).registerReceiver(mBroadcastReceiver, intentFilter);
        }
    }

    // Subscribe to activity updates.
    public void start(long detectionIntervalMillis) {
        interval = detectionIntervalMillis;
        if (!started) {
            mActivityRecognitionClient.requestActivityUpdates(
                    detectionIntervalMillis,
                    getActivityDetectionPendingIntent()
            ).addOnSuccessListener(aVoid -> {
                Log.d(TAG, "Successfully requested activity updates");
                started = true;
            }).addOnFailureListener(e -> {
                Log.e(TAG, "Failed to request activity updates", e);
            });
        }
    }

    // Subscribe to mock activity updates.
    public void startMocked(long detectionIntervalMillis, final int mockActivityType) {
        mockTimer = new Timer();
        mockTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                final ArrayList<DetectedActivity> detectedActivities = new ArrayList<>();
                DetectedActivity detectedActivity = new DetectedActivity(mockActivityType, 100);
                detectedActivities.add(detectedActivity);
                onUpdate(detectedActivities);
            }
        }, 0, detectionIntervalMillis);

        started = true;
    }

    // Unsubscribe from mock activity updates.
    public void stopMocked() {
        if (started) {
            mockTimer.cancel();
            started = false;
        }
    }

    // Unsubscribe from activity updates.
    public void stop() {
        if (started) {
            mActivityRecognitionClient.removeActivityUpdates(
                    getActivityDetectionPendingIntent()
            ).addOnSuccessListener(aVoid -> {
                Log.d(TAG, "Successfully removed activity updates");
                started = false;
            }).addOnFailureListener(e -> {
                Log.e(TAG, "Failed to remove activity updates", e);
            });

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                mContext.unregisterReceiver(mBroadcastReceiver);
            } else {
                LocalBroadcastManager.getInstance(mContext).unregisterReceiver(mBroadcastReceiver);
            }
        }
    }

    // Create a PendingIntent to be sent for each activity detection
    private PendingIntent getActivityDetectionPendingIntent() {
        Intent intent = new Intent(mReactContext, DetectionService.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getService(mReactContext, 0, intent, flags);
    }

    // Create key-value map with activity recognition result
    private void onUpdate(ArrayList<DetectedActivity> detectedActivities) {
        WritableMap params = Arguments.createMap();
        for (DetectedActivity activity : detectedActivities) {
            params.putInt(DetectionService.getActivityString(activity.getType()), activity.getConfidence());
        }
        sendEvent("DetectedActivity", params);
    }

    // Send result back to JavaScript land
    private void sendEvent(String eventName, @Nullable WritableMap params) {
        try {
            mReactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit(eventName, params);
        } catch (RuntimeException e) {
            Log.e(TAG, "java.lang.RuntimeException: Trying to invoke JS before CatalystInstance has been set!", e);
        }
    }

    // Listen to events broadcasted by the DetectionService
    public class ActivityDetectionBroadcastReceiver extends BroadcastReceiver {
        protected static final String TAG = "ActivityDetectionBroadcastReceiver";

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "Received activity update");
            ArrayList<DetectedActivity> updatedActivities = intent.getParcelableArrayListExtra(DetectionService.ACTIVITY_EXTRA);
            onUpdate(updatedActivities);
        }
    }
}
