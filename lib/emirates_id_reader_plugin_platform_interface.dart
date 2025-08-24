import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'emirates_id_reader_plugin_method_channel.dart';

abstract class EmiratesIdReaderPluginPlatform extends PlatformInterface {
  /// Constructs a EmiratesIdReaderPluginPlatform.
  EmiratesIdReaderPluginPlatform() : super(token: _token);

  static final Object _token = Object();

  static EmiratesIdReaderPluginPlatform _instance = MethodChannelEmiratesIdReaderPlugin();

  /// The default instance of [EmiratesIdReaderPluginPlatform] to use.
  ///
  /// Defaults to [MethodChannelEmiratesIdReaderPlugin].
  static EmiratesIdReaderPluginPlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [EmiratesIdReaderPluginPlatform] when
  /// they register themselves.
  static set instance(EmiratesIdReaderPluginPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<String?> getPlatformVersion() {
    throw UnimplementedError('platformVersion() has not been implemented.');
  }
}
