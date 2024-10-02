package com.rxw.panconnection.service;

import android.app.Application;
import android.content.Intent;

public class PanConnectionServiceApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        startPanConnectionService();
        startWifiConnectionManagerService();
    }

    private void startPanConnectionService() {
        Intent intent = new Intent(this, PanConnectionService.class);
        startService(intent);
    }

    private void startWifiConnectionManagerService() {
        Intent intent = new Intent(this, WifiConnectionManagerService.class);
        startService(intent);
    }

}
