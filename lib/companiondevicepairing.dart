import 'dart:developer';

import 'companiondevicepairing_platform_interface.dart';

class CompanionDevicePairing {
  Future<void> registerCallbacks(ReadCallback readCb, FwUpdateCallback fwUpdateCb, ReadAllServicesCallback readAllServicesCb, CharacteristicChangedCallback characteristicChangedCb) async {
    CompanionDevicePairingPlatform.instance.registerCallbacks(readCb, fwUpdateCb, readAllServicesCb, characteristicChangedCb);
    log("Registered callbacks!");
  }

  Future<String?> getPlatformVersion() {
    return CompanionDevicePairingPlatform.instance.getPlatformVersion();
  }

  Future<void> setUp(String deviceName) async {
    CompanionDevicePairingPlatform.instance.setUp(deviceName);
    log("Setting up companion device pairing!");
  }

  Future<bool> connectToDevice(String deviceName) async {
    bool ok = await CompanionDevicePairingPlatform.instance.connectToDevice(deviceName);
    log("Connecting to BLE device!");
    return ok;
  }

  Future<int> getConnectionStatus() async {
    int status = await CompanionDevicePairingPlatform.instance.getConnectionStatus();
    log("Getting BLE connection status!");
    return status;
  }

  Future<void> getAllServices() async {
    return CompanionDevicePairingPlatform.instance.getAllServices();
  }

  Future<void> updateFirmware(String serviceUuid, String characteristicUuid, String firmwareFilePath) async {
    CompanionDevicePairingPlatform.instance.updateFirmware(serviceUuid, characteristicUuid, firmwareFilePath);
    log("Updating firmware!");
  }

  Future<bool> readCharacteristic(String serviceUuid, String characteristicUuid) async {
    bool ok = await CompanionDevicePairingPlatform.instance.readCharacteristic(serviceUuid, characteristicUuid);
    log("Reading characteristic from device");
    return ok;
  }

  Future<bool> writeCharacteristic(String serviceUuid, String characteristicUuid, int value) async {
    bool ok = await CompanionDevicePairingPlatform.instance.writeCharacteristic(serviceUuid, characteristicUuid, value);
    log("Writing characteristic to device");
    return ok;
  }

  Future<String> readAllCharacteristics() async {
    return CompanionDevicePairingPlatform.instance.readAllCharacteristics();
  }

  Future<void> subscribeToCharacteristic(String serviceUuid, String characteristicUuid) async {
    await CompanionDevicePairingPlatform.instance.subscribeToCharacteristic(serviceUuid, characteristicUuid);
    log("Subscribing to characteristic");
  }
}
