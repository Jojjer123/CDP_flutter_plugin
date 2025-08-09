package com.jojjer.companiondevicepairing;

import android.os.Build;
import android.os.Looper;
import android.os.IBinder;
import android.os.Handler;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanResult; 
import android.companion.AssociationInfo;
import android.companion.AssociatedDevice; 
import android.companion.AssociationRequest;
import android.companion.BluetoothDeviceFilter;
import android.companion.CompanionDeviceManager;
import android.companion.CompanionDeviceService;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.content.ServiceConnection;
import android.content.ComponentName;
import android.content.BroadcastReceiver;
import android.util.Log;
import android.util.Base64;
import android.net.MacAddress;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.annotation.SuppressLint;

import java.io.File;

import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.concurrent.Executor;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.PluginRegistry;

import android.provider.Settings;

import androidx.core.content.ContextCompat;
import androidx.core.app.ActivityCompat;

/** CompanionDevicePairing */
public class CompanionDevicePairing implements FlutterPlugin, MethodCallHandler, ActivityAware, BleManager.BleCallback {
  public static final String CDP_TAG = "CDP";

  /* USE THIS FOR SMARTWATCH */
  public static final String DEVICE_PROFILE_WATCH = "android.app.role.COMPANION_DEVICE_WATCH";

  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  private MethodChannel channel;
  private static final int SELECT_DEVICE_REQUEST_CODE = 0;
  private Context context;
  private Activity activity;
  private boolean isBound = false;
  private BroadcastReceiver broadcastReceiver;

  public static final String SERVICE_INTERFACE = "android.companion.CompanionDeviceService";

  final int REQUEST_CODE_FOR_NOTIFICATIONS = 1199;
  final int REQUEST_CODE_FOR_RUN_IN_BACKGROUND = 1200;
  final int REQUEST_CODE_FOR_USE_DATA_IN_BACKGROUND = 1201;
  final int REQUEST_CODE_FOR_OBSERVE_COMPANION_DEVICE_PRESENCE = 1202;

  private BleManager bleManager;

  private ServiceConnection serviceConnection = new ServiceConnection() {
    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
      Log.d(CDP_TAG, "Companion device service connected!");
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
      Log.d(CDP_TAG, "Companion device service disconnected!");
    }
  };

  @Override
  public void firmwareProgressCallback(double progress) {
    if (channel != null) {
      new Handler(Looper.getMainLooper()).post(() -> {
        channel.invokeMethod("fwUpdateCallback", progress);
      });
    }
    else {
      Log.d(CDP_TAG, "Can't invoke firmware progress callback as the MethodChannel is null");
    }
  }

  @Override
  public void readCharacteristicCallback(String serviceUuid, String characteristicUuid, int value) {
    if (channel != null) {
      // Log.d(CDP_TAG, "Read characteristic callback: " + serviceUuid + ", " + characteristicUuid + ", " + value);
      new Handler(Looper.getMainLooper()).post(() -> {
        Map<String, Object> map = new HashMap<>();
        map.put("serviceUuid", serviceUuid);
        map.put("characteristicUuid", characteristicUuid);
        map.put("value", value);
        channel.invokeMethod("readCallback", map);
      });
    }
    else {
      Log.d(CDP_TAG, "Can't invoke read callback as the MethodChannel is null");
    }
  }

  @Override
  public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
    context = flutterPluginBinding.getApplicationContext();
    channel = new MethodChannel(flutterPluginBinding.getBinaryMessenger(), "companiondevicepairing");
    channel.setMethodCallHandler(this);

    bleManager = BleManager.getInstance();
    bleManager.initialize(context);
    bleManager.registerBleCallbacks(this);
  }

  @Override
  public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
    if (call.method.equals("getPlatformVersion")) {
      result.success("Android " + android.os.Build.VERSION.RELEASE);
    } else if (call.method.equals("setUp")) {
      String deviceName = call.arguments.toString();
      Log.d(CDP_TAG, "Setting up CompanionDeviceManager, looking for device: \"" + deviceName + "\"");
      setUp(deviceName);
      result.success(null);
    } else if (call.method.equals("connectToDevice")) {
      String deviceName = call.arguments.toString();
      boolean ok = connectToDevice(deviceName);
      result.success(ok);
    } else if (call.method.equals("getConnectionStatus")) {
      int status = checkConnectionStatus();
      result.success(status);
    } else if (call.method.equals("updateFirmware")) {
      String params = call.arguments.toString();
      String[] data = params.split(",");
      if (data.length != 3) {
        Log.d(CDP_TAG, "Invalid arguments: " + params);
        result.error("INVALID_ARGUMENTS", "Expected 3 parameters", null);
        result.success(false);
        return;
      }
      new Thread(() -> updateFirmware(data[0], data[1], data[2])).start();
      result.success(null);
    } else if (call.method.equals("readCharacteristic")) {
      String params = call.arguments.toString();
      String[] uuids = params.split(",");
      if (uuids.length != 2) {
        result.error("INVALID_ARGUMENTS", "Expected 2 UUIDs", null);
        result.success(false);
        return;
      }
      boolean ok = readCharacteristic(uuids[0], uuids[1]);
      result.success(ok);
    } else if (call.method.equals("writeCharacteristic")) {
      String params = call.arguments.toString();
      String[] data = params.split(",");
      if (data.length != 3) {
        Log.d(CDP_TAG, "Invalid arguments: " + params);
        result.error("INVALID_ARGUMENTS", "Expected 3 parameters", null);
        result.success(false);
        return;
      }
      byte[] value = toBytes(Integer.parseInt(data[2]));
      boolean ok = writeCharacteristic(data[0], data[1], value);
      result.success(ok);
    } else if (call.method.equals("readCharacteristicValue")) {
      result.success(null);

    } else if (call.method.equals("readAllCharacteristics")) {
      
      result.success(null);
    } else {
      result.notImplemented();
    }
  }

  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    channel.setMethodCallHandler(null);
  }

  @Override
  public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
    activity = binding.getActivity();
  }

  @Override
  public void onDetachedFromActivityForConfigChanges() {
  }

  @Override
  public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
    onAttachedToActivity(binding);
  }

  @Override
  public void onDetachedFromActivity() {
    activity = null;
  }

  // Function to check and request permission
  public void checkPermission(String permission, int requestCode)
  {
    Log.d(CDP_TAG, "Checking if permission " + permission + " has been accepted!");
    if (ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_DENIED) {
      ActivityCompat.requestPermissions(activity, new String[] { permission }, requestCode);
      Log.d(CDP_TAG, "Permission was denied");
    }
  }

  byte[] toBytes(int i)
  {
    byte[] result = new byte[4];

    result[0] = (byte) (i >> 24);
    result[1] = (byte) (i >> 16);
    result[2] = (byte) (i >> 8);
    result[3] = (byte) i;

    return result;
  }

  public void setUp(String deviceName) {
    checkPermission(Manifest.permission.REQUEST_COMPANION_RUN_IN_BACKGROUND, REQUEST_CODE_FOR_RUN_IN_BACKGROUND);
    checkPermission(Manifest.permission.REQUEST_COMPANION_USE_DATA_IN_BACKGROUND, REQUEST_CODE_FOR_USE_DATA_IN_BACKGROUND);

    CompanionDeviceManager deviceManager = (CompanionDeviceManager) context.getSystemService(Context.COMPANION_DEVICE_SERVICE);
    if (deviceManager != null) {
      // Check if device is already associated and only connect if that is the case
      List<AssociationInfo> associations = deviceManager.getMyAssociations();
      Optional<AssociationInfo> ble_device = associations.stream()
              .filter(device -> device.getDisplayName().equals(deviceName))
              .findFirst();

      if (ble_device.isPresent()) { // Not working as intended if an asociated device has been unpaired
        Log.d(CDP_TAG, "About to connect to device: " + ble_device.toString());
        bleManager.connectToDevice(ble_device.get().getAssociatedDevice().getBluetoothDevice());
        return;
      }
    } else {
      Log.d(CDP_TAG, "CompanionDeviceManager is null");
      return;
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      BluetoothDeviceFilter deviceFilter = new BluetoothDeviceFilter.Builder()
              .setNamePattern(Pattern.compile(deviceName))
              .build();
      AssociationRequest pairingRequest = new AssociationRequest.Builder()
              .addDeviceFilter(deviceFilter)
              .setSingleDevice(true)
              .setDeviceProfile(DEVICE_PROFILE_WATCH)
              .build();

      Executor executor = runnable -> runnable.run();

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
          AssociatedDevice device = associationInfo.getAssociatedDevice();
          if (device == null) {
            Log.d(CDP_TAG, "Associated device is NULL");
            return;
          }

          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ScanResult device_to_pair = device.getBleDevice();
            BluetoothDevice bl_device = device.getBluetoothDevice();
            if (device_to_pair != null) {
              BluetoothDevice ble_device = device_to_pair.getDevice();
              ble_device.createBond();
            }
            else if (bl_device != null) {
              bl_device.createBond();
            }
            else {
              Log.d(CDP_TAG, "No device to pair available: " + device.toString());
            }
          }
        }

        @Override
        public void onFailure(@Nullable CharSequence error) {
          Log.d(CDP_TAG, "onFailure: " + error);
        }
      });

      List<AssociationInfo> associations = deviceManager.getMyAssociations();
      Optional<AssociationInfo> ble_device = associations.stream()
              .filter(device -> device.getDisplayName().equals(deviceName))
              .findFirst();

      deviceManager.startObservingDevicePresence(ble_device.get().getDeviceMacAddress().toString());
      Intent intent = new Intent(context, CompanionDeviceService.class);
      intent.setAction(SERVICE_INTERFACE);
      context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }
  }

  public boolean connectToDevice(String deviceName) {
    if (bleManager == null) {
      Log.d(CDP_TAG, "BleManager is null");
      return false;
    }

    CompanionDeviceManager deviceManager = (CompanionDeviceManager) context.getSystemService(Context.COMPANION_DEVICE_SERVICE);
    if (deviceManager == null) {
      Log.d(CDP_TAG, "DeviceManager is null");
      return false;
    }

    List<AssociationInfo> associations = deviceManager.getMyAssociations();
    Optional<AssociationInfo> ble_device = associations.stream()
            .filter(device -> device.getDisplayName().equals(deviceName))
            .findFirst();
    
    if (ble_device.isPresent()) {
      Log.d(CDP_TAG, "About to connect to device: " + ble_device.toString());
      return bleManager.connectToDevice(ble_device.get().getAssociatedDevice().getBluetoothDevice());
    }
    
    Log.d(CDP_TAG, "No device found");
    return false;
  }

  public int checkConnectionStatus() {
    if (bleManager != null) {
      return bleManager.getConnectionStatus();
    }
    else {
      Log.d(CDP_TAG, "BleManager is null");
      return 2;
    }
  }

  public void updateFirmware(String serviceUuid, String characteristicUuid, String firmwareFilePath) {
    if (bleManager != null) {
      Log.d(CDP_TAG, "Update firmware: " + firmwareFilePath);
      File firmwareFile = new File(firmwareFilePath);
      bleManager.updateFirmware(serviceUuid, characteristicUuid, firmwareFile);
    }
    else {
      Log.d(CDP_TAG, "BleManager is null");
    }
  }

  public boolean readCharacteristic(String serviceUuid, String characteristicUuid)
  {
    if (bleManager != null) {
      return bleManager.readCharacteristic(serviceUuid, characteristicUuid);
    }
    else {
      Log.d(CDP_TAG, "BleManager is null");
      return false;
    }
  }

  public boolean writeCharacteristic(String serviceUuid, String characteristicUuid, byte[] value)
  {
    if (bleManager != null) {
      return bleManager.sendCommand(value, serviceUuid, characteristicUuid);
    }
    else {
      Log.d(CDP_TAG, "BleManager is null");
      return false;
    }
  }
}
