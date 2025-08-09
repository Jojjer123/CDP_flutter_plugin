
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'companiondevicepairing_platform_interface.dart';

/// An implementation of [CompaniondevicepairingPlatform] that uses method channels.
class MethodChannelCompanionDevicePairing
    extends CompanionDevicePairingPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel("companiondevicepairing");

  @override
  Future<void> registerCallbacks(ReadCallback readCb, FwUpdateCallback fwUpdateCb) async {
    print("Registering callbacks in plugin");
    methodChannel.setMethodCallHandler((call) async {
      if (call.method == "readCallback") {
        try {
          final map = Map<String, Object>.from(call.arguments);
          readCb(
            map["serviceUuid"] as String,
            map["characteristicUuid"] as String,
            map["value"] as int,
          );
        } catch (e, stack) {
          print("Error during readCallback handling: $e");
          print(stack);
        }
      } else if (call.method == "fwUpdateCallback") {
        try {
          final double progress = call.arguments as double;
          fwUpdateCb(progress);
        } catch (e, stack) {
          print("Error during fwUpdateCallback handling: $e");
          print(stack);
        }
      } else {
        print("Unknown method called: ${call.method}");
      }
    });
  }

  @override
  Future<String?> getPlatformVersion() async {
    final version =
        await methodChannel.invokeMethod<String>("getPlatformVersion");
    return version;
  }

  @override
  Future<void> setUp(String deviceName) async {
    methodChannel.invokeMethod("setUp", "$deviceName");
  }

  @override
  Future<bool> connectToDevice(String deviceName) async {
    return await methodChannel.invokeMethod("connectToDevice", "$deviceName");
  }

  @override
  Future<int> getConnectionStatus() async {
    return await methodChannel.invokeMethod("getConnectionStatus");
  }

  @override
  Future<void> updateFirmware(String serviceUuid, String characteristicUuid, String firmwareFilePath) async {
    methodChannel.invokeMethod("updateFirmware", "$serviceUuid,$characteristicUuid,$firmwareFilePath");
  }

  @override
  Future<bool> readCharacteristic(String serviceUuid, String characteristicUuid) async {
    return await methodChannel.invokeMethod("readCharacteristic", "$serviceUuid,$characteristicUuid");
  }

  @override
  Future<bool> writeCharacteristic(String serviceUuid, String characteristicUuid, int value) async {
    return await methodChannel.invokeMethod("writeCharacteristic", "$serviceUuid,$characteristicUuid,$value");
  }

  @override
  Future<String> readAllCharacteristics() async {
    return await methodChannel.invokeMethod("readAllCharacteristics");
  }
}
