import 'package:flutter_test/flutter_test.dart';
import 'package:emirates_id_reader_plugin/emirates_id_reader_plugin.dart';
import 'package:emirates_id_reader_plugin/emirates_id_reader_plugin_platform_interface.dart';
import 'package:emirates_id_reader_plugin/emirates_id_reader_plugin_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockEmiratesIdReaderPluginPlatform
    with MockPlatformInterfaceMixin
    implements EmiratesIdReaderPluginPlatform {

  @override
  Future<String?> getPlatformVersion() => Future.value('42');
}

void main() {
  final EmiratesIdReaderPluginPlatform initialPlatform = EmiratesIdReaderPluginPlatform.instance;

  test('$MethodChannelEmiratesIdReaderPlugin is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelEmiratesIdReaderPlugin>());
  });

  test('getPlatformVersion', () async {
    EmiratesIdReaderPlugin emiratesIdReaderPlugin = EmiratesIdReaderPlugin();
    MockEmiratesIdReaderPluginPlatform fakePlatform = MockEmiratesIdReaderPluginPlatform();
    EmiratesIdReaderPluginPlatform.instance = fakePlatform;

    expect(await emiratesIdReaderPlugin.getPlatformVersion(), '42');
  });
}
