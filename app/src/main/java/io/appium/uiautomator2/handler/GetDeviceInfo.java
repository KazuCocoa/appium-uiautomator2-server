/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.appium.uiautomator2.handler;

import android.Manifest;
import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.telephony.TelephonyManager;

import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import io.appium.uiautomator2.common.exceptions.UiAutomator2Exception;
import io.appium.uiautomator2.handler.request.SafeRequestHandler;
import io.appium.uiautomator2.http.AppiumResponse;
import io.appium.uiautomator2.http.IHttpRequest;
import io.appium.uiautomator2.utils.DeviceInfoHelper;
import io.appium.uiautomator2.utils.Logger;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static io.appium.uiautomator2.utils.JSONUtils.formatNull;
import static io.appium.uiautomator2.utils.ReflectionUtils.getField;

public class GetDeviceInfo extends SafeRequestHandler {
    private final Instrumentation mInstrumentation = getInstrumentation();

    public GetDeviceInfo(String mappedUri) {
        super(mappedUri);
    }

    private static Object extractSafeJSONValue(String fieldName, Object source) {
        try {
            return formatNull(getField(fieldName, source));
        } catch (UiAutomator2Exception ign) {
            return JSONObject.NULL;
        }
    }

    private static String[] TRANSPORT_NAMES = {
            "CELLULAR",
            "WIFI",
            "BLUETOOTH",
            "ETHERNET",
            "VPN",
            "WIFI_AWARE",
            "LOWPAN",
            "TEST"
    };

    // A note for For API 30+
    private static List<String> getTransport(NetworkCapabilities networkCapabilities) {
        // https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/net/NetworkCapabilities.java#702
        List<String> transport = new ArrayList<>();

        int index;
        for (index = 0; index < TRANSPORT_NAMES.length; index++) {
            if (networkCapabilities.hasTransport(index)) {
                transport.add(String.valueOf(index));
                transport.add(TRANSPORT_NAMES[index]);
                return transport;
            }
        }
        transport.add(String.valueOf(index));
        transport.add("UNKNOWN");
        return transport;
    }

    private static String getTelephonyCondition(int dataState) {
        switch (dataState) {
        case TelephonyManager.DATA_DISCONNECTED:
            return "DISCONNECTED";
        case TelephonyManager.DATA_CONNECTING:
            return "CONNECTING";
        case TelephonyManager.DATA_CONNECTED:
            return "CONNECTED";
        case TelephonyManager.DATA_SUSPENDED:
            return "SUSPENDED";
        default:
            return "UNKNOWN";
        }
    }

    private static JSONArray extractNetworkInfo(DeviceInfoHelper deviceInfoHelper) throws JSONException {
        JSONArray result = new JSONArray();
        for (Network network : deviceInfoHelper.getNetworks()) {
            JSONObject resultItem = new JSONObject();

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
                TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

                if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                    if (telephonyManager != null) {
                        resultItem.put("cellState", getTelephonyCondition(telephonyManager.getDataState()));
                        if (telephonyManager.getSignalStrength() != null) {
                            // signal strength of 'data network' (Celler)
                            resultItem.put("cellSignalStrength", telephonyManager.getSignalStrength().getLevel());
                        }
                    }

                    if (connectivityManager != null)
                        connectivityManager.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback());
                        Network netw = connectivityManager.getActiveNetwork();

                        NetworkCapabilities networkCapabilities = connectivityManager.getNetworkCapabilities(netw);
                        if (networkCapabilities != null) {
                            List<String> transport = getTransport(networkCapabilities);
                            resultItem.put("type", transport.get(0));
                            resultItem.put("typeName", transport.get(1));
                        }
                        // Return whether the data network is currently active
//                        connectivityManager.isDefaultNetworkActive();
//                        resultItem.put("isConnected", networkInfo.isConnected());
//
//                        resultItem.put("detailedState", networkInfo.getDetailedState().name());
//                        resultItem.put("state", networkInfo.getState().name());
//                        resultItem.put("isAvailable", networkInfo.isAvailable());
//                        resultItem.put("isFailover", networkInfo.isFailover());
//                        resultItem.put("isRoaming", networkInfo.isRoaming());
                }
            } else {
                NetworkInfo networkInfo = deviceInfoHelper.extractInfo(network);
                if (networkInfo != null) {
                    resultItem.put("type", networkInfo.getType());
                    resultItem.put("typeName", networkInfo.getTypeName());
                    resultItem.put("subtype", networkInfo.getSubtype());
                    resultItem.put("subtypeName", networkInfo.getSubtypeName());
                    resultItem.put("isConnected", networkInfo.isConnected());
                    resultItem.put("detailedState", networkInfo.getDetailedState().name());
                    resultItem.put("state", networkInfo.getState().name());
                    resultItem.put("extraInfo", formatNull(networkInfo.getExtraInfo()));
                    resultItem.put("isAvailable", networkInfo.isAvailable());
                    resultItem.put("isFailover", networkInfo.isFailover());
                    resultItem.put("isRoaming", networkInfo.isRoaming());
                }
            }

            NetworkCapabilities networkCaps = deviceInfoHelper.extractCapabilities(network);
            JSONObject caps = new JSONObject();
            if (networkCaps != null) {
                caps.put("transportTypes", DeviceInfoHelper.extractTransportTypes(networkCaps));
                caps.put("networkCapabilities", DeviceInfoHelper.extractCapNames(networkCaps));
                caps.put("linkUpstreamBandwidthKbps", networkCaps.getLinkUpstreamBandwidthKbps());
                caps.put("linkDownBandwidthKbps", networkCaps.getLinkDownstreamBandwidthKbps());
                caps.put("signalStrength",
                        extractSafeJSONValue("mSignalStrength", networkCaps));
                caps.put("networkSpecifier",
                        extractSafeJSONValue("mNetworkSpecifier", networkCaps));
                caps.put("SSID", extractSafeJSONValue("mSSID", networkCaps));
            }
            resultItem.put("capabilities", formatNull(networkCaps == null ? null : caps));

            if (resultItem.length() > 0) {
                result.put(resultItem);
            }
        }
        return result;
    }

    @Override
    protected AppiumResponse safeHandle(IHttpRequest request) throws JSONException {
        Logger.info("Get Device Info command");
        final JSONObject response = new JSONObject();
        final DeviceInfoHelper deviceInfoHelper = new DeviceInfoHelper(mInstrumentation
                .getTargetContext());
        response.put("androidId", deviceInfoHelper.getAndroidId());
        response.put("manufacturer", deviceInfoHelper.getManufacturer());
        response.put("model", deviceInfoHelper.getModelName());
        response.put("brand", deviceInfoHelper.getBrand());
        response.put("apiVersion", deviceInfoHelper.getApiVersion());
        response.put("platformVersion", deviceInfoHelper.getPlatformVersion());
        response.put("carrierName", formatNull(deviceInfoHelper.getCarrierName()));
        response.put("realDisplaySize", deviceInfoHelper.getRealDisplaySize());
        response.put("displayDensity", deviceInfoHelper.getDisplayDensity());
        response.put("networks", extractNetworkInfo(deviceInfoHelper));
        response.put("locale", deviceInfoHelper.getLocale());
        response.put("timeZone", deviceInfoHelper.getTimeZone());

        return new AppiumResponse(getSessionId(request), response);
    }
}
