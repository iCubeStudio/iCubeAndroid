package com.rxw.panconnection.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import android.net.wifi.WifiManager;
import android.net.TetheringManager;
import android.net.ConnectivityManager;
import android.net.TetheringManager;
import android.net.wifi.SoftApConfiguration;
import android.net.wifi.SoftApCapability;
import android.net.wifi.SoftApInfo;
import android.net.wifi.WifiClient;
import java.util.List;

import com.rxw.panconnection.service.wifi.WifiTetheringHandler;
import com.rxw.panconnection.service.wifi.WifiTetheringObserver;
import com.rxw.panconnection.service.wifi.WifiTetheringAvailabilityListener;

public class PanConnectionService extends Service {

    private static final String TAG = "PanConnectionService";
    private WifiTetheringHandler mWifiTetheringHandler;

    @Override
    public void onCreate() {
        super.onCreate();
        // Set the service as a foreground service to avoid being killed by the system
        setForegroundService();
        // Initialize and start WiFi AP
        startWifiAp();
        // Test WifiTetheringObserver
        testWifiTetheringObserver();
    }

    /**
     * Initializes the WiFi AP and starts it.
     */
    private void startWifiAp() {
        // Get system services for WiFi and tethering
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        TetheringManager tetheringManager = (TetheringManager) getApplicationContext().getSystemService(Context.TETHERING_SERVICE);

        mWifiTetheringHandler = new WifiTetheringHandler(getApplicationContext(), wifiManager, tetheringManager, new WifiTetheringAvailabilityListener() {
            @Override
            public void onWifiTetheringAvailable() {
                Log.d(TAG, "onWifiTetheringAvailable!");
            }

            @Override
            public void onWifiTetheringUnavailable() {
                Log.d(TAG, "onWifiTetheringUnavailable!");
            }

            @Override
            public void onConnectedClientsChanged(int clientCount) {
                Log.d(TAG, "onConnectedClientsChanged, clientCount: " + clientCount);
            }

        });

        // Configure and start the hotspot
        mWifiTetheringHandler.softApConfigureAndStartHotspot();
    }

    /**
     * Test WifiTetheringObserver
     */
    public void testWifiTetheringObserver() {
        mWifiTetheringHandler.registerObserver(new WifiTetheringObserver() {

            @Override
            public void onDiscoveryStateChanged(String deviceList, int discoveryState) {
                Log.d(TAG, "onDiscoveryStateChanged, deviceList: " + deviceList.toString() + ", discoveryState: " + discoveryState);
            }
            
            @Override
            public void onConnectionStateChanged(int deviceType, String macAddress, int state) {
                Log.d(TAG, "onConnectionStateChanged, deviceType: " + deviceType + ", macAddress: " + macAddress + ", state: " + state);
            }

            @Override
            public void onConnectionFailed(int deviceType, String macAddress, int blockedReason) {
                Log.d(TAG, "onConnectionFailed, deviceType: " + deviceType + ", macAddress: " + macAddress + ", blockedReason: " + blockedReason);
            }

            @Override
            public void onUnbondStateChanged(int deviceType, String macAddress, int state) {
                Log.d(TAG, "onUnbondStateChanged, deviceType: " + deviceType + ", macAddress: " + macAddress + ", state: " + state);
            }

            @Override
            public void onUnbondFailed(int deviceType, String macAddress, int errorCode) {
                Log.d(TAG, "onUnbondFailed, deviceType: " + deviceType + ", macAddress: " + macAddress + ", errorCode: " + errorCode);
            }


            /*===================================================================*
             * Getter Observer                                          
             *===================================================================*/
            @Override
            public void getOnStateChanged(int state, int failureReason) {
                Log.d(TAG, "getSoftApState, state: " + state + ", failureReason: " + failureReason);
            }

            @Override
            public void getOnConnectedClientsChanged(SoftApInfo info, List<WifiClient> clients) {
                Log.d(TAG, "onConnectedClientsChanged" + clients + ", info: " + info);
            }

            @Override
            public void getOnBlockedClientConnecting(WifiClient client, int blockedReason) {
                Log.d(TAG, "onBlockedClientConnecting: " + client.getMacAddress().toString() + ", blockedReason: " + blockedReason);
            }

            @Override
            public void getOnCapabilityChanged(SoftApCapability softApCapability) {
                Log.d(TAG, "onCapabilityChanged: " + softApCapability);
            }

            @Override
            public void getOnInfoChanged(List<SoftApInfo> softApInfoList) {
                Log.d(TAG, "onInfoChanged: " + softApInfoList);
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        // Clean up resources when the service is destroyed
        if (mWifiTetheringHandler != null) {
            mWifiTetheringHandler.onStopInternal();
        }
    }

    /**
     * Sets this service as a foreground service with a notification.
     */
    private void setForegroundService() {
        NotificationChannel notificationChannel = new NotificationChannel("PanConnectionService", "PanConnectionService", NotificationManager.IMPORTANCE_LOW);
        Notification.Builder notificationBuilder = new Notification.Builder(getApplicationContext(), "PanConnectionService");
        notificationBuilder.setSmallIcon(android.R.drawable.sym_def_app_icon);

        NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.createNotificationChannel(notificationChannel);
        startForeground(-999, notificationBuilder.build());
    }
}
