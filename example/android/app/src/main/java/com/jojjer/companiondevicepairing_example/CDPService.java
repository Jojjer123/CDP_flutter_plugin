package example.android.app.src.main.java.com.jojjer.companiondevicepairing_example;

import java.util.List;
import java.util.UUID;
import java.util.ArrayList;

import android.os.Build;
import android.os.Bundle;
import android.os.Binder;
import android.os.IBinder;

import android.companion.AssociationInfo;
import android.companion.AssociatedDevice;
import android.companion.CompanionDeviceService;

// import android.bluetooth.le.ScanResult;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothGattCharacteristic;

import android.util.Log;

import android.content.Intent;

public class CDPService extends CompanionDeviceService {
    static final int BOND_NONE = 10;
    static final int BOND_BONDING = 11;
    static final int BOND_BONDED = 12;

    private BluetoothGatt gatt;

    // private final IBinder binder = new LocalBinder();

    // public class LocalBinder extends Binder {
    //     CDPService getService() {
    //         return CDPService.this;
    //     }
    // }

    // @Override
    // public IBinder onBind(Intent intent) {
    //     return binder;
    // }

    // List<String> readCharacteristicsUUIDS = new ArrayList<String>();
    // List<String> writeCharacteristicsUUIDS = new ArrayList<String>();
    // String batteryServiceUUID;

    // boolean foundServices = false;

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            // Log.d("Android", "Hello from connection state change");
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                boolean started = gatt.discoverServices();
                // Log.d("Android", "Hello from gatt.discoverServices started: " + started);
            }
            else {
                Log.d("CDP", "Disconnected from GATT server");
            }
        }

        // @Override
        // public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        //     // if (foundServices) {
        //     //     return;
        //     // }
        //     // for (BluetoothGattService s : gatt.getServices()) {
        //     //     // Log.d("Android", "Hello from service: " + s.getUuid().toString());
        //     //     if (s.getUuid().toString().equals("0000180f-0000-1000-8000-00805f9b34fb")) {
        //     //         // Battery service: 0000180f-0000-1000-8000-00805f9b34fb
        //     //         batteryServiceUUID = s.getUuid().toString();
        //     //         // Log.d("Android", "Hello from battery service: " + s.getUuid());
        //     //     }
        //     //     for (BluetoothGattCharacteristic characteristic : s.getCharacteristics()) {
        //     //         // Log.d("Android", "Hello from service: " + s.getUuid().toString() + ", characteristic: " + characteristic.getUuid().toString());
        //     //         if (s.getUuid().toString().equals("12340000-14e1-41b1-b3b6-6bb8b548ee82") && characteristic.getUuid().toString().equals("12340001-14e1-41b1-b3b6-6bb8b548ee82")) {
        //     //             // Read characteristic: 12340001-14e1-41b1-b3b6-6bb8b548ee82, on service: 12340000-14e1-41b1-b3b6-6bb8b548ee82
        //     //             // Log.d("Android", "Hello from read characteristic added to list: " + characteristic.getUuid());
        //     //             readCharacteristicsUUIDS.add(characteristic.getUuid().toString());
        //     //         }
        //     //         if (s.getUuid().toString().equals("12345000-14e1-41b1-b3b6-6bb8b548ee82") && characteristic.getUuid().toString().equals("12345001-14e1-41b1-b3b6-6bb8b548ee82")) {
        //     //             // Write characteristic: 12345001-14e1-41b1-b3b6-6bb8b548ee82, on service: 12345000-14e1-41b1-b3b6-6bb8b548ee82
        //     //             // Log.d("Android", "Hello from write characteristic added to list: " + characteristic.getUuid());
        //     //             writeCharacteristicsUUIDS.add(characteristic.getUuid().toString());
        //     //         }
        //     //     }
        //     // }

        //     // if (batteryServiceUUID.length() != 0 && !readCharacteristicsUUIDS.isEmpty() && !writeCharacteristicsUUIDS.isEmpty()) {
        //     //     foundServices = true;
        //     // }
        // }
    };

    @Override
    public void onDeviceAppeared(AssociationInfo associationInfo) {
        // Log.d("Android", "Hello from the device that appeared: " + associationInfo);
        AssociatedDevice a_device = associationInfo.getAssociatedDevice();
        // if (a_device.getBleDevice() != null) {
        //     Log.d("Android", "Hello from the ble device");
        // }
        // else if (a_device.getBluetoothDevice() != null) {
        //     Log.d("Android", "Hello from the bluetooth device");
        // }
        // else if (a_device.getWifiDevice() != null) {
        //     Log.d("Android", "Hello from the wifi device");
        // }

        if (a_device != null) {
            // Log.d("Android", "Hello from the associated device: " + a_device.toString());
            // ScanResult scan_result = a_device.getBleDevice();
            BluetoothDevice bl_device = a_device.getBluetoothDevice();
            if (bl_device.getBondState() == BOND_NONE) {
                // TODO: Bond with the device and connect to it after having bonded
            }
            else if (bl_device.getBondState() == BOND_BONDING) {
                // TODO: Wait until bonded before connecting
            }
            else if (bl_device.getBondState() == BOND_BONDED) {
                if (Build.VERSION.SDK_INT >= 23) {
                    gatt = bl_device.connectGatt(this, true, gattCallback, BluetoothDevice.TRANSPORT_LE);
                }
                else {
                    gatt = bl_device.connectGatt(this, true, gattCallback);
                }
            }
        }
        else {
            // Log.d("Android", "Hello from an associated device (null)");
            // Log.d("Android", "Hello from AssociationInfo: " + associationInfo.getDisplayName());
        }
        // TODO: Start the background functionality (preferably a callback to some function that the user of this plugin can set)
    }

    @Override
    public void onDeviceDisappeared(AssociationInfo associationInfo) {
        Log.d("CDP", "The device that disappeared: " + associationInfo);
    }

    // public void readCharacteristic() {
    //     Log.d("CDP", "Hello, Reading characteristic...");
    // }
}
