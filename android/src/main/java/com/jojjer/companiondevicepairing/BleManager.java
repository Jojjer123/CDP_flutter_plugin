package com.jojjer.companiondevicepairing;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Time;
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
import android.content.SharedPreferences;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattCharacteristic;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public final class BleManager {
    private static final String noConnectionStr = "No connection to device!";
    private static final String logTag = "BleManager";

    private static BleManager instance = null;

    private BluetoothGatt gatt;
    private Context ctx;

    private boolean isConnected = false;
    private String sensorData;

    private List<String> foundServiceUUIDs = new ArrayList<String>();
    private Map<String, Object> deviceModel;

    private Queue<Runnable> gattOperationQueue = new LinkedList();
    private boolean gattOperationInProgress = false;

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

    private void enqueueGattOperation(Runnable op) {
        gattOperationQueue.add(op);
        if (!gattOperationInProgress) {
            nextGattOperation();
        }
    }

    private void nextGattOperation() {
        if (gattOperationQueue.isEmpty()) {
            gattOperationInProgress = false;
            return;
        }

        gattOperationInProgress = true;
        gattOperationQueue.poll().run();
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
            Log.d(logTag, "About to check for notification subscriptions");
            subscribeToNotifications();

            checkTimeSyncRequested();
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic,
                                            byte[] value) {
            Log.d(logTag, "--------------------Characteristic changed-------------------");
            Log.d(logTag, "Sensor data received");
            if (bleCallback != null) {
                bleCallback.characteristicChangedCallback(characteristic.getService().getUuid().toString(), characteristic.getUuid().toString(), value);
            }
            else {
                Log.d(logTag, "BleCallback is null, cannot send data to frontend");
                Log.d(logTag, "Characteristic that changed: " + characteristic.getUuid().toString());
                if (!deviceModel.isEmpty()) {
                    Log.d(logTag, "checking for available characteristic and possible response");
                    respondToChangedCharacteristic(characteristic.getService().getUuid().toString(),
                                                   characteristic.getUuid().toString(),
                                                   value);
                }
            }
            Log.d(logTag, "------------------Characteristic changed done----------------");
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         byte[] value,
                                         int status) {
            Log.d(logTag, "---------------------Characteristic read---------------------");
            Log.d(logTag, "Characteristic read: " + characteristic.getUuid()/*characteristic.toString()*/);
            Log.d(logTag, Arrays.toString(value));

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (value.length == 0) {
                    Log.d(logTag, "Read characteristic with no value");
                    return;
                }
                int data = bytesToInt(value);
                Log.d(logTag, "Data: " + data);
                if (!deviceModel.isEmpty()) {
                    Log.d(logTag, "checking for available characteristic and possible response");
                    respondToChangedCharacteristic(characteristic.getService().getUuid().toString(),
                                                   characteristic.getUuid().toString(),
                                                   value);
                }
                if (bleCallback != null) {
                    bleCallback.readCharacteristicCallback(characteristic.getService().getUuid().toString(), characteristic.getUuid().toString(), data);
                }
                else {
                    Log.d(logTag, "BleCallback is null, cannot send data to frontend");
                }
            } else {
                Log.d(logTag, "Failed to read characteristic");
            }

            gattOperationInProgress = false;
            nextGattOperation();

            Log.d(logTag, "-------------------Characteristic read done------------------");
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                                          BluetoothGattCharacteristic characteristic,
                                          int status) {
            Log.d(logTag, "---------------------Characteristic write--------------------");
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(logTag, "Write failed to: " + characteristic.getUuid() + ", status: " + status);
            }

            gattOperationInProgress = false;
            nextGattOperation();
            Log.d(logTag, "-------------------Characteristic write done-----------------");
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt,
                                      BluetoothGattDescriptor descriptor,
                                      int status) {
            Log.d(logTag, "-----------------------Descriptor write----------------------");
            gattOperationInProgress = false;
            nextGattOperation();
            Log.d(logTag, "--------------------Descriptor write done--------------------");
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
        Log.d(logTag, "-----------------Subscribing to notification-----------------");
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

                enqueueGattOperation(() -> gatt.writeDescriptor(descriptor));

                gatt.setCharacteristicNotification(characteristic, true);
            } else {
                Log.d(logTag, "Service not found: " + serviceUuid);
            }
        } else {
            Log.d(logTag, "UUID not found in discovered services: " + serviceUuid);
        }
        Log.d(logTag, "-------------------------------------------------------------");
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

        enqueueGattOperation(() -> gatt.writeCharacteristic(otaCharacteristic, otaStartCommand, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT));

        int offset = 0;
        int packetIndex = 0;

        // TODO: Rewrite the code for callbacks of percentage of packages transferred

        while (offset < firmwareData.length) {
            int chunkSize = Math.min(400, firmwareData.length - offset);
            byte[] chunk = Arrays.copyOfRange(firmwareData, offset, offset + chunkSize);
            enqueueGattOperation(() -> gatt.writeCharacteristic(otaCharacteristic, chunk, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT));

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
        Log.d(logTag, "-----------------------Sending command-----------------------");
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

            gatt.requestMtu(400);

            BluetoothGattService service = gatt.getService(UUID.fromString(serviceUuid));
            if (service != null) {
                Log.d(logTag, "Service found");
                BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString(characteristicUuid));
                if (characteristic == null) {
                    Log.d(logTag, "Characteristic not found: " + characteristicUuid);
                    return false;
                }
                Log.d(logTag, "Characteristic found");
                enqueueGattOperation(() -> gatt.writeCharacteristic(characteristic, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT));
            } else {
                Log.d(logTag, "Service not found: " + serviceUuid);
                return false;
            }
        } else {
            Log.d(logTag, "UUID not found in discovered services: " + serviceUuid);
            return false;
        }

        Log.d(logTag, "-------------------------------------------------------------");
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

            enqueueGattOperation(() -> gatt.readCharacteristic(characteristic));

            return true;
        }
        else {
            Log.d(logTag, noConnectionStr);
            return false;
        }
    }

    public void loadDeviceModel(Map<String, Object> model) {
        deviceModel = model;
    }

    public void loadDeviceModelFromStorage() {
        SharedPreferences sharedPref = this.ctx.getSharedPreferences("my_prefs", Context.MODE_PRIVATE);
        String jsonModel = sharedPref.getString("model", "{}");
        Gson gson = new Gson();
        Map<String, Object> model = gson.fromJson(jsonModel, new TypeToken<Map<String, Object>>(){}.getType());
        deviceModel = model;
        Log.d(logTag, "Device model loaded device model from shared preferences: " + model);
    }

    public void subscribeToNotifications() {
        Log.d(logTag, "Checking for characteristics that require subscribing to notifications");
        if (!modelKnown()) return;

        for (Map.Entry<String, Object> serviceEntry : ((Map<String, Object>)deviceModel.get("serviceMap")).entrySet()) {
            String serviceUuid = (String)((Map<String, Object>)serviceEntry.getValue()).get("uuid");
            List<Map<String, Object>> characteristics = (List<Map<String, Object>>)((Map<String, Object>)serviceEntry.getValue()).get("characteristics");
            for (Map<String, Object> c : characteristics) {
                Object notificationsEnabled = c.get("notifications");
                if (notificationsEnabled == null) continue;

                if ((boolean)notificationsEnabled) {
                    subscribeToNotification(serviceUuid, (String)c.get("uuid"));
                }
            }
        }
    }

    public void checkTimeSyncRequested() {
        Log.d(logTag, "Checking if time sync has been requested");
        if (!modelKnown()) return;

        for (Map.Entry<String, Object> serviceEntry : ((Map<String, Object>)deviceModel.get("serviceMap")).entrySet()) {
            String serviceUuid = (String)((Map<String, Object>)serviceEntry.getValue()).get("uuid");
            List<Map<String, Object>> characteristics = (List<Map<String, Object>>)((Map<String, Object>)serviceEntry.getValue()).get("characteristics");
            for (Map<String, Object> c : characteristics) {
                Object timeSync = c.get("timeSync");
                if (timeSync == null) continue;
                Log.d(logTag, "Found timeSync attriute");

                if ((boolean)timeSync) {
                    Log.d(logTag, "Requesting read of characteristic");

                    if(!readCharacteristic(serviceUuid, c.get("uuid").toString())) {
                        Log.e(logTag, "Failed reading characteristic: " + c.get("uuid").toString());
                    }
                }
            }
        }
    }

    public void respondToChangedCharacteristic(String service, String characteristic, byte[] value) {
        for (Map.Entry<String, Object> serviceEntry : ((Map<String, Object>)deviceModel.get("serviceMap")).entrySet()) {
            String serviceUuid = (String)((Map<String, Object>)serviceEntry.getValue()).get("uuid");
            if (service.equals(serviceUuid)) {
                // String serviceKey = serviceEntry.getKey().toString();
                List<Map<String, Object>> characteristics = (List<Map<String, Object>>)((Map<String, Object>)serviceEntry.getValue()).get("characteristics");
                for (Map<String, Object> c : characteristics) {
                    String characteristicUuid = c.get("uuid").toString();
                    if (characteristic.equals(characteristicUuid)) {
                        // TODO: Implement predefined responses/response templates to be registered from flutter app and used here
                        switch (value[0]) {
                            case 0x1:
                                Log.d(logTag, "Time sync requested");
                                syncTime(service, characteristic);
                                break;
                            case 0x2:
                                Log.d(logTag, "Notification");
                                break;
                            default:
                                Log.d(logTag, "Header id: " + value[0] + ", not implemented");
                                break;
                        }
                    }
                }
            }
        }
    }

    public void sendNotificationToWatch(Long ts, String pkg, String title, String text) {
        Log.d(logTag, "About to forward notification to watch");
        byte[] notificationPayload = new byte[300];

        String tsAsString = ts.toString();

        byte[] tsBytes = tsAsString.getBytes(StandardCharsets.UTF_8);
        byte[] pkgBytes = pkg.getBytes(StandardCharsets.UTF_8);
        byte[] titleBytes = title.getBytes(StandardCharsets.UTF_8);
        byte[] textBytes = text.getBytes(StandardCharsets.UTF_8);

        int tsLen = tsBytes.length;
        int pkgLen = Math.min(pkgBytes.length, 50);
        int titleLen = Math.min(titleBytes.length, 50);

        int remainingPayloadMax = 300 - 4 - tsLen - pkgLen - titleLen;
        int textLen = Math.min(textBytes.length, remainingPayloadMax);

        notificationPayload[0] = 3;
        notificationPayload[1] = (byte) tsLen;
        notificationPayload[2] = (byte) pkgLen;
        notificationPayload[3] = (byte) titleLen;
        notificationPayload[4] = (byte) textLen;

        int offset = 5;
        System.arraycopy(tsBytes, 0, notificationPayload, offset, tsLen);
        offset += tsLen;

        System.arraycopy(pkgBytes, 0, notificationPayload, offset, pkgLen);
        offset += pkgLen;

        System.arraycopy(titleBytes, 0, notificationPayload, offset, titleLen);
        offset += titleLen;

        System.arraycopy(textBytes, 0, notificationPayload, offset, textLen);

        // TODO: Replace the service and characteristic UUIDs with dynamic check for notification specific from device model
        sendCommand(notificationPayload,
                    "12342000-14e1-41b1-b3b6-6bb8b548ee82",
                    "12342001-14e1-41b1-b3b6-6bb8b548ee82");
    }

    private void syncTime(String service, String characteristic) {
        long currentTime = Math.ceilDiv(System.currentTimeMillis(), 1000);
        byte[] timeBytes = new byte[9];
        timeBytes[0] = 2;
        for (int i = 8; i >= 1; --i) {
          timeBytes[i] = (byte) (currentTime & 0xFF);
          currentTime >>= 8;
        }

        StringBuilder sb = new StringBuilder();
        for (byte b : timeBytes) {
          sb.append(b & 0xFF)
            .append(", ");
        }
        sendCommand(timeBytes, service, characteristic);
    }

    private boolean modelKnown() {
        Object modelName = deviceModel.get("modelName");
        if (modelName == null) {
            Log.d(logTag, "No model found");
            return false;
        }

        return !modelName.equals("Unknown");
    }
}
