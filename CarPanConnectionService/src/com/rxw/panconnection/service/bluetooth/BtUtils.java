package com.rxw.panconnection.service.bluetooth;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public class BtUtils {

    private static final String TAG = BtUtils.class.getSimpleName();

    protected static final boolean DEBUG_ALL = true;                // TODO
    protected static final boolean DEBUG_BLE = DEBUG_ALL || false;
    protected static final boolean DEBUG_BC = DEBUG_ALL || false;

    private static final UUID DEFAULT_SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // Context
    private Context mContext;

    // Bluetooth
    private BluetoothManager mManager;
    private BluetoothAdapter mAdapter;

    // Bluetooth Classic Only
    private BroadcastReceiver mBcStateReceiver;

    private final Map<BluetoothDevice, Long> mBcDeviceFoundTimestamp = new HashMap<>(); // Place `BluetoothDevice` objects of bc (classic bluetooth) and ble (bluetooth low energy) separately to allow independent modification if one changes.

    /// Observers
    private final List<BcStateObserver> mBcStateObserverList = new ArrayList<>();



    // Bluetooth Low Energy Only
    private static final long SCAN_PERIOD_DEFAULT = 30 * 1000;  // 30s

    private boolean mIsScanning = false;
    private BluetoothLeScanner mBleScanner;

    private BleService mBleService;
    private ServiceConnection mServiceConnection;
    private ScanCallback mBleScanCallback;

    private final Map<BluetoothDevice, Long> mBleDeviceFoundTimestamp = new HashMap<>();    // Place `BluetoothDevice` objects of bc (classic bluetooth) and ble (bluetooth low energy) separately to allow independent modification if one changes.

    /// Observers
    private final List<BleStateObserver> mBleStateObserverList = new ArrayList<>();


    public BtUtils(Context context) {
        this.mContext = context;

        boolean result = this.initialize();
        // Bluetooth Classic
        this.mBcStateReceiver = new BroadcastReceiver() {

            @Override
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                BluetoothDevice device;
                int state;

                switch (action) {
                    case BluetoothDevice.ACTION_FOUND:
                         device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);


                        if (DEBUG_BC) {
                            String address = device.getAddress();   // MAC address.
                            String name = device.getName();         // Device Name.
                            int type = device.getType();            // Device Type: 0: DEVICE_TYPE_UNKNOWN; 1: DEVICE_TYPE_CLASSIC; 2:DEVICE_TYPE_LE; 3:DEVICE_TYPE_DUAL;
                            ParcelUuid[] uuids = device.getUuids();

                            Log.d(TAG, "==========");
                            Log.d(TAG, "Address: " + address);
                            Log.d(TAG, "Name: " + name);
                            Log.d(TAG, "Name is null? : " + (name == null));
                            Log.d(TAG, "Name is \"null\"?: " + (TextUtils.equals(name, "null")));
                            Log.d(TAG, "Type: " + type);
                            if (uuids != null) {
                                for (ParcelUuid uuid : uuids) {
                                    Log.d(TAG, "UUID: " + uuid.getUuid().toString());
                                }
                            } else {
                                Log.d(TAG, "Uuids not found.");
                            }
                        }


                        BtUtils.this.mBcDeviceFoundTimestamp.put(device, System.currentTimeMillis());   // TODO: Is it necessary to detect duplication according to MAC address?

                        for (BcStateObserver observer : BtUtils.this.mBcStateObserverList) {
                            observer.onDiscoveryStateChanged(new ArrayList<>(BtUtils.this.mBcDeviceFoundTimestamp.keySet()), STATE_DISCOVERY_SEARCHING);
                        }

                        break;
                    case BluetoothAdapter.ACTION_DISCOVERY_STARTED:
                        for (BcStateObserver observer : BtUtils.this.mBcStateObserverList) {
                            observer.onDiscoveryStateChanged(new ArrayList<>(BtUtils.this.mBcDeviceFoundTimestamp.keySet()), STATE_DISCOVERY_SEARCHING);
                        }

                        break;
                    case BluetoothAdapter.ACTION_DISCOVERY_FINISHED:
                        for (BcStateObserver observer : BtUtils.this.mBcStateObserverList) {
                            observer.onDiscoveryStateChanged(new ArrayList<>(BtUtils.this.mBcDeviceFoundTimestamp.keySet()), STATE_DISCOVERY_END);
                        }

                        break;
                    case BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED:
                        state = intent.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE, BluetoothAdapter.ERROR);
                        device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
                        switch (state) {
                            case BluetoothAdapter.STATE_CONNECTED:
                                for (BcStateObserver observer : BtUtils.this.mBcStateObserverList) {
                                    observer.onConnectionStateChanged(device, STATE_CONNECTED);
                                }
                                break;
                            case BluetoothAdapter.STATE_DISCONNECTED:
                                for (BcStateObserver observer : BtUtils.this.mBcStateObserverList) {
                                    observer.onConnectionStateChanged(device, STATE_DISCONNECTED);
                                }
                                break;
                            case BluetoothAdapter.STATE_CONNECTING:
                                for (BcStateObserver observer : BtUtils.this.mBcStateObserverList) {
                                    observer.onConnectionStateChanged(device, STATE_CONNECTING);
                                }
                                break;
                            case BluetoothAdapter.STATE_DISCONNECTING:
                                for (BcStateObserver observer : BtUtils.this.mBcStateObserverList) {
                                    observer.onConnectionStateChanged(device, STATE_DISCONNECTING);
                                }
                                break;
                            default:
                                // TODO: Error
                        }

                    case BluetoothDevice.ACTION_BOND_STATE_CHANGED:
                        state = intent.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE, BluetoothAdapter.ERROR);
                        device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
                        switch (state) {
                            case BluetoothDevice.BOND_NONE:
                                for (BcStateObserver observer : BtUtils.this.mBcStateObserverList) {
                                    observer.onBondStateChanged(device, STATE_UNBONDED);
                                }
                                break;
                            case BluetoothDevice.BOND_BONDING:
                            case BluetoothDevice.BOND_BONDED:
                            default:
                                break;
                        }


                        break;
                    default:
                        break;
                }
            }
        };

        // Bluetooth Low Energy
        this.mBleScanner = this.mAdapter.getBluetoothLeScanner();
        this.mServiceConnection = new ServiceConnection() {
            @Override
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                if (DEBUG_BLE) {
                    Log.d(TAG, "BleService connected.");
                }

                BtUtils.this.mBleService = ((BleService.LocalBinder) iBinder).getService();
                if (BtUtils.this.mBleService != null) {
                    if (!BtUtils.this.mBleService.initialize()) {
                        Log.e(TAG, "Unable to initialize Ble.");
                        // TODO: finish();
                    }
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName componentName) {
                if (DEBUG_BLE) {
                    Log.d(TAG, "BleService disconnected.");
                }
                BtUtils.this.mBleService = null;
            }
        };
        this.mBleScanCallback  = new ScanCallback() {

            @Override
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            public void onScanResult(int callbackType, ScanResult result) {
                super.onScanResult(callbackType, result);

                BluetoothDevice device = result.getDevice();

                // Test
                if (DEBUG_BLE) {
                    String address = device.getAddress();   // MAC address.
                    String name = device.getName();         // Device Name.
                    int type = device.getType();            // Device Type: 0: DEVICE_TYPE_UNKNOWN; 1: DEVICE_TYPE_CLASSIC; 2:DEVICE_TYPE_LE; 3:DEVICE_TYPE_DUAL;
                    ParcelUuid[] uuids = device.getUuids();

                    Log.d(TAG, "==========");
                    Log.d(TAG, "Address: " + address);
                    Log.d(TAG, "Name: " + name);
                    Log.d(TAG, "Name is null? : " + (name == null));
                    Log.d(TAG, "Name is \"null\"?: " + (TextUtils.equals(name, "null")));
                    Log.d(TAG, "Type: " + type);
                    if (uuids != null) {
                        for (ParcelUuid uuid : uuids) {
                            Log.d(TAG, "UUID: " + uuid.getUuid().toString());
                        }
                    } else {
                        Log.d(TAG, "Uuids not found.");
                    }
                }

                BtUtils.this.mBleDeviceFoundTimestamp.put(device, System.currentTimeMillis());

                for (BleStateObserver observer : BtUtils.this.mBleStateObserverList) {
                    observer.onDiscoveryStateChanged(new ArrayList<>(BtUtils.this.mBleDeviceFoundTimestamp.keySet()), STATE_DISCOVERY_SEARCHING);
                }

            }

            @Override
            public void onBatchScanResults(List<ScanResult> results) {
                super.onBatchScanResults(results);
            }

            @Override
            public void onScanFailed(int errorCode) {
                super.onScanFailed(errorCode);
//                for (BleStateObserver observer : BtUtils.this.mBleStateObserverList) {
//                    // TODO:
//                }
            }
        };
        /// BleService
        Intent gattServiceIntent = new Intent(this.mContext, BleService.class);
        this.mContext.bindService(gattServiceIntent, this.mServiceConnection, Context.BIND_AUTO_CREATE);
        if (DEBUG_BLE) {
            Log.d(TAG, "constructor called.");
        }

    }

    public static BluetoothAdapter initialize(@NonNull Context context) {
        BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        return manager.getAdapter();
    }


    private boolean initialize() {
        if (this.mContext == null) {
            Log.e(TAG, "Unable to obtained a Context.");
            return false;
        }

        this.mManager = (BluetoothManager) this.mContext.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = this.mManager.getAdapter();

        if (adapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        this.mAdapter = adapter;
        return true;
    }




    /*===================*
     * General Methods   *
     *===================*/

    public boolean hasComponent() {
        return this.mAdapter != null;
    }

    public boolean isEnabled() {
        if (this.hasComponent()) {
            return this.mAdapter.isEnabled();
        }

        return false;
    }


//    @DeprecatedSinceApi(api = Build.VERSION_CODES.TIRAMISU)
//    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
//    public void enable() {
//        if (this.mAdapter != null && !this.mAdapter.isEnabled()) {
//            this.mAdapter.enable();
//        }
//    }
//
//    @DeprecatedSinceApi(api = Build.VERSION_CODES.TIRAMISU)
//    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
//    public void disable() {
//        if (this.mAdapter != null && !this.mAdapter.isEnabled()) {
//            this.mAdapter.disable();
//        }
//    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public static String getDeviceInfo(BluetoothDevice device) {
        String name = device.getName();
        String address = device.getAddress();
        String info = String.format(
                "%s (%s)",
                name,
                address
        );

        return info;
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public static JSONObject getDeviceInfoJson(BluetoothDevice device) throws JSONException {
        String name = device.getName();
        String address = device.getAddress();
        int clazz = device.getBluetoothClass().getDeviceClass();

        JSONObject obj = new JSONObject();
        obj.append("device_type", clazz);
        obj.append("device_name", name);
        obj.append("device_id", address);

        return obj;
    }


    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public static JSONArray getDeviceInfoJsonArray(List<BluetoothDevice> list) throws JSONException {

        JSONArray array = new JSONArray();
        for (Object obj : list) {

            if (obj instanceof BluetoothDevice) {
                array.put(getDeviceInfoJson((BluetoothDevice) obj));
            }

            if (obj instanceof JSONObject) {
                array.put(obj);
            }

        }

        return array;
    }

    public void updateLastFoundBcDeviceInfo(Consumer<Set<BluetoothDevice>> consumer) {
        consumer.accept(this.mBcDeviceFoundTimestamp.keySet());
    }

    public void updateLastFoundBleDeviceInfo(Consumer<Set<BluetoothDevice>> consumer) {
        consumer.accept(this.mBleDeviceFoundTimestamp.keySet());
    }

    public void clear() {
        this.mBcDeviceFoundTimestamp.clear();
        this.mBleDeviceFoundTimestamp.clear();
    }


    public void onDestroy() {
        this.unregisterBcStateReceiver();

        this.mContext.unbindService(this.mServiceConnection);

        Intent gattServiceIntent = new Intent(this.mContext, BleService.class);
        this.mContext.stopService(gattServiceIntent);
    }


    /*=============================*
     * BC (Bluetooth Classic) Only *
     *=============================*/

    public void registerBcStateReceiver() {
        IntentFilter filter = new IntentFilter();

        // Action
        /// Discovery
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        /// Connect
        filter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);

        // Register.
        this.mContext.registerReceiver(this.mBcStateReceiver, filter);
    }


    public void unregisterBcStateReceiver() {
        this.mContext.unregisterReceiver(this.mBcStateReceiver);
    }


    // Discovery

//    @RequiresPermission(allOf = {Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION})
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    public void startDiscoveryBc() {
        if (DEBUG_BC) {
            Log.d(TAG, "startDiscoveryBc");
        }

        this.registerBcStateReceiver();
        boolean result = this.mAdapter.startDiscovery();
        if (DEBUG_BC) {
            Log.d(TAG, "startDiscovery result: " + result);
        }


    }


    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    public void stopDiscoveryBc() {
        this.mAdapter.cancelDiscovery();
        this.unregisterBcStateReceiver();
    }

    /// Connect & Disconnect

    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        public ConnectThread(BluetoothDevice device) {
            this.mmDevice = device;

            BluetoothSocket socket = null;

            try {
                socket = (BluetoothSocket) device.getClass().getMethod("createRfcommSocket", int.class).invoke(device, 1);
            } catch (Exception e2) {
                Log.e(TAG, "Socket's create() method failed", e2);
                e2.printStackTrace();
            }

            this.mmSocket = socket;
        }

        @Override
        @RequiresPermission(allOf = {Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT})
        public void run() {
            super.run();

            // Cancel discovery because it otherwise slows down the connection.
            Log.d(TAG, "Close discovery before connect.");
            BtUtils.this.mAdapter.cancelDiscovery();

            if (DEBUG_BC) {
                Log.d(TAG, "To connect Name: " + this.mmDevice.getName());
                Log.d(TAG, "To connect Address: " + this.mmDevice.getAddress());
            }

            try {
                // Connect to the remote device through the socket. This call blocks
                // until it succeeds or throws an exception.
                this.mmSocket.connect();
//                Log.d(TAG, "Connected?");   // TODO: check!
            } catch (IOException connectException) {
                // Unable to connect; close the socket and return.
                try {
                    mmSocket.close();
                } catch (IOException closeException) {
                    Log.e(TAG, "Could not close the client socket", closeException);
                }
                return;
            }

            // The connection attempt succeeded. Perform work associated with
            // the connection in a separate thread.

            // TODO


        }

        // Closes the client socket and causes the thread to finish.
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the client socket", e);
            }
        }
    }


    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public void connectBc(BluetoothDevice device) {
        Thread thread = new ConnectThread(device);
        thread.start();
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public void connectBc(final String address) {
        BluetoothDevice device = this.mAdapter.getRemoteDevice(address);
        Thread thread = new ConnectThread(device);
        thread.start();
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private static Set<BluetoothDevice> getBondedBcDevices(BluetoothAdapter adapter) {
        return adapter.getBondedDevices();
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public Set<BluetoothDevice> getBondedBcDevices() {
        return BtUtils.getBondedBcDevices(this.mAdapter);
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public void getBondedBcDevices(Consumer<Set<BluetoothDevice>> consumer) {
        consumer.accept(BtUtils.getBondedBcDevices(this.mAdapter));
    }


    public void removeBondBc(BluetoothDevice device) {
        try {
            Method method = BluetoothDevice.class.getMethod("removeBond");
            method.invoke(device);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            e.printStackTrace();
        }

    }

    public void removeBondBc(final String address) {
        BluetoothDevice device = this.mAdapter.getRemoteDevice(address);
        this.removeBondBc(device);
    }

    /*==================================*
     * BLE (Bluetooth Low Energy) Only  *
     *==================================*/

    // Discovery

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    public void startDiscoveryLe() {
        if (!this.mIsScanning) {

            this.mIsScanning = true;
            if (DEBUG_BLE) {
                Log.d(TAG, "this.mBleScanCallback is null? " + (this.mBleScanCallback == null));
                Log.d(TAG, "this.mBleScanner is null? " + (this.mBleScanner == null));
            }
            this.mBleScanner.startScan(this.mBleScanCallback);
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    public void stopDiscoveryLe() {
        if (this.mIsScanning) {
            this.mIsScanning = false;
            this.mBleScanner.stopScan(this.mBleScanCallback);
        }
    }


    // Connect & Disconnect

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public void connectBle(final String address) {
        if (this.mBleService != null) {
            this.mBleService.connect(address);
        }

    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public void disconnectBle(String address) {
        if (this.mBleService != null) {
            this.mBleService.disconnect(address);
        }
    }

    public Set<String> getBondedBleDeviceAddressSet() {
        if (this.mBleService != null) {
            return this.mBleService.getBondedDeviceAddressSet();
        }

        return null;
    }

    public void getBondedBleDevices(Consumer<Map<String, BluetoothGatt>> consumer) {
        if (DEBUG_BLE) {
            Log.d(TAG, "consumer is null? " + (consumer == null));
            Log.d(TAG, "BleService is null? " + (this.mBleService == null));
        }
        if (this.mBleService != null) {
            this.mBleService.getBondedDevices(consumer);
        }
    }


    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public void removeBondBle(String address) {
        if (this.mBleService != null) {
            this.mBleService.close(address);
        }
    }


    // Observers
    public static final int STATE_DISCOVERY_SEARCHING   = 1;
    public static final int STATE_DISCOVERY_END         = 2;
    public static final int STATE_CONNECTING            = 3;
    public static final int STATE_CONNECTED             = 4;
    public static final int STATE_DISCONNECTING         = 5;
    public static final int STATE_DISCONNECTED          = 6;
    public static final int STATE_UNBONDING             = 7;
    public static final int STATE_UNBONDED              = 8;

    public static final int STATE_ERROR_UNKNOWN         = -1;



    public interface BcStateObserver {
        default void onDiscoveryStateChanged(List<BluetoothDevice> list, int state) {}
        default void onConnectionStateChanged(BluetoothDevice device, int state) {}
        default void onBondStateChanged(BluetoothDevice device, int state) {}
        default void onConnectionFailed(BluetoothDevice device, int errorCode) {}
        default void onUnbondFailed(BluetoothDevice device, int errorCode) {}
    }

    public boolean registerObserver(BcStateObserver observer) {
        if (this.mBcStateObserverList.contains(observer)) {
            return false;
        }

        this.mBcStateObserverList.add(observer);
        return true;
    }

    public boolean unregisterObserver(BcStateObserver observer) {
        return this.mBcStateObserverList.remove(observer);
    }

    public interface BleStateObserver {
        default void onDiscoveryStateChanged(List<BluetoothDevice> list, int state) {}
        default void onConnectionStateChanged(BluetoothDevice device, int state) {}
        default void onBondStateChanged(BluetoothDevice device, int state) {}
        default void onConnectionFailed(BluetoothDevice device, int errorCode) {}
        default void onUnbondFailed(BluetoothDevice device, int errorCode) {}
    }

    public boolean registerObserver(BleStateObserver observer) {
        if (this.mBleStateObserverList.contains(observer)) {
            return false;
        }

        this.mBleStateObserverList.add(observer);
        return true;
    }

    public boolean unregisterObserver(BleStateObserver observer) {
        return this.mBleStateObserverList.remove(observer);
    }

}
