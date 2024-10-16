package com.rxw.panconnection.service.aidl;

import com.rxw.panconnection.service.aidl.IUniDeviceConnectionCallback;

interface IUniDeviceConnection {

  /**
     * 发现泛设备
     *
     * @param uniConnectionCallback 发现泛设备后，通过此callback回调已发现的设备列表和 DISCOVERY_SEARCHING 给外部
     */
    void discoverUniDevice(IUniDeviceConnectionCallback uniConnectionCallback);

    /**
     * 停止发现泛设备
     *
     * @param uniConnectionCallback 停止发现泛设备后，通过此callback回调已发现的设备列表和 DISCOVERY_END 给外部
     */
    void stopDiscoverUniDevice(IUniDeviceConnectionCallback uniConnectionCallback);

    /**
     * 连接泛设备
     *
     * @param deviceType 泛设备的类型 ：摄像头:camera,香氛机:aroma
     * @param deviceId 泛设备的唯一标识id
     * @param uniConnectionCallback 连接泛设备的回调接口，连接状态通过此callback回调给外部
     */
    void connectUniDevice(String deviceType, String deviceId, IUniDeviceConnectionCallback uniConnectionCallback);

    /**
     * 解绑泛设备
     *
     * @param deviceType 泛设备的类型: 摄像头:camera,香氛机:aroma
     * @param deviceId 泛设备的唯一标识id
     * @param uniConnectionCallback 解绑泛设备的回调接口，解绑状态通过此callback回调给外部
     */
    void removeBondedUniDevice(String deviceType, String deviceId, IUniDeviceConnectionCallback uniConnectionCallback);

    /**
     * 获取当前某个类型下已经连接上的泛设备
     * @param deviceType 泛设备的类型，
     * @return 当前连接上的泛设备列表
     */
    String getConnectedUniDevices(String deviceType);
}