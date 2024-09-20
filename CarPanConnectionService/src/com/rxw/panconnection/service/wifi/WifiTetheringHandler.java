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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class WifiTetheringHandler {

    private final Context mContext;
    private final WifiManager mWifiManager;
    private final TetheringManager mTetheringManager;
    private final WifiTetheringAvailabilityListener mWifiTetheringAvailabilityListener;

    private List<String> connectedDeviceMacs = new ArrayList<>();
    private List<String> blockedDeviceMacs = new ArrayList<>();
    private List<String> tempdisconnectingDeviceMacs = new ArrayList<>();
    private static JSONArray MultiDeviceArray = new JSONArray();
    
    private final static String TAG = "WifiTetheringHandler";
    private static final String TARGET_SSID = "DEMO";
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

    private boolean DISCOVERY_TAG = false;
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

    private final WifiManager.SoftApCallback mSoftApCallback = new WifiManager.SoftApCallback() 
    {
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
        public void onStateChanged(int state, int failureReason)
        {
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
        public void onConnectedClientsChanged(@NonNull SoftApInfo info, @NonNull List<WifiClient> clients) 
        {
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
        public void onBlockedClientConnecting(@NonNull WifiClient client, @SapClientBlockedReason int blockedReason) 
        {
            Log.d(TAG, "the currently blocked client: " + client + ", blockedReason: " + blockedReason);
            // Do nothing: can be used to ask user to update client to allowed list or blocked list.
        }
        /**
         * Called when capability of Soft AP changes.
         *
         * @param softApCapability is the Soft AP capability. {@link SoftApCapability}
         */
        public void onCapabilityChanged(@NonNull SoftApCapability softApCapability) 
        {
            // Do nothing: can be updated to add SoftApCapability details (e.g. meximum supported
            // client number) to the UI.
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
        public void onInfoChanged(@NonNull List<SoftApInfo> softApInfoList) 
        {
            // Do nothing: can be updated to add SoftApInfo details (e.g. channel) to the UI.
        }
    };

    public void handleConnectedClientsChanged(List<WifiClient> clients)
    {
        DISCOVERY_TAG = true; // Set DISCOVERY_TAG true

        List<String> currentDeviceMacs = new ArrayList<>();
        for (WifiClient client : clients) 
        {
            String deviceType = DEVICE_TYPE_CAMERA;
            String deviceName = DEVICE_TYPE_CAMERA;
            String macAddress = client.getMacAddress().toString();

            currentDeviceMacs.add(macAddress);  // current connected UniDevice
            
            // Package MultiDeviceArray 
            MultiDeviceArray = new JSONArray(); 
            createMultiDeviceArray(deviceType, deviceName, macAddress);

            if (!connectedDeviceMacs.contains(macAddress)) 
            {
                connectedDeviceMacs.add(macAddress); // previous connected UniDevice
                sendBroadcastToAPP(deviceType, deviceName, macAddress);
                blockDevice(macAddress);
            }
        }

        // Subtraction method: Search disconnectedDeviceMacs
        List<String> disconnectedDeviceMacs = new ArrayList<>(connectedDeviceMacs);  
        disconnectedDeviceMacs.removeAll(currentDeviceMacs);

        for (int i = 0; i < disconnectedDeviceMacs.size(); i++) 
        {
            Log.d("disconnectedDeviceMacs", "Device " + i + ": " + disconnectedDeviceMacs.get(i));
        }
        
        tempdisconnectingDeviceMacs.clear(); //Clear the previous disconnectingDeviceMacs
        // Mark the disconnected device as disconnecting and remove it from blockedDeviceMacs
        for (String macAddress : disconnectedDeviceMacs) 
        {
            tempdisconnectingDeviceMacs.add(macAddress); 
            Log.d(TAG, "UniDevice is disconnecting: " + macAddress);

            // Remove it from blockedDeviceMacs
            if (blockedDeviceMacs.contains(macAddress)) 
            {
                blockedDeviceMacs.remove(macAddress);
                Log.d(TAG, "Remove MACaddress from blockedDeviceMacs: " + macAddress);
            }
        }
        
        DISCOVERY_TAG = false;  // Set DISCOVERY_TAG false

        // Output ConnectedDevices and blockedDeviceMacs
        for (int i = 0; i < connectedDeviceMacs.size(); i++) 
        {
            Log.d("ConnectedDevices", "Device " + i + ": " + connectedDeviceMacs.get(i));
        }
        for (int i = 0; i < blockedDeviceMacs.size(); i++) 
        {
            Log.d("blockedDeviceMacs", "Device " + i + ": " + blockedDeviceMacs.get(i));
        }
    }

    public boolean isDisconnectingDevice(String macAddress) 
    {
        return tempdisconnectingDeviceMacs.contains(macAddress);
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
    
    private void sendBroadcastToAPP(String deviceType, String deviceName, String macAddress) {
        try 
        {
            List<String> allowedDeviceTypes = Arrays.asList("UNKNOW", "CAMERA", "FRAGRANCE", "ATMOSPHERE_LIGHT", 
                                                            "MIRROR", "DISINFECTION", "HUMIDIFIER", "MICROPHONE");

            if (!allowedDeviceTypes.contains(deviceType)) 
            {
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
        } 
        catch (Exception e) 
        {
            Log.e(TAG, "Error creating broadcast JSON: ", e);
        }
    }

    public void createMultiDeviceArray(String deviceType, String deviceName, String macAddress) 
    {
        try 
        {
            JSONObject deviceObject = new JSONObject();
            deviceObject.put("device_type", deviceType);  
            deviceObject.put("device_name", deviceName);
            deviceObject.put("device_id", macAddress);

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

    // unbond UniDevice
    public void blockDevice(String macAddress) 
    {
        if (!blockedDeviceMacs.contains(macAddress)) 
        {
            blockedDeviceMacs.add(macAddress);
        }
        if (connectedDeviceMacs.contains(macAddress)) 
        {
            connectedDeviceMacs.remove(macAddress);;
        }

        SoftApConfiguration.Builder builder = new SoftApConfiguration.Builder();
        List<MacAddress> blockedMacAddressList = new ArrayList<>();

        for (String macString : blockedDeviceMacs) 
        {
            try 
            {
                MacAddress macAddrObj = MacAddress.fromString(macString); 
                blockedMacAddressList.add(macAddrObj);
            } 
            catch (IllegalArgumentException e) 
            {
                e.printStackTrace();
            }
        }
        
        // Set blocked UniDevice
        builder.setBlockedClientList(blockedMacAddressList);
        SoftApConfiguration config = builder.build();
        mWifiManager.setSoftApConfiguration(config);
        updateWifiTetheringState(true);

        Log.d(TAG, "UniDevice blocked: " + macAddress);
    }

    // Connect UniDevice
    public void unblockDevice(String macAddress) 
    {
        if (blockedDeviceMacs.contains(macAddress)) 
        {
            blockedDeviceMacs.remove(macAddress);
        }
        if (!connectedDeviceMacs.contains(macAddress)) 
        {
            connectedDeviceMacs.add(macAddress);;
        }

        SoftApConfiguration.Builder builder = new SoftApConfiguration.Builder();
        List<MacAddress> allowedMacAddressList = new ArrayList<>();

        for (String macString : connectedDeviceMacs) 
        {
            try 
            {
                MacAddress macAddrObj = MacAddress.fromString(macString); 
                allowedMacAddressList.add(macAddrObj);
            } 
            catch (IllegalArgumentException e) 
            {
                e.printStackTrace();
            }
        }
        
        // Set allowed UniDevice
        builder.setAllowedClientList(allowedMacAddressList);
        SoftApConfiguration config = builder.build();
        mWifiManager.setSoftApConfiguration(config);
        updateWifiTetheringState(true);

        Log.d(TAG, "UniDevice unblocked: " + macAddress);
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
                .setClientControlByUserEnabled(true)
                .setHiddenSsid(false);

        SoftApConfiguration config = builder.build();
        mWifiManager.setSoftApConfiguration(config);

        onStartInternal();  // Being register SoftApCallback
        updateWifiTetheringState(true);
    }

    /**
     * Handles operations that should happen in host's onStartInternal().
     */
    private void onStartInternal() 
    {
        mWifiManager.registerSoftApCallback(mContext.getMainExecutor(), mSoftApCallback);
    }

    /**
     * Handles operations that should happen in host's onStopInternal().
     */
    private void onStopInternal() 
    {
        mWifiManager.unregisterSoftApCallback(mSoftApCallback);
    }

    /**
     * Returns whether wifi tethering is enabled
     * @return whether wifi tethering is enabled
     */
    public boolean isWifiTetheringEnabled() 
    {
        return mWifiManager.isWifiApEnabled();
    }

    public void updateWifiTetheringState(boolean enable)
     {
        if (enable) 
        {
            startTethering();
        } 
        else 
        {
            stopTethering();
        }
    }

    private void handleWifiApStateChanged(int state) 
    {
        Log.d(TAG, "handleWifiApStateChanged, state: " + state);
        switch (state) 
        {
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
                if (mRestartBooked) 
                {
                    startTethering();
                    mRestartBooked = false;
                }
                break;
            default:
                mWifiTetheringAvailabilityListener.onWifiTetheringUnavailable();
                break;
        }
    }

    private void startTethering() 
    {
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

    private void stopTethering() 
    {
        mTetheringManager.stopTethering(ConnectivityManager.TETHERING_WIFI);
    }

    private void restartTethering() 
    {
        stopTethering();
        mRestartBooked = true;
    }

    public interface WifiTetheringAvailabilityListener 
    {
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
         * @param clientCount number of connected clients
         */
        default void onConnectedClientsChanged(int clientCount) 
        {
        }
    }
}