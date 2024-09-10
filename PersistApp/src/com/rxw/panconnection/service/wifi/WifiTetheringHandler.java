/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.rxw.panconnection.service.wifi;

import android.annotation.NonNull;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.TetheringManager;
import android.net.wifi.SoftApConfiguration;
import android.net.wifi.SoftApInfo;
import android.net.wifi.WifiClient;
import android.net.wifi.WifiManager;
import android.util.Log;
import android.content.Intent;
import android.net.MacAddress;

import com.android.internal.util.ConcurrentUtils;
import java.util.ArrayList;
import java.util.List;


public class WifiTetheringHandler 
{

    private final static String TAG = "WifiTetheringHandler";

    private final Context mContext;
    private final WifiManager mWifiManager;
    private final TetheringManager mTetheringManager;
    private final WifiTetheringAvailabilityListener mWifiTetheringAvailabilityListener;
    private boolean mRestartBooked = false;

    private List<WifiClient> mConnectedClients;
    private List<MacAddress> mBlockedClients;

    //监听 Wi-Fi 热点（Soft AP）的状态和连接设备
    private final WifiManager.SoftApCallback mSoftApCallback = new WifiManager.SoftApCallback() 
    {
        @Override
        public void onStateChanged(int state, int failureReason) 
        {
            // Wi-Fi 热点的状态发生变化时调用此方法
            handleWifiApStateChanged(state);
        }

        @Override
        public void onConnectedClientsChanged(@NonNull SoftApInfo info, @NonNull List<WifiClient> clients) 
        {
            // 当客户端数量发生变化时，会调用此方法通知监听者连接设备的数量变化。
            // 通过这个方法，应用程序可以实时监控连接的设备数。
            mConnectedClients = clients;
            for (WifiClient client : clients) 
            {
                MacAddress macAddress = client.getMacAddress();
                if (isFirstTimeConnection(macAddress)) 
                {
                    blockClient(macAddress);  // 如果是首次连接则屏蔽
                    sendBroadcastToApp(macAddress);  // 发送广播通知其他App
                }
            }
            mWifiTetheringAvailabilityListener.onConnectedClientsChanged(clients.size());
        }
    };

    // 将设备加入屏蔽名单
    private void blockClient(MacAddress macAddress) 
    {
        if (mBlockedClients == null) {
            mBlockedClients = new ArrayList<>();  // 确保mBlockedClients不为null
        }

        mBlockedClients.add(macAddress);
        SoftApConfiguration.Builder builder = new SoftApConfiguration.Builder(mWifiManager.getSoftApConfiguration());
        builder.setBlockedClientList(mBlockedClients);
        mWifiManager.setSoftApConfiguration(builder.build());
    }

    // 发送广播通知其他App进行用户确认
    private void sendBroadcastToApp(MacAddress macAddress) 
    {
        Intent intent = new Intent("com.rxw.panconnection.CONNECTION_REQUEST");
        intent.putExtra("mac_address", macAddress.toString());
        mContext.sendBroadcast(intent);
    }

    // 移除屏蔽设备，允许连接
    public void allowClientConnection(MacAddress macAddress) 
    {
        mBlockedClients.remove(macAddress);
        SoftApConfiguration.Builder builder = new SoftApConfiguration.Builder(mWifiManager.getSoftApConfiguration());
        builder.setBlockedClientList(mBlockedClients);
        mWifiManager.setSoftApConfiguration(builder.build());
    }

    // 判断是否为首次连接
    private boolean isFirstTimeConnection(MacAddress macAddress)
    {
        return !mConnectedClients.stream().anyMatch(client -> client.getMacAddress().equals(macAddress));
    }

    public WifiTetheringHandler(Context context, WifiTetheringAvailabilityListener wifiTetherAvailabilityListener) 
    {
        this(context,context.getSystemService(WifiManager.class), context.getSystemService(TetheringManager.class), wifiTetherAvailabilityListener);
        mConnectedClients = new ArrayList<>();
        mBlockedClients = new ArrayList<>();
    }

    private WifiTetheringHandler(Context context, WifiManager wifiManager, TetheringManager tetheringManager, WifiTetheringAvailabilityListener wifiTetherAvailabilityListener) 
    {
        mContext = context;
        mWifiManager = wifiManager;
        mTetheringManager = tetheringManager;
        mWifiTetheringAvailabilityListener = wifiTetherAvailabilityListener;
    }

    public void configureAndStartHotspot() 
    {
        SoftApConfiguration.Builder builder = new SoftApConfiguration.Builder();
        builder.setSsid("PanConnectionSSID")
                .setPassphrase("12345678", SoftApConfiguration.SECURITY_TYPE_WPA2_PSK)
                .setBand(SoftApConfiguration.BAND_2GHZ)
                .setHiddenSsid(false)
                .setClientControlByUserEnabled(true) // 启用用户控制
                .setBlockedClientList(mBlockedClients) // 初始化屏蔽名单
                .setMaxNumberOfClients(10); // 允许的最大客户端数

        SoftApConfiguration config = builder.build();
        mWifiManager.setSoftApConfiguration(config);
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

    /**
     * Changes the Wifi tethering state
     *
     * @param enable Whether to attempt to turn Wifi tethering on or off
     */
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

    // 处理 Wi-Fi 热点状态变化，执行相应的操作
    private void handleWifiApStateChanged(int state) 
    {
        Log.d(TAG, "handleWifiApStateChanged, state: " + state);
        switch (state) 
        {
            // （1）热点正在启用
            case WifiManager.WIFI_AP_STATE_ENABLING:
                break;
            // （2）热点已经启用
            case WifiManager.WIFI_AP_STATE_ENABLED:
                // 通知监听器 Wi-Fi 共享（热点）功能已经可用。
                mWifiTetheringAvailabilityListener.onWifiTetheringAvailable();
                break;
            // （3）热点正在禁用
            case WifiManager.WIFI_AP_STATE_DISABLING:
                // 通知监听器热点不可用
                mWifiTetheringAvailabilityListener.onWifiTetheringUnavailable();
                break;
            // （4）热点已经被禁用
            case WifiManager.WIFI_AP_STATE_DISABLED:
                
                mWifiTetheringAvailabilityListener.onWifiTetheringUnavailable();
                if (mRestartBooked) 
                {
                    startTethering(); // 检查 mRestartBooked，如果 true，说明热点是由于重启请求而被禁用的
                    mRestartBooked = false;
                }
                break;
            default:
                // 默认热点不可用，并通知监听器
                mWifiTetheringAvailabilityListener.onWifiTetheringUnavailable();
                break;
        }
    }

    // 启动 Wi-Fi 共享（热点）
    private void startTethering() 
    {
        mTetheringManager.startTethering(ConnectivityManager.TETHERING_WIFI, ConcurrentUtils.DIRECT_EXECUTOR, new TetheringManager.StartTetheringCallback() 
                {
                    // 热点启动失败时调用
                    @Override
                    public void onTetheringFailed(int error) 
                    {
                        Log.d(TAG, "onTetheringFailed, error: " + error);
                        mWifiTetheringAvailabilityListener.onWifiTetheringUnavailable();
                    }
                    // 热点成功启动时调用
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

    /**
     * Interface for receiving Wifi tethering status updates
     */
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
