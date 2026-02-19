# Setup and Testing Guide

This guide will help you build, install, and test the MyFirstAPK Android application.

## Prerequisites

### For Building
- Java Development Kit (JDK) 8 or higher
- Android SDK (API level 21 or higher)
- Android Studio (recommended) or Gradle command line tools

### For Testing
- Android device or emulator running Android 5.0 (API 21) or higher
- Python 3 (for test server)
- Network connection between device and server

## Building the Application

### Option 1: Using Android Studio (Recommended)

1. Open Android Studio
2. Click "Open an existing project"
3. Navigate to the MyFirstAPK directory
4. Wait for Gradle sync to complete
5. Build the APK:
   - Menu: Build → Build Bundle(s) / APK(s) → Build APK(s)
   - Or use the toolbar build button
6. APK will be generated in: `app/build/outputs/apk/debug/app-debug.apk`

### Option 2: Using Gradle Command Line

```bash
# On Linux/Mac
./gradlew assembleDebug

# On Windows
gradlew.bat assembleDebug
```

The APK will be generated in: `app/build/outputs/apk/debug/app-debug.apk`

## Installing the Application

### Install via ADB (Android Debug Bridge)

1. Enable USB debugging on your Android device:
   - Go to Settings → About Phone
   - Tap "Build Number" 7 times to enable Developer Options
   - Go to Settings → Developer Options
   - Enable "USB Debugging"

2. Connect device via USB and install:
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Install on Emulator

```bash
adb -e install app/build/outputs/apk/debug/app-debug.apk
```

### Manual Installation

1. Copy `app-debug.apk` to your device
2. Open the file on your device
3. Allow installation from unknown sources if prompted
4. Tap "Install"

## Testing the Application

### Step 1: Start the Test Server

On your computer, run the provided test server:

```bash
python test_server.py 8080
```

You should see:
```
============================================================
MyFirstAPK Test Server
============================================================
Listening on port 8080...
Waiting for connections from Android app...
Press Ctrl+C to stop
============================================================
```

### Step 2: Get Your Computer's IP Address

#### On Linux/Mac:
```bash
ifconfig | grep "inet " | grep -v 127.0.0.1
```

#### On Windows:
```bash
ipconfig
```

Look for your local network IP (usually starts with 192.168.x.x or 10.0.x.x)

### Step 3: Configure the Android App

1. Launch "MyFirstAPK" on your Android device
2. In the "Server IP Address" field, enter your computer's IP address
3. Leave the port as 8080 (or change if you used a different port)

### Step 4: Connect to Server

1. Tap "Start Service" button
2. The status should change to "Service Status: Running"
3. On your server terminal, you should see:
   ```
   [HH:MM:SS] Client connected from (device-ip, port)
   [HH:MM:SS] Received: CLIENT_CONNECTED
     -> Client successfully connected
   ```

### Step 5: Capture and Upload Photo

1. Tap "Capture and Upload Photo" button
2. Grant camera permission if prompted
3. Take a photo using the camera
4. The photo will appear in the preview area
5. On the server terminal, you should see:
   ```
   [HH:MM:SS] Received: PHOTO_UPLOAD
     -> Receiving photo: XXXXX bytes
     -> Progress: 100.0%
     -> Photo saved: photo_YYYYMMDD_HHMMSS_1.jpg (XXXXX bytes)
   ```
6. Check your server directory for the received photo file

### Step 6: Stop the Service

1. Tap "Stop Service" button in the app
2. The status should change to "Service Status: Stopped"
3. The server connection will close

## Troubleshooting

### Connection Issues

**Problem**: Cannot connect to server
- **Solution**: Ensure your device and computer are on the same network
- **Solution**: Check firewall settings on your computer
- **Solution**: Verify the IP address is correct
- **Solution**: Ensure the server is running before starting the service

**Problem**: "Permission denied" on server
- **Solution**: Try a different port (e.g., 8081) or run with sudo (Linux/Mac)

### Camera Issues

**Problem**: Camera not working
- **Solution**: Grant camera permission in Settings → Apps → MyFirstAPK → Permissions
- **Solution**: Ensure your device has a camera
- **Solution**: Try restarting the app

**Problem**: Photo not uploading
- **Solution**: Ensure the service is running (Status shows "Running")
- **Solution**: Check the server logs for errors
- **Solution**: Restart both app and server

### Build Issues

**Problem**: Gradle sync fails
- **Solution**: Ensure you have a stable internet connection
- **Solution**: Update Android Studio to the latest version
- **Solution**: File → Invalidate Caches / Restart

**Problem**: SDK not found
- **Solution**: Install Android SDK through Android Studio SDK Manager
- **Solution**: Set ANDROID_HOME environment variable

## Advanced Testing

### Testing Commands

You can modify the test server to send commands to the app:

```python
# In handle_client function, after CLIENT_CONNECTED:
send_utf8_string(client_socket, "PING")
```

Supported commands:
- `PING` - App responds with `PONG`
- `STATUS` - App responds with `STATUS_OK`
- `CAPTURE_PHOTO` - App acknowledges capture request

### Network Testing

Test over different networks:
1. **Same WiFi**: Direct connection (easiest)
2. **Mobile Hotspot**: Create hotspot on one device
3. **Remote Server**: Deploy test_server.py on a cloud server

### Load Testing

Test multiple photo uploads:
1. Capture and upload multiple photos in sequence
2. Verify all photos are received correctly
3. Check for memory leaks or performance issues

## Security Testing Considerations

⚠️ **Important**: This application is for educational purposes.

For security testing:
1. Always use in a controlled environment
2. Never use on production networks
3. Don't transmit sensitive data
4. Monitor network traffic with tools like Wireshark
5. Test permission handling thoroughly

## Next Steps

After successful testing:
1. Review the code to understand the implementation
2. Explore modifying the protocol
3. Add encryption (TLS/SSL)
4. Implement authentication
5. Add more C2 commands
6. Improve error handling

## Getting Help

If you encounter issues:
1. Check the logcat output: `adb logcat | grep MyFirstAPK`
2. Review server logs for connection errors
3. Verify network connectivity: `ping <server-ip>`
4. Check the README.md for detailed documentation
