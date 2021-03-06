package io.lbry.browser;

import android.os.Build;
import android.os.Bundle;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.Manifest;
import android.net.Uri;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.telephony.TelephonyManager;
import android.widget.Toast;

import com.brentvatne.react.ReactVideoPackage;
import com.facebook.react.common.LifecycleState;
import com.facebook.react.modules.core.DefaultHardwareBackBtnHandler;
import com.facebook.react.ReactRootView;
import com.facebook.react.ReactInstanceManager;
import com.facebook.react.shell.MainReactPackage;

import io.lbry.browser.reactpackages.LbryReactPackage;
import io.lbry.browser.reactmodules.DownloadManagerModule;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

public class MainActivity extends Activity implements DefaultHardwareBackBtnHandler {

    private static final int OVERLAY_PERMISSION_REQ_CODE = 101;

    private static final int STORAGE_PERMISSION_REQ_CODE = 201;

    private static final int PHONE_STATE_PERMISSION_REQ_CODE = 202;

    private ReactRootView mReactRootView;

    private ReactInstanceManager mReactInstanceManager;

    public static final String SHARED_PREFERENCES_NAME = "LBRY";

    public static final String SALT_KEY = "salt";

    public static final String DEVICE_ID_KEY = "deviceId";

    /**
     * Flag which indicates whether or not the service is running. Will be updated in the
     * onResume method.
     */
    private boolean serviceRunning;

    protected String getMainComponentName() {
        return "LBRYApp";
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Request external storage permission on Android version >= 6
            checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            STORAGE_PERMISSION_REQ_CODE,
                            "LBRY requires access to your device storage to be able to download files and media.",
                            this);
        }

        super.onCreate(savedInstanceState);

        // Start the daemon service if it is not started
        serviceRunning = isServiceRunning(LbrynetService.class);
        if (!serviceRunning) {
            ServiceHelper.start(this, "", LbrynetService.class, "lbrynetservice");
        }

        mReactRootView = new ReactRootView(this);
        mReactInstanceManager = ReactInstanceManager.builder()
                .setApplication(getApplication())
                .setBundleAssetName("index.android.bundle")
                .setJSMainModulePath("index")
                .addPackage(new MainReactPackage())
                .addPackage(new ReactVideoPackage())
                .addPackage(new LbryReactPackage())
                .setUseDeveloperSupport(true)
                .setInitialLifecycleState(LifecycleState.RESUMED)
                .build();
        mReactRootView.startReactApplication(mReactInstanceManager, "LBRYApp", null);

        setContentView(mReactRootView);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == OVERLAY_PERMISSION_REQ_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!Settings.canDrawOverlays(this)) {
                    // SYSTEM_ALERT_WINDOW permission not granted...
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case STORAGE_PERMISSION_REQ_CODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Request for the READ_PHONE_STATE permission
                    checkPhoneStatePermission(this);

                    if (BuildConfig.DEBUG && !Settings.canDrawOverlays(this)) {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                   Uri.parse("package:" + getPackageName()));
                        startActivityForResult(intent, OVERLAY_PERMISSION_REQ_CODE);
                    }
                } else {
                    // Permission not granted. Show a message and terminate the application
                    Toast.makeText(this,
                        "LBRY requires access to your device storage to be able to download files and media." +
                        " Please enable the storage permission and restart the app.", Toast.LENGTH_LONG).show();
                    if (serviceRunning) {
                        ServiceHelper.stop(this, LbrynetService.class);
                    }
                    finish();
                }
                break;

            case PHONE_STATE_PERMISSION_REQ_CODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission granted
                    acquireDeviceId(this);
                } else {
                    // Permission not granted. Simply show a message.
                    Toast.makeText(this,
                        "No permission granted to read your device state. Rewards cannot be claimed.", Toast.LENGTH_LONG).show();
                }
                break;
        }
    }

    public static String acquireDeviceId(Context context) {
        TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        String id = null;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                id = telephonyManager.getImei(); // GSM
                if (id == null) {
                    id = telephonyManager.getMeid(); // CDMA
                }
            } else {
                id = telephonyManager.getDeviceId();
            }

        } catch (SecurityException ex) {
            // Maybe the permission was not granted? Try to acquire permission
            checkPhoneStatePermission(context);
        } catch (Exception ex) {
            // id could not be obtained. Display a warning that rewards cannot be claimed.
        }

        if (id == null || id.trim().length() == 0) {
            Toast.makeText(context, "Rewards cannot be claimed because we could not identify your device.", Toast.LENGTH_LONG).show();
        }

        try {
            MessageDigest md = MessageDigest.getInstance("SHA-384");
            md.update(id.getBytes("UTF-8"));
            String hash = new BigInteger(1, md.digest()).toString(16);

            SharedPreferences sp = context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sp.edit();
            editor.putString(DEVICE_ID_KEY, hash);
            editor.commit();
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException ex) {
            // SHA-384 not found, UTF-8 encoding not supported
            Toast.makeText(context, "Rewards cannot be claimed because we could not identify your device.", Toast.LENGTH_LONG).show();
        }

        return id;
    }

    @Override
    public void invokeDefaultOnBackPressed() {
        super.onBackPressed();
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mReactInstanceManager != null) {
            mReactInstanceManager.onHostPause(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        SharedPreferences sp = getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
        serviceRunning = isServiceRunning(LbrynetService.class);
        if (!serviceRunning) {
            ServiceHelper.start(this, "", LbrynetService.class, "lbrynetservice");
        }

        if (mReactInstanceManager != null) {
            mReactInstanceManager.onHostResume(this, this);
        }
    }

    @Override
    protected void onDestroy() {
        // check service running setting and end it here
        SharedPreferences sp = getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
        boolean shouldKeepDaemonRunning = sp.getBoolean("keepDaemonRunning", true);
        if (!shouldKeepDaemonRunning) {
            serviceRunning = isServiceRunning(LbrynetService.class);
            if (serviceRunning) {
               ServiceHelper.stop(this, LbrynetService.class);
            }
        }

        super.onDestroy();

        if (mReactInstanceManager != null) {
            mReactInstanceManager.onHostDestroy(this);
        }
    }

    @Override
    public void onBackPressed() {
        if (mReactInstanceManager != null) {
            mReactInstanceManager.onBackPressed();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        if (mReactInstanceManager != null) {
            mReactInstanceManager.onNewIntent(intent);
        }
        super.onNewIntent(intent);
    }

    private static void checkPermission(String permission, int requestCode, String rationale, Context context) {
        if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale((Activity) context, permission)) {
                Toast.makeText(context, rationale, Toast.LENGTH_LONG).show();
            } else {
                ActivityCompat.requestPermissions((Activity) context, new String[] { permission }, requestCode);
            }
        }
    }

    private static void checkPhoneStatePermission(Context context) {
        // Request read phone state permission
        checkPermission(Manifest.permission.READ_PHONE_STATE,
                        PHONE_STATE_PERMISSION_REQ_CODE,
                        "LBRY requires optional access to be able to identify your device for rewards. " +
                        "You cannot claim rewards without this permission.",
                        context);
    }

    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo serviceInfo : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(serviceInfo.service.getClassName())) {
                return true;
            }
        }

        return false;
    }
}
