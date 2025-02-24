# Android MIDI Controller

This is a lightweight Android application for communicating with Aila MIDI-enabled devices.

## Overview

This application demonstrates establishing a MIDI connection with an Aila kiosk device and sending simple commands:
- Beep - Triggers a beep sound on the device
- Flash - Flashes the LEDs on the device

## Requirements

- Android 6.0 (API 23) or higher
- A device with MIDI support
- An Aila kiosk device

## Implementation Details

The app uses the Android MIDI API to establish a connection with the Aila device. The communication protocol follows the custom MIDI protocol defined in the Aila SDK:

1. The app searches for available MIDI devices
2. When a device is found, it attempts to connect to it
3. When connected, the app enables buttons to send beep and flash commands
4. Commands are sent using the defined protocol format

## Custom MIDI Protocol

The app implements the custom MIDI protocol used by the Aila devices:

- Commands are sent with headers, payloads, and footers
- CRC32 is used for error checking
- Specific opcodes are used for different commands (beep, flash, etc.)
- SysEx MIDI messages are used for transport

## Getting Started

1. Clone this repository
2. Open the project in Android Studio
3. Connect your Android device 
4. Build and run the application
5. Click the "Connect" button to search for Aila MIDI devices
6. Once connected, use the "Beep" and "Flash" buttons to control the device

## Customization

You can extend this application to support additional Aila commands by:

1. Adding new opcode definitions
2. Implementing the packet structure for the command
3. Adding UI elements to trigger the command

## Troubleshooting

If you have trouble connecting to the device:

1. Ensure Bluetooth is enabled on your Android device
2. Check that the Aila device is powered on and in range
3. Verify that the device is advertising itself as a MIDI device
4. Check logcat for detailed connection information

## Note

This is a proof-of-concept application and may require adjustments to work with specific Aila device configurations.
