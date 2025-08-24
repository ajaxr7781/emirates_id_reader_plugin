import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'emirates_id_reader_plugin_platform_interface.dart';

/// An implementation of [EmiratesIdReaderPluginPlatform] that uses method channels.
class MethodChannelEmiratesIdReaderPlugin extends EmiratesIdReaderPluginPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('emirates_id_reader_plugin');

  @override
  Future<String?> getPlatformVersion() async {
    final version = await methodChannel.invokeMethod<String>('getPlatformVersion');
    return version;
  }
}
