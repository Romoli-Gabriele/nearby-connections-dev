package com.native1;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Callback;
import android.Manifest;
import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.app.Activity;
import com.facebook.react.modules.core.PermissionAwareActivity;
import com.facebook.react.modules.core.PermissionListener;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.messages.Message;
import com.google.android.gms.nearby.messages.MessageListener;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.ConnectionResult;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.Arguments;
import com.google.android.gms.nearby.messages.MessagesClient;
import com.google.android.gms.nearby.messages.MessagesOptions;
import com.google.android.gms.nearby.messages.NearbyPermissions;
import com.google.android.gms.nearby.messages.PublishOptions;
import com.google.android.gms.nearby.messages.StatusCallback;
import com.google.android.gms.nearby.messages.Strategy;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.os.BuildCompat;

import java.util.ArrayList;

public class MyNativeModule extends ReactContextBaseJavaModule  implements PermissionListener {

    private static ReactApplicationContext reactContext;
    private Message message;
    private MessageListener messageListener;
    private Intent serviceIntent;

    private static final int REQUEST_ENABLE_BT = 1;
    private static final int MY_PERMISSIONS_REQUEST_CODE = 100;

    private String messageText;

    private MessagesClient messagesClient;


    MyNativeModule(ReactApplicationContext context) {
        super(context);
        this.reactContext = context;
    }

    @Override
    public String getName() {
        return "MyNativeModule";
    }

    // REACT-NATIVE: AVVIA
    @ReactMethod
    public void start(String message) {
        // Imostro il testo del messaggio
        messageText = message;
        // Avvia tutto
        startAll();
    }

    // REACT-NATIVE: STOP SCAN e PUBLISH
    @ReactMethod
    public void stop() {
        Activity currentActivity = getCurrentActivity();
        if (currentActivity != null) {
            if (this.message != null) {
                messagesClient.unpublish(this.message);
                this.message = null;
                emitMessageEvent("onActivityStop", "stopped"); // @remove
            }
            if (this.messageListener != null)
                Nearby.getMessagesClient(currentActivity).unsubscribe(this.messageListener);
            if (this.serviceIntent != null) {
                currentActivity.stopService(this.serviceIntent);
                emitMessageEvent("onActivityStop", "stopped");
                Log.d("ReactNative", "KILLING SERVICE FROM FOREGROUND");
            }
        }

    }


    // REACT-NATIVE: SE IL SERVIZIO E' ATTIVO
    @ReactMethod
    public void isActivityRunning(Callback callBack) {
        Boolean running = isServiceRunning();
        callBack.invoke(running);
    }



    // ---------------------------------------------------------------------------------------------------------------



    // AVVIA TUTTO
    void startAll(){
        Activity currentActivity = getCurrentActivity();
        if (currentActivity == null) return;

        // Controlla se Google Play Services è disponibile
        boolean isGooglePlayServicesAvailable = isGooglePlayServicesAvailable();
        if(!isGooglePlayServicesAvailable) {
            GoogleApiAvailability.getInstance().makeGooglePlayServicesAvailable(currentActivity);
            Log.d("ReactNative", "Google Play Services not available");
            emitMessageEvent("onGooglePlayServicesNotAvailable", "Google Play Services not available");
            return;
        }
        // Controlla i permessi
        boolean isPermissionGranted = checkPermissions();
        if(!isPermissionGranted) {
            Log.d("ReactNative", "Permissions not granted");
            return;
        }

        // Inizializzazione
        initAll();
        // avvia scansione
        startScan();
        // avvia servizio backgrund di publish
       //startPublishActivity(messageText);
        sendMessage();
    }

    // INIZIALIZZA EVENTI
    void initAll(){

        BluetoothManager bluetoothManager = (BluetoothManager) getCurrentActivity().getSystemService(reactContext.BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();

        // Verifica se il Bluetooth è attivato sul dispositivo
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            getCurrentActivity().startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            Log.d("ReactNative", "MISSING BLUETOOTH ADAPTER");
        }



        this.messageListener = new MessageListener() {
            @Override
            public void onFound(Message message) {
                Log.d("ReactNative", "Found message: " + new String(message.getContent()));
                emitMessageEvent("onMessageFound", new String(message.getContent()));
            }

            @Override
            public void onLost(Message message) {
                Log.d("ReactNative", "Lost sight of message: " + new String(message.getContent()));
                emitMessageEvent("onMessageLost", new String(message.getContent()));
            }
        };


        messagesClient = Nearby.getMessagesClient(getCurrentActivity(),  new MessagesOptions.Builder()
                .setPermissions(NearbyPermissions.BLE)
                .build());
    }


    public void startPublishActivity(String message) {
        if (!isServiceRunning()) {
            Log.d("ReactNative", "INIZIALIZZAZIONE FOREGROUND SERVICE");

            Activity currentActivity = getCurrentActivity();
            this.serviceIntent = new Intent(currentActivity, MyForegroundService.class);
            Bundle bundle = new Bundle();
            bundle.putString("message", message);
            this.serviceIntent.putExtras(bundle);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                currentActivity.startForegroundService(this.serviceIntent);
            } else {
                currentActivity.startService(this.serviceIntent);
            }
            emitMessageEvent("onActivityStart", "started");
        }
    }

    void sendMessage(){
        Activity currentActivity = getCurrentActivity();
        if (currentActivity != null) {
            this.message = new Message(messageText.getBytes());


            Strategy strategy = new Strategy.Builder().setTtlSeconds(Strategy.TTL_SECONDS_MAX).build();
            PublishOptions options = new PublishOptions.Builder().setStrategy(strategy).build();

            messagesClient.publish(this.message,options);


            emitMessageEvent("onActivityStart", "started");
        }



    }

    // START SCAN
    public void startScan() {
        Activity currentActivity = getCurrentActivity();
        if (currentActivity != null) {
            Nearby.getMessagesClient(currentActivity).registerStatusCallback(new StatusCallback() {
                @Override
                public void onPermissionChanged(boolean b) {
                    Log.d("ReactNative", "onPermissionChanged: " + b);
                    super.onPermissionChanged(b);
                }
            });
            Nearby.getMessagesClient(currentActivity).subscribe(this.messageListener).addOnCompleteListener(new OnCompleteListener<Void>() {
                @Override
                public void onComplete(@NonNull Task<Void> task) {
                    Log.d("ReactNative", "is scan success: "+  task.isSuccessful());
                }
            });
        }
    }

    // SE SONO DISPONIBILI I PLAY SERVICES
    private boolean isGooglePlayServicesAvailable() {
        Activity currentActivity = getCurrentActivity();

        final GoogleApiAvailability googleApi = GoogleApiAvailability.getInstance();
        final int availability = googleApi.isGooglePlayServicesAvailable(currentActivity);
        final boolean result = availability == ConnectionResult.SUCCESS;
        if (!result && googleApi.isUserResolvableError(availability)) {
            googleApi.getErrorDialog(currentActivity, availability, 9000).show();
        }
        return result;
    }

// INVIA MESSAGGIO A REACT-NATIVE
    private void emitMessageEvent(String eventName, String message) {
        WritableMap params = Arguments.createMap();
        params.putString("message", message);

        reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, params);
    }

    // SE IL SERVIZIO E' ATTIVO
    private boolean isServiceRunning() {
        Activity context = getCurrentActivity();

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && context!=null) {
            ActivityManager manager = (ActivityManager) context.getSystemService(context.ACTIVITY_SERVICE);
            for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
                if (MyForegroundService.class.getName().equals(service.service.getClassName())) {
                    return service.foreground;
                }
            }
            return false;
        }
        return false;
    }


    // OTTIENE LO STATUS DEL GPS
    public static boolean checkGPSStatus(){
        Context context = reactContext.getCurrentActivity();
        LocationManager manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        return manager.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }
    // OTTIENE LO STATUS DEI PERESSI
    private Boolean checkPermissions() {
        PermissionAwareActivity activity = (PermissionAwareActivity) getCurrentActivity();
        if (activity == null) return false;

        ArrayList<String> missingPermissionsList = new ArrayList<String>();

        // ACCESS_FINE_LOCATION
        if (ActivityCompat.checkSelfPermission(reactContext.getCurrentActivity(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            missingPermissionsList.add(Manifest.permission.ACCESS_FINE_LOCATION);
            Log.d("ReactNative", "Missing ACCESS_FINE_LOCATION");
        }
        // Android >= 12
        if (BuildCompat.isAtLeastS()) {
            // BLUETOOTH_CONNECT
            if (ActivityCompat.checkSelfPermission(reactContext.getCurrentActivity(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                missingPermissionsList.add(Manifest.permission.BLUETOOTH_CONNECT);
                Log.d("ReactNative", "Missing BLUETOOTH_CONNECT");
            }
            // BLUETOOTH_SCAN
            if (ActivityCompat.checkSelfPermission(reactContext.getCurrentActivity(), Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                missingPermissionsList.add(Manifest.permission.BLUETOOTH_SCAN);
                Log.d("ReactNative", "Missing BLUETOOTH_SCAN");
            }
            // BLUETOOTH_ADVERTISE
            if (ActivityCompat.checkSelfPermission(reactContext.getCurrentActivity(), Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
                missingPermissionsList.add(Manifest.permission.BLUETOOTH_ADVERTISE);
                Log.d("ReactNative", "Missing BLUETOOTH_ADVERTISE");
            }
        }
        // Android < 12
        else {
            Log.d("ReactNative", "Android < 12");
            // BLUETOOTH
            if (ActivityCompat.checkSelfPermission(reactContext.getCurrentActivity(), Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                missingPermissionsList.add(Manifest.permission.BLUETOOTH);
                Log.d("ReactNative", "Missing BLUETOOTH");
            }
            // BLUETOOTH_ADMIN
            if (ActivityCompat.checkSelfPermission(reactContext.getCurrentActivity(), Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
                missingPermissionsList.add(Manifest.permission.BLUETOOTH_ADMIN);
                Log.d("ReactNative", "Missing BLUETOOTH_ADMIN");
            }
        }

        int size = missingPermissionsList.size();
        if(size == 0 ) {
            // Controlla se il GPS è attivo
            boolean gpsStatus = checkGPSStatus();
            if(gpsStatus == false) {
                Log.d("Permissions", "MISSING GPS");

                emitMessageEvent("gpsOff",  "MISSING GPS");
                //  Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                //   getCurrentActivity().startActivity(intent);
                return false;
            }
            return true;
        }

        String[] permissions = new String[size];
        permissions = missingPermissionsList.toArray(permissions);
        // Richiede i permessi
        activity.requestPermissions( permissions, MY_PERMISSIONS_REQUEST_CODE,this);

        return false;
    }

    @Override
    public boolean onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if(requestCode == MY_PERMISSIONS_REQUEST_CODE) {
            Boolean success = true;

            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    Log.d("ReactNative", "Permission granted: " + permissions[i]);
                } else if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                    Log.d("ReactNative", "Permission NOT granted: " + permissions[i]);
                    success = false;
                }
            }
            if(success) {
                // START
                startAll();
            } else {
                emitMessageEvent("onPermissionsRejected", "Permissions rejected");
            }
        }

        return true;
    }
}