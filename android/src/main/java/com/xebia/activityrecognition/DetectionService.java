package com.xebia.activityrecognition;

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;
import android.os.Build;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;

import java.util.ArrayList;

public class DetectionService extends IntentService {

    private static final String TAG = DetectionService.class.getSimpleName();
    public static final String BROADCAST_ACTION = "com.xebia.activityrecognition.BROADCAST_ACTION";
    public static final String ACTIVITY_EXTRA = "com.xebia.activityrecognition.ACTIVITY_EXTRA";

    public DetectionService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (ActivityRecognitionResult.hasResult(intent)) {
            ActivityRecognitionResult result = ActivityRecognitionResult.extractResult(intent);
            ArrayList<DetectedActivity> detectedActivities = (ArrayList<DetectedActivity>) result.getProbableActivities();

            Intent localIntent = new Intent(BROADCAST_ACTION);
            localIntent.putParcelableArrayListExtra(ACTIVITY_EXTRA, detectedActivities);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // For Android 13 (API level 33) and above, use explicit broadcasts
                localIntent.setPackage(getPackageName());
                sendBroadcast(localIntent);
            } else {
                // For older versions, continue using LocalBroadcastManager
                LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
            }

            Log.d(TAG, "Broadcasting detected activities: " + detectedActivities);
        } else {
            Log.e(TAG, "No ActivityRecognitionResult in intent");
        }
    }

    public static String getActivityString(int detectedActivityType) {
        switch (detectedActivityType) {
            case DetectedActivity.IN_VEHICLE:
                return "IN_VEHICLE";
            case DetectedActivity.ON_BICYCLE:
                return "ON_BICYCLE";
            case DetectedActivity.ON_FOOT:
                return "ON_FOOT";
            case DetectedActivity.RUNNING:
                return "RUNNING";
            case DetectedActivity.STILL:
                return "STILL";
            case DetectedActivity.TILTING:
                return "TILTING";
            case DetectedActivity.UNKNOWN:
                return "UNKNOWN";
            case DetectedActivity.WALKING:
                return "WALKING";
            default:
                return "UNIDENTIFIABLE";
        }
    }
}
