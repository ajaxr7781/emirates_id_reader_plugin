
import 'package:flutter/services.dart';
import 'emirates_id_reader_plugin_platform_interface.dart';

class EmiratesIdReaderPlugin {
  // Same channel name used on Android
  static const MethodChannel _channel = MethodChannel('emirates_id_reader_plugin');

  Future<String?> getPlatformVersion() {
    return EmiratesIdReaderPluginPlatform.instance.getPlatformVersion();
  }

  /// Reads public data from the Emirates ID card via the native plugin.
  /// If you want the native side to auto-generate requestId, call without args.
  Future<Map<String, String>?> getPublicCardData({String? requestId}) async {
    final Object? args = (requestId == null) ? null : <String, Object>{'requestId': requestId};

    final Map<dynamic, dynamic>? result =
        await _channel.invokeMethod<Map<dynamic, dynamic>>('getPublicCardData', args);

    if (result == null) return null;

    // Normalize to Map<String, String>
    return result.map((key, value) => MapEntry(key.toString(), value?.toString() ?? ''));
  }
}
