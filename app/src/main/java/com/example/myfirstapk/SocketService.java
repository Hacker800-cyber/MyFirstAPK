package com.example.myfirstapk;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class SocketService extends Service {
    
    private static final String TAG = "SocketService";
    
    private String serverIp;
    private int serverPort;
    private Socket socket;
    private DataInputStream inputStream;
    private DataOutputStream outputStream;
    private Thread connectionThread;
    private volatile boolean isRunning = false;
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "SocketService created");
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            // Check if this is a photo upload request
            if (intent.hasExtra("UPLOAD_PHOTO")) {
                byte[] photoData = intent.getByteArrayExtra("UPLOAD_PHOTO");
                if (photoData != null) {
                    uploadPhotoData(photoData);
                }
                return START_STICKY;
            }
            
            // Otherwise, start/restart the socket connection
            if (intent.hasExtra("SERVER_IP") && intent.hasExtra("SERVER_PORT")) {
                serverIp = intent.getStringExtra("SERVER_IP");
                serverPort = intent.getIntExtra("SERVER_PORT", 8080);
                
                startSocketConnection();
            }
        }
        
        return START_STICKY;
    }
    
    private void startSocketConnection() {
        if (isRunning) {
            Log.d(TAG, "Connection already running");
            return;
        }
        
        isRunning = true;
        
        connectionThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.d(TAG, "Connecting to " + serverIp + ":" + serverPort);
                    socket = new Socket(serverIp, serverPort);
                    inputStream = new DataInputStream(socket.getInputStream());
                    outputStream = new DataOutputStream(socket.getOutputStream());
                    
                    Log.d(TAG, "Connected successfully");
                    
                    // Send initial connection message
                    sendMessage("CLIENT_CONNECTED");
                    
                    // Start listening for commands
                    listenForCommands();
                    
                } catch (IOException e) {
                    Log.e(TAG, "Connection error: " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    closeConnection();
                }
            }
        });
        
        connectionThread.start();
    }
    
    private void listenForCommands() {
        while (isRunning && socket != null && !socket.isClosed()) {
            try {
                // Blocking read - will wait for data to arrive
                String command = inputStream.readUTF();
                Log.d(TAG, "Received command: " + command);
                handleCommand(command);
                
            } catch (IOException e) {
                if (isRunning) {
                    Log.e(TAG, "Error reading command: " + e.getMessage());
                }
                break;
            }
        }
    }
    
    private void handleCommand(String command) {
        Log.d(TAG, "Handling command: " + command);
        
        switch (command) {
            case "PING":
                sendMessage("PONG");
                break;
            case "STATUS":
                sendMessage("STATUS_OK");
                break;
            case "CAPTURE_PHOTO":
                sendMessage("PHOTO_CAPTURE_REQUESTED");
                // In a real implementation, this would trigger photo capture
                break;
            default:
                sendMessage("UNKNOWN_COMMAND: " + command);
                break;
        }
    }
    
    private void sendMessage(String message) {
        if (outputStream != null) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        outputStream.writeUTF(message);
                        outputStream.flush();
                        Log.d(TAG, "Sent message: " + message);
                    } catch (IOException e) {
                        Log.e(TAG, "Error sending message: " + e.getMessage());
                    }
                }
            }).start();
        }
    }
    
    private void uploadPhotoData(byte[] photoData) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (outputStream != null && socket != null && !socket.isClosed()) {
                    try {
                        // Send header indicating photo upload
                        outputStream.writeUTF("PHOTO_UPLOAD");
                        // Send photo size
                        outputStream.writeInt(photoData.length);
                        // Send photo data
                        outputStream.write(photoData);
                        outputStream.flush();
                        
                        Log.d(TAG, "Photo uploaded: " + photoData.length + " bytes");
                    } catch (IOException e) {
                        Log.e(TAG, "Error uploading photo: " + e.getMessage());
                    }
                } else {
                    Log.e(TAG, "Cannot upload photo: socket not connected");
                }
            }
        }).start();
    }
    
    private void closeConnection() {
        isRunning = false;
        
        try {
            if (inputStream != null) {
                inputStream.close();
            }
            if (outputStream != null) {
                outputStream.close();
            }
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error closing connection: " + e.getMessage());
        }
        
        Log.d(TAG, "Connection closed");
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        closeConnection();
        Log.d(TAG, "SocketService destroyed");
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
