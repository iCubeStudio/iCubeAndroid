package com.rxw.panconnection.service.bluetooth;

import static com.rxw.panconnection.service.bluetooth.BtUtils.DEBUG_BLE;

import android.Manifest;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class BleService extends Service {
    private static final String TAG = BleService.class.getSimpleName();

    private BluetoothAdapter mAdapter;


    /*===================*
     * Initialize        *
     *===================*/

    public boolean initialize(BluetoothAdapter adapter) {
        this.mAdapter = adapter;
        if (adapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }
        return true;
    }

    public boolean initialize() {

        BluetoothAdapter adapter = BtUtils.initialize(this);

        if (adapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        this.mAdapter = adapter;
        return true;
    }


    /*===================*
     * Observer          *
     *===================*/

    private List<BtUtils.BleStateObserver> mObservers;

    void shareObservers(List<BtUtils.BleStateObserver> observers) {
        this.mObservers = observers;
    }


    /*===================*
     * Bind              *
     *===================*/
    private Binder mBinder = new LocalBinder();

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return this.mBinder;
    }


    @Override
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public boolean onUnbind(Intent intent) {
        this.close();
        return super.onUnbind(intent);
    }

    class LocalBinder extends Binder {
        public BleService getService() {
            return BleService.this;
        }
    }


    /*===================*
     * Connect           *
     *===================*/

    public final static String ACTION_GATT_CONNECTED = "com.example.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED = "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTED = 2;

    private int mConnectionState;

//    private BluetoothGatt mGatt;
    private Map<String, BluetoothGatt> mGattMap = new HashMap<>();


    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        this.sendBroadcast(intent);
    }

    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // Successfully connected to the GATT Server.
                BleService.this.mConnectionState = STATE_CONNECTED;
                /// Broadcast
                BleService.this.broadcastUpdate(ACTION_GATT_CONNECTED);
                // TODO


            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                // Disconnected from the GATT Server.
                BleService.this.mConnectionState = STATE_DISCONNECTED;
                /// Broadcast
                BleService.this.broadcastUpdate(ACTION_GATT_DISCONNECTED);
                // TODO


            }

        }
    };

    public Set<String> getBondedDeviceAddressSet() {
        return this.mGattMap.keySet();
    }

    public void getBondedDevices(Consumer<Map<String, BluetoothGatt>> consumer) {
        if (DEBUG_BLE) {
            Log.d(TAG, "consumer is null? " + (consumer == null));
        }
        consumer.accept(this.mGattMap);
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public boolean connect(final String address) {
        if (this.mAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        try {
            final BluetoothDevice device = this.mAdapter.getRemoteDevice(address);
            // Connect to the GATT server on the device.
            BluetoothGatt gatt = device.connectGatt(this, false, this.mGattCallback);   // Manifest.permission.BLUETOOTH_CONNECT

            this.mGattMap.put(address, gatt);

            return true;
        } catch (IllegalArgumentException exception) {
            Log.w(TAG, "Device not found with provided address.");
            return false;
        }
    }


    /*===================*
     * Disconnect        *
     *===================*/

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public boolean disconnect(String address) {
        if (!this.mGattMap.containsKey(address)) {
            return false;
        }

        BluetoothGatt gatt = this.mGattMap.get(address);

        gatt.disconnect();
        return true;
    }


    /*===================*
     * Close             *
     *===================*/
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public boolean close(String address) {
        if (!this.mGattMap.containsKey(address)) {
            return false;
        }

        BluetoothGatt gatt = this.mGattMap.get(address);
        gatt.close();

        this.mGattMap.remove(gatt);

        return true;
    }


    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public boolean close() {
        for (Map.Entry<String, BluetoothGatt> entry : this.mGattMap.entrySet()) {
            entry.getValue().close();
        }

        this.mGattMap.clear();

        return true;
    }

}