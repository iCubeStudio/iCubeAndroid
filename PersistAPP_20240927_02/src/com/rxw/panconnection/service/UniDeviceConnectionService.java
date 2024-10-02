package com.rxw.panconnection.service;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;

import org.json.JSONException;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import com.rxw.panconnection.service.aidl.IUniDeviceConnection;
import com.rxw.panconnection.service.aidl.IUniDeviceConnectionCallback;
import com.rxw.panconnection.service.bluetooth.BtUtils;
import com.rxw.panconnection.service.unidevice.UniDevice;
import com.rxw.panconnection.service.wifi.WifiTetheringHandler;

public class UniDeviceConnectionService extends Service {
    private static final String TAG = UniDeviceConnectionService.class.getSimpleName();

    private static final boolean DEBUG_ALL          = true;
    private static final boolean DEBUG_CALL         = DEBUG_ALL | false;
    private static final boolean DEBUG_CALLBACK     = DEBUG_ALL | false;
    private static final boolean DEBUG_PERMISSION   = DEBUG_ALL | false;
    private static final boolean DEBUG_DISCOVERY    = DEBUG_ALL | false;

    private BtManager mBtManager;
    private WifiSoftApManager mWifiSoftApManager;

    private final Map<String, UniDevice> mDevices = new HashMap<>();

    // deviceType
    private static final String DEVICE_TYPE_UNKNOWN = "UNKNOW";
    private static final String DEVICE_TYPE_CAMERA = "CAMERA";
    private static final String DEVICE_TYPE_FRAGRANCE = "FRAGRANCE";
    private static final String DEVICE_TYPE_ATMOSPHERE_LIGHT = "ATMOSPHERE_LIGHT";
    private static final String DEVICE_TYPE_MIRROR = "MIRROR";
    private static final String DEVICE_TYPE_DISINFECTION = "DISINFECTION";
    private static final String DEVICE_TYPE_HUMIDIFIER = "HUMIDIFIER";
    private static final String DEVICE_TYPE_MICROPHONE = "MICROPHONE";


    /*===================*
     * Getters 弃用      *
     *===================*/

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private String getLastFoundUniDeviceInfo() throws JSONException, RemoteException {
        Set<UniDevice> set = new HashSet<>();
        this.mBtManager.updateLastFoundBcDeviceInfo(
                devices -> {
                    for (BluetoothDevice device : devices) {
                        set.add(new UniDevice(device.getAddress(), UniDevice.PROTOCOL_TYPE_BLUETOOTH_CLASSIC, device.getName(), device.getBluetoothClass().getDeviceClass()));
                    }
                }
        );
        this.mBtManager.updateLastFoundBleDeviceInfo(
                devices -> {
                    for (BluetoothDevice device : devices) {
                        set.add(new UniDevice(device.getAddress(), UniDevice.PROTOCOL_TYPE_BLUETOOTH_LE, device.getName(), device.getBluetoothClass().getDeviceClass()));
                    }
                }
        );

        // TODO: Wi-Fi

        String info = UniDevice.getDeviceInfoJsonArray(set).toString();

        return info;
    }

    // 弃用
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private String getLastFoundUniDeviceInfoAndBroadcast() throws JSONException, RemoteException {
        String info = this.getLastFoundUniDeviceInfo();

        Intent intent = new Intent();
        intent.setAction("com.rxw.ACTION_DISCOVERED_DEVICES");
        intent.setPackage("com.rxw.car.panconnection");
        intent.putExtra("discovered_devices", info);
        UniDeviceConnectionService.this.sendBroadcast(intent);

        return info;
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private String updateLastFoundUniDeviceInfo() throws JSONException, RemoteException {
        this.mBtManager.updateLastFoundBcDeviceInfo(
                devices -> {
                    for (BluetoothDevice device : devices) {
                        String address = device.getAddress();
                        if (!this.mDevices.containsKey(address)) {
                            this.mDevices.put(address, new UniDevice(address, UniDevice.PROTOCOL_TYPE_BLUETOOTH_CLASSIC, device.getName(), device.getBluetoothClass().getDeviceClass()));
                        }
                    }
                }
        );
        this.mBtManager.updateLastFoundBleDeviceInfo(
                devices -> {
                    for (BluetoothDevice device : devices) {
                        String address = device.getAddress();
                        if (!this.mDevices.containsKey(address)) {
                            this.mDevices.put(address, new UniDevice(address, UniDevice.PROTOCOL_TYPE_BLUETOOTH_LE, device.getName(), device.getBluetoothClass().getDeviceClass()));
                        }
                    }
                }
        );

        // TODO: Wi-Fi
        for (WifiClient client : mWifiTetheringHandler.getClients();) {
            String deviceType = DEVICE_TYPE_CAMERA;
            String deviceName = DEVICE_TYPE_CAMERA;
            String macAddress = client.getMacAddress().toString();
            MacAddress macAddrObj = MacAddress.fromString(macAddress);
            if (!this.mDevices.containsKey(macAddress)) {
                this.mDevices.put(macAddress, new UniDevice(macAddress, UniDevice.PROTOCOL_TYPE_WIFI, DEVICE_TYPE_CAMERA, DEVICE_TYPE_CAMERA));
            }
        }
        String info = UniDevice.getDeviceInfoJsonArray(this.mDevices.values()).toString();

        return info;
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private String updateLastFoundUniDeviceInfoAndBroadcast() throws JSONException, RemoteException {
        String info = this.updateLastFoundUniDeviceInfo();

        Intent intent = new Intent();
        intent.setAction("com.rxw.ACTION_DISCOVERED_DEVICES");
        intent.setPackage("com.rxw.car.panconnection");
        intent.putExtra("discovered_devices", info);
        UniDeviceConnectionService.this.sendBroadcast(intent);

        return info;

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private String getLastConnectedUniDeviceInfo() throws JSONException {
        Set<UniDevice> devices = new HashSet<>();
        UniDevice device;

        // Classic Bluetooth
        for (Map.Entry<String, UniDevice> entry : this.mBtManager.getLastBondedBcDevices().entrySet()) {
            device = entry.getValue();
            if (!devices.contains(device)) {
                devices.add(device);
            }
        }

        // Bluetooth Low Energy
        for (Map.Entry<String, UniDevice> entry : this.mBtManager.getLastBondedBleDevices().entrySet()) {
            device = entry.getValue();
            if (!devices.contains(device)) {
                devices.add(device);
            }
        }

        // Wi-Fi
        // TODO: Wi-Fi
        for (WifiClient client : mWifiTetheringHandler.getClients();) {
            String deviceType = DEVICE_TYPE_CAMERA;
            String deviceName = DEVICE_TYPE_CAMERA;
            String macAddress = client.getMacAddress().toString();
            MacAddress macAddrObj = MacAddress.fromString(macAddress);
            if (!devices.contains(device)) {
                devices.add(new UniDevice(macAddress, UniDevice.PROTOCOL_TYPE_WIFI, DEVICE_TYPE_CAMERA, DEVICE_TYPE_CAMERA));
            }
        }

        return UniDevice.getDeviceInfoJsonArray(devices).toString();
    }



    /*===================*
     * State Observer    *
     *===================*/

    private final RemoteCallbackList<IUniDeviceConnectionCallback> mCallbackList = new RemoteCallbackList<>();

    private void registerCallback(IUniDeviceConnectionCallback callback) {
        this.mCallbackList.register(callback);
    }

    private void unregisterCallback(IUniDeviceConnectionCallback callback) {
        this.mCallbackList.unregister(callback);
    }

    private void callbackAllOnDiscoveryStateChanged(String deviceList, int state) throws RemoteException {
        int count = this.mCallbackList.beginBroadcast();
        for (int i = 0; i < count; i++) {
            IUniDeviceConnectionCallback callback = this.mCallbackList.getBroadcastItem(i);
            if (callback != null) {
                callback.onDiscoveryStateChanged(deviceList, state);
            }
        }
        this.mCallbackList.finishBroadcast();
    }

    private void callbackAllOnConnectionStateChanged(int deviceClass, String address, int state) throws RemoteException {
        int count = this.mCallbackList.beginBroadcast();
        for (int i = 0; i < count; i++) {
            IUniDeviceConnectionCallback callback = this.mCallbackList.getBroadcastItem(i);
            if (callback != null) {
                callback.onConnectionStateChanged(String.valueOf(deviceClass), address, state);
            }
        }
        this.mCallbackList.finishBroadcast();
    }

    private void callbackAllOnConnectionStateChanged(UniDevice device, int state) throws RemoteException {
        this.callbackAllOnConnectionStateChanged(device.getDeviceClass(), device.getAddress(), state);
    }

    private void callbackAllOnBondStateChanged(int deviceClass, String address, int state) throws RemoteException {
        int count = this.mCallbackList.beginBroadcast();
        for (int i = 0; i < count; i++) {
            IUniDeviceConnectionCallback callback = this.mCallbackList.getBroadcastItem(i);
            if (callback != null) {
                callback.onUnbondStateChanged(String.valueOf(deviceClass), address, state);
            }
        }
        this.mCallbackList.finishBroadcast();
    }

    private void callbackAllOnBondStateChanged(UniDevice device, int state) throws RemoteException {
        this.callbackAllOnBondStateChanged(device.getDeviceClass(), device.getAddress(), state);
    }

    private void callbackAllOnConnectionFailed(int deviceClass, String address, int errorCode) throws RemoteException {
        int count = UniDeviceConnectionService.this.mCallbackList.beginBroadcast();
        for (int i = 0; i < count; i++) {
            IUniDeviceConnectionCallback callback = UniDeviceConnectionService.this.mCallbackList.getBroadcastItem(i);
            if (callback != null) {
                callback.onConnectionFailed(String.valueOf(deviceClass), address, errorCode);
            }
        }
        UniDeviceConnectionService.this.mCallbackList.finishBroadcast();
    }

    private void callbackAllOnConnectionFailed(UniDevice device, int errorCode) throws RemoteException {
        this.callbackAllOnConnectionFailed(device.getDeviceClass(), device.getAddress(), errorCode);
    }

    private void callbackAllOnUnbondFailed(int deviceClass, String address, int errorCode) throws RemoteException {
        int count = UniDeviceConnectionService.this.mCallbackList.beginBroadcast();
        for (int i = 0; i < count; i++) {
            IUniDeviceConnectionCallback callback = UniDeviceConnectionService.this.mCallbackList.getBroadcastItem(i);
            if (callback != null) {
                callback.onUnbondFailed(String.valueOf(deviceClass), address, errorCode);
            }
        }
        UniDeviceConnectionService.this.mCallbackList.finishBroadcast();
    }

    private void callbackAllOnUnbondFailed(UniDevice device, int errorCode) throws RemoteException {
        this.callbackAllOnUnbondFailed(device.getDeviceClass(), device.getAddress(), errorCode);
    }

    /*===================*
     * Discovery         *
     *===================*/


    private boolean mIsBcDiscovering;
    private boolean mIsBleDiscovering;
    private boolean mIsWifiDiscovering;

    private boolean isDiscoveryEnd() {
        return (!this.mIsBcDiscovering
                && !this.mIsBleDiscovering
                && !this.mIsWifiDiscovering
        );
    }

//    @RequiresPermission(allOf = {Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION})
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private void startDiscoverUniDevice(IUniDeviceConnectionCallback callback) {
        this.mDevices.clear();
        this.registerCallback(callback);

        // Bluetooth
        this.mBtManager.startBtDiscovery();

        // Wi-Fi
        // TODO: Wi-Fi discovery.
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private void stopDiscoverUniDevice(IUniDeviceConnectionCallback callback) {
        this.registerCallback(callback);

        // Bluetooth
        this.mBtManager.stopBluetoothDiscovery();

        // Wi-Fi
        // TODO: Stop Wi-Fi discovery.
    }



    /*==============*
     * Connect      *
     *==============*/

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private boolean connectUniDevice(final String address) {
        UniDevice device = this.mDevices.get(address);
        if (device != null) {
            int type = device.getProtocolType();
            // Classic Bluetooth
            if (UniDevice.isBcDevice(type)) {
                this.mBtManager.connectBc(address);
                return true;
            }

            // Bluetooth Low Energy
            else if (UniDevice.isBleDevice(type)) {
                this.mBtManager.connectBle(address);
                return true;
            }

            // Wi-Fi
            else if (UniDevice.isWifiDevice(type)) {
                MacAddress macAddrObj = MacAddress.fromString(address);
                mWifiTetheringHandler.connnectDevice(macAddrObj);
                return true;
            }
        }

        return false;
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private boolean connectUniDevice(final int deviceClass, final String address) {
        UniDevice device = this.mDevices.get(address);
        if (device != null && device.getDeviceClass() == deviceClass) {
            int type = device.getProtocolType();
            if (UniDevice.isBcDevice(type)) {
                this.mBtManager.connectBc(address);
                return true;
            }

            if (UniDevice.isBleDevice(type)) {
                this.mBtManager.connectBle(address);
                return true;
            }


        }
        
        if (UniDevice.isWifiDevice(type)) {
            // TODO: WiFi
            MacAddress macAddrObj = MacAddress.fromString(address);
            mWifiTetheringHandler.connnectDevice(macAddrObj);
            return true;
        }

        return false;
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private boolean connectUniDevice(final int deviceClass, final String deviceAddress, IUniDeviceConnectionCallback callback) {
        this.registerCallback(callback);

        // TODO: device class
        return this.connectUniDevice(deviceAddress);
    }



    /*==============*
     * Remove Bond  *
     *==============*/

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private boolean removeBondedUniDevice(final String address) {
        boolean flag = false;

        if (this.mBtManager.getLastBondedBcDeviceAddressSet().contains(address)) {
            this.mBtManager.removeBondBc(address);
            flag = true;
        }

        if (this.mBtManager.getLastBondedBleDeviceAddressSet().contains(address)) {
            this.mBtManager.removeBondBle(address);
            flag = true;
        }

        // TODO: Wi-Fi
        MacAddress macAddrObj = MacAddress.fromString(address);
        mWifiTetheringHandler.blockDevice(macAddrObj);
        return flag;
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private boolean removeBondedUniDevice(final int deviceClass, final String address) {
        boolean flag = false;
        Map<String, UniDevice> map;
        UniDevice device;

        // Classic Bluetooth
        map = this.mBtManager.getLastBondedBcDevices();
        if (map.containsKey(address)) {
            device = map.get(address);
            if (device.getProtocolType() == deviceClass) { // TODO 修改
                this.mBtManager.removeBondBc(address);
                flag = true;
            }
        }

        // Bluetooth Low Energy
        map = this.mBtManager.getLastBondedBleDevices();
        if (map.containsKey(address)) {
            device = map.get(address);
            if (device.getProtocolType() == deviceClass) {
                this.mBtManager.removeBondBle(address);
                flag = true;
            }
        }

        // Wi-Fi
        // TODO: WiFi

        return flag;
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private boolean removeBondedUniDevice(final int deviceClass, final String deviceAddress, IUniDeviceConnectionCallback callback) {
        this.registerCallback(callback);

        // TODO: device class
        return this.removeBondedUniDevice(deviceClass, deviceAddress);
    }



    /*===================*
     * IPC               *
     *===================*/

    private final class UniDeviceConnectionServiceBinder extends IUniDeviceConnection.Stub {

        /**
         * 发现泛设备
         *
         * @param uniConnectionCallback 发现泛设备后，通过此callback回调已发现的设备列表和 DISCOVERY_SEARCHING 给外部
         */
        @Override
        public void discoverUniDevice(IUniDeviceConnectionCallback uniConnectionCallback) throws RemoteException {
            if (DEBUG_CALL) {
                Log.d(TAG, "call: discoverUniDevice");
            }

            UniDeviceConnectionService.this.startDiscoverUniDevice(uniConnectionCallback);

        }

        /**
         * 停止发现泛设备
         *
         * @param uniConnectionCallback 停止发现泛设备后，通过此callback回调已发现的设备列表和 DISCOVERY_END 给外部
         */
        @Override
        public void stopDiscoverUniDevice(IUniDeviceConnectionCallback uniConnectionCallback) throws RemoteException {
            UniDeviceConnectionService.this.stopDiscoverUniDevice(uniConnectionCallback);
        }

        /**
         * 连接泛设备
         *
         * @param deviceType            泛设备的类型 ：摄像头:camera,香氛机:aroma
         * @param deviceId              泛设备的唯一标识id
         * @param uniConnectionCallback 连接泛设备的回调接口，连接状态通过此callback回调给外部
         */
        @Override
        public void connectUniDevice(String deviceType, String deviceId, IUniDeviceConnectionCallback uniConnectionCallback) throws RemoteException {
            UniDeviceConnectionService.this.connectUniDevice(Integer.parseInt(deviceType), deviceId, uniConnectionCallback);
        }

        /**
         * 解绑泛设备
         *
         * @param deviceType            泛设备的类型: 摄像头:camera,香氛机:aroma
         * @param deviceId              泛设备的唯一标识id
         * @param uniConnectionCallback 解绑泛设备的回调接口，解绑状态通过此callback回调给外部
         */
        @Override
        public void removeBondedUniDevice(String deviceType, String deviceId, IUniDeviceConnectionCallback uniConnectionCallback) throws RemoteException {
            UniDeviceConnectionService.this.removeBondedUniDevice(Integer.parseInt(deviceType), deviceId, uniConnectionCallback);
        }


        /**
         * 获取当前某个类型下已经连接上的泛设备
         *
         * @param deviceType 泛设备的类型，
         * @return 当前连接上的泛设备列表
         */
        @SuppressLint("MissingPermission")
        @Override
        public String getConnectedUniDevices(String deviceType) throws RemoteException {
            try {
                return UniDeviceConnectionService.this.getLastConnectedUniDeviceInfo();
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }
    }


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind: " + intent);
        return new UniDeviceConnectionServiceBinder();
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate");
        super.onCreate();

        // Bluetooth
        this.mBtManager = new BtManager(this);
        this.mBtManager.registerObserver(new BtUtils.BcStateObserver() {
            @Override
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            public void onDiscoveryStateChanged(List<BluetoothDevice> list, int state) {
                BtUtils.BcStateObserver.super.onDiscoveryStateChanged(list, state);

                for (BluetoothDevice device : list) {
                    String address = device.getAddress();
                    if (!UniDeviceConnectionService.this.mDevices.containsKey(address)) {
                        UniDeviceConnectionService.this.mDevices.put(address, new UniDevice(address, UniDevice.PROTOCOL_TYPE_BLUETOOTH_CLASSIC, device.getName(), device.getBluetoothClass().getDeviceClass()));   // TODO: Multiple Mode.
                    }
                }
                int callbackState = BtUtils.STATE_DISCOVERY_SEARCHING;

                switch (state) {
                    case BtUtils.STATE_DISCOVERY_SEARCHING:
                        UniDeviceConnectionService.this.mIsBcDiscovering = true;
                        break;
                    case BtUtils.STATE_DISCOVERY_END:
                        UniDeviceConnectionService.this.mIsBcDiscovering = false;
                        if (!UniDeviceConnectionService.this.mIsBleDiscovering) {
                            callbackState = BtUtils.STATE_DISCOVERY_END;
                        }
                        break;
                    default:
                        return;
                }

                try {
                    UniDeviceConnectionService.this.callbackAllOnDiscoveryStateChanged(
                            UniDeviceConnectionService.this.updateLastFoundUniDeviceInfoAndBroadcast(),
                            callbackState
                    );
                } catch (JSONException | RemoteException e) {
                    throw new RuntimeException(e);
                }

            }

            @Override
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            public void onConnectionStateChanged(BluetoothDevice device, int state) {
                BtUtils.BcStateObserver.super.onConnectionStateChanged(device, state);
                try {
                    UniDeviceConnectionService.this.callbackAllOnConnectionStateChanged(device.getBluetoothClass().getDeviceClass(), device.getAddress(), state);
                } catch (RemoteException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            public void onBondStateChanged(BluetoothDevice device, int state) {
                BtUtils.BcStateObserver.super.onBondStateChanged(device, state);

                try {
                    UniDeviceConnectionService.this.callbackAllOnBondStateChanged(device.getBluetoothClass().getDeviceClass(), device.getAddress(), state);
                } catch (RemoteException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            public void onConnectionFailed(BluetoothDevice device, int errorCode) {
                BtUtils.BcStateObserver.super.onConnectionFailed(device, errorCode);
                try {
                    UniDeviceConnectionService.this.callbackAllOnConnectionFailed(device.getBluetoothClass().getDeviceClass(), device.getAddress(), errorCode);
                } catch (RemoteException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            public void onUnbondFailed(BluetoothDevice device, int errorCode) {
                BtUtils.BcStateObserver.super.onUnbondFailed(device, errorCode);
                try {
                    UniDeviceConnectionService.this.callbackAllOnUnbondFailed(device.getBluetoothClass().getDeviceClass(), device.getAddress(), errorCode);
                } catch (RemoteException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        this.mBtManager.registerObserver(new BtUtils.BleStateObserver() {
            @Override
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            public void onDiscoveryStateChanged(List<BluetoothDevice> list, int state) {
                BtUtils.BleStateObserver.super.onDiscoveryStateChanged(list, state);

                for (BluetoothDevice device : list) {
                    String address = device.getAddress();
                    if (!UniDeviceConnectionService.this.mDevices.containsKey(address)) {
                        UniDeviceConnectionService.this.mDevices.put(address, new UniDevice(address, UniDevice.PROTOCOL_TYPE_BLUETOOTH_CLASSIC, device.getName(), device.getBluetoothClass().getDeviceClass()));   // TODO: Multiple Mode.
                    }
                }
                int callbackState = BtUtils.STATE_DISCOVERY_SEARCHING;

                switch (state) {
                    case BtUtils.STATE_DISCOVERY_SEARCHING:
                        UniDeviceConnectionService.this.mIsBleDiscovering = true;


                        break;
                    case BtUtils.STATE_DISCOVERY_END:
                        UniDeviceConnectionService.this.mIsBleDiscovering = false;
                        if (!UniDeviceConnectionService.this.mIsBcDiscovering) {
                            callbackState = BtUtils.STATE_DISCOVERY_END;
                        }
                        break;
                    default:
                        return;
                }

                try {
                    UniDeviceConnectionService.this.callbackAllOnDiscoveryStateChanged(
                            UniDeviceConnectionService.this.updateLastFoundUniDeviceInfoAndBroadcast(),
                            callbackState
                    );
                } catch (JSONException | RemoteException e) {
                    throw new RuntimeException(e);
                }

            }

            @Override
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            public void onConnectionStateChanged(BluetoothDevice device, int state) {
                BtUtils.BleStateObserver.super.onConnectionStateChanged(device, state);
                try {
                    UniDeviceConnectionService.this.callbackAllOnConnectionStateChanged(device.getBluetoothClass().getDeviceClass(), device.getAddress(), state);
                } catch (RemoteException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            public void onBondStateChanged(BluetoothDevice device, int state) {
                BtUtils.BleStateObserver.super.onBondStateChanged(device, state);
                try {
                    UniDeviceConnectionService.this.callbackAllOnBondStateChanged(device.getBluetoothClass().getDeviceClass(), device.getAddress(), state);
                } catch (RemoteException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            public void onConnectionFailed(BluetoothDevice device, int errorCode) {
                BtUtils.BleStateObserver.super.onConnectionFailed(device, errorCode);
                try {
                    UniDeviceConnectionService.this.callbackAllOnConnectionFailed(device.getBluetoothClass().getDeviceClass(), device.getAddress(), errorCode);
                } catch (RemoteException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            public void onUnbondFailed(BluetoothDevice device, int errorCode) {
                BtUtils.BleStateObserver.super.onUnbondFailed(device, errorCode);

                try {
                    UniDeviceConnectionService.this.callbackAllOnUnbondFailed(device.getBluetoothClass().getDeviceClass(), device.getAddress(), errorCode);
                } catch (RemoteException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        // Wi-Fi
        this.mWifiManager = new WifiManager();  // TODO: input
        // TODO: Wi-Fi Callback

    }

    @Override
    public void onDestroy() {
        // Bluetooth
        this.mBtManager.onDestroy();

        // Wi-Fi
        this.mWifiManager.onDestroy();

        super.onDestroy();
    }

    private class BtManager {
        private final BtUtils mmBtUtils;

        public BtManager(Context context) {
            this.mmBtUtils = new BtUtils(context);

        }

        /*===================*
         * Getters           *
         *===================*/

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        private Set<String> getLastBondedBcDeviceAddressSet() {
            Set<String> addressSet = new HashSet<>();
            for (BluetoothDevice device : this.mmBtUtils.getBondedBcDevices()) {
                addressSet.add(device.getAddress());
            }
            return addressSet;
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        private Map<String, UniDevice> getLastBondedBcDevices() {
            Map<String, UniDevice> devices = new HashMap<>();

            Consumer<Set<BluetoothDevice>> consumer = set -> {
                for (BluetoothDevice device : set) {
                    String address = device.getAddress();
                    UniDevice uniDevice = new UniDevice(address, UniDevice.PROTOCOL_TYPE_BLUETOOTH_CLASSIC, device.getName(), device.getBluetoothClass().getDeviceClass());
                    devices.put(address, uniDevice);
                }
            };

            this.mmBtUtils.getBondedBcDevices(consumer);

            return devices;
        }

        private Set<String> getLastBondedBleDeviceAddressSet() {
            return this.mmBtUtils.getBondedBleDeviceAddressSet();
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        private Map<String, UniDevice> getLastBondedBleDevices() {
            Map<String, UniDevice> devices = new HashMap<>();

            Consumer<Map<String, BluetoothGatt>> consumer = map -> {
                for (Map.Entry entry : map.entrySet()) {
                    String address = (String) entry.getKey();
                    BluetoothGatt gatt = (BluetoothGatt) entry.getValue();
                    BluetoothDevice device = gatt.getDevice();
                    UniDevice uniDevice = new UniDevice(address, UniDevice.PROTOCOL_TYPE_BLUETOOTH_LE, device.getName(), device.getBluetoothClass().getDeviceClass());
                    devices.put(address, uniDevice);
                }
            };

            this.mmBtUtils.getBondedBleDevices(consumer);

            return devices;
        }

        private void updateLastFoundBcDeviceInfo(Consumer<Set<BluetoothDevice>> consumer) {
            this.mmBtUtils.updateLastFoundBcDeviceInfo(consumer);
        }

        private void updateLastFoundBleDeviceInfo(Consumer<Set<BluetoothDevice>> consumer) {
            this.mmBtUtils.updateLastFoundBleDeviceInfo(consumer);
        }

        private void clear() {
            this.mmBtUtils.clear();
        }



        /*===================*
         * State Observer    *
         *===================*/

        private void registerObserver(BtUtils.BcStateObserver observer) {
            this.mmBtUtils.registerObserver(observer);
        }

        private void registerObserver(BtUtils.BleStateObserver observer) {
            this.mmBtUtils.registerObserver(observer);
        }



        /*===================*
         * Discovery         *
         *===================*/

//        @RequiresPermission(allOf = {Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION})
        @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
        private void startBtDiscovery() {
            this.mmBtUtils.clear();

            /* Discover BLE (Bluetooth Low Energy) Device */
            Log.d(TAG, "Start discover bluetooth low energy devices...");
            this.mmBtUtils.startDiscoveryLe();

            /* Discover BC (Bluetooth Classic) Device */
            Log.d(TAG, "Start discover classic bluetooth devices...");
            this.mmBtUtils.startDiscoveryBc();
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
        private void stopBluetoothDiscovery() {
            /* Discover BLE (Bluetooth Low Energy) Device */
            Log.d(TAG, "Stop discover bluetooth low energy devices...");
            this.mmBtUtils.stopDiscoveryLe();

            /* Discover BC (Bluetooth Classic) Device */
            Log.d(TAG, "Stop discover classic bluetooth devices...");
            this.mmBtUtils.stopDiscoveryBc();
        }



        /*==============*
         * Connect      *
         *==============*/

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        private void connectBc(final String address) {
            this.mmBtUtils.connectBc(address);
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        private void connectBle(final String address) {
            this.mmBtUtils.connectBle(address);
        }



        /*==============*
         * Remove       *
         *==============*/

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        private void removeBondBc(final String address) {
            this.mmBtUtils.removeBondBc(address);
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        private void removeBondBle(final String address) {
            this.mmBtUtils.removeBondBle(address);
        }




        private void onDestroy() {
            this.mmBtUtils.onDestroy();
        }
    }

    private class WifiSoftApManager {
        // TODO: Wi-Fi
        private WifiTetheringHandler mWifiTetheringHandler;

        public WifiSoftApManager(Context context) {
            this.mWifiTetheringHandler = new WifiTetheringHandler(context, new WifiTetheringHandler.WifiTetheringAvailabilityListener() {
                @Override
                public void onWifiTetheringAvailable() {
                    Log.d(TAG, "onWifiTetheringAvailable");
                }

                @Override
                public void onWifiTetheringUnavailable() {
                    Log.d(TAG, "onWifiTetheringUnavailable");
                }
            });
        }

        private void startBtDiscovery() {

            this.mWifiTetheringHandler
            Log.d(TAG, "Start discover wifi devices...");
        }

        private void onDestroy() {
            this.mWifiTetheringHandler.onDestroy();
        }
    }

}
