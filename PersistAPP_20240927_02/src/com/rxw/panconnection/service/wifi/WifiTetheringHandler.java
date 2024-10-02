package com.rxw.panconnection.service.wifi;

import android.annotation.NonNull;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.TetheringManager;
import android.net.wifi.SoftApConfiguration;
import android.net.wifi.SoftApCapability;
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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class WifiTetheringHandler {

    private final Context mContext;
    private final WifiManager mWifiManager;
    private final TetheringManager mTetheringManager; 
    private final WifiTetheringAvailabilityListener mWifiTetheringAvailabilityListener;

    public List<WifiClient> clients = new ArrayList<>();
    public ArrayList<MacAddress> mTestBlockedList = new ArrayList<>();
    public ArrayList<MacAddress> mTestAllowedList = new ArrayList<>(); 
    public ArrayList<MacAddress> currentDeviceList = new ArrayList<>();
    


    private static JSONArray MultiDeviceArray = new JSONArray();

    private final static String TAG = "WifiTetheringHandler";

    // WifiTethering setting
    private static final String TEST_SSID = "zhang";
    private static final String TEST_PASSPHRASE = "12345678";
    private static final int TEST_SECURITY = SoftApConfiguration.SECURITY_TYPE_WPA2_PSK;
    private static final int TEST_CHANNEL = 40;
    private static final boolean TEST_HIDDEN = false;
    private static final boolean TEST_CLIENTCONTROLBYUSER = true;
    private static final int TEST_BAND_2G = SoftApConfiguration.BAND_2GHZ;
    private static final int TEST_BAND_5G = SoftApConfiguration.BAND_5GHZ;
    private static final int TEST_MAXNUMBEROFCLIENTS = 10;

    // deviceType
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

    // stateTAG
    private boolean Connecting_TAG = false;
    private boolean Connected_TAG = false;
    private boolean Discovery_TAG = false;

    private boolean mRestartBooked = false;

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

    /**
     * Base class for soft AP callback. Should be extended by applications and set when calling
     * {@link WifiManager#registerSoftApCallback(Executor, SoftApCallback)}.
     *
     * @hide
     */
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
            // system API: getWifiApState()
            // WIFI_AP_STATE_FAILED = 14
            // WIFI_AP_STATE_ENABLED = 13
            // WIFI_AP_STATE_ENABLING = 12
            // WIFI_AP_STATE_DISABLED = 11
            //WIFI_AP_STATE_DISABLING = 10
            Log.d(TAG, "onStateChanged, state: " + state + ", failureReason: " + failureReason);
            handleWifiApStateChanged(state);
        }

        /**
         * Called when the connected clients for a soft AP instance change.
         *
         * When the Soft AP is configured in single AP mode, this callback is invoked
         * with the same {@link SoftApInfo} for all connected clients changes.
         * When the Soft AP is configured as multiple Soft AP instances (using
         * {@link SoftApConfiguration.Builder#setBands(int[])} or
         * {@link SoftApConfiguration.Builder#setChannels(android.util.SparseIntArray)}), this
         * callback is invoked with the corresponding {@link SoftApInfo} for the instance in which
         * the connected clients changed.
         *
         * @param info The {@link SoftApInfo} of the AP.
         * @param clients The currently connected clients on the AP instance specified by
         *                {@code info}.
         */
        @Override
        public void onConnectedClientsChanged(@NonNull SoftApInfo info, @NonNull List<WifiClient> clients) {
            Log.d(TAG, "onConnectedClientsChanged" + clients + ", info: " + info);

            handleConnectedClientsChanged(clients);
            mWifiTetheringAvailabilityListener.onConnectedClientsChanged(clients.size());
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
            Log.d(TAG, "onBlockedClientConnecting: " + client + ", blockedReason: " + blockedReason);
            handleonBlockedClientConnecting(client);
            // Do nothing: can be used to ask user to update client to allowed list or blocked list.
        }

        /**
         * Called when capability of Soft AP changes.
         *
         * @param softApCapability is the Soft AP capability. {@link SoftApCapability}
         */
        public void onCapabilityChanged(@NonNull SoftApCapability softApCapability) {
            Log.d(TAG, "onCapabilityChanged: " + softApCapability);
            // Do nothing: can be updated to add SoftApCapability details (e.g. meximum supported
            // client number) to the UI.
            boolean isFeatureSupported = softApCapability.areFeaturesSupported(SoftApCapability.SOFTAP_FEATURE_CLIENT_FORCE_DISCONNECT);
            if (isFeatureSupported) {
                Log.d(TAG, "SOFTAP_FEATURE_CLIENT_FORCE_DISCONNECT: supported");
            } else {
                Log.d(TAG, "SOFTAP_FEATURE_CLIENT_FORCE_DISCONNECT: unsupported");
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
        }
    };

    // callback: BlockedClientConnecting
    public void handleonBlockedClientConnecting(WifiClient client) {
        
        String macAddress = client.getMacAddress().toString();
        Log.d(TAG, "handleonBlockedClientConnecting macAddress: " + macAddress);
    }

    // callback: ConnectedClientsChanged
    public void handleConnectedClientsChanged(List<WifiClient> clients) {
        
        // 【1】Parameter settings
        Discovery_TAG = true;  // Set Discovery_TAG true
        currentDeviceList.clear();
        this.clients = clients;

        // 【2】For each client
        for (WifiClient client : clients) {
            
            // (1) Get currentDeviceList
            String deviceType = DEVICE_TYPE_CAMERA;
            String deviceName = DEVICE_TYPE_CAMERA;
            String macAddress = client.getMacAddress().toString();
            MacAddress macAddrObj = MacAddress.fromString(macAddress);
            currentDeviceList.add(macAddrObj);
            
            if (!mTestAllowedList.contains(macAddrObj)) {
                
                // (2) sendBroadcastToAPP
                sendBroadcastToAPP(deviceType, deviceName, macAddress);
                // (3) block UniDevice
                blockDevice(macAddrObj);
            }

            // (4) Package MultiDeviceArray 
            MultiDeviceArray = new JSONArray();
            createMultiDeviceArray(deviceType, deviceName, macAddress);
        }

        ArrayList<MacAddress> disconnectedDeviceList = new ArrayList<>(mTestAllowedList);
        disconnectedDeviceList.removeAll(currentDeviceList);

        // TEST1: Output currentDeviceList (Connecting Clients)
        for (MacAddress macAddress1 : currentDeviceList) {
            Log.d(TAG, "(1) currentDeviceList: " + macAddress1.toString());
        }

        // TEST2: Output disconnectedDeviceList (disConnected Clients)
        for (MacAddress macAddress2 : disconnectedDeviceList) {
            Log.d(TAG, "(2) disconnectedDeviceList: " + macAddress2.toString());
        }

        // TEST3: Output mTestAllowedList 
        Log.d(TAG, "output mTestAllowedList and mTestBlockedList");
        for (MacAddress macAddress3 : mTestAllowedList) {
            Log.d(TAG, "(3) mTestAllowedList: " + macAddress3.toString());
        }

        // TEST4: Output mTestBlockedList
        for (MacAddress macAddress4 : mTestBlockedList) {
            Log.d(TAG, "(4) mTestBlockedList: " + macAddress4.toString());
            // refresh Device
            if (mTestBlockedList.contains(macAddress4) && !currentDeviceList.contains(macAddress4) && !mTestAllowedList.contains(macAddress4)) {
                mTestBlockedList.remove(macAddress4);
            }
        }

        Discovery_TAG = false;  // Set DISCOVERY_TAG false
    }

    // sendBroadcastToAPP
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

    public ArrayList<MacAddress> getmTestAllowedList() {
        return mTestAllowedList;
    }

    public ArrayList<MacAddress> getmTestBlockedList() {
        return mTestBlockedList;
    }

    public String getMultiDeviceArray() {
        return MultiDeviceArray.toString();
    }

    public boolean getDiscoveryTAG() {
        return Discovery_TAG;
    }

    public boolean getConnecting_TAG() {
        return Connecting_TAG;
    }

    public boolean getConnected_TAG() {
        return Connected_TAG;
    }

    public ArrayList<MacAddress> getCurrentDeviceList() {
        return currentDeviceList;
    }

    public List<WifiClient> getClients() {
        return new ArrayList<>(this.clients);
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

    // unbond UniDevice
    public void blockDevice(MacAddress macAddrObj) {

        // update mTestBlockedList and mTestAllowedList
        if (!mTestBlockedList.contains(macAddrObj)) {
            mTestBlockedList.add(macAddrObj);
        }
        if (mTestAllowedList.contains(macAddrObj)) {
            mTestAllowedList.remove(macAddrObj);
        }

        // WifiTethering setting
        SoftApConfiguration currentConfig = mWifiManager.getSoftApConfiguration();
        SoftApConfiguration.Builder builder = new SoftApConfiguration.Builder(currentConfig);
        if (!mTestBlockedList.isEmpty()) {
            builder.setBlockedClientList(mTestBlockedList);
        }
        SoftApConfiguration config = builder.build();
        mWifiManager.setSoftApConfiguration(config);

        Log.d(TAG, "UniDevice blocked: " + macAddrObj.toString());
    }

    // Connect UniDevice
    public void connnectDevice(MacAddress macAddrObj) {
        Connecting_TAG = true;
        Connected_TAG = false;

        // update mTestBlockedList and mTestAllowedList
        if (mTestBlockedList.contains(macAddrObj)) {
            mTestBlockedList.remove(macAddrObj);
        }
        if (!mTestAllowedList.contains(macAddrObj)) {
            mTestAllowedList.add(macAddrObj);
        }

        // WifiTethering setting
        SoftApConfiguration currentConfig = mWifiManager.getSoftApConfiguration();
        SoftApConfiguration.Builder builder = new SoftApConfiguration.Builder(currentConfig);
        if (!mTestBlockedList.isEmpty()) {
            builder.setBlockedClientList(mTestBlockedList);
        }
        SoftApConfiguration config = builder.build();
        mWifiManager.setSoftApConfiguration(config);

        Log.d(TAG, "UniDevice connnected: " + macAddrObj.toString());

        Connecting_TAG = false;
        Connected_TAG = true;
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

    // @@@@WifiManager.startTetheredHotspot(@Nullable SoftApConfiguration softApConfig)
    /**
     * Start Soft AP (hotspot) mode for tethering purposes with the specified configuration.
     * Note that starting Soft AP mode may disable station mode operation if the device does not
     * support concurrency.
     *
     * @param softApConfig A valid SoftApConfiguration specifying the configuration of the SAP,
     *                     or null to use the persisted Soft AP configuration that was previously
     *                     set using {@link #setSoftApConfiguration(softApConfiguration)}.
     * @return {@code true} if the operation succeeded, {@code false} otherwise
     * @hide
     */

    // @@@ SoftApConfiguration.setBlockedClientList(List<MacAddress> blockedClientList)
    /**
     * This API configures the list of clients which are blocked and cannot associate
     * to the Soft AP.
     *
     * <p>
     * This method requires HAL support. HAL support can be determined using
     * {@link WifiManager.SoftApCallback#onCapabilityChanged(SoftApCapability)} and
     * {@link SoftApCapability#areFeaturesSupported(long)}
     * with {@link SoftApCapability.SOFTAP_FEATURE_CLIENT_FORCE_DISCONNECT}
     *
     * <p>
     * If the method is called on a device without HAL support then starting the soft AP
     * using {@link WifiManager#startTetheredHotspot(SoftApConfiguration)} will fail with
     * {@link WifiManager#SAP_START_FAILURE_UNSUPPORTED_CONFIGURATION}.
     *
     * @param blockedClientList list of clients which are not allowed to associate to the AP.
     * @return Builder for chaining.
     */
    public void configureAndStartHotspot() {
        SoftApConfiguration.Builder builder = new SoftApConfiguration.Builder();

        // WifiTethering setting
        builder.setSsid(TEST_SSID);
        builder.setPassphrase(TEST_PASSPHRASE, TEST_SECURITY);
        builder.setBand(TEST_BAND_5G);
        builder.setMaxNumberOfClients(TEST_MAXNUMBEROFCLIENTS);
        builder.setHiddenSsid(TEST_HIDDEN);
        if (!mTestBlockedList.isEmpty()) {
            builder.setBlockedClientList(mTestBlockedList);
        }
        SoftApConfiguration config = builder.build();
        mWifiManager.setSoftApConfiguration(config);

        onStartInternal();  // Being register SoftApCallback!
        updateWifiTetheringState(true);  // only activate the hotspot once! 
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

    /**
     * Handles operations that should happen in host's onStartInternal().
     */
    private void onStartInternal() {
        mWifiManager.registerSoftApCallback(mContext.getMainExecutor(), mSoftApCallback);
    }

    /**
     * Handles operations that should happen in host's onStopInternal().
     */
    private void onStopInternal() {
        mWifiManager.unregisterSoftApCallback(mSoftApCallback);
    }

    /**
     * Returns whether wifi tethering is enabled
     *
     * @return whether wifi tethering is enabled
     */
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
                ConcurrentUtils.DIRECT_EXECUTOR, new TetheringManager.StartTetheringCallback() {
                    @Override
                    public void onTetheringFailed(int error) {
                        Log.d(TAG, "onTetheringFailed, error: " + error);
                        mWifiTetheringAvailabilityListener.onWifiTetheringUnavailable();
                    }

                    @Override
                    public void onTetheringStarted() {
                        Log.d(TAG, "onTetheringStarted!");
                    }
                });
    }

    private void stopTethering() {
        mTetheringManager.stopTethering(ConnectivityManager.TETHERING_WIFI);
    }

    public void restartTethering() {
        stopTethering();
        mRestartBooked = true;
    }

    public interface WifiTetheringAvailabilityListener {
        /**
         * Callback for when Wifi tethering is available
         */
        void onWifiTetheringAvailable();

        /**
         * Callback for when Wifi tethering is unavailable
         */
        void onWifiTetheringUnavailable();

        /**
         * Callback for when the number of tethered devices has changed
         *
         * @param clientCount number of connected clients
         */
        default void onConnectedClientsChanged(int clientCount) {
        }
    }
}