# MyFirstAPK
Android C2 Socket Service with Photo Upload

## Overview
This is an Android application that implements a Command and Control (C2) socket-based communication system with photo capture and upload functionality.

## Features
- **Socket-based C2 Communication**: Establishes a persistent connection to a remote server for command and control operations
- **Photo Capture**: Uses the device camera to capture photos
- **Photo Upload**: Automatically uploads captured photos to the connected server
- **Real-time Command Processing**: Listens for and responds to commands from the server

## Architecture

### Main Components

#### 1. MainActivity
- User interface for configuring server connection
- Controls for starting/stopping the socket service
- Camera integration for photo capture
- Displays photo preview after capture

#### 2. SocketService
- Background service that maintains socket connection to C2 server
- Handles incoming commands from the server
- Sends status updates and photo data
- Supported commands:
  - `PING` - Responds with `PONG`
  - `STATUS` - Responds with `STATUS_OK`
  - `CAPTURE_PHOTO` - Triggers photo capture request

#### 3. Photo Upload Protocol
When a photo is captured and uploaded:
1. Sends `PHOTO_UPLOAD` command
2. Sends photo size (integer)
3. Sends raw photo data (JPEG format)

## Permissions Required
- `INTERNET` - For socket communication
- `ACCESS_NETWORK_STATE` - To check network connectivity
- `CAMERA` - For photo capture
- `READ_EXTERNAL_STORAGE` - For accessing photos
- `WRITE_EXTERNAL_STORAGE` - For saving photos (API < 29)
- `FOREGROUND_SERVICE` - For background service operation

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

## Server Implementation

To test this application, you need a server that:
1. Listens on the specified port
2. Accepts socket connections
3. Can send/receive UTF-8 encoded strings
4. Can receive binary photo data

Example server implementation (Python):
```python
import socket
import struct

server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
server.bind(('0.0.0.0', 8080))
server.listen(1)

print("Waiting for connection...")
client, addr = server.accept()
print(f"Connected: {addr}")

while True:
    try:
        # Read message length (UTF-8)
        msg = client.recv(1024).decode('utf-8')
        print(f"Received: {msg}")
        
        if msg == "PHOTO_UPLOAD":
            # Read photo size
            size = struct.unpack('!I', client.recv(4))[0]
            # Read photo data
            photo_data = client.recv(size)
            with open('received_photo.jpg', 'wb') as f:
                f.write(photo_data)
            print(f"Photo saved: {size} bytes")
    except Exception as e:
        print(f"Error: {e}")
        break

client.close()
server.close()
```

## Security Considerations

⚠️ **WARNING**: This application is for educational purposes only. 

- The application transmits data over unencrypted sockets
- No authentication mechanism is implemented
- Camera and internet permissions provide significant access
- Should not be used in production without proper security measures

### Recommended Security Enhancements:
1. Implement TLS/SSL encryption for socket communication
2. Add authentication and authorization mechanisms
3. Validate and sanitize all incoming commands
4. Implement certificate pinning
5. Add rate limiting and input validation
6. Use secure storage for sensitive configuration

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
│           │   └── SocketService.java
│           └── res/
│               ├── layout/
│               │   └── activity_main.xml
│               └── values/
│                   └── strings.xml
├── build.gradle
├── settings.gradle
└── README.md
```

## License
This project is provided as-is for educational purposes.

## Disclaimer
This software is provided for educational and research purposes only. Users are responsible for ensuring compliance with all applicable laws and regulations. The authors are not responsible for any misuse of this software.
