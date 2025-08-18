package com.jojjer.companiondevicepairing;

import java.io.File;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Queue;
import java.util.Arrays;
import java.util.HashMap;
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
import android.bluetooth.BluetoothGattDescriptor;
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

    private List<String> foundServiceUUIDs = new ArrayList<String>();

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
        void readAllServicesCallback(Map<String, Object> serviceMap);
        void characteristicChangedCallback(String serviceUuid, String characteristicUuid, byte[] value);
    }

    private BleCallback bleCallback;

    public void registerBleCallbacks(BleCallback cb) {
        this.bleCallback = cb;
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.d(logTag, "Connection state change, new state: " + newState);
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
            Log.d(logTag, "Services discovered: " + gatt.toString());
            if (status == BluetoothGatt.GATT_SUCCESS) {
                List<BluetoothGattService> services = gatt.getServices();
                for (BluetoothGattService s : services) {
                    if (!foundServiceUUIDs.contains(s)) {
                        String uuidToAdd = UuidUtils.normalizeUuid(s.getUuid().toString());
                        foundServiceUUIDs.add(uuidToAdd);
                        Log.d(logTag, "Found service: " + s.getUuid().toString());
                        for (BluetoothGattCharacteristic c : s.getCharacteristics()) {
                            Log.d(logTag, "Characteristic discovered: " + c.getUuid().toString());
                        }
                    }
                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic,
                                            byte[] value) {
            Log.d(logTag, "Sensor data received");
            bleCallback.characteristicChangedCallback(characteristic.getService().getUuid().toString(), characteristic.getUuid().toString(), value);
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         byte[] value,
                                         int status) {
            Log.d(logTag, "Characteristic read: " + characteristic.getUuid()/*characteristic.toString()*/);
            Log.d(logTag, Arrays.toString(value));
            synchronized (readLock) {
                readCompleted = true;
                isReading = false;
                readLock.notify();
            }
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (value.length == 0) {
                    Log.d(logTag, "Read characteristic with no value");
                    return;
                }
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
        return ((bytes[3] & 0xFF) << 24) |
               ((bytes[2] & 0xFF) << 16) |
               ((bytes[1] & 0xFF) << 8)  |
               ((bytes[0] & 0xFF));
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
        Log.d(logTag, "Connecting to device: " + device.toString());
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

    public String getDeviceId() {
        BluetoothDevice bluetoothDevice = gatt.getDevice();
        if (bluetoothDevice != null) {
            return bluetoothDevice.getAddress();
        }
        return "";
    }

    public void getAllServices() {
        List<BleServiceInfo> serviceList = new ArrayList<>();

        for (String serviceUuid : foundServiceUUIDs) {
            BleServiceInfo serviceInfo = new BleServiceInfo(serviceUuid);

            BluetoothGattService gattService = gatt.getService(UUID.fromString(serviceUuid));
            if (gattService != null) {
                List<BluetoothGattCharacteristic> characteristics = gattService.getCharacteristics();
                for (BluetoothGattCharacteristic c : characteristics) {
                    serviceInfo.addCharacteristic(c.getUuid().toString());
                }
            } else {
                Log.d(logTag, "Service not found: " + serviceUuid);
            }

            serviceList.add(serviceInfo);
        }

        Map<String, Object> deviceData = new HashMap<>();
        deviceData.put("deviceId", getDeviceId());

        List<Map<String, Object>> servicesAsMaps = new ArrayList<>();
        for (BleServiceInfo service : serviceList) {
            servicesAsMaps.add(service.toMap());
        }
        deviceData.put("services", servicesAsMaps);

        bleCallback.readAllServicesCallback(deviceData);
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
            serviceUuid = UuidUtils.normalizeUuid(serviceUuid);
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
                    BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    gatt.writeDescriptor(descriptor);
                    gatt.setCharacteristicNotification(characteristic, true);
                } else {
                    Log.d(logTag, "Service not found: " + serviceUuid);
                }
            } else {
                Log.d(logTag, "UUID not found in discovered services: " + serviceUuid);
        }
    }

    private void processNextWrite() {
        Log.d(logTag, "Processing write");
        if (writeQueue.isEmpty()) {
            Log.d(logTag, "Write queue is empty");
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
        else {
            Log.d(logTag, "Successfully wrote to characteristic: " + request.characteristic.getUuid());
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
            Log.d(logTag, "Service UUID has been found for which the command should be sent");
            if (gatt == null) {
                Log.d(logTag, "Gatt is null, cannot send command");
                return false;
            }
            BluetoothGattService service = gatt.getService(UUID.fromString(serviceUuid));
            if (service != null) {
                Log.d(logTag, "Service found");
                BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString(characteristicUuid));
                if (characteristic == null) {
                    Log.d(logTag, "Characteristic not found: " + characteristicUuid);
                    return false;
                }
                Log.d(logTag, "Characteristic found");
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
