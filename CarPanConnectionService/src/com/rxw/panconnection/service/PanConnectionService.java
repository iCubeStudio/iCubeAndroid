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
        setForegroundService();
        startWifiAp();
    }

    private void startWifiAp() {
        
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

        mWifiTetheringHandler.softApConfigureAndStartHotspot();
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

        if (mWifiTetheringHandler != null) {
            mWifiTetheringHandler.onStopInternal();
        }
    }

    private void setForegroundService() {
        NotificationChannel notificationChannel = new NotificationChannel("PanConnectionService", "PanConnectionService", NotificationManager.IMPORTANCE_LOW);
        Notification.Builder notificationBuilder = new Notification.Builder(getApplicationContext(), "PanConnectionService");
        notificationBuilder.setSmallIcon(android.R.drawable.sym_def_app_icon);

        NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.createNotificationChannel(notificationChannel);
        startForeground(-999, notificationBuilder.build());
    }
}
