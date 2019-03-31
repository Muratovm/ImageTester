package com.michaelmuratov.imagetester;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

public class Permissions {

    public static boolean READ_EXTERNAL_GRANTED = false;

    private static final int REQUEST_READ_EXTERNAL = 1;

    public static void requestExternalRead(Activity activity) {
        // Here, thisActivity is the current activity
        if (ContextCompat.checkSelfPermission(activity,
                Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        REQUEST_READ_EXTERNAL);
        } else {
            // Permission has already been granted
            READ_EXTERNAL_GRANTED = true;
        }
    }
}
