package com.example.jian;

import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.util.Log;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;

import androidx.core.app.JobIntentService;

public class WifiJobIntentService extends JobIntentService
{
    private static final String TAG = "WifiJobIntentService";
    private static final String TARGET_SSID = "J1511";
    private static final String TARGET_PASSWORD = "kcdsj151101";

    public static void enqueueWork(Context context, Intent intent)
    {
        enqueueWork(context, WifiJobIntentService.class, 1001, intent);
    }

    @Override
    protected void onHandleWork(Intent intent)
    {
        Log.d(TAG, "Handling work in WifiJobIntentService...");

        WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
        if (wifiManager != null)
        {
            handleWifiConnection(wifiManager);
        }
        else
        {
            Log.e(TAG, "WifiManager is null, cannot connect to Wi-Fi.");
        }
    }

    private void handleWifiConnection(WifiManager wifiManager)
    {
        if (!wifiManager.isWifiEnabled())
        {
            wifiManager.setWifiEnabled(true);
        }

        WifiInfo currentWifi = wifiManager.getConnectionInfo();
        String currentSSID = currentWifi.getSSID();

        // 核查是否已经连接到目标 Wi-Fi
        if (currentSSID != null && currentSSID.equals("\"" + TARGET_SSID + "\""))
        {
            Log.d(TAG, "已连接目标Wi-Fi，不需要再次连接！");
            logConnectionInfo(wifiManager);
            return;
        }

        ConnectivityManager connManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        if (networkInfo != null && networkInfo.isConnected())
        {
            Log.d(TAG, "当前已连接到非目标Wi-Fi，正在断开连接...");
            wifiManager.disconnect();
        }

        connectToTargetWifi(wifiManager);
    }

    private void connectToTargetWifi(WifiManager wifiManager)
    {
        WifiConfiguration wifiConfig = new WifiConfiguration();
        wifiConfig.SSID = "\"" + TARGET_SSID + "\"";
        wifiConfig.preSharedKey = "\"" + TARGET_PASSWORD + "\"";

        int netId = wifiManager.addNetwork(wifiConfig);
        if (netId != -1)
        {
            wifiManager.disconnect();
            wifiManager.enableNetwork(netId, true);
            wifiManager.reconnect();
            Log.d(TAG, "正在连接到Wi-Fi: " + TARGET_SSID);

            //连接成功后，加载信息
            logConnectionInfo(wifiManager);
        }
        else
        {
            Log.e(TAG, "无法添加网络配置，SSID: " + TARGET_SSID);
        }
    }

    private void logConnectionInfo(WifiManager wifiManager)
    {
        WifiInfo wifiInfo;
        int maxRetry = 10;
        int retry = 0;

        while (retry < maxRetry)
        {
            wifiInfo = wifiManager.getConnectionInfo();
            if (wifiInfo != null && wifiInfo.getIpAddress() != 0)
            {
                int ipAddress = wifiInfo.getIpAddress();
                String ipString = String.format(
                        "%d.%d.%d.%d",
                        (ipAddress & 0xff),
                        (ipAddress >> 8 & 0xff),
                        (ipAddress >> 16 & 0xff),
                        (ipAddress >> 24 & 0xff)
                );
                String macAddress = wifiInfo.getMacAddress();
                Log.d(TAG, "Wi-Fi连接成功! SSID: " + TARGET_SSID + ", IP地址: " + ipString + ", MAC地址: " + macAddress);
                return;
            }
            try
            {
                Thread.sleep(1000); // 等待1秒
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
            retry++;
        }

        Log.e(TAG, "无法获取有效的IP地址!");
    }

}
