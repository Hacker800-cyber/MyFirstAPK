import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

/**
 * SocketService manages the socket connection for the application.
 * It handles the communication between the client and the server.
 * 
 * Improvements made: 
 * 1. Added error handling for socket exceptions.
 * 2. Implemented a mechanism to gracefully close connections.
 * 3. Included logging for better debugging.
 */
public class SocketService extends Service {
    private static final String TAG = "SocketService";
    private Socket socket;
    private String serverIp;
    private int serverPort;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "SocketService created");
        // Initialize socket connection here
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        serverIp = intent.getStringExtra("SERVER_IP");
        serverPort = intent.getIntExtra("SERVER_PORT", 0);
        // Start socket connection
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    socket = new Socket(serverIp, serverPort);
                    Log.d(TAG, "Connected to server: " + serverIp + ":" + serverPort);
                } catch (IOException e) {
                    Log.e(TAG, "Error connecting to server", e);
                    stopSelf(); // Stop service if connection fails
                }
            }
        }).start();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Clean up resources and close socket on service destruction
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
                Log.d(TAG, "Socket closed");
            } catch (IOException e) {
                Log.e(TAG, "Error closing socket", e);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        // Return null as this is a started service
        return null;
    }
}