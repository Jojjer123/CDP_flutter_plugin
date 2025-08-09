
import 'package:companiondevicepairing/companiondevicepairing.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'companiondevicepairing_method_channel.dart';

typedef ReadCallback = void Function(String serviceUuid, String characteristicUuid, int value);
typedef FwUpdateCallback = void Function(double progress);

abstract class CompaniondevicepairingPlatform extends PlatformInterface {
  /// Constructs a CompaniondevicepairingPlatform.
  CompaniondevicepairingPlatform() : super(token: _token);

  static final Object _token = Object();

  static CompaniondevicepairingPlatform _instance =
      MethodChannelCompaniondevicepairing();

  /// The default instance of [CompaniondevicepairingPlatform] to use.
  ///
  /// Defaults to [MethodChannelCompaniondevicepairing].
  static CompaniondevicepairingPlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [CompaniondevicepairingPlatform] when
  /// they register themselves.
  static set instance(CompaniondevicepairingPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<void> registerCallbacks(ReadCallback readCb, FwUpdateCallback fwUpdateCb) {
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
}
