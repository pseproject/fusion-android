package com.ubiqsmart.app.utils;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.v4.app.ActivityCompat;

public class ExternalStorageHandler {

  public static final int REQUEST_WRITE_STORAGE = 112;
  public static final int REQUEST_READ_STORAGE = 113;

  public static void askForPermission(Activity c) {
    if (Build.VERSION.SDK_INT < 23) {
      return;
    }
    ActivityCompat.requestPermissions(c, new String[] { Manifest.permission.WRITE_EXTERNAL_STORAGE }, REQUEST_WRITE_STORAGE);
  }

  public static void askForPermissionRead(Activity c) {
    if (Build.VERSION.SDK_INT < 23) {
      return;
    }
    ActivityCompat.requestPermissions(c, new String[] { Manifest.permission.READ_EXTERNAL_STORAGE }, REQUEST_READ_STORAGE);
  }

  public static boolean hasReadPermission(Context c) {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
        || c.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
  }

  public static boolean hasPermission(Context c) {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
        || c.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
  }
}
