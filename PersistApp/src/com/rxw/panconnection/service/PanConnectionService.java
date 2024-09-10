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

import com.rxw.panconnection.service.wifi.WifiTetheringHandler;

public class PanConnectionService extends Service 
{

    private static final String TAG = "PanConnectionService";
    private WifiTetheringHandler mWifiTetheringHandler;

    @Override
    public void onCreate() 
    {
        super.onCreate();
        setForegroundService();
        startWifiAp();

        registerReceiver(mConnectionReceiver, new IntentFilter("com.rxw.panconnection.ALLOW_CONNECTION"));
    }

    private final BroadcastReceiver mConnectionReceiver = new BroadcastReceiver() 
    {
        @Override
        public void onReceive(Context context, Intent intent) 
        {
            if ("com.rxw.panconnection.ALLOW_CONNECTION".equals(intent.getAction())) 
            {
                String macAddressStr = intent.getStringExtra("mac_address");
                if (macAddressStr != null) 
                {
                    MacAddress macAddress = MacAddress.fromString(macAddressStr);
                    mWifiTetheringHandler.allowClientConnection(macAddress);
                    Log.d(TAG, "Allowed device with MAC: " + macAddressStr);
                }
            }
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
        return null;
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
        try 
        {
            unregisterReceiver(mConnectionReceiver);
        } 
        catch (IllegalArgumentException e) 
        {
            Log.w(TAG, "Receiver was not registered.");
        }
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
