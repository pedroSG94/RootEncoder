package com.pedro.rtpstreamer.utils;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;

import java.io.File;

/**
 * Created by pedro on 21/06/17.
 * Get absolute path from onActivityResult
 * https://stackoverflow.com/questions/33295300/how-to-get-absolute-path-in-android-for-file
 */

public class PathUtils {

  public static File getRecordPath(Context context) {
    return new File(context.getExternalFilesDir(null).getAbsolutePath()
        + "/rtmp-rtsp-stream-client-java");
  }

  public static String getPath(final Context context, final Uri uri) {

    // DocumentProvider
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && DocumentsContract.isDocumentUri(
        context, uri)) {

      if (isExternalStorageDocument(uri)) {// ExternalStorageProvider
        final String docId = DocumentsContract.getDocumentId(uri);
        final String[] split = docId.split(":");
        final String type = split[0];
        String storageDefinition;

        if ("primary".equalsIgnoreCase(type)) {

          return Environment.getExternalStorageDirectory() + "/" + split[1];
        } else {

          if (Environment.isExternalStorageRemovable()) {
            storageDefinition = "EXTERNAL_STORAGE";
          } else {
            storageDefinition = "SECONDARY_STORAGE";
          }

          return System.getenv(storageDefinition) + "/" + split[1];
        }
      } else if (isDownloadsDocument(uri)) {// DownloadsProvider

        final String id = DocumentsContract.getDocumentId(uri);
        final Uri contentUri =
            ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"),
                Long.valueOf(id));

        return getDataColumn(context, contentUri, null, null);
      } else if (isMediaDocument(uri)) {// MediaProvider
        final String docId = DocumentsContract.getDocumentId(uri);
        final String[] split = docId.split(":");
        final String type = split[0];

        Uri contentUri = null;
        if ("image".equals(type)) {
          contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        } else if ("video".equals(type)) {
          contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        } else if ("audio".equals(type)) {
          contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        }

        final String selection = "_id=?";
        final String[] selectionArgs = new String[] {
            split[1]
        };

        return getDataColumn(context, contentUri, selection, selectionArgs);
      }
    } else if ("content".equalsIgnoreCase(uri.getScheme())) {// MediaStore (and general)

      // Return the remote address
      if (isGooglePhotosUri(uri)) return uri.getLastPathSegment();

      return getDataColumn(context, uri, null, null);
    } else if ("file".equalsIgnoreCase(uri.getScheme())) {// File
      return uri.getPath();
    }

    return null;
  }

  private static String getDataColumn(Context context, Uri uri, String selection,
      String[] selectionArgs) {

    Cursor cursor = null;
    final String column = "_data";
    final String[] projection = {
        column
    };

    try {
      cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null);
      if (cursor != null && cursor.moveToFirst()) {
        final int column_index = cursor.getColumnIndexOrThrow(column);
        return cursor.getString(column_index);
      }
    } finally {
      if (cursor != null) cursor.close();
    }
    return null;
  }

  private static boolean isExternalStorageDocument(Uri uri) {
    return "com.android.externalstorage.documents".equals(uri.getAuthority());
  }

  private static boolean isDownloadsDocument(Uri uri) {
    return "com.android.providers.downloads.documents".equals(uri.getAuthority());
  }

  private static boolean isMediaDocument(Uri uri) {
    return "com.android.providers.media.documents".equals(uri.getAuthority());
  }

  private static boolean isGooglePhotosUri(Uri uri) {
    return "com.google.android.apps.photos.content".equals(uri.getAuthority());
  }
}
