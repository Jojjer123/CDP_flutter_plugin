package com.jojjer.companiondevicepairing;

import java.util.List;
import java.util.UUID;
import java.util.ArrayList;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
// import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

import java.lang.Math;

import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.ComponentName;

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
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattCharacteristic;

import android.util.Log;

public class CDPService extends CompanionDeviceService {
    static final int BOND_NONE = 10;
    static final int BOND_BONDING = 11;
    static final int BOND_BONDED = 12;

    private BluetoothGatt gatt;
    private Long lastTimeSyncSent = 0L;
    private BleManager bleManager;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(CompanionDevicePairing.CDP_TAG, "CDPService created");
        bleManager = BleManager.getInstance();
        bleManager.initialize(getApplicationContext());
    }

    @Override
    public void onDeviceAppeared(AssociationInfo associationInfo) {
        AssociatedDevice device = associationInfo.getAssociatedDevice();
        Log.d(CompanionDevicePairing.CDP_TAG, "Device appeared: " + device);
        boolean ok = bleManager.connectToDevice(device.getBluetoothDevice());
        if (!ok) {
            Log.e(CompanionDevicePairing.CDP_TAG, "Failed connecting to device");
        }
    }

    @Override
    public void onDeviceDisappeared(AssociationInfo associationInfo) {
        Log.d(CompanionDevicePairing.CDP_TAG, "The device that disappeared: " + associationInfo);
    }
}
