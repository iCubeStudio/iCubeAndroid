package com.rxw.panconnection.service.unidevice;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collection;

public class UniDevice {
    private static final String TAG = UniDevice.class.getSimpleName();

    private String mAddress;
    private int mProtocolType;
    private String mDeviceName;
    private int mDeviceClass;

    /*===================*
     * Utils             *
     *===================*/

    public static final int PROTOCOL_TYPE_UNKNOWN           = 0     ;
    public static final int PROTOCOL_TYPE_BLUETOOTH_CLASSIC = 1     ;
    public static final int PROTOCOL_TYPE_BLUETOOTH_LE      = 1 << 1;
    public static final int PROTOCOL_TYPE_WIFI              = 1 << 2;

    public static boolean isBcDevice(final int type) {
        return (type & (PROTOCOL_TYPE_BLUETOOTH_CLASSIC)) != 0;
    }

    public static boolean isBleDevice(final int type) {
        return (type & (PROTOCOL_TYPE_BLUETOOTH_LE)) != 0;
    }

    public static boolean isWifiDevice(final int type) {
        return (type & (PROTOCOL_TYPE_WIFI)) != 0;
    }

    /*===================*
     * Constructors      *
     *===================*/

    public UniDevice(String address, int protocolType, String deviceName, int deviceClass) {
        this.mAddress = address;
        this.mProtocolType = protocolType;
        this.mDeviceName = deviceName;
        this.mDeviceClass = deviceClass;
    }

    /*========================*
     * Getters and Setters    *
     *========================*/

    public String getAddress() {
        return this.mAddress;
    }

    public int getProtocolType() {
        return this.mProtocolType;
    }

    public void addProtocolType(int mask) {
        this.mProtocolType = this.mProtocolType | mask;
    }

    public void deleteProtocolType(int mask) {
        this.mProtocolType = this.mProtocolType & (~mask);
    }

    public String getDeviceName() {
        return this.mDeviceName;
    }

    public int getDeviceClass() {
        return this.mDeviceClass;
    }

    public static JSONObject toJSONObject(UniDevice device) throws JSONException {
        String name = device.getDeviceName();
        String address = device.getAddress();
        int clazz = device.getDeviceClass();

        JSONObject obj = new JSONObject();
        obj.append("device_type", clazz);
        obj.append("device_name", name);
        obj.append("device_id", address);

        return obj;
    }

    public JSONObject toJSONObject() throws JSONException {

        JSONObject obj = new JSONObject();
        obj.append("device_type", this.mDeviceClass);
        obj.append("device_name", this.mDeviceName);
        obj.append("device_id", this.mAddress);

        return obj;
    }

    public static JSONArray getDeviceInfoJsonArray(Collection<UniDevice> collection) throws JSONException {

        JSONArray array = new JSONArray();
        for (Object obj : collection) {

            if (obj instanceof UniDevice) {
                array.put(((UniDevice) obj).toJSONObject());
            }

            if (obj instanceof JSONObject) {
                array.put(obj);
            }

        }
        return array;
    }
    
}
