package com.rxw.panconnection.service.wifi;

import android.annotation.NonNull;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.TetheringManager;
import android.net.wifi.SoftApConfiguration;
import android.net.wifi.SoftApCapability;
import android.net.wifi.SoftApInfo;
import android.net.wifi.WifiClient;
import android.net.wifi.WifiSsid;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import android.net.MacAddress;
import android.util.Log;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

import com.android.internal.util.ConcurrentUtils;
import com.rxw.panconnection.service.wifi.WifiTetheringAvailabilityListener;
import com.rxw.panconnection.service.wifi.WifiTetheringObserver;

public class WifiTetheringHandler {

    // Initialization
    private final static String TAG = "WifiTetheringHandler";
    private final Context mContext;
    private final WifiManager mWifiManager;
    private final TetheringManager mTetheringManager;
    private final WifiTetheringAvailabilityListener mWifiTetheringAvailabilityListener;

    // Define variables
    private List<WifiTetheringObserver> observers = new ArrayList<>();
    private List<WifiClient> previousClients = new ArrayList<>();
    private JSONArray MultiDeviceArray = new JSONArray();
    private static List<WifiClient> connectedClients = null;

    // WifiTethering setting
    private static final String TARGET_SSID = "HIT";
    private static final String TARGET_PASSPHRASE = "12345678";
    private static final int TARGET_SECURITY = SoftApConfiguration.SECURITY_TYPE_WPA2_PSK;
    private static final int TARGET_CHANNEL = 40;
    private static final boolean TARGET_HIDDEN = false;  // Do not broadcast SSID
    private static final boolean TARGET_CLIENTCONTROLBYUSER = true;
    private static final int TARGET_BAND_2G = SoftApConfiguration.BAND_2GHZ;
    private static final int TARGET_BAND_5G = SoftApConfiguration.BAND_5GHZ;
    private static final int TARGET_MAXNUMBEROFCLIENTS = 10;

    // deviceType
    private static final int deviceType = 0;
    private static final String DEVICE_TYPE_UNKNOWN = "UNKNOW";
    private static final String DEVICE_TYPE_CAMERA = "CAMERA";
    private static final String DEVICE_TYPE_FRAGRANCE = "FRAGRANCE";
    private static final String DEVICE_TYPE_ATMOSPHERE_LIGHT = "ATMOSPHERE_LIGHT";
    private static final String DEVICE_TYPE_MIRROR = "MIRROR";
    private static final String DEVICE_TYPE_DISINFECTION = "DISINFECTION";
    private static final String DEVICE_TYPE_HUMIDIFIER = "HUMIDIFIER";
    private static final String DEVICE_TYPE_MICROPHONE = "MICROPHONE";

    // intent
    private static final String ACTION_DISCOVERED_DEVICES = "com.rxw.ACTION_DISCOVERED_DEVICES";
    private static final String PACKAGE_DISCOVERED_DEVICES = "com.rxw.car.panconnection";

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
    private static final int ERRORCODE_CONNECTION_NOTEXIST = 0;
    private static final int ERRORCODE_CONNECTION_NOCLIENTSNUM = 1;
    private static final int ERRORCODE_CONNECTION_SOFTAPSHUTDOWN = 2;
    private static final int ERRORCODE_CONNECTION_OTHER = 4;
    private static final int ERRORCODE_UNBOND = 5;

    /*==============================================================================*
     * SoftApCallback                                                               *
     *==============================================================================*/

    private final WifiManager.SoftApCallback mSoftApCallback = new WifiManager.SoftApCallback() {
        /**
         * Called when soft AP state changes.
         *
         * @param state         the new AP state. One of {@link #WIFI_AP_STATE_DISABLED},
         *                      {@link #WIFI_AP_STATE_DISABLING}, {@link #WIFI_AP_STATE_ENABLED},
         *                      {@link #WIFI_AP_STATE_ENABLING}, {@link #WIFI_AP_STATE_FAILED}
         * @param failureReason reason when in failed state. One of
         *                      {@link #SAP_START_FAILURE_GENERAL},
         *                      {@link #SAP_START_FAILURE_NO_CHANNEL},
         *                      {@link #SAP_START_FAILURE_UNSUPPORTED_CONFIGURATION}
         */
        @Override
        public void onStateChanged(int state, int failureReason) {

            /**
             * WIFI_AP_STATE_DISABLING = 10 : Wi-Fi AP is currently being disabled. 
             * WIFI_AP_STATE_DISABLED = 11 : Wi-Fi AP is disabled.
             * WIFI_AP_STATE_ENABLING = 12 : Wi-Fi AP is currently being enabled. 
             * WIFI_AP_STATE_ENABLING = 13 : Wi-Fi AP is enabled.
             * WIFI_AP_STATE_FAILED = 14 : Wi-Fi AP is in a failed state.
             */

            Log.d(TAG, "onStateChanged, state: " + state + ", failureReason: " + failureReason);
            handleWifiApStateChanged(state);

            // TODO: observer
            for (WifiTetheringObserver observer : observers) {

                observer.getOnStateChanged(state, failureReason);
            }
        }

        @Override
        public void onConnectedClientsChanged(@NonNull SoftApInfo info, @NonNull List<WifiClient> currentClients) {
            Log.d(TAG, "onConnectedClientsChanged" + currentClients + ", info: " + info);
            handleConnectedClientsChanged(currentClients);
            mWifiTetheringAvailabilityListener.onConnectedClientsChanged(currentClients.size());

            // TODO: observer
            for (WifiTetheringObserver observer : observers) {
                observer.getOnConnectedClientsChanged(info, currentClients);
            }
        }

        /**
         * Called when client trying to connect but device blocked the client with specific reason.
         *
         * Can be used to ask user to update client to allowed list or blocked list
         * when reason is {@link SAP_CLIENT_BLOCK_REASON_CODE_BLOCKED_BY_USER}, or
         * indicate the block due to maximum supported client number limitation when reason is
         * {@link SAP_CLIENT_BLOCK_REASON_CODE_NO_MORE_STAS}.
         *
         * @param client the currently blocked client.
         * @param blockedReason one of blocked reason from {@link SapClientBlockedReason}
         */
        @Override
        public void onBlockedClientConnecting(WifiClient client, int blockedReason) {
            // =================== public @interface SapClientBlockedReason {} ====================== 
            //
            /**
             *  ### blockedReason = 0
             *  If Soft Ap client is blocked, this reason code means that client doesn't exist in the
             *  specified configuration {@link SoftApConfiguration.Builder#setBlockedClientList(List)}
             *  and {@link SoftApConfiguration.Builder#setAllowedClientList(List)}
             *  and the {@link SoftApConfiguration.Builder#setClientControlByUserEnabled(boolean)}
             *  is configured as well.
             */

            /**
             *  ### blockedReason = 1
             *  If Soft Ap client is blocked, this reason code means that no more clients can be
             *  associated to this AP since it reached maximum capacity. The maximum capacity is
             *  the minimum of {@link SoftApConfiguration.Builder#setMaxNumberOfClients(int)} and
             *  {@link SoftApCapability#getMaxSupportedClients} which get from
             *  {@link WifiManager.SoftApCallback#onCapabilityChanged(SoftApCapability)}.
             */ 
            
             /**
             * ### blockedReason = 2
             * Client disconnected for unspecified reason. This could for example be because the AP is being
             * shut down.
             */
            // Do nothing: can be used to ask user to update client to allowed list or blocked list.
            Log.d(TAG, "onBlockedClientConnecting: " + client.getMacAddress().toString() + ", blockedReason: " + blockedReason);
            handleonBlockedClientConnecting(client, blockedReason);

            // TODO: observer
            for (WifiTetheringObserver observer : observers) {
                observer.getOnBlockedClientConnecting(client, blockedReason);
            }
        }

        /**
         * Called when capability of Soft AP changes.
         *
         * @param softApCapability is the Soft AP capability. {@link SoftApCapability}
         */
        public void onCapabilityChanged(@NonNull SoftApCapability softApCapability) {
            Log.d(TAG, "onCapabilityChanged: " + softApCapability);
            
            // TODO: observer
            for (WifiTetheringObserver observer : observers) {
                observer.getOnCapabilityChanged(softApCapability);
            }
        }

        /**
         * Called when the Soft AP information changes.
         *
         * Returns information on all configured Soft AP instances. The number of the elements in
         * the list depends on Soft AP configuration and state:
         * <ul>
         * <li>An empty list will be returned when the Soft AP is disabled.
         * <li>One information element will be returned in the list when the Soft AP is configured
         *     as a single AP or when a single Soft AP remains active.
         * <li>Two information elements will be returned in the list when the multiple Soft APs are
         *     configured and are active.
         *     (configured using {@link SoftApConfiguration.Builder#setBands(int[])} or
         *     {@link SoftApConfiguration.Builder#setChannels(android.util.SparseIntArray)}).
         * </ul>
         *
         * Note: When multiple Soft AP instances are configured, one of the Soft APs may
         * be shut down independently of the other by the framework. This can happen if no devices
         * are connected to it for some duration. In that case, one information element will be
         * returned.
         *
         * See {@link #isBridgedApConcurrencySupported()} for support info of multiple (bridged) AP.
         *
         * @param softApInfoList is the list of the Soft AP information elements -
         *        {@link SoftApInfo}.
         */
        public void onInfoChanged(@NonNull List<SoftApInfo> softApInfoList) {
            Log.d(TAG, "onInfoChanged: " + softApInfoList);
            // Do nothing: can be updated to add SoftApInfo details (e.g. channel) to the UI.

            // TODO: observer
            for (WifiTetheringObserver observer : observers) {

                observer.getOnInfoChanged(softApInfoList);
            }
        }
    };

    /*==============================================================================*
     * callback: WifiApStateChanged                                                 *
     *==============================================================================*/

    private void handleWifiApStateChanged(int state) {
        
        switch (state) {
            case WifiManager.WIFI_AP_STATE_ENABLING:
                break;
            case WifiManager.WIFI_AP_STATE_ENABLED:
                mWifiTetheringAvailabilityListener.onWifiTetheringAvailable();

                /*=========================================*
                 * Output SoftApConfiguration info         *
                 *=========================================*/

                SoftApConfiguration originalConfig = mWifiManager.getSoftApConfiguration();
                if (originalConfig != null) {
                    String SSID = originalConfig.getSsid();
                    String Passphrase = originalConfig.getPassphrase();
                    String MaxNumberOfClients = String.valueOf(originalConfig.getMaxNumberOfClients());
                    String HiddenSsid = String.valueOf(originalConfig.isHiddenSsid());
                    Log.d(TAG, "onTetheringStarted!" + " SSID: " + SSID + ", Passphrase: " + Passphrase + ", MaxNumberOfClients: " + MaxNumberOfClients + ", HiddenSsid: " + HiddenSsid);
                    
                    if (!originalConfig.getBlockedClientList().isEmpty()) {
                        for (MacAddress macAddress : originalConfig.getBlockedClientList()) {
                            Log.d(TAG, "onTetheringStarted!" + " currentBlockedClientList: " + macAddress.toString());
                        }
                    } else {
                        Log.d(TAG, "onTetheringStarted!" + " currentBlockedClientList is null!");
                    }

                    if (!originalConfig.getAllowedClientList().isEmpty()) {
                        // Output currentBlockedClientList and currentAllowedClientList
                        for (MacAddress macAddress : originalConfig.getBlockedClientList()) {
                            Log.d(TAG, "onTetheringStarted!" + " currentAllowedClientList: " + macAddress.toString());
                        }
                    } else {
                        Log.d(TAG, "onTetheringStarted!" + " currentAllowedClientList is null!");
                    }

                }
                break;
            case WifiManager.WIFI_AP_STATE_DISABLING:
                mWifiTetheringAvailabilityListener.onWifiTetheringUnavailable();
                break;
            case WifiManager.WIFI_AP_STATE_DISABLED:
                mWifiTetheringAvailabilityListener.onWifiTetheringUnavailable();
                break;
            default:
                mWifiTetheringAvailabilityListener.onWifiTetheringUnavailable();
                break;
        }
    }

    /*==============================================================================*
     * callback: BlockedClientConnecting                                            *
     *==============================================================================*/

    public void handleonBlockedClientConnecting(WifiClient client, int blockedReason) {
        
        if (blockedReason == 0) {
            // TODO: observer
            for (WifiTetheringObserver observer : observers) {
                observer.onConnectionFailed(deviceType, client.getMacAddress().toString(), ERRORCODE_CONNECTION_NOTEXIST);
            }
        }
        if (blockedReason == 1) {
            // TODO: observer
            for (WifiTetheringObserver observer : observers) {
                observer.onConnectionFailed(deviceType, client.getMacAddress().toString(), ERRORCODE_CONNECTION_NOCLIENTSNUM);
            }
        }

        if (blockedReason == 2) {
            // TODO: observer
            for (WifiTetheringObserver observer : observers) {
                observer.onConnectionFailed(deviceType, client.getMacAddress().toString(), ERRORCODE_CONNECTION_SOFTAPSHUTDOWN);
            }
        }

        // TODO: send BroadcastToAPP
        SoftApConfiguration originalConfig = mWifiManager.getSoftApConfiguration();
        if (originalConfig != null) {
            List<MacAddress> currentBlockedClientList = originalConfig.getBlockedClientList();
            List<MacAddress> currentAllowedClientList = originalConfig.getAllowedClientList();
            if (!currentBlockedClientList.contains(client.getMacAddress())) {
                sendBroadcastToAPP(DEVICE_TYPE_CAMERA, DEVICE_TYPE_CAMERA, client.getMacAddress().toString());
            }
        }

        // Wait for 5 seconds before executing the operation
        bondDeviceAndSoftApConfigureWithDelay(client);
        // Wait for 5 seconds before executing the operation
        // unbondDeviceAndSoftApConfigureWithDelay(client);
    }

    /*==============================================================================*
     * WithDelay: bondDeviceAndSoftApConfigure and unbondDeviceAndSoftApConfigure   *
     *==============================================================================*/

    public void bondDeviceAndSoftApConfigureWithDelay(WifiClient client) {

        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                // Execute bondDevice
                Log.d(TAG, "Wait for 5 seconds!" + " bondDeviceAndSoftApConfigureWithDelay: " + client.getMacAddress().toString());
                bondDeviceAndSoftApConfigure(client.getMacAddress());
            }
        }, 10000);
    }

    public void unbondDeviceAndSoftApConfigureWithDelay(WifiClient client) {

        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                // Execute unbondDevice
                unbondDeviceAndSoftApConfigure(client.getMacAddress());
                Log.d(TAG, "Wait for 5 seconds!" + " unbondDeviceAndSoftApConfigureWithDelay: " + client.getMacAddress().toString());
            }
        }, 10000);
    }

    /*==============================================================================*
     * callback: ConnectedClientsChanged                                            *
     *==============================================================================*/

    public void handleConnectedClientsChanged(List<WifiClient> currentClients) {

        // find DisconnectedClients and refresh previousClients
        List<WifiClient> disconnectedClients = findDisconnectedClients(previousClients, currentClients);
        previousClients = new ArrayList<>(currentClients);

        // TODO: observer (disconnectedClients)
        for (WifiClient disconnectedClient : disconnectedClients) {
            for (WifiTetheringObserver observer : observers) {
                observer.onConnectionStateChanged(deviceType, disconnectedClient.getMacAddress().toString(), DISCONNECTING );
                observer.onConnectionStateChanged(deviceType, disconnectedClient.getMacAddress().toString(), DISCONNECTED);
            }
        }

        // TODO: observer (currentClients)
        for (WifiClient client : currentClients) {
            for (WifiTetheringObserver observer : observers) {
                observer.onConnectionStateChanged(deviceType, client.getMacAddress().toString(), CONNECTING);
                observer.onConnectionStateChanged(deviceType, client.getMacAddress().toString(), CONNECTED);
            }
            // Package MultiDeviceArray 
            MultiDeviceArray = new JSONArray();
            createMultiDeviceArray(DEVICE_TYPE_CAMERA, DEVICE_TYPE_CAMERA, client.getMacAddress().toString());
        }

        // TODO: observer (deviceList)
        Log.d(TAG, "MultiDeviceArray: " + getMultiDeviceArray());
        for (WifiTetheringObserver observer : observers) {
            observer.onDiscoveryStateChanged(getMultiDeviceArray(), DISCOVERY_SEARCHING);
            observer.onDiscoveryStateChanged(getMultiDeviceArray(), DISCOVERY_END);
        }

    }

    /*==============================================================================*
     * restart WifiSoftAp                                                     *
     *==============================================================================*/
    private void restartSoftAp() {
        if (mWifiManager.getWifiApState() == WifiManager.WIFI_AP_STATE_ENABLED) {
            
            stopTethering();
    
            Handler handler1 = new Handler(Looper.getMainLooper());
            handler1.postDelayed(new Runnable() {
                @Override
                public void run() {
                    startTethering();
                }
            }, 1000);
    
            Log.d(TAG, "Restart softApConfigureAndStartHotspot begin first!");
        }
    }

    /*==============================================================================*
     * find DisconnectedClients                                                     *
     *==============================================================================*/

    private List<WifiClient> findDisconnectedClients(List<WifiClient> previousClients, List<WifiClient> currentClients) {
        List<WifiClient> disconnectedClients = new ArrayList<>();
        for (WifiClient previousClient : previousClients) {
            if (!currentClients.contains(previousClient)) {
                disconnectedClients.add(previousClient);
            }
        }
        return disconnectedClients;
    }

    /*==============================================================================*
     * send Broadcast                                                               *
     *==============================================================================*/

    public void sendBroadcastToAPP(String deviceType, String deviceName, String macAddress) {
        try {
            List<String> allowedDeviceTypes = Arrays.asList("UNKNOW", "CAMERA", "FRAGRANCE", "ATMOSPHERE_LIGHT",
                    "MIRROR", "DISINFECTION", "HUMIDIFIER", "MICROPHONE");

            if (!allowedDeviceTypes.contains(deviceType)) {
                deviceType = "UNKNOW";
            }

            JSONArray deviceArray = new JSONArray();
            JSONObject deviceObject = new JSONObject();
            deviceObject.put("device_type", deviceType);
            deviceObject.put("device_name", deviceName);
            deviceObject.put("device_id", macAddress);
            deviceArray.put(deviceObject);

            Intent intent = new Intent(ACTION_DISCOVERED_DEVICES);
            intent.setPackage(PACKAGE_DISCOVERED_DEVICES);

            intent.putExtra("discovered_devices", deviceArray.toString());
            mContext.sendBroadcast(intent);

            Log.d(TAG, "Broadcast sented for new device: " + deviceArray);
        } catch (Exception e) {
            Log.e(TAG, "Error creating broadcast JSON: ", e);
        }
    }

    /*==============================================================================*
     * register and unregister Observer                                             *
     *==============================================================================*/

    public void registerObserver(WifiTetheringObserver observer) {
        observers.add(observer);
    }

    public void unregisterObserver(WifiTetheringObserver observer) {
        observers.remove(observer);
    }

    /*==============================================================================*
     * Getters                                                                      *
     *==============================================================================*/

    public String getMultiDeviceArray() {
        return MultiDeviceArray.toString();
    }

    public static List<WifiClient> getConnectedClients() {
        return connectedClients;
    }
    
    public void createMultiDeviceArray(String deviceType, String deviceName, String macAddress) {
        try {
            JSONObject deviceObject = new JSONObject();
            deviceObject.put("device_type", deviceType);
            deviceObject.put("device_name", deviceName);
            deviceObject.put("device_id", macAddress);

            MultiDeviceArray.put(deviceObject);
        } catch (JSONException e) {
            Log.e(TAG, "Error creating device array JSON: ", e);
        }
    }

    /*==============================================================================*
     * bond UniDevice                                                               *
     *==============================================================================*/
    
    public void bondDeviceAndSoftApConfigure(MacAddress macAddrObj) {
        try {
            // softApConfigure setting
            SoftApConfiguration originalConfig = mWifiManager.getSoftApConfiguration();
            if (originalConfig != null) {
                List<MacAddress> currentBlockedClientList = originalConfig.getBlockedClientList();
                List<MacAddress> currentAllowedClientList = originalConfig.getAllowedClientList();
                if (currentBlockedClientList.contains(macAddrObj)) {
                    originalConfig.getBlockedClientList().remove(macAddrObj);
                }
                if (!currentAllowedClientList.contains(macAddrObj)) {
                    originalConfig.getAllowedClientList().add(macAddrObj);
                }
                mWifiManager.setSoftApConfiguration(originalConfig);

                // Output currentBlockedClientList and currentAllowedClientList
                for (MacAddress macAddress : mWifiManager.getSoftApConfiguration().getBlockedClientList()) {
                    Log.d(TAG, "Output bondDeviceAndSoftApConfigure" + " currentBlockedClientList: " + macAddress.toString());
                }
                for (MacAddress macAddress : mWifiManager.getSoftApConfiguration().getAllowedClientList()) {
                    Log.d(TAG, "Output bondDeviceAndSoftApConfigure" + " currentAllowedClientList: " + macAddress.toString());
                }
            }

            Log.d(TAG, "UniDevice bonded: " + macAddrObj.toString());

        } catch (Exception e) {
            // Handle the exception, for example, log it or notify the user
            Log.e(TAG, "Failed to bond device and configure Soft AP: " + e.getMessage());
            
            // TODO: observer
            for (WifiTetheringObserver observer : observers) {
                observer.onConnectionFailed(deviceType, macAddrObj.toString(), ERRORCODE_CONNECTION_OTHER);
            }
        }
    }

    /*==============================================================================*
    * unbond UniDevice                                                              *
    *===============================================================================*/

    public void unbondDeviceAndSoftApConfigure(MacAddress macAddrObj) {
        
        try {
            // softApConfigure setting
            SoftApConfiguration originalConfig = mWifiManager.getSoftApConfiguration();
            if (originalConfig != null) {
                List<MacAddress> currentBlockedClientList = originalConfig.getBlockedClientList();
                List<MacAddress> currentAllowedClientList = originalConfig.getAllowedClientList();
                if (currentBlockedClientList.contains(macAddrObj)) {
                    originalConfig.getBlockedClientList().remove(macAddrObj);
                }
                if (currentAllowedClientList.contains(macAddrObj)) {
                    originalConfig.getAllowedClientList().remove(macAddrObj);
                }
                mWifiManager.setSoftApConfiguration(originalConfig);

                // Output currentBlockedClientList and currentAllowedClientList
                for (MacAddress macAddress : mWifiManager.getSoftApConfiguration().getBlockedClientList()) {
                    Log.d(TAG, "Output unbondDeviceAndSoftApConfigure" + " currentBlockedClientList: " + macAddress.toString());
                }
                for (MacAddress macAddress : mWifiManager.getSoftApConfiguration().getAllowedClientList()) {
                    Log.d(TAG, "Output unbondDeviceAndSoftApConfigure" + " currentAllowedClientList: " + macAddress.toString());
                }

            }
            
            Log.d(TAG, "UniDevice unbonded: " + macAddrObj.toString());

            // TODO: observer
            for (WifiTetheringObserver observer : observers) {
                observer.onUnbondStateChanged(deviceType, macAddrObj.toString(), UNBONDING);
                observer.onUnbondStateChanged(deviceType, macAddrObj.toString(), UNBONDED);
            }
            
        } catch (Exception e) {
            // Handle the exception, for example, log it or notify the user
            Log.e(TAG, "Failed to unbond device and configure Soft AP: " + e.getMessage());
            // TODO: observer
            for (WifiTetheringObserver observer : observers) {
                observer.onUnbondFailed(deviceType, macAddrObj.toString(), ERRORCODE_UNBOND);
            }
        }
    }

    // @@@@@@WifiManager.setSoftApConfiguration(@NonNull SoftApConfiguration softApConfig)
    /**
     * Sets the tethered Wi-Fi AP Configuration.
     *
     * If the API is called while the tethered soft AP is enabled, the configuration will apply to
     * the current soft AP if the new configuration only includes
     * {@link SoftApConfiguration.Builder#setMaxNumberOfClients(int)}
     * or {@link SoftApConfiguration.Builder#setShutdownTimeoutMillis(long)}
     * or {@link SoftApConfiguration.Builder#setClientControlByUserEnabled(boolean)}
     * or {@link SoftApConfiguration.Builder#setBlockedClientList(List)}
     * or {@link SoftApConfiguration.Builder#setAllowedClientList(List)}
     * or {@link SoftApConfiguration.Builder#setAutoShutdownEnabled(boolean)}
     * or {@link SoftApConfiguration.Builder#setBridgedModeOpportunisticShutdownEnabled(boolean)}
     *
     * Otherwise, the configuration changes will be applied when the Soft AP is next started
     * (the framework will not stop/start the AP).
     *
     * @param softApConfig  A valid SoftApConfiguration specifying the configuration of the SAP.
     * @return {@code true} if the operation succeeded, {@code false} otherwise
     *
     * @hide
     */

    /*==============================================================================*
     * softApConfigureAndStartHotspot begin first!                                  *
     *==============================================================================*/
    public void softApConfigureAndStartHotspot() {

        // softApConfigure setting
        SoftApConfiguration originalConfig = mWifiManager.getSoftApConfiguration();
        if (originalConfig != null) {
            
            List<MacAddress> mcurrentBlockedClientList = originalConfig.getBlockedClientList();
            List<MacAddress> mcurrentAllowedClientList = originalConfig.getAllowedClientList();

            // Delete duplicate elements
            Set<MacAddress> uniqueSetcurrentBlockedClientList = new LinkedHashSet<>(mcurrentBlockedClientList);
            Set<MacAddress> uniqueSetcurrentAllowedClientList = new LinkedHashSet<>(mcurrentAllowedClientList);
            List<MacAddress> currentBlockedClientList = new ArrayList<>(uniqueSetcurrentBlockedClientList);
            List<MacAddress> currentAllowedClientList = new ArrayList<>(uniqueSetcurrentAllowedClientList);

            /*==========================================*
            * New builder (restartSoftAp)               *
            *===========================================*/
            SoftApConfiguration.Builder builder = new SoftApConfiguration.Builder();
            // currentBlockedClientList.clear();  // Test
            // currentAllowedClientList.clear();  // Test
            builder.setBlockedClientList(currentBlockedClientList);
            builder.setBlockedClientList(currentAllowedClientList);
            builder.setSsid(TARGET_SSID);
            builder.setPassphrase(TARGET_PASSPHRASE, TARGET_SECURITY);
            builder.setBand(TARGET_BAND_5G);
            builder.setMaxNumberOfClients(TARGET_MAXNUMBEROFCLIENTS);
            builder.setHiddenSsid(TARGET_HIDDEN);
            builder.setClientControlByUserEnabled(TARGET_CLIENTCONTROLBYUSER); 
            SoftApConfiguration currentConfig = builder.build();
            mWifiManager.setSoftApConfiguration(currentConfig);

            // Changed: Output currentBlockedClientList and currentAllowedClientList
            for (MacAddress macAddress : mWifiManager.getSoftApConfiguration().getBlockedClientList()) {
                Log.d(TAG, "Output softApConfigureAndStartHotspot!" + " currentBlockedClientList: " + macAddress.toString());
            }
            for (MacAddress macAddress : mWifiManager.getSoftApConfiguration().getAllowedClientList()) {
                Log.d(TAG, "Output softApConfigureAndStartHotspot!" + " currentAllowedClientList: " + macAddress.toString());
            }

        } else {
            SoftApConfiguration.Builder builder = new SoftApConfiguration.Builder();
            builder.setSsid(TARGET_SSID);
            builder.setPassphrase(TARGET_PASSPHRASE, TARGET_SECURITY);
            builder.setBand(TARGET_BAND_5G);
            builder.setMaxNumberOfClients(TARGET_MAXNUMBEROFCLIENTS);
            builder.setHiddenSsid(TARGET_HIDDEN);
            builder.setClientControlByUserEnabled(TARGET_CLIENTCONTROLBYUSER); 
            SoftApConfiguration currentConfig = builder.build();
            mWifiManager.setSoftApConfiguration(currentConfig);
        }  

        onStartInternal();  // Being register SoftApCallback!
        updateWifiTetheringState(true);  // only activate the hotspot once!

        Log.d(TAG, "softApConfigureAndStartHotspot、onStartInternal and updateWifiTetheringState all completed for the first time!"); 
    }

    /*==============================================================================*
     * constructor: initialize an object when creating it                           *
     *==============================================================================*/

    public WifiTetheringHandler(Context context, WifiTetheringAvailabilityListener wifiTetherAvailabilityListener) {
        this(context, context.getSystemService(WifiManager.class), context.getSystemService(TetheringManager.class), wifiTetherAvailabilityListener);
    }

    public WifiTetheringHandler(Context context, WifiManager wifiManager, TetheringManager tetheringManager, WifiTetheringAvailabilityListener wifiTetherAvailabilityListener) {
        mContext = context;
        mWifiManager = wifiManager;
        mTetheringManager = tetheringManager;
        mWifiTetheringAvailabilityListener = wifiTetherAvailabilityListener;
    }

    /**
     * Handles operations that should happen in host's onStartInternal().
     */
    public void onStartInternal() {
        mWifiManager.registerSoftApCallback(mContext.getMainExecutor(), mSoftApCallback);
    }

    /**
     * Handles operations that should happen in host's onStopInternal().
     */
    public void onStopInternal() {
        mWifiManager.unregisterSoftApCallback(mSoftApCallback);
    }

    /**
     * Starts WiFi tethering.
     * Callback for use with {@link #startTethering} to find out whether tethering succeeded.
     */
    private void startTethering() {
        mTetheringManager.startTethering(ConnectivityManager.TETHERING_WIFI,
                ConcurrentUtils.DIRECT_EXECUTOR, new TetheringManager.StartTetheringCallback() {
                    /**
                     * Called when starting tethering failed.
                     *
                     * @param error The error that caused the failure.
                     */
                    @Override
                    public void onTetheringFailed(int error) {
                        Log.d(TAG, "onTetheringFailed, error: " + error);
                        mWifiTetheringAvailabilityListener.onWifiTetheringUnavailable();
                    }

                    /**
                     * Called when tethering has been successfully started.
                     */
                    @Override
                    public void onTetheringStarted() {
                        Log.d(TAG, "onTetheringStarted!");                    
                        mWifiTetheringAvailabilityListener.onWifiTetheringAvailable();
                    }
                });
    }

    /**
     * Stops WiFi tethering if it's enabled.
     */
    private void stopTethering() {
        if (isWifiTetheringEnabled()) {
            mTetheringManager.stopTethering(ConnectivityManager.TETHERING_WIFI);
        }
    }

    /**
     * Update Tethering State.
     */
    public void updateWifiTetheringState(boolean enable) {
        if (enable) {
            startTethering();
        } else {
            stopTethering();
        }
    }

    /**
     * Returns whether wifi tethering is enabled
     *
     * @return whether wifi tethering is enabled
     */
    public boolean isWifiTetheringEnabled() {
        return mWifiManager.isWifiApEnabled();
    }

}