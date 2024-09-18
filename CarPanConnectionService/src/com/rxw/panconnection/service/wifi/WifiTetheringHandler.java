package com.rxw.panconnection.service.wifi;

import android.annotation.NonNull;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.TetheringManager;
import android.net.wifi.SoftApConfiguration;
import android.net.wifi.SoftApInfo;
import android.net.wifi.WifiClient;
import android.net.wifi.WifiManager;
import android.content.Intent;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import android.net.MacAddress;
import android.util.Log;
import com.android.internal.util.ConcurrentUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class WifiTetheringHandler {

    private final Context mContext;
    private final WifiManager mWifiManager;
    private final TetheringManager mTetheringManager;
    private final WifiTetheringAvailabilityListener mWifiTetheringAvailabilityListener;
    private boolean mRestartBooked = false;

    private List<String> connectedDeviceMacs = new ArrayList<>();
    private List<String> blockedDeviceMacs = new ArrayList<>();
    private List<String> disconnectingDeviceMacs = new ArrayList<>();  // 用来跟踪正在断开连接的设备
    private static JSONArray MultiDeviceArray = new JSONArray();
    
    private final static String TAG = "WifiTetheringHandler";
    private static final String TARGET_SSID = "SSID";
    private static final String TARGET_PASSWORD = "12345678";  
    private static final String ACTION_DISCOVERED_DEVICES = "com.rxw.ACTION_DISCOVERED_DEVICES";
    private static final String PACKAGE_DISCOVERED_DEVICES = "com.rxw.car.panconnection";

    // deviceType
    private static final String DEVICE_TYPE_UNKNOWN = "UNKNOW"; 
    private static final String DEVICE_TYPE_CAMERA = "CAMERA";  
    private static final String DEVICE_TYPE_FRAGRANCE = "FRAGRANCE"; 
    private static final String DEVICE_TYPE_ATMOSPHERE_LIGHT = "ATMOSPHERE_LIGHT";  
    private static final String DEVICE_TYPE_MIRROR = "MIRROR";  
    private static final String DEVICE_TYPE_DISINFECTION = "DISINFECTION";  
    private static final String DEVICE_TYPE_HUMIDIFIER = "HUMIDIFIER"; 
    private static final String DEVICE_TYPE_MICROPHONE = "MICROPHONE";  

    private static final boolean DISCOVERY_TAG = false;

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


    private final WifiManager.SoftApCallback mSoftApCallback = new WifiManager.SoftApCallback() 
    {
        @Override
        public void onStateChanged(int state, int failureReason) 
        {
            Log.d(TAG, "onStateChanged, state: " + state + ", failureReason: " + failureReason);
            handleWifiApStateChanged(state);
        }

        @Override
        public void onConnectedClientsChanged(@NonNull SoftApInfo info, @NonNull List<WifiClient> clients) 
        {
            DISCOVERY_TAG = true;
            Log.d(TAG, "onConnectedClientsChanged" + clients + ", info: " + info);

            List<String> currentDeviceMacs = new ArrayList<>();
            for (WifiClient client : clients) 
            {
                String deviceType = DEVICE_TYPE_CAMERA;
                String deviceName = DEVICE_TYPE_CAMERA;
                String macAddress = client.getMacAddress().toString();

                currentDeviceMacs.add(macAddress);
                
                MultiDeviceArray = new JSONArray();
                createMultiDeviceArray(deviceType, deviceName, macAddress);

                if (!connectedDeviceMacs.contains(macAddress)) 
                {
                    connectedDeviceMacs.add(macAddress); 
                    sendBroadcastToAPP(deviceType, deviceName, macAddress);
                    //blockDevice(macAddress);
                }
            }

            // 相减法：查找已经断开的设备Mac地址
            List<String> disconnectedDeviceMacs = new ArrayList<>(connectedDeviceMacs);
            disconnectedDeviceMacs.removeAll(currentDeviceMacs);

            // 将断开的设备标记为正在断开连接，并从 blockedDeviceMacs 中移除
            for (String macAddress : disconnectedDeviceMacs) 
            {
                if (!disconnectingDeviceMacs.contains(macAddress)) 
                {
                    disconnectingDeviceMacs.add(macAddress); 
                    Log.d(TAG, "设备正在断开连接: " + macAddress);

                    // 从 blockedDeviceMacs 中移除断开连接的设备
                    if (blockedDeviceMacs.contains(macAddress)) {
                        blockedDeviceMacs.remove(macAddress);
                        Log.d(TAG, "从 blockedDeviceMacs 移除 MAC 地址: " + macAddress);
                    }
                }
            }

            mWifiTetheringAvailabilityListener.onConnectedClientsChanged(clients.size());
            
            DISCOVERY_TAG = false;
        }
    };

    public boolean isDisconnectingDevice(String macAddress) 
    {
        return disconnectingDeviceMacs.contains(macAddress);
    }

    public List<String> getConnectedDeviceMacs() 
    {
        return connectedDeviceMacs;
    }

    public List<String> getBlockedDeviceMacs() 
    {
        return blockedDeviceMacs;
    }

    public boolean isDiscoveryTAG() 
    {
        return DISCOVERY_TAG;
    }
    
    private void sendBroadcastToAPP(String deviceType, String deviceName, String macAddress) 
    {
        try {
            Intent intent = new Intent(ACTION_DISCOVERED_DEVICES);
            intent.setPackage(PACKAGE_DISCOVERED_DEVICES);

            intent.putExtra("discovered_devices", createSingleDeviceArray(deviceType, deviceName, macAddress));  
            mContext.sendBroadcast(intent);

            Log.d(TAG, "Broadcast sent for new device: " + macAddress);
        } 
        catch (Exception e) 
        {
            Log.e(TAG, "Error creating broadcast JSON: ", e);
        }
    }

    public String createSingleDeviceArray(String deviceType, String deviceName, String macAddress) 
    {
        JSONArray deviceArray = new JSONArray();
        try {
            JSONObject deviceObject = new JSONObject();
            deviceObject.put("device_type", deviceType);  
            deviceObject.put("device_name", deviceName);
            deviceObject.put("device_id", macAddress);
            deviceArray.put(deviceObject);
        } 
        catch (JSONException e) 
        {
            Log.e(TAG, "Error creating device array JSON: ", e);
        }
        return deviceArray.toString(); 
    }

    public void createMultiDeviceArray(String deviceType, String deviceName, String macAddress) 
    {
        try {
            // 创建单个设备的 JSONObject
            JSONObject deviceObject = new JSONObject();
            deviceObject.put("device_type", deviceType);  
            deviceObject.put("device_name", deviceName);
            deviceObject.put("device_id", macAddress);

            // 将这个设备对象添加到 MultiDeviceArray 中
            MultiDeviceArray.put(deviceObject);
        } 
        catch (JSONException e) 
        {
            Log.e(TAG, "Error creating device array JSON: ", e);
        }
    }

    public static String getMultiDeviceArray() 
    {
        return MultiDeviceArray.toString();  
    }

    // 加锁（解绑泛设备）
    public void blockDevice(String macAddress) 
    {
        blockedDeviceMacs.add(macAddress);
        connectedDeviceMacs.remove(macAddress);

        SoftApConfiguration.Builder builder = new SoftApConfiguration.Builder();
        builder.setBlockedClientList(blockedDeviceMacs);
        SoftApConfiguration config = builder.build();
        mWifiManager.setSoftApConfiguration(config);
        updateWifiTetheringState(true);

        Log.d(TAG, "Device blocked: " + macAddress);
    }

    // 解锁（连接泛设备）
    public void unblockDevice(String macAddress) 
    {
        blockedDeviceMacs.remove(macAddress);
        connectedDeviceMacs.add(macAddress);

        SoftApConfiguration.Builder builder = new SoftApConfiguration.Builder();
        builder.setAllowedClientList(connectedDeviceMacs);
        SoftApConfiguration config = builder.build();
        mWifiManager.setSoftApConfiguration(config);
        updateWifiTetheringState(true);
    
        Log.d(TAG, "Device unblocked: " + macAddress);
    }


    public WifiTetheringHandler(Context context, WifiTetheringAvailabilityListener wifiTetherAvailabilityListener) {
        this(context, context.getSystemService(WifiManager.class), context.getSystemService(TetheringManager.class), wifiTetherAvailabilityListener);
    }

    private WifiTetheringHandler(Context context, WifiManager wifiManager, TetheringManager tetheringManager, WifiTetheringAvailabilityListener wifiTetherAvailabilityListener) {
        mContext = context;
        mWifiManager = wifiManager;
        mTetheringManager = tetheringManager;
        mWifiTetheringAvailabilityListener = wifiTetherAvailabilityListener;
    }

    public void configureAndStartHotspot() 
    {
        SoftApConfiguration.Builder builder = new SoftApConfiguration.Builder();
        builder.setSsid(TARGET_SSID)
                .setPassphrase(TARGET_PASSWORD, SoftApConfiguration.SECURITY_TYPE_WPA2_PSK)
                .setBand(SoftApConfiguration.BAND_2GHZ)
                .setMaxNumberOfClients(10)
                .setHiddenSsid(false);

        SoftApConfiguration config = builder.build();
        mWifiManager.setSoftApConfiguration(config);
        updateWifiTetheringState(true);
    }

    private void onStartInternal() {
        mWifiManager.registerSoftApCallback(mContext.getMainExecutor(), mSoftApCallback);
    }

    private void onStopInternal() {
        mWifiManager.unregisterSoftApCallback(mSoftApCallback);
    }

    public boolean isWifiTetheringEnabled() {
        return mWifiManager.isWifiApEnabled();
    }

    public void updateWifiTetheringState(boolean enable) {
        if (enable) {
            startTethering();
        } else {
            stopTethering();
        }
    }

    private void handleWifiApStateChanged(int state) {
        Log.d(TAG, "handleWifiApStateChanged, state: " + state);
        switch (state) {
            case WifiManager.WIFI_AP_STATE_ENABLING:
                break;
            case WifiManager.WIFI_AP_STATE_ENABLED:
                mWifiTetheringAvailabilityListener.onWifiTetheringAvailable();
                break;
            case WifiManager.WIFI_AP_STATE_DISABLING:
                mWifiTetheringAvailabilityListener.onWifiTetheringUnavailable();
                break;
            case WifiManager.WIFI_AP_STATE_DISABLED:
                mWifiTetheringAvailabilityListener.onWifiTetheringUnavailable();
                if (mRestartBooked) {
                    startTethering();
                    mRestartBooked = false;
                }
                break;
            default:
                mWifiTetheringAvailabilityListener.onWifiTetheringUnavailable();
                break;
        }
    }

    private void startTethering() {
        mTetheringManager.startTethering(ConnectivityManager.TETHERING_WIFI,
                ConcurrentUtils.DIRECT_EXECUTOR, new TetheringManager.StartTetheringCallback() 
                {
                    @Override
                    public void onTetheringFailed(int error) 
                    {
                        Log.d(TAG, "onTetheringFailed, error: " + error);
                        mWifiTetheringAvailabilityListener.onWifiTetheringUnavailable();
                    }

                    @Override
                    public void onTetheringStarted() 
                    {
                        Log.d(TAG, "onTetheringStarted!");
                    }
                });
    }

    private void stopTethering() {
        mTetheringManager.stopTethering(ConnectivityManager.TETHERING_WIFI);
    }

    private void restartTethering() {
        stopTethering();
        mRestartBooked = true;
    }

    public interface WifiTetheringAvailabilityListener {
        void onWifiTetheringAvailable();
        void onWifiTetheringUnavailable();
        default void onConnectedClientsChanged(int clientCount) {
        }

    }
}