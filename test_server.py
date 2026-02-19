#!/usr/bin/env python3
"""
Simple test server for MyFirstAPK Android application.
This server accepts socket connections and handles C2 commands and photo uploads.

Usage:
    python test_server.py [port]
    
Default port is 8080.
"""

import socket
import struct
import sys
import time
from datetime import datetime

def read_utf8_string(sock):
    """Read a UTF-8 string with Java's DataOutputStream format (2-byte length prefix)"""
    try:
        length_bytes = sock.recv(2)
        if len(length_bytes) < 2:
            return None
        length = struct.unpack('!H', length_bytes)[0]
        data = b''
        while len(data) < length:
            chunk = sock.recv(length - len(data))
            if not chunk:
                return None
            data += chunk
        return data.decode('utf-8')
    except Exception as e:
        print(f"Error reading string: {e}")
        return None

def send_utf8_string(sock, msg):
    """Send a UTF-8 string with Java's DataInputStream format (2-byte length prefix)"""
    try:
        encoded = msg.encode('utf-8')
        length = len(encoded)
        sock.sendall(struct.pack('!H', length))
        sock.sendall(encoded)
        return True
    except Exception as e:
        print(f"Error sending string: {e}")
        return False

def handle_client(client_socket, addr):
    """Handle communication with a connected client"""
    print(f"\n[{datetime.now().strftime('%H:%M:%S')}] Client connected from {addr}")
    
    photo_count = 0
    
    try:
        while True:
            # Read message from client
            msg = read_utf8_string(client_socket)
            if not msg:
                print(f"[{datetime.now().strftime('%H:%M:%S')}] Connection closed")
                break
            
            print(f"[{datetime.now().strftime('%H:%M:%S')}] Received: {msg}")
            
            if msg == "CLIENT_CONNECTED":
                print("  -> Client successfully connected")
                # Optionally send a welcome command
                # send_utf8_string(client_socket, "STATUS")
                
            elif msg == "PHOTO_UPLOAD":
                # Read photo size (4 bytes, big-endian integer)
                size_bytes = client_socket.recv(4)
                if len(size_bytes) < 4:
                    print("  -> Error: Could not read photo size")
                    break
                    
                size = struct.unpack('!I', size_bytes)[0]
                print(f"  -> Receiving photo: {size} bytes")
                
                # Read photo data in chunks
                photo_data = b''
                remaining = size
                while remaining > 0:
                    chunk_size = min(4096, remaining)
                    chunk = client_socket.recv(chunk_size)
                    if not chunk:
                        print("  -> Error: Connection lost during photo transfer")
                        break
                    photo_data += chunk
                    remaining -= len(chunk)
                    
                    # Progress indicator
                    progress = ((size - remaining) / size) * 100
                    print(f"  -> Progress: {progress:.1f}%", end='\r')
                
                if len(photo_data) == size:
                    photo_count += 1
                    timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
                    filename = f"photo_{timestamp}_{photo_count}.jpg"
                    
                    with open(filename, 'wb') as f:
                        f.write(photo_data)
                    
                    print(f"\n  -> Photo saved: {filename} ({len(photo_data)} bytes)")
                else:
                    print(f"\n  -> Error: Received {len(photo_data)} bytes, expected {size}")
                    
            elif msg == "PONG":
                print("  -> Received PONG response")
                
            elif msg == "STATUS_OK":
                print("  -> Client status is OK")
                
            elif msg.startswith("UNKNOWN_COMMAND"):
                print(f"  -> Client received unknown command: {msg}")
                
            else:
                print(f"  -> Unknown message type: {msg}")
    
    except Exception as e:
        print(f"[{datetime.now().strftime('%H:%M:%S')}] Error handling client: {e}")
    
    finally:
        client_socket.close()
        print(f"[{datetime.now().strftime('%H:%M:%S')}] Client disconnected")
        if photo_count > 0:
            print(f"  -> Total photos received: {photo_count}")

def main():
    # Get port from command line or use default
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8080
    
    # Create server socket
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    
    try:
        server.bind(('0.0.0.0', port))
        server.listen(5)
        print(f"=" * 60)
        print(f"MyFirstAPK Test Server")
        print(f"=" * 60)
        print(f"Listening on port {port}...")
        print(f"Waiting for connections from Android app...")
        print(f"Press Ctrl+C to stop")
        print(f"=" * 60)
        
        while True:
            client_socket, addr = server.accept()
            handle_client(client_socket, addr)
            
    except KeyboardInterrupt:
        print("\n\nServer stopped by user")
    except Exception as e:
        print(f"Server error: {e}")
    finally:
        server.close()
        print("Server closed")

if __name__ == "__main__":
    main()
