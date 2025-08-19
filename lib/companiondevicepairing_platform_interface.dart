import 'package:plugin_platform_interface/plugin_platform_interface.dart';
import 'dart:typed_data';

import 'companiondevicepairing_method_channel.dart';

typedef ReadCallback = void Function(String serviceUuid, String characteristicUuid, int value);
typedef FwUpdateCallback = void Function(double progress);
typedef ReadAllServicesCallback = void Function(Map<String, Object> allServicesMap);
typedef CharacteristicChangedCallback = void Function(String serviceUuid, String characteristicUuid, Uint8List value);

abstract class CompanionDevicePairingPlatform extends PlatformInterface {
  /// Constructs a CompanionDevicePairingPlatform.
  CompanionDevicePairingPlatform() : super(token: _token);

  static final Object _token = Object();

  static CompanionDevicePairingPlatform _instance =
      MethodChannelCompanionDevicePairing();

  /// The default instance of [CompanionDevicePairingPlatform] to use.
  ///
  /// Defaults to [MethodChannelCompanionDevicePairing].
  static CompanionDevicePairingPlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [CompanionDevicePairingPlatform] when
  /// they register themselves.
  static set instance(CompanionDevicePairingPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<void> registerCallbacks(ReadCallback readCb, FwUpdateCallback fwUpdateCb, ReadAllServicesCallback readAllServicesCb, CharacteristicChangedCallback characteristicChangedCb) {
    throw UnimplementedError("registerCallbacks has not been implemented.");
  }

  Future<String?> getPlatformVersion() {
    throw UnimplementedError("platformVersion() has not been implemented.");
  }

  Future<void> setUp(String deviceName) {
    throw UnimplementedError("setUp has not been implemented.");
  }

  Future<bool> connectToDevice(String deviceName) {
    throw UnimplementedError("connectToDevice has not been implemented.");
  }

  Future<int> getConnectionStatus() {
    throw UnimplementedError("getConnectionStatus has not been implemented.");
  }

  Future<void> getAllServices() {
    throw UnimplementedError("getAllServices has not been implemented.");
  }

  Future<void> updateFirmware(String serviceUuid, String characteristicUuid, String firmwareFilePath) {
    throw UnimplementedError("updateFirmware has not been implemented.");
  }

  Future<bool> readCharacteristic(String serivceUuid, String characteristicUuid) {
    throw UnimplementedError("readCharacteristic has not been implemented.");
  }

  Future<bool> writeCharacteristic(String serviceUuid, String characteristicUuid, int value) {
    throw UnimplementedError("writeCharacteristic has not been implemented.");
  }

  Future<String> readAllCharacteristics() {
    throw UnimplementedError("readAllCharacteristics has not been implemented.");
  }

  Future<void> subscribeToCharacteristic(String serviceUuid, String characteristicUuid) {
    throw UnimplementedError("subscribeToCharacteristic has not been implemented.");
  }

  Future<void> storeDeviceModel(Map<String, dynamic> model) {
    throw UnimplementedError("storeDeviceModel has not been implemented.");
  }
}
