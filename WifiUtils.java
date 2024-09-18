package com.rxw.panconnection.service.wifi;

import android.content.Context;
import android.content.Intent;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.util.Log;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class WifiUtils {

    private static final String TAG = "WifiUtils";
    private static final String TARGET_SSID = "SSID";
    private static final String TARGET_PASSWORD = "12345678";  

    // 设备类型定义
    private static final String DEVICE_TYPE_UNKNOWN = "UNKNOW";  //未知类型
    private static final String DEVICE_TYPE_CAMERA = "CAMERA";  //摄像头
    private static final String DEVICE_TYPE_FRAGRANCE = "FRAGRANCE"; //香氛机
    private static final String DEVICE_TYPE_ATMOSPHERE_LIGHT = "ATMOSPHERE_LIGHT";  //氛围灯
    private static final String DEVICE_TYPE_MIRROR = "MIRROR";  //美妆镜
    private static final String DEVICE_TYPE_DISINFECTION = "DISINFECTION";  //消毒器
    private static final String DEVICE_TYPE_HUMIDIFIER = "HUMIDIFIER"; //加湿器
    private static final String DEVICE_TYPE_MICROPHONE = "MICROPHONE";  //麦克风

    private WifiManager wifiManager;

    public WifiUtils(Context context) {
        wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
    }

    // 1. 发现/扫描附近Wi-Fi设备
    public JSONArray discoverNearbyWifiDevices() {
        wifiManager.startScan();
        List<ScanResult> results = wifiManager.getScanResults();
        JSONArray wifiDeviceList = new JSONArray();

        for (ScanResult result : results) {
            try {
                JSONObject wifiDevice = new JSONObject();
                wifiDevice.put("device_type", DEVICE_TYPE_UNKNOWN);  // 设备类型默认为UNKNOW
                wifiDevice.put("device_name", result.SSID);  // SSID
                wifiDevice.put("device_id", result.BSSID);   // MAC地址
                wifiDeviceList.put(wifiDevice);
                Log.i(TAG, "发现Wi-Fi设备: " + result.SSID);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return wifiDeviceList;
    }

    // 2. 停止发现/扫描附近Wi-Fi设备
    public void stopWifiScan() {
        wifiManager.disconnect();
        Log.i(TAG, "已停止Wi-Fi扫描");
    }

    // 3. 连接指定Wi-Fi设备 (使用默认SSID和密码)
    public void connectToWifiDevice(String deviceMacAddress) {
        connectToWifiDevice(deviceMacAddress, TARGET_SSID, TARGET_PASSWORD);
    }

    // 3. 连接指定Wi-Fi设备 (自定义SSID和密码)
    public void connectToWifiDevice(String deviceMacAddress, String ssid, String password) {
        List<ScanResult> results = wifiManager.getScanResults();
        for (ScanResult result : results) {
            if (result.BSSID.equals(deviceMacAddress)) {
                Log.i(TAG, "正在连接到设备: " + result.SSID);
                connectToWifi(result.SSID, password);
                break;
            }
        }
    }

    // 连接到Wi-Fi
    private void connectToWifi(String ssid, String password) {
        WifiConfiguration wifiConfig = new WifiConfiguration();
        wifiConfig.SSID = "\"" + ssid + "\"";
        wifiConfig.preSharedKey = "\"" + password + "\"";
        int netId = wifiManager.addNetwork(wifiConfig);
        wifiManager.disconnect();
        wifiManager.enableNetwork(netId, true);
        wifiManager.reconnect();
        Log.i(TAG, "已连接到Wi-Fi: " + ssid);
    }

    // 4. 解绑Wi-Fi设备
    public void unbindWifiDevice(String deviceMacAddress) {
        List<WifiConfiguration> configuredNetworks = wifiManager.getConfiguredNetworks();
        for (WifiConfiguration config : configuredNetworks) {
            if (config.BSSID != null && config.BSSID.equals(deviceMacAddress)) {
                wifiManager.removeNetwork(config.networkId);
                Log.i(TAG, "已解绑设备: " + deviceMacAddress);
                break;
            }
        }
    }

    // 5. 获取当前已连接的设备列表
    public List<String> getConnectedDevices(String deviceType) {
        List<String> connectedDevices = new ArrayList<>();
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();

        if (wifiInfo != null && wifiInfo.getNetworkId() != -1) {
            connectedDevices.add(wifiInfo.getSSID().replace("\"", ""));
            Log.i(TAG, "已连接的设备名称: " + wifiInfo.getSSID());
        } else {
            Log.i(TAG, "未连接到任何设备");
        }

        return connectedDevices;
    }

}
