package com.rxw.panconnection.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import com.rxw.panconnection.service.wifi.WifiTetheringHandler;

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
        mWifiTetheringHandler = new WifiTetheringHandler(getApplicationContext(), new WifiTetheringHandler.WifiTetheringAvailabilityListener() {
            @Override
            public void onWifiTetheringAvailable() {
                Log.d(TAG, "onWifiTetheringAvailable");
            }

            @Override
            public void onWifiTetheringUnavailable() {
                Log.d(TAG, "onWifiTetheringUnavailable");
            }

        });

        mWifiTetheringHandler.configureAndStartHotspot();

    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
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
