package com.rxw.panconnection.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.net.MacAddress;
import android.os.RemoteException;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


import com.rxw.panconnection.service.aidl.IUniDeviceConnection;
import com.rxw.panconnection.service.aidl.IUniDeviceConnectionCallback;
import com.rxw.panconnection.service.wifi.WifiTetheringHandler;

public class PanConnectionService extends Service {
    private static final String TAG = "PanConnectionService";
    private WifiTetheringHandler mWifiTetheringHandler;

    // deviceType
    private static final String DEVICE_TYPE_UNKNOWN = "UNKNOW"; 
    private static final String DEVICE_TYPE_CAMERA = "CAMERA";  
    private static final String DEVICE_TYPE_FRAGRANCE = "FRAGRANCE"; 
    private static final String DEVICE_TYPE_ATMOSPHERE_LIGHT = "ATMOSPHERE_LIGHT";  
    private static final String DEVICE_TYPE_MIRROR = "MIRROR";  
    private static final String DEVICE_TYPE_DISINFECTION = "DISINFECTION";  
    private static final String DEVICE_TYPE_HUMIDIFIER = "HUMIDIFIER"; 
    private static final String DEVICE_TYPE_MICROPHONE = "MICROPHONE";  

    // discoveryState
    private static final int DISCOVERY_SEARCHING = 1;
    private static final int DISCOVERY_END = 2;

    // connectionState
    private static final int CONNECTING = 3;
    private static final int CONNECTED = 4;
    private static final int DISCONNECTING = 5;
    private static final int DISCONNECTED = 6;
    
    // bondState
    private static final int UNBONDING = 7;
    private static final int UNBONDED = 8;

    // errorCode
    private static final int UNFOUNDDEVICE = 404;
    private static final int INTERNALERROR = 500;

    @Override
    public void onCreate() 
    {
        super.onCreate();
        setForegroundService();
        startWifiAp();
    }

    private final IUniDeviceConnection.Stub mBinder = new IUniDeviceConnection.Stub() {
         
        // Discovery UniDevice (wifi)
        public void discoverUniDevice(IUniDeviceConnectionCallback callback) throws RemoteException
        {
            String deviceList = mWifiTetheringHandler.getMultiDeviceArray();
            if (mWifiTetheringHandler.isDiscoveryTAG()) {
                callback.onDiscoveryStateChanged(deviceList, DISCOVERY_SEARCHING);
                Log.d(TAG, "Calling hotspot AIDL interface! Discovering...");
            }
        }

        // Stop Discovery UniDevice (wifi)
        public void stopDiscoverUniDevice(IUniDeviceConnectionCallback callback) throws RemoteException
        {
            String deviceList = mWifiTetheringHandler.getMultiDeviceArray();
            if (!mWifiTetheringHandler.isDiscoveryTAG())
            {
                callback.onDiscoveryStateChanged(deviceList, DISCOVERY_END);
                Log.d(TAG, "Calling hotspot AIDL interface! Discovery complete!");
            }
        }

        // Connect UniDevice (wifi)
        @Override
        public void connectUniDevice(String deviceType, String deviceId, IUniDeviceConnectionCallback callback) throws RemoteException 
        {
            try {
                List<String> connectedDevices = mWifiTetheringHandler.getConnectedDeviceMacs();
                List<String> blockedDeviceMacs = mWifiTetheringHandler.getBlockedDeviceMacs();

                // Connecting
                if (blockedDeviceMacs.contains(deviceId)) {
                    mWifiTetheringHandler.unblockDevice(deviceId);
                    callback.onConnectionStateChanged(deviceType, deviceId, CONNECTING); 
                    Log.d(TAG, "Calling hotspot AIDL interface! UniDevice connectiNG: " + deviceId);
                }
                // Connected
                else if (connectedDevices.contains(deviceId)) {
                    callback.onUnbondStateChanged(deviceType, deviceId, CONNECTED); 
                    Log.d(TAG, "Calling hotspot AIDL interface! UniDevice connected: " + deviceId);
                }
                // Disconnecting
                else if (mWifiTetheringHandler.isDisconnectingDevice(deviceId)) {
                    callback.onUnbondStateChanged(deviceType, deviceId, DISCONNECTING); 
                    Log.d(TAG, "Calling hotspot AIDL interface! UniDevice disconnecting: " + deviceId);
                }
                // Disconnected
                else if (!blockedDeviceMacs.contains(deviceId) && !connectedDevices.contains(deviceId)) {
                    callback.onUnbondStateChanged(deviceType, deviceId, DISCONNECTED); 
                    Log.d(TAG, "Calling hotspot AIDL interface! UniDevice disconnected: " + deviceId);
                }
                else {
                    callback.onConnectionFailed(deviceType, deviceId, UNFOUNDDEVICE); 
                    Log.e(TAG, "Calling hotspot AIDL interface! Connection failed, UniDevice not found: " + deviceId);
                }
            } catch (Exception e) {
                Log.e(TAG, "Calling hotspot AIDL interface! UniDevice connection error: ", e);
                callback.onConnectionFailed(deviceType, deviceId, INTERNALERROR); 
            }
        }

        // unbond UniDevice (wifi)
        @Override
        public void removeBondedUniDevice(String deviceType, String deviceId, IUniDeviceConnectionCallback callback) throws RemoteException 
        {
            try {
                List<String> connectedDevices = mWifiTetheringHandler.getConnectedDeviceMacs();
                List<String> blockedDeviceMacs = mWifiTetheringHandler.getBlockedDeviceMacs();

                // Unbonding
                if (connectedDevices.contains(deviceId)) {
                    mWifiTetheringHandler.blockDevice(deviceId);
                    callback.onUnbondStateChanged(deviceType, deviceId, UNBONDING); 
                    Log.d(TAG, "Calling hotspot AIDL interface! UniDevice unbonding: " + deviceId);
                }
                // Unbonded
                else if (blockedDeviceMacs.contains(deviceId)) {
                    callback.onUnbondStateChanged(deviceType, deviceId, UNBONDED); 
                    Log.d(TAG, "Calling hotspot AIDL interface! UniDevice unbonded: " + deviceId);
                }
                else {
                    callback.onUnbondFailed(deviceType, deviceId, UNFOUNDDEVICE); 
                    Log.e(TAG, "Calling hotspot AIDL interface! UniDevice Unbond failed: " + deviceId);
                }
            } catch (Exception e) {
                Log.e(TAG, "Calling hotspot AIDL interface! UniDevice Unbond error: ", e);
                callback.onUnbondFailed(deviceType, deviceId, INTERNALERROR); 
            }
        }

        // Retrieve connected Unidevices of a specific type
        @Override
        public String getConnectedUniDevices(String deviceType) throws RemoteException {
            Log.d(TAG, "Calling hotspot AIDL interface! Retrieve connected Unidevices: " + deviceType);
            JSONArray deviceArray = new JSONArray();
            for (String macAddress : mWifiTetheringHandler.getConnectedDeviceMacs()) {
                JSONObject deviceObject = new JSONObject();
                try {
                    deviceObject.put("device_type", deviceType);
                    deviceObject.put("device_name", "Device Name"); 
                    deviceObject.put("device_id", macAddress);
                    deviceArray.put(deviceObject);
                } catch (Exception e) {
                    Log.e(TAG, "Error creating JSON object", e);
                }
            }
            return deviceArray.toString();
        }
    };

    private void startWifiAp() 
    {
        mWifiTetheringHandler = new WifiTetheringHandler(getApplicationContext(), new WifiTetheringHandler.WifiTetheringAvailabilityListener() 
        {
            @Override
            public void onWifiTetheringAvailable() 
            {
                Log.d(TAG, "onWifiTetheringAvailable");
            }

            @Override
            public void onWifiTetheringUnavailable() 
            {
                Log.d(TAG, "onWifiTetheringUnavailable");
            }

        });

        mWifiTetheringHandler.configureAndStartHotspot();
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) 
    {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) 
    {
        return mBinder;
    }
    
    private void setForegroundService() 
    {
        NotificationChannel notificationChannel = new NotificationChannel(
                "PanConnectionService",
                "PanConnectionService",
                NotificationManager.IMPORTANCE_LOW);

        Notification.Builder notificationBuilder =
                new Notification.Builder(getApplicationContext(), "PanConnectionService");
                
        notificationBuilder.setSmallIcon(android.R.drawable.sym_def_app_icon);

        NotificationManager notificationManager =
                (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.createNotificationChannel(notificationChannel);
        startForeground(-999, notificationBuilder.build());
    }
}