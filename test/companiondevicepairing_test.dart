import 'package:flutter_test/flutter_test.dart';
import 'package:companiondevicepairing/companiondevicepairing.dart';
import 'package:companiondevicepairing/companiondevicepairing_platform_interface.dart';
import 'package:companiondevicepairing/companiondevicepairing_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockCompaniondevicepairingPlatform
    with MockPlatformInterfaceMixin
    implements CompaniondevicepairingPlatform {

  @override
  Future<String?> getPlatformVersion() => Future.value('42');
}

void main() {
  final CompaniondevicepairingPlatform initialPlatform = CompaniondevicepairingPlatform.instance;

  test('$MethodChannelCompaniondevicepairing is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelCompaniondevicepairing>());
  });

  test('getPlatformVersion', () async {
    Companiondevicepairing companiondevicepairingPlugin = Companiondevicepairing();
    MockCompaniondevicepairingPlatform fakePlatform = MockCompaniondevicepairingPlatform();
    CompaniondevicepairingPlatform.instance = fakePlatform;

    expect(await companiondevicepairingPlugin.getPlatformVersion(), '42');
  });
}
