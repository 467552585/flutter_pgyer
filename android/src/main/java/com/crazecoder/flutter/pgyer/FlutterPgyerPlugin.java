package com.crazecoder.flutter.pgyer;

import android.Manifest;
import android.app.Activity;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.content.PermissionChecker;

import com.crazecoder.flutter.pgyer.bean.InitResultInfo;
import com.crazecoder.flutter.pgyer.utils.JsonUtil;
import com.crazecoder.flutter.pgyer.utils.MapUtil;
import com.pgyer.pgyersdk.PgyerSDKManager;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.Registrar;

/**
 * FlutterPgyerPlugin
 */
public class FlutterPgyerPlugin implements FlutterPlugin, MethodCallHandler, ActivityAware {

    private Activity activity;
    private FlutterPluginBinding flutterPluginBinding;
    private MethodChannel channel;

    private final PgyerSDKManager.InitSdk sdkManager = new PgyerSDKManager.InitSdk();

    public FlutterPgyerPlugin(Activity activity,MethodChannel channel){
        this.activity = activity;
        this.channel = channel;
    }
    /**
     * Plugin registration.
     */
    @Deprecated
    public static void registerWith(Registrar registrar) {
        final MethodChannel channel = new MethodChannel(registrar.messenger(), "crazecoder/flutter_pgyer");
        FlutterPgyerPlugin plugin = new FlutterPgyerPlugin(registrar.activity(),channel);
        channel.setMethodCallHandler(plugin);
    }

    @Override
    public void onMethodCall(final MethodCall call, @NonNull Result result) {
        if (call.method.equals("initSdk")) {
            String json;
            try {
                if (!hasPermission(Manifest.permission.READ_PHONE_STATE)) {
                    json = JsonUtil.toJson(MapUtil.deepToMap(getResultBean(false, "Permission denied: " + Manifest.permission.READ_PHONE_STATE)));
                    result.success(json);
                    return;
                }
                String apiKey = call.argument("apiKey");
                String frontJSToken = call.argument("frontJSToken");
                sdkManager.setContext(activity.getApplication())
                        .setApiKey(apiKey) //添加apikey
                        .setFrontJSToken(frontJSToken)    //添加 token
                        .build();
//                PgyCrashManager.setIsIgnoreDefaultHander(false);
                json = JsonUtil.toJson(MapUtil.deepToMap(getResultBean(true, "初始化成功")));
            } catch (Exception e) {
                json = JsonUtil.toJson(MapUtil.deepToMap(getResultBean(false, "初始化失败：" + e.getMessage())));
            }
            result.success(json);
        } else if (call.method.equals("reportException")) {
            String message = "";
            String detail = null;
            if (call.hasArgument("crash_message")) {
                message = call.argument("crash_message");
            }
            if (call.hasArgument("crash_detail")) {
                detail = call.argument("crash_detail");
            }
            if (TextUtils.isEmpty(detail)) return;
            String[] details = detail.split("#");
            List<StackTraceElement> elements = new ArrayList<>();
            for (String s : details) {
                if (!TextUtils.isEmpty(s)) {
                    String methodName = null;
                    String fileName = null;
                    int lineNum = -1;
                    String[] contents = s.split(" \\(");
                    if (contents.length > 0) {
                        methodName = contents[0];
                        if (contents.length < 2) {
                            break;
                        }
                        String packageContent = contents[1].replace(")", "");
                        String[] packageContentArray = packageContent.split("\\.dart:");
                        if (packageContentArray.length > 0) {
                            if (packageContentArray.length == 1) {
                                fileName = packageContentArray[0];
                            } else {
                                fileName = packageContentArray[0] + ".dart";
                                Pattern patternTrace = Pattern.compile("[1-9]\\d*");
                                Matcher m = patternTrace.matcher(packageContentArray[1]);
                                if (m.find()) {
                                    String lineNumStr = m.group();
                                    lineNum = Integer.parseInt(lineNumStr);
                                }
                            }
                        }
                    }
                    StackTraceElement element = new StackTraceElement("Dart", methodName, fileName, lineNum);
                    elements.add(element);
                }
            }
            Throwable throwable = new Throwable(message);
            if (elements.size() > 0) {
                StackTraceElement[] elementsArray = new StackTraceElement[elements.size()];
                throwable.setStackTrace(elements.toArray(elementsArray));
            }
            PgyerSDKManager.reportException(new Exception(throwable));
            result.success(null);
        } else if (call.method.equals("checkUpdate")) {
            PgyerSDKManager.checkSoftwareUpdate(activity);
            result.success(null);
        } else {
            result.notImplemented();
        }
    }

    private InitResultInfo getResultBean(boolean isSuccess, String msg) {
        InitResultInfo bean = new InitResultInfo();
        bean.setSuccess(isSuccess);
        bean.setMessage(msg);
        return bean;
    }

    private boolean hasPermission(String permission) {
        return ContextCompat.checkSelfPermission(activity, permission) == PermissionChecker.PERMISSION_GRANTED;
    }

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
        this.flutterPluginBinding = binding;

    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        channel.setMethodCallHandler(null);
        flutterPluginBinding = null;
    }

    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
        activity = binding.getActivity();
        channel = new MethodChannel(flutterPluginBinding.getBinaryMessenger(), "crazecoder/flutter_bugly");
        channel.setMethodCallHandler(this);
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {

    }

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {

    }

    @Override
    public void onDetachedFromActivity() {

    }
}
