package com.jojjer.companiondevicepairing;

import java.io.File;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;

import java.util.List;
import java.util.UUID;
import java.util.Queue;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.LinkedList;

import android.util.Log;

import android.content.Context;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;

class WriteRequest {
    BluetoothGattCharacteristic characteristic;
    byte[] data;
    int writeType;

    WriteRequest(BluetoothGattCharacteristic characteristic, byte[] data, int writeType) {
        this.characteristic = characteristic;
        this.data = data;
        this.writeType = writeType;
    }
}

public final class BleManager {
    private static final String noConnectionStr = "No connection to device!";
    private static final String logTag = "BleManager";

    private static BleManager instance = null;

    private BluetoothGatt gatt;
    private Context ctx;

    private boolean isConnected = false;
    private String sensorData;

    private List foundServiceUUIDs = new ArrayList<String>();

    Queue<WriteRequest> writeQueue = new LinkedList<>();
    boolean isWriting = false;
    boolean isReading = false;

    private final Object writeLock = new Object();
    private final Object readLock = new Object();
    private volatile boolean writeCompleted = false;
    private volatile boolean readCompleted = false;

    private BleManager() {}

    public static synchronized BleManager getInstance() {
        if (instance == null) {
            instance = new BleManager();
        }
        return instance;
    }

    public interface BleCallback {
        void firmwareProgressCallback(double progress);
        void readCharacteristicCallback(String serviceUuid, String characteristicUuid, int value);
    }

    private BleCallback bleCallback;

    public void registerBleCallbacks(BleCallback cb) {
        this.bleCallback = cb;
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            isConnected = (newState == BluetoothProfile.STATE_CONNECTED);
            if (isConnected) {
                Log.d(logTag, "Connected to GATT server.");
                gatt.discoverServices();
            } else {
                Log.d(logTag, "Disconnected from GATT server.");
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                List<BluetoothGattService> services = gatt.getServices();
                for (BluetoothGattService s : services) {
                    if (!foundServiceUUIDs.contains(s)) {
                        foundServiceUUIDs.add(s.getUuid().toString());
                        if (s.getUuid().toString().equals("12345000-14e1-41b1-b3b6-6bb8b548ee82")) {
                            for (BluetoothGattCharacteristic c : s.getCharacteristics()) {
                                Log.d(logTag, "Characteristic discovered: " + c.getUuid().toString());
                            }
                        }
                    }
                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic,
                                            byte[] value) {
            Log.d(logTag, "Sensor data received: " + value[0]);
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         byte[] value,
                                         int status) {
            synchronized (readLock) {
                readCompleted = true;
                isReading = false;
                readLock.notify();
            }
            if (status == BluetoothGatt.GATT_SUCCESS) {
                int data = bytesToInt(value);
                Log.d(logTag, "Data: " + data);
                if (bleCallback != null) {
                    bleCallback.readCharacteristicCallback(characteristic.getService().getUuid().toString(), characteristic.getUuid().toString(), data);
                }
                else {
                    Log.d(logTag, "BleCallback is null, cannot send data");
                }
            } else {
                Log.d(logTag, "Failed to read characteristic");
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                                          BluetoothGattCharacteristic characteristic,
                                          int status) {
            synchronized (writeLock) {
                writeCompleted = true;
                writeLock.notify();
            }

            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(logTag, "Write failed to: " + characteristic.getUuid() + ", status: " + status);
            }
            isWriting = false;
            processNextWrite();
        }
    };

    public static int bytesToInt(byte[] bytes) {
        return ((bytes[0] & 0xFF) << 24) |
               ((bytes[1] & 0xFF) << 16) |
               ((bytes[2] & 0xFF) << 8)  |
               ((bytes[3] & 0xFF));
    }

    public void initialize(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    private boolean isBluetoothOn() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null) {
            return bluetoothAdapter.getState() == BluetoothAdapter.STATE_ON ? true : false;
        }
        Log.d(logTag, "Adapter is not ok");
        return false;
    }

    public boolean connectToDevice(BluetoothDevice device) {
        if (!isBluetoothOn()) {
            Log.d(logTag, "Bluetooth is off");
            return false;
        }
        gatt = device.connectGatt(ctx, false, gattCallback);
        if (gatt != null) {
            return true;
        }
        Log.d(logTag, "Failed to connect to device: " + device.getName());
        return false;
    }

    public int getConnectionStatus() {
        if (!isBluetoothOn()) {
            return 0;
        }
        return isConnected ? 1 : 0;
    }

    public BluetoothGattCharacteristic getCharacteristic(String serviceUuid, String characteristicUuid) {
        if (gatt == null) {
            Log.d(logTag, "Gatt is null, cannot get characteristic");
            return null;
        }
        BluetoothGattService service = gatt.getService(UUID.fromString(serviceUuid));
        if (service != null) {
            BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString(characteristicUuid));
            if (characteristic != null) {
                return characteristic;
            } else {
                Log.d(logTag, "Characteristic not found: " + characteristicUuid);
            }
        } else {
            Log.d(logTag, "Service not found: " + serviceUuid);
        }
        return null;
    }

    public void subscribeToNotification(String serviceUuid, String characteristicUuid) {
        if (foundServiceUUIDs.contains(serviceUuid)) {
            if (gatt == null) {
                Log.d(logTag, "Gatt is null, cannot subscribe to notifications");
                return;
            }
            BluetoothGattService service = gatt.getService(UUID.fromString(serviceUuid));
            if (service != null) {
                BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString(characteristicUuid));
                if (characteristic == null) {
                    Log.d(logTag, "Characteristic not found: " + characteristicUuid);
                    return;
                }
                gatt.setCharacteristicNotification(characteristic, true);
            } else {
                Log.d(logTag, "Service not found: " + serviceUuid);
            }
        } else {
            Log.d(logTag, "UUID not found in discovered services: " + serviceUuid);
        }
    }

    private void processNextWrite() {
        if (writeQueue.isEmpty()) {
            isWriting = false;
            return;
        }

        isWriting = true;
        WriteRequest request = writeQueue.poll();
        int success = gatt.writeCharacteristic(request.characteristic, request.data, request.writeType);

        if (success != 0) {
            Log.e(logTag, "Failed to write to characteristic: " + request.characteristic.getUuid());
            isWriting = false;
            processNextWrite();
        }
    }

    private void waitForWriteComplete() {
        synchronized (writeLock) {
            while (!writeCompleted) {
                try {
                    writeLock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    Log.e(logTag, "Write wait interrupted: " + e.getMessage());
                }
            }
            writeCompleted = false;
        }
    }

    private void waitForReadComplete() {
        synchronized (readLock) {
            while (!readCompleted) {
                try {
                    readLock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    Log.e(logTag, "Read wait interrupted: " + e.getMessage());
                }
            }
            readCompleted = false;
        }
    }

    private byte[] loadFirmwareFile(File firmwareFile) {
        try (FileInputStream inputStream = new FileInputStream(firmwareFile)) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] temp = new byte[1024];
            int read;
            while ((read = inputStream.read(temp)) != -1) {
                buffer.write(temp, 0, read);
            }
            return buffer.toByteArray();
        } catch (IOException e) {
            Log.e(logTag, "Failed to load firmware file: " + e.getMessage());
            return null;
        }
    }

    public void updateFirmware(String serviceUuid, String characteristicUuid, File firmwareFile) {
        byte[] firmwareData = loadFirmwareFile(firmwareFile);
        if (firmwareData == null) {
            Log.e(logTag, "Failed to load firmware data");
            return;
        }

        if (gatt != null) {
            gatt.requestMtu(400);
        }

        BluetoothGattCharacteristic otaCharacteristic = getCharacteristic(serviceUuid, characteristicUuid);
        if (otaCharacteristic == null) {
            Log.e(logTag, "OTA characteristic not found");
            return;
        }

        int fileSize = firmwareData.length;
        byte[] otaStartCommand = new byte[5];
        otaStartCommand[0] = (byte) 0x7B; // Command to start OTA
        otaStartCommand[1] = (byte) ((fileSize >> 24) & 0xFF);
        otaStartCommand[2] = (byte) ((fileSize >> 16) & 0xFF);
        otaStartCommand[3] = (byte) ((fileSize >> 8) & 0xFF);
        otaStartCommand[4] = (byte) (fileSize & 0xFF);

        gatt.writeCharacteristic(otaCharacteristic, otaStartCommand, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);

        try {
            waitForWriteComplete();
        } catch (Exception e) {
            Log.e(logTag, "Error waiting for write complete: " + e.getMessage());
            return;
        }

        int offset = 0;
        int packetIndex = 0;

        while (offset < firmwareData.length) {
            int chunkSize = Math.min(400, firmwareData.length - offset);
            byte[] chunk = Arrays.copyOfRange(firmwareData, offset, offset + chunkSize);
            int success = gatt.writeCharacteristic(otaCharacteristic, chunk, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            if (success != BluetoothGatt.GATT_SUCCESS) {
                Log.e(logTag, "Failed to write chunk, error code: " + success);
                return;
            }

            // Wait for write to complete before sending next chunk
            waitForWriteComplete();
            offset += chunkSize;
            packetIndex++;

            if (bleCallback != null && packetIndex % 3 == 0) {
                bleCallback.firmwareProgressCallback((double) offset / fileSize);
            }

            if (packetIndex % 50 == 0) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    Log.e(logTag, "Sleep interrupted: " + e.getMessage());
                }
            }
        }
    }

    public boolean sendCommand(byte[] value, String serviceUuid, String characteristicUuid) {
        if (!isBluetoothOn()) {
            Log.d(logTag, noConnectionStr);
            return false;
        }
        if (foundServiceUUIDs.contains(serviceUuid)) {
            if (gatt == null) {
                Log.d(logTag, "Gatt is null, cannot send command");
                return false;
            }
            BluetoothGattService service = gatt.getService(UUID.fromString(serviceUuid));
            if (service != null) {
                BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString(characteristicUuid));
                if (characteristic == null) {
                    Log.d(logTag, "Characteristic not found: " + characteristicUuid);
                    return false;
                }
                writeQueue.add(new WriteRequest(characteristic, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT));
                if (!isWriting) {
                    processNextWrite();
                }
            } else {
                Log.d(logTag, "Service not found: " + serviceUuid);
                return false;
            }
        } else {
            Log.d(logTag, "UUID not found in discovered services: " + serviceUuid);
            return false;
        }

        return true;
    }

    public boolean readCharacteristic(String serviceUuid, String characteristicUuid) {
        if (!isBluetoothOn()) {
            Log.d(logTag, noConnectionStr);
            return false;
        }

        if (foundServiceUUIDs.contains(serviceUuid)) {
            if (gatt == null) {
                Log.d(logTag, "Gatt is null, cannot read characteristic");
                return false;
            }

            BluetoothGattService service = gatt.getService(UUID.fromString(serviceUuid));
            if (service == null) {
                Log.d(logTag, "Service not found: " + serviceUuid);
                return false;
            }

            BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString(characteristicUuid));
            if (characteristic == null) {
                Log.d(logTag, "Characteristic not found: " + characteristicUuid);
                return false;
            }

            if (isReading) {
                try {
                    waitForReadComplete();
                } catch (Exception e) {
                    Log.e(logTag, "Error waiting for read complete: " + e.getMessage());
                    return false;
                }
            }
            isReading = true;

            return gatt.readCharacteristic(characteristic);
        }
        else {
            Log.d(logTag, noConnectionStr);
            return false;
        }
    }
}
