import 'dart:developer';


import 'companiondevicepairing_platform_interface.dart';

class Companiondevicepairing {
  Future<void> registerCallbacks(ReadCallback readCb, FwUpdateCallback fwUpdateCb) async {
    CompaniondevicepairingPlatform.instance.registerCallbacks(readCb, fwUpdateCb);
    log("Registered callbacks!");
  }

  Future<String?> getPlatformVersion() {
    return CompaniondevicepairingPlatform.instance.getPlatformVersion();
  }

  Future<void> setUp(String deviceName) async {
    CompaniondevicepairingPlatform.instance.setUp(deviceName);
    log("Setting up companion device pairing!");
  }

  Future<bool> connectToDevice(String deviceName) async {
    bool ok = await CompaniondevicepairingPlatform.instance.connectToDevice(deviceName);
    log("Connecting to BLE device!");
    return ok;
  }

  Future<int> getConnectionStatus() async {
    int status = await CompaniondevicepairingPlatform.instance.getConnectionStatus();
    log("Getting BLE connection status!");
    return status;
  }

  Future<void> updateFirmware(String serviceUuid, String characteristicUuid, String firmwareFilePath) async {
    CompaniondevicepairingPlatform.instance.updateFirmware(serviceUuid, characteristicUuid, firmwareFilePath);
    log("Updating firmware!");
  }

  Future<bool> readCharacteristic(String serviceUuid, String characteristicUuid) async {
    bool ok = await CompaniondevicepairingPlatform.instance.readCharacteristic(serviceUuid, characteristicUuid);
    log("Reading characteristic from device");
    return ok;
  }

  Future<bool> writeCharacteristic(String serviceUuid, String characteristicUuid, int value) async {
    bool ok = await CompaniondevicepairingPlatform.instance.writeCharacteristic(serviceUuid, characteristicUuid, value);
    log("Writing characteristic to device");
    return ok;
  }

  Future<String> readAllCharacteristics() async {
    return CompaniondevicepairingPlatform.instance.readAllCharacteristics();
  }
}
