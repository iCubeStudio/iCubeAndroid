package com.rxw.panconnection.service.wifi;

import java.util.List;
import android.net.wifi.SoftApCapability;
import android.net.wifi.SoftApInfo;
import android.net.wifi.WifiClient;

public interface WifiTetheringObserver {

    /*===================================================================*
     * WifiTethering Observer                                          
     *===================================================================*/

    /**
     * Callback for pan device discovery state
     * @param deviceList JSON list of discovered devices
     * @param discoveryState DISCOVERY_SEARCHING (1) or DISCOVERY_END (2)
     */
    default void onDiscoveryStateChanged(String deviceList, int discoveryState) {
    }

    /**
     * Callback for pan device connection state
     * @param deviceType Type of device (e.g., camera, aroma machine)
     * @param deviceId ID of the device with connection state change
     * @param connectionState CONNECTING (3), CONNECTED (4), DISCONNECTING (5), DISCONNECTED (6)
     */
    default void onConnectionStateChanged(int deviceType, String deviceId, int connectionState) {
    }

    /**
     * Callback for pan device connection failure
     * @param deviceType Type of device (e.g., camera, aroma machine)
     * @param deviceId ID of the device that failed to connect
     * @param errorCode Error code for connection failure
     */
    default void onConnectionFailed(int deviceType, String deviceId, int errorCode) {
    }

    /**
     * Callback for pan device unbonding state
     * @param deviceType Type of device (e.g., camera, aroma machine)
     * @param deviceId ID of the device being unbonded
     * @param bondState UNBONDING (7), UNBONDED (8)
     */
    default void onUnbondStateChanged(int deviceType, String deviceId, int bondState) {
    }

    /**
     * Callback for pan device unbonding failure
     * @param deviceType Type of device (e.g., camera, aroma machine)
     * @param deviceId ID of the device that failed to unbond
     * @param errorCode Error code for unbonding failure
     */
    default void onUnbondFailed(int deviceType, String deviceId, int errorCode) {
    }

    /*===================================================================*
     * Getter Observer                                          
     *===================================================================*/
    default void getOnStateChanged(int state, int failureReason) {
    }

    default void getOnConnectedClientsChanged(SoftApInfo info, List<WifiClient> clients) {
    }

    default void getOnBlockedClientConnecting(WifiClient client, int blockedReason) {
    }

    default void getOnCapabilityChanged(SoftApCapability softApCapability) {
    }

    default void getOnInfoChanged(List<SoftApInfo> softApInfoList) {
    }
}