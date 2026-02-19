package com.example.myfirstapk;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.ByteArrayOutputStream;

public class MainActivity extends AppCompatActivity {
    
    private static final int CAMERA_PERMISSION_CODE = 100;
    private static final int CAMERA_REQUEST_CODE = 101;
    
    private EditText serverIpInput;
    private EditText serverPortInput;
    private Button startServiceButton;
    private Button stopServiceButton;
    private Button capturePhotoButton;
    private ImageView photoPreview;
    private TextView statusText;
    
    private Bitmap capturedPhoto;
    private boolean isServiceRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        initializeViews();
        setupListeners();
    }
    
    private void initializeViews() {
        serverIpInput = findViewById(R.id.serverIpInput);
        serverPortInput = findViewById(R.id.serverPortInput);
        startServiceButton = findViewById(R.id.startServiceButton);
        stopServiceButton = findViewById(R.id.stopServiceButton);
        capturePhotoButton = findViewById(R.id.capturePhotoButton);
        photoPreview = findViewById(R.id.photoPreview);
        statusText = findViewById(R.id.statusText);
        
        // Set default values
        serverIpInput.setHint("e.g., 192.168.1.100");
        serverPortInput.setText("8080");
        
        updateServiceButtons();
    }
    
    private void setupListeners() {
        startServiceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startSocketService();
            }
        });
        
        stopServiceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopSocketService();
            }
        });
        
        capturePhotoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkCameraPermissionAndCapture();
            }
        });
    }
    
    private void startSocketService() {
        String serverIp = serverIpInput.getText().toString();
        String serverPort = serverPortInput.getText().toString();
        
        if (serverIp.isEmpty() || serverPort.isEmpty()) {
            Toast.makeText(this, "Please enter server IP and port", Toast.LENGTH_SHORT).show();
            return;
        }
        
        Intent serviceIntent = new Intent(this, SocketService.class);
        serviceIntent.putExtra("SERVER_IP", serverIp);
        serviceIntent.putExtra("SERVER_PORT", Integer.parseInt(serverPort));
        
        startService(serviceIntent);
        
        isServiceRunning = true;
        updateServiceButtons();
        statusText.setText("Service Status: Running");
        Toast.makeText(this, "Socket service started", Toast.LENGTH_SHORT).show();
    }
    
    private void stopSocketService() {
        Intent serviceIntent = new Intent(this, SocketService.class);
        stopService(serviceIntent);
        
        isServiceRunning = false;
        updateServiceButtons();
        statusText.setText("Service Status: Stopped");
        Toast.makeText(this, "Socket service stopped", Toast.LENGTH_SHORT).show();
    }
    
    private void updateServiceButtons() {
        startServiceButton.setEnabled(!isServiceRunning);
        stopServiceButton.setEnabled(isServiceRunning);
    }
    
    private void checkCameraPermissionAndCapture() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, 
                new String[]{Manifest.permission.CAMERA}, 
                CAMERA_PERMISSION_CODE);
        } else {
            capturePhoto();
        }
    }
    
    private void capturePhoto() {
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (cameraIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(cameraIntent, CAMERA_REQUEST_CODE);
        } else {
            Toast.makeText(this, "Camera not available", Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
                                          @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                capturePhoto();
            } else {
                Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == CAMERA_REQUEST_CODE && resultCode == RESULT_OK) {
            if (data != null && data.getExtras() != null) {
                capturedPhoto = (Bitmap) data.getExtras().get("data");
                photoPreview.setImageBitmap(capturedPhoto);
                
                // Upload photo if service is running
                if (isServiceRunning && capturedPhoto != null) {
                    uploadPhoto();
                }
            }
        }
    }
    
    private void uploadPhoto() {
        if (capturedPhoto == null) {
            Toast.makeText(this, "No photo captured", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Convert bitmap to byte array
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        capturedPhoto.compress(Bitmap.CompressFormat.JPEG, 80, stream);
        byte[] photoData = stream.toByteArray();
        
        // Send photo data to service for upload
        Intent uploadIntent = new Intent(this, SocketService.class);
        uploadIntent.putExtra("UPLOAD_PHOTO", photoData);
        startService(uploadIntent);
        
        Toast.makeText(this, "Photo uploaded: " + photoData.length + " bytes", Toast.LENGTH_SHORT).show();
    }
}
