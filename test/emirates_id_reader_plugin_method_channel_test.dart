import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:emirates_id_reader_plugin/emirates_id_reader_plugin_method_channel.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  MethodChannelEmiratesIdReaderPlugin platform = MethodChannelEmiratesIdReaderPlugin();
  const MethodChannel channel = MethodChannel('emirates_id_reader_plugin');

  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(
      channel,
      (MethodCall methodCall) async {
        return '42';
      },
    );
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(channel, null);
  });

  test('getPlatformVersion', () async {
    expect(await platform.getPlatformVersion(), '42');
  });
}
