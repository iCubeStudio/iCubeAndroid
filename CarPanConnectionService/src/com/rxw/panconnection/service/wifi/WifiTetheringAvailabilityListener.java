package com.rxw.panconnection.service.wifi;

public interface WifiTetheringAvailabilityListener {

    /*===================================================================*
     * WifiTethering Listener                                            
     *===================================================================*/

    /**
     * Callback for when Wifi tethering is available
     */
    default void onWifiTetheringAvailable() {
    }

    /**
     * Callback for when Wifi tethering is unavailable
     */
    default void onWifiTetheringUnavailable() {
    }
    
    /**
     * Callback for when the number of tethered devices has changed
     *
     * @param clientCount number of connected clients
     */
    default void onConnectedClientsChanged(int clientCount) {
    }
}