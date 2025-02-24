// File: app/src/main/java/com/example/midicontroller/MainActivity.java
package com.example.midicontroller;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.midi.MidiDevice;
import android.media.midi.MidiDeviceInfo;
import android.media.midi.MidiManager;
import android.media.midi.MidiOutputPort;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MidiController";
    private static final int PERMISSION_REQUEST_CODE = 101;

    // MIDI connection components
    private MidiManager midiManager;
    private MidiDevice midiDevice;
    private MidiOutputPort outputPort;
    private boolean isConnected = false;

    // UI Components
    private Button connectButton;
    private Button beepButton;
    private Button flashButton;
    private TextView statusText;

    // Packet info
    private static final byte SYNC_BYTE = 0x62; // MIDI_API_SYNC
    private static final short MIDI_API_FOOTER = 0x2323;
    private AtomicInteger sequenceNumber = new AtomicInteger(0);

    // Command Opcodes
    private static final int MIDI_API_OPCODE_BUZZER_BEEP = 0x0100;
    private static final int MIDI_API_OPCODE_LEDS_FLASH = 0x0202;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize UI components
        connectButton = findViewById(R.id.connect_button);
        beepButton = findViewById(R.id.beep_button);
        flashButton = findViewById(R.id.flash_button);
        statusText = findViewById(R.id.status_text);

        // Initially disable command buttons until connection is established
        beepButton.setEnabled(false);
        flashButton.setEnabled(false);

        // Check and request MIDI permissions
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) 
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, 
                        new String[]{Manifest.permission.BLUETOOTH}, PERMISSION_REQUEST_CODE);
            } else {
                initializeMidi();
            }
        } else {
            initializeMidi();
        }

        // Set up button click listeners
        connectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isConnected) {
                    scanAndConnectToMidiDevice();
                } else {
                    disconnectMidi();
                }
            }
        });

        beepButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendBeepCommand();
            }
        });

        flashButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendFlashCommand();
            }
        });
    }

    private void initializeMidi() {
        // Get the MIDI manager
        midiManager = (MidiManager) getSystemService(MIDI_SERVICE);
        if (midiManager == null) {
            showStatus("MIDI not supported on this device");
            return;
        }
        showStatus("Ready to connect to MIDI device");
    }

    private void scanAndConnectToMidiDevice() {
        if (midiManager == null) {
            showStatus("MIDI Manager is not available");
            return;
        }

        MidiDeviceInfo[] devices = midiManager.getDevices();
        if (devices.length == 0) {
            showStatus("No MIDI devices found");
            return;
        }

        // Look for a device with "Aila" in the name
        MidiDeviceInfo targetDevice = null;
        for (MidiDeviceInfo device : devices) {
            Bundle properties = device.getProperties();
            String manufacturer = properties.getString(MidiDeviceInfo.PROPERTY_MANUFACTURER);
            String product = properties.getString(MidiDeviceInfo.PROPERTY_PRODUCT);
            
            Log.d(TAG, "Found MIDI device: " + product + " from " + manufacturer);
            
            if ((product != null && product.contains("Aila")) || 
                (manufacturer != null && manufacturer.contains("Aila"))) {
                targetDevice = device;
                break;
            }
        }

        if (targetDevice == null) {
            // No specific device found, try the first one
            if (devices.length > 0) {
                targetDevice = devices[0];
                Log.d(TAG, "No Aila device found, using first available device");
            } else {
                showStatus("No MIDI devices found");
                return;
            }
        }

        // Open the MIDI device
        final MidiDeviceInfo finalTargetDevice = targetDevice;
        midiManager.openDevice(finalTargetDevice, new MidiManager.OnDeviceOpenedListener() {
            @Override
            public void onDeviceOpened(MidiDevice device) {
                if (device == null) {
                    showStatusOnUiThread("Failed to open MIDI device");
                    return;
                }

                midiDevice = device;
                // Get the first output port (we are sending to the device)
                int portCount = finalTargetDevice.getOutputPortCount();
                if (portCount > 0) {
                    outputPort = device.openOutputPort(0);
                    if (outputPort != null) {
                        isConnected = true;
                        showStatusOnUiThread("Connected to MIDI device");
                        updateUIControlsOnUiThread();
                    } else {
                        showStatusOnUiThread("Failed to open output port");
                    }
                } else {
                    showStatusOnUiThread("No output ports available");
                }
            }
        }, new Handler(Looper.getMainLooper()));
    }

    private void disconnectMidi() {
        if (outputPort != null) {
            try {
                outputPort.close();
                outputPort = null;
            } catch (IOException e) {
                Log.e(TAG, "Error closing output port", e);
            }
        }

        if (midiDevice != null) {
            try {
                midiDevice.close();
                midiDevice = null;
            } catch (IOException e) {
                Log.e(TAG, "Error closing MIDI device", e);
            }
        }

        isConnected = false;
        showStatus("Disconnected from MIDI device");
        updateUIControls();
    }

    private void sendBeepCommand() {
        if (!isConnected || outputPort == null) {
            showStatus("Not connected to a MIDI device");
            return;
        }

        byte[] packet = createBeepPacket();
        if (packet != null) {
            byte[] formattedPacket = formatMidiPacket(packet);
            sendMidiMessage(formattedPacket);
            showStatus("Beep command sent");
        } else {
            showStatus("Failed to create beep command");
        }
    }

    private void sendFlashCommand() {
        if (!isConnected || outputPort == null) {
            showStatus("Not connected to a MIDI device");
            return;
        }

        byte[] packet = createFlashPacket();
        if (packet != null) {
            byte[] formattedPacket = formatMidiPacket(packet);
            sendMidiMessage(formattedPacket);
            showStatus("Flash command sent");
        } else {
            showStatus("Failed to create flash command");
        }
    }

    private byte[] createBeepPacket() {
        // Create a buzzer beep command packet
        try {
            // Structure size: header(10) + duration(2) + footer(2) = 14 bytes
            byte[] packet = new byte[14];
            int offset = 0;

            // Sync byte
            packet[offset++] = SYNC_BYTE;

            // CRC (will be calculated later)
            for (int i = 0; i < 4; i++) {
                packet[offset++] = 0; // Placeholder for CRC
            }

            // Sequence number (16-bit)
            int seq = sequenceNumber.incrementAndGet();
            packet[offset++] = (byte) (seq >> 8);
            packet[offset++] = (byte) (seq & 0xFF);

            // Op code (16-bit) - MIDI_API_OPCODE_BUZZER_BEEP
            packet[offset++] = (byte) (MIDI_API_OPCODE_BUZZER_BEEP >> 8);
            packet[offset++] = (byte) (MIDI_API_OPCODE_BUZZER_BEEP & 0xFF);

            // Payload length (8-bit) - 2 bytes for duration
            packet[offset++] = 2;

            // Duration (16-bit) - 0 for default
            packet[offset++] = 0;
            packet[offset++] = 0;

            // Footer (16-bit)
            packet[offset++] = (byte) (MIDI_API_FOOTER >> 8);
            packet[offset++] = (byte) (MIDI_API_FOOTER & 0xFF);

            // Calculate CRC
            int crc = calculateCRC(packet, 5, packet.length - 5);
            packet[1] = (byte) ((crc >> 24) & 0xFF);
            packet[2] = (byte) ((crc >> 16) & 0xFF);
            packet[3] = (byte) ((crc >> 8) & 0xFF);
            packet[4] = (byte) (crc & 0xFF);

            return packet;
        } catch (Exception e) {
            Log.e(TAG, "Error creating beep packet", e);
            return null;
        }
    }

    private byte[] createFlashPacket() {
        // Create an LED flash command packet
        try {
            // Structure size: header(10) + duration(2) + footer(2) = 14 bytes
            byte[] packet = new byte[14];
            int offset = 0;

            // Sync byte
            packet[offset++] = SYNC_BYTE;

            // CRC (will be calculated later)
            for (int i = 0; i < 4; i++) {
                packet[offset++] = 0; // Placeholder for CRC
            }

            // Sequence number (16-bit)
            int seq = sequenceNumber.incrementAndGet();
            packet[offset++] = (byte) (seq >> 8);
            packet[offset++] = (byte) (seq & 0xFF);

            // Op code (16-bit) - MIDI_API_OPCODE_LEDS_FLASH
            packet[offset++] = (byte) (MIDI_API_OPCODE_LEDS_FLASH >> 8);
            packet[offset++] = (byte) (MIDI_API_OPCODE_LEDS_FLASH & 0xFF);

            // Payload length (8-bit) - 2 bytes for duration
            packet[offset++] = 2;

            // Duration (16-bit) - 0 for default
            packet[offset++] = 0;
            packet[offset++] = 0;

            // Footer (16-bit)
            packet[offset++] = (byte) (MIDI_API_FOOTER >> 8);
            packet[offset++] = (byte) (MIDI_API_FOOTER & 0xFF);

            // Calculate CRC
            int crc = calculateCRC(packet, 5, packet.length - 5);
            packet[1] = (byte) ((crc >> 24) & 0xFF);
            packet[2] = (byte) ((crc >> 16) & 0xFF);
            packet[3] = (byte) ((crc >> 8) & 0xFF);
            packet[4] = (byte) (crc & 0xFF);

            return packet;
        } catch (Exception e) {
            Log.e(TAG, "Error creating flash packet", e);
            return null;
        }
    }

    private byte[] formatMidiPacket(byte[] rawPacket) {
        // Convert into MIDI SysEx format
        // In SysEx, data is sent in chunks where the first byte indicates the status
        // 0x04: Message start or continue (all 3 bytes are valid)
        // 0x05: Message end (only 1st byte is valid)
        // 0x06: Message end (only 1st & 2nd bytes are valid)
        // 0x07: Message end (all 3 bytes are valid)

        int midiPacketLength = (int) Math.ceil(rawPacket.length / 3.0) * 4;
        byte[] midiPacket = new byte[midiPacketLength];
        
        int inIdx = 0;
        int outIdx = 0;
        
        while (inIdx < rawPacket.length) {
            int bytesLeft = rawPacket.length - inIdx;
            
            if (bytesLeft >= 3) {
                // Full 3-byte chunk
                midiPacket[outIdx++] = 0x04; // All 3 bytes valid
                midiPacket[outIdx++] = rawPacket[inIdx++];
                midiPacket[outIdx++] = rawPacket[inIdx++];
                midiPacket[outIdx++] = rawPacket[inIdx++];
            } else if (bytesLeft == 2) {
                // 2 bytes left
                midiPacket[outIdx++] = 0x06; // First 2 bytes valid
                midiPacket[outIdx++] = rawPacket[inIdx++];
                midiPacket[outIdx++] = rawPacket[inIdx++];
                midiPacket[outIdx++] = 0; // Padding
            } else if (bytesLeft == 1) {
                // 1 byte left
                midiPacket[outIdx++] = 0x05; // First byte valid
                midiPacket[outIdx++] = rawPacket[inIdx++];
                midiPacket[outIdx++] = 0; // Padding
                midiPacket[outIdx++] = 0; // Padding
            }
        }
        
        return midiPacket;
    }

    private void sendMidiMessage(byte[] data) {
        if (outputPort != null) {
            try {
                outputPort.send(data, 0, data.length);
                Log.d(TAG, "Sent MIDI message, length: " + data.length);
            } catch (Exception e) {
                Log.e(TAG, "Error sending MIDI message", e);
                showStatus("Error sending MIDI command: " + e.getMessage());
            }
        } else {
            showStatus("Output port is not open");
        }
    }

    private int calculateCRC(byte[] data, int offset, int length) {
        // Simple implementation of CRC32 calculation
        // Note: In production, this should be replaced with the exact same CRC algorithm 
        // as used in the iOS code
        int crc = 0xFFFFFFFF;
        for (int i = offset; i < offset + length; i++) {
            crc ^= (data[i] & 0xFF) << 24;
            for (int j = 0; j < 8; j++) {
                if ((crc & 0x80000000) != 0) {
                    crc = (crc << 1) ^ 0x04C11DB7; // Ethernet polynomial
                } else {
                    crc <<= 1;
                }
            }
        }
        return crc;
    }

    private void showStatus(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                statusText.setText(message);
                Log.d(TAG, message);
            }
        });
    }

    private void showStatusOnUiThread(final String message) {
        statusText.setText(message);
        Log.d(TAG, message);
    }

    private void updateUIControls() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (isConnected) {
                    connectButton.setText("Disconnect");
                    beepButton.setEnabled(true);
                    flashButton.setEnabled(true);
                } else {
                    connectButton.setText("Connect");
                    beepButton.setEnabled(false);
                    flashButton.setEnabled(false);
                }
            }
        });
    }

    private void updateUIControlsOnUiThread() {
        if (isConnected) {
            connectButton.setText("Disconnect");
            beepButton.setEnabled(true);
            flashButton.setEnabled(true);
        } else {
            connectButton.setText("Connect");
            beepButton.setEnabled(false);
            flashButton.setEnabled(false);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initializeMidi();
            } else {
                Toast.makeText(this, "Permission denied, MIDI functionality may be limited", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        disconnectMidi();
        super.onDestroy();
    }
}
