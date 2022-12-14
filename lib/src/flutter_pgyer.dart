import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:isolate';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_pgyer/src/bean/check_result.dart';
import 'package:flutter_pgyer/src/bean/check_soft_model.dart';
import 'package:flutter_pgyer/src/bean/ios_check_model.dart';
import 'package:flutter_pgyer/src/bean/result.dart';

typedef FlutterPgyerInitCallBack = Function(InitResultInfo);

class FlutterPgyer {
  static const MethodChannel _channel =
      const MethodChannel('crazecoder/flutter_pgyer');
  static final _onCheckUpgrade = StreamController<CheckResult>.broadcast();

  FlutterPgyer._();

  static Future<Null> _handleMessages(MethodCall call) async {
    switch (call.method) {
      case 'onCheckUpgrade':
        CheckResult _result = CheckResult(
          model: call.arguments == null ||
                  (Platform.isAndroid && call.arguments["model"] == null)
              ? null
              : Platform.isIOS
                  ? IOSCheckModel.fromJson(call.arguments)
                  : CheckSoftModel.fromJson(call.arguments["model"]),
          checkEnum: Platform.isIOS
              ? call.arguments == null
                  ? CheckEnum.NO_VERSION
                  : CheckEnum.SUCCESS
              : CheckEnum.values[call.arguments["enum"]],
        );
        _onCheckUpgrade.add(_result);
        break;
    }
  }

  static Stream<CheckResult> get onCheckUpgrade => _onCheckUpgrade.stream;

  static void init({
    FlutterPgyerInitCallBack? callBack,
    String? androidApiKey,
    String? frontJSToken,
    String? iOSAppKey,
  }) {
    assert(
        (Platform.isAndroid && androidApiKey != null && frontJSToken != null) ||
            (Platform.isIOS && iOSAppKey != null));
    _channel.setMethodCallHandler(_handleMessages);
    Map<String, Object?> map = {
      "apiKey": androidApiKey,
      "frontJSToken": frontJSToken,
      "appId": iOSAppKey,
    };
    var resultBean;
    Isolate.current.addErrorListener(new RawReceivePort((dynamic pair) {
      var isolateError = pair as List<dynamic>;
      var _error = isolateError.first;
      var _stackTrace = isolateError.last;
      Zone.current.handleUncaughtError(_error, _stackTrace);
    }).sendPort);
    runZonedGuarded(
      () async {
        final String result =
            await (_channel.invokeMethod('initSdk', map) as FutureOr<dynamic>);
        Map resultMap = json.decode(result);
        resultBean = InitResultInfo.fromJson(resultMap as Map<String, dynamic>);
        callBack!(resultBean);
      },
      (error, stackTrace) {
        resultBean = InitResultInfo();
        callBack!(resultBean);
      },
    );
    FlutterError.onError = (details) {
      if (details.stack == null) {
        FlutterError.presentError(details);
        return;
      }
      Zone.current.handleUncaughtError(details.exception, details.stack!);
    };
  }

  ///???????????? iOS??????
  static Future<Null> setEnableFeedback({
    bool enable = true,
    String? colorHex, //???????????????????????????16???????????????????????????#FFFFFF
    bool isDialog = true, // android???????????????????????????????????????false ???activity
    Map<String, String>? param, // android?????????????????????????????????
    bool isThreeFingersPan =
        false, // ios?????????????????????????????????????????????????????? android?????????true???????????????????????????????????????show
    double shakingThreshold = 2.3, //ios????????????????????????????????????????????????2.3??????????????????????????????
  }) async {
    if (Platform.isAndroid) {
      return;
    }
    Map<String, Object?> map = {
      "enable": enable,
      "colorHex": colorHex,
      "isDialog": isDialog,
      "param": param,
      "shakingThreshold": shakingThreshold,
      "isThreeFingersPan": isThreeFingersPan,
    };
    await _channel.invokeMethod('setEnableFeedback', map);
  }

  ///???????????????????????????????????????setEnableFeedback????????? iOS??????
  static Future<Null> showFeedbackView() async {
    await _channel.invokeMethod('showFeedbackView');
  }

  ///????????????
  static Future<Null> checkSoftwareUpdate({bool justNotify = true}) async {
    Map<String, Object> map = {
      "justNotify": justNotify,
    };
    await _channel.invokeMethod('checkSoftwareUpdate', map);
  }

  ///????????????
  static Future<Null> checkVersionUpdate() async {
    await _channel.invokeMethod('checkVersionUpdate');
  }

  ///???????????????????????????
  ///???????????????iOS????????????????????????????????????????????????????????????????????????????????????
  static void reportException<T>(
    T callback(), {
    FlutterExceptionHandler? handler, //??????????????????????????????????????????
    String? filterRegExp, //?????????????????????????????????message
  }) {
    bool useLog = false;
    assert(useLog = true);

    Isolate.current.addErrorListener(RawReceivePort((dynamic pair) async {
      var isolateError = pair as List<dynamic>;
      var _error = isolateError.first;
      var _stackTrace = isolateError.last;
      Zone.current.handleUncaughtError(_error, _stackTrace);
    }).sendPort);
    runZonedGuarded<Future<Null>>(() async {
      callback();
    }, (error, stackTrace) async {
      if (useLog || handler != null) {
        FlutterErrorDetails details =
            FlutterErrorDetails(exception: error, stack: stackTrace);
        // In development mode simply print to console.
        handler == null
            ? FlutterError.dumpErrorToConsole(details)
            : handler(details);
      }
      var errorStr = error.toString();
      //????????????
      if (filterRegExp != null) {
        RegExp reg = new RegExp(filterRegExp);
        Iterable<Match> matches = reg.allMatches(errorStr);
        if (matches.length > 0) {
          return;
        }
      }
      uploadException(message: errorStr, detail: stackTrace.toString());
    });
    FlutterError.onError = (FlutterErrorDetails details) async {
      Zone.current.handleUncaughtError(details.exception, details.stack!);
    };
  }

  static Future<Null> uploadException({String? message, String? detail}) async {
    assert(message != null && detail != null);
    var map = {};
    map.putIfAbsent("crash_message", () => message);
    map.putIfAbsent("crash_detail", () => detail);
    await _channel.invokeMethod('reportException', map);
  }

  static void dispose() {
    _onCheckUpgrade.close();
  }
}
