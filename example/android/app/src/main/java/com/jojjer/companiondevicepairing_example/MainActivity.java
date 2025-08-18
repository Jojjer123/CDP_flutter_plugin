package com.jojjer.companiondevicepairing_example;

import static android.companion.AssociationRequest.DEVICE_PROFILE_WATCH;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.companion.AssociationInfo;
import android.companion;
import android.companion.AssociationRequest;
import android.companion.BluetoothDeviceFilter;
import android.companion.CompanionDeviceManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import java.util.concurrent.Executor;
import java.util.regex.Pattern;

import io.flutter.embedding.android.FlutterActivity;
import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugins.GeneratedPluginRegistrant;

public class MainActivity extends FlutterActivity {
    public void setUp() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            BluetoothDeviceFilter deviceFilter = new BluetoothDeviceFilter.Builder()
                    .setNamePattern(Pattern.compile("SomeDevice"))
                    .build();
            AssociationRequest pairingRequest = new AssociationRequest.Builder()
                    .addDeviceFilter(deviceFilter)
                    .setSingleDevice(true)
                    .setDeviceProfile(DEVICE_PROFILE_WATCH)
                    .build();

            Executor executor = runnable -> runnable.run();

            CompanionDeviceManager deviceManager = (CompanionDeviceManager) context.getSystemService(Context.COMPANION_DEVICE_SERVICE);
            deviceManager.associate(pairingRequest, executor, new CompanionDeviceManager.Callback() {
                @Override
                public void onDeviceFound(IntentSender chooserLauncher) {
                    try {
                        activity.startIntentSenderForResult(chooserLauncher, SELECT_DEVICE_REQUEST_CODE, null, 0, 0, 0);
                    } catch (IntentSender.SendIntentException e) {
                    }
                }

                @Override
                public void onAssociationCreated(AssociationInfo associationInfo) {
                    Log.d("Android", "onAssociationCreated: " + associationInfo.getDeviceProfile());

                    AssociatedDevice device = associationInfo.getAssociatedDevice();
                    if (device == null) {
                        Log.d("Android", "Associated device is NULL");
                        return;
                    }

                    ScanResult device_to_pair = device.getBleDevice();
                    if (device_to_pair != null) {
                        device_to_pair.createBond();
                        // TODO: Finalize interactions with device
                    }
                }

                @Override
                public void onFailure(@Nullable CharSequence error) {
                    Log.d("Android", "onFailure: " + error);
                }
            });
        }
    }
}
