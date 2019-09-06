package io.appium.uiautomator2.handler;

import android.app.Activity;
import android.app.Instrumentation;
import android.graphics.Rect;
import android.os.Build;
import android.view.Window;

import org.json.JSONException;
import org.json.JSONObject;

import io.appium.uiautomator2.handler.request.SafeRequestHandler;
import io.appium.uiautomator2.http.AppiumResponse;
import io.appium.uiautomator2.http.IHttpRequest;
import io.appium.uiautomator2.utils.Logger;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

public class GetSystemBars extends SafeRequestHandler {

    public GetSystemBars(String mappedUri) {
        super(mappedUri);
    }

    @Override
    protected AppiumResponse safeHandle(IHttpRequest request) throws JSONException {
        Logger.info("Get status bar height of the device");

        Instrumentation instrumentation = getInstrumentation();

        int height = getStatusBarHeight(instrumentation);

        JSONObject result = new JSONObject();
        result.put("statusBar", height);
        return new AppiumResponse(getSessionId(request), result);
    }

    private int getStatusBarHeight(Instrumentation instrumentation) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // https://stackoverflow.com/questions/3407256/height-of-status-bar-in-android/47125610#47125610
            Rect rectangle = new Rect();
            Window window = ((Activity) instrumentation.getContext()).getWindow();
            window.getDecorView().getWindowVisibleDisplayFrame(rectangle);
            return rectangle.top;
        } else {
            int result = 0;
            int resourceId = instrumentation.getContext().getResources().getIdentifier("status_bar_height", "dimen", "android");
            if (resourceId > 0) {
                result = instrumentation.getContext().getResources().getDimensionPixelSize(resourceId);
            }
            return result;
        }
    }
}
