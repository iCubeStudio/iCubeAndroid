package com.example.jian;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;  // 检查应用程序是否有特定权限
import android.net.wifi.ScanResult;  // 表示一个Wi-Fi扫描结果
import android.net.wifi.WifiConfiguration;  // 表示Wi-Fi网络的配置
import android.net.wifi.WifiManager;  // 管理Wi-Fi功能
import android.net.wifi.WifiInfo;
import android.view.View;  // 表示用户界面的基本构建块
import android.widget.ArrayAdapter;  // 将数组或列表的数据与ListView等视图绑定
import android.widget.Button;  // 表示一个按钮组件
import android.widget.ListView;  // 表示一个可滚动的列表视图
import android.widget.Toast;  // 用于显示短暂的消息提示
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;  // 用于指示不能为null的参数或返回值
import androidx.core.app.ActivityCompat;  // 提供用于处理权限请求的兼容性方法。
import androidx.core.content.ContextCompat;  // 提供与上下文相关的兼容性功能。

import java.util.ArrayList;  // 实现动态数组，常用于存储Wi-Fi扫描结果。
import java.util.List;  // 用于定义一个列表结构
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import java.util.HashSet;
import java.util.Set;

import android.os.Handler;  // 处理定时任务
import android.os.Looper;   // 用于创建与主线程相关联的Looper

public class MainActivity extends AppCompatActivity {
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;  // 定义一个常量，用于标识位置权限请求的代码
    private WifiManager wifiManager;
    private ListView listView;  // 声明一个ListView对象，用于显示Wi-Fi扫描结果
    private ArrayAdapter<String> adapter;   // 声明一个ArrayAdapter对象，将Wi-Fi扫描结果绑定到ListView
    private List<String> wifiList;  // 声明一个List对象，用于存储Wi-Fi扫描结果

    private static final String TARGET_SSID = "Rivotek-Visitor";
    private static final String TARGET_PASSWORD = "1234@abcd";  // "J1511" && "kcdsj151101"

    private Handler handler;  // 用于处理定时扫描的Handler
    private Runnable scanRunnable;  // 用于执行扫描操作的Runnable
    private static final int SCAN_INTERVAL = 5000;  // 设定5秒的扫描间隔

    private boolean isConnectedToTarget = false;

    private Switch wifiSwitch;
    private Button forgetWifiButton;
    private TextView wifiListHeader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);  // 调用父类的onCreate方法，保证基础功能的正常运行
        EdgeToEdge.enable(this);  // 用Edge-to-Edge模式，使内容延伸到系统栏区域
        setContentView(R.layout.activity_main);  // 设置活动的布局文件activity_main.xml作为用户界面
        // 设置一个监听器，处理窗口插图的变化，如系统状态栏和导航栏
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);  // 获取系统的WifiManager服务，用于管理Wi-Fi。

        listView = findViewById(R.id.listView);  // 根据ID找到布局中的ListView组件
        wifiList = new ArrayList<>();  // 初始化Wi-Fi列表，存储扫描结果。
        adapter = new ArrayAdapter<>(this, R.layout.list_item, R.id.textViewItem, wifiList); // 创建一个ArrayAdapter，将Wi-Fi列表绑定到简单列表项布局中。
        listView.setAdapter(adapter);  // 为ListView设置适配器，显示Wi-Fi扫描结果。

        wifiSwitch = findViewById(R.id.wifiSwitch);
        forgetWifiButton = findViewById(R.id.forgetWifiButton);
        wifiListHeader = findViewById(R.id.wifiListHeader);
        handler = new Handler(Looper.getMainLooper());  // 创建与主线程相关联的Handler


        listView.setOnItemClickListener((parent, view, position, id) -> {
            // 获取点击的Wi-Fi信息
            String selectedWifiSSID = wifiList.get(position);

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            {
                return;
            }

            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            String connectedSSID = wifiInfo.getSSID().replace("\"", "");

            // 检查所选Wi-Fi是否为当前连接的目标Wi-Fi
            if (selectedWifiSSID.equals(connectedSSID) && connectedSSID.equals(TARGET_SSID))
            {
                int ipAddress = wifiInfo.getIpAddress();
                String formattedIpAddress = String.format(
                        "%d.%d.%d.%d",
                        (ipAddress & 0xff),
                        (ipAddress >> 8 & 0xff),
                        (ipAddress >> 16 & 0xff),
                        (ipAddress >> 24 & 0xff)
                );

                String wifiInfoDisplay = "SSID: " + connectedSSID + "\n" +
                        "IP Address: " + formattedIpAddress + "\n" +
                        "BSSID: " + wifiInfo.getBSSID();
                Toast.makeText(MainActivity.this, wifiInfoDisplay, Toast.LENGTH_SHORT).show();
            }
            else
            {
                // 如果选中的不是当前连接的目标Wi-Fi，只显示SSID和BSSID
                List<ScanResult> results = wifiManager.getScanResults();
                for (ScanResult result : results)
                {
                    if (result.SSID.equals(selectedWifiSSID))
                    {
                        String wifiInfoDisplay = "SSID: " + result.SSID + "\n" +
                                "BSSID: " + result.BSSID;
                        Toast.makeText(MainActivity.this, wifiInfoDisplay, Toast.LENGTH_SHORT).show();
                        break;
                    }
                }
            }

        });

        wifiSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked)
            {
                isConnectedToTarget = false;

                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED)
                {
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                            LOCATION_PERMISSION_REQUEST_CODE);
                }
                else
                {
                    startContinuousScan();
                }
            }
            else
            {
                stopContinuousScan();
                wifiListHeader.setVisibility(View.GONE);
                forgetWifiButton.setEnabled(false);
            }
        });

        forgetWifiButton.setOnClickListener(v -> {
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            if (wifiInfo != null && wifiInfo.getNetworkId() != -1)
            {
                int netId = wifiInfo.getNetworkId();
                wifiManager.removeNetwork(netId);
                wifiManager.saveConfiguration();
                Toast.makeText(MainActivity.this, "已忘记当前Wi-Fi连接", Toast.LENGTH_SHORT).show();
                forgetWifiButton.setEnabled(false);

                isConnectedToTarget = true;  // 设置忘记连接后，是否继续连接Wi-Fi
            }
        });
    }

    // IP地址转换
    private String intToIp(int ip)
    {
        return (ip & 0xFF) + "." +
                ((ip >> 8) & 0xFF) + "." +
                ((ip >> 16) & 0xFF) + "." +
                ((ip >> 24) & 0xFF);
    }

    private void stopContinuousScan()
    {
        handler.removeCallbacks(scanRunnable);
        wifiList.clear();
        adapter.notifyDataSetChanged();
        wifiManager.setWifiEnabled(false);
    }

    private void startContinuousScan()
    {
        scanRunnable = new Runnable()
        {
            @Override
            public void run()
            {
                checkCurrentConnection();
                handler.postDelayed(this, SCAN_INTERVAL);  // 5秒后再次执行此Runnable
            }
        };
        handler.post(scanRunnable);  // 立即开始第一次扫描
    }

    private void checkCurrentConnection()
    {
        // 检查 Wi-Fi 是否开启
        if (!wifiManager.isWifiEnabled())
        {
            Toast.makeText(this, "WiFi正在开启中...", Toast.LENGTH_SHORT).show();  // 显示一条Toast消息，通知用户正在启用Wi-Fi。
            wifiManager.setWifiEnabled(true);
        }

        // 检查是否连接目标 Wi-Fi
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        if (wifiInfo != null && wifiInfo.getNetworkId() != -1)
        {
            String currentSSID = wifiInfo.getSSID().replace("\"", "");  // 获取当前连接的SSID
            if (currentSSID.equals(TARGET_SSID))
            {
                isConnectedToTarget = true;
            }
            else
            {
                // 如果连接到的不是目标 Wi-Fi，则断开
                wifiManager.disconnect();
                Toast.makeText(this, "已断开非目标Wi-Fi连接！", Toast.LENGTH_SHORT).show();
                isConnectedToTarget = false;
            }
            forgetWifiButton.setEnabled(true);
        }
        else
        {
            forgetWifiButton.setEnabled(false);
        }

        // 如果未连接任何Wi-Fi，直接扫描
        scanAndConnect();
    }

    // 执行Wi-Fi扫描和连接操作
    private void scanAndConnect()
    {
        try
        {

            wifiManager.startScan();  //开始扫描附近的Wi-Fi网络
            List<ScanResult> results = wifiManager.getScanResults();  //获取扫描结果的列表
            wifiList.clear();  // 清空Wi-Fi列表，为新扫描结果做准备。

            Set<String> uniqueSSIDs = new HashSet<>();  // 使用Set来过滤重复的SSID

            for (ScanResult result : results)
            {
                if (!uniqueSSIDs.contains(result.SSID))
                {
                    wifiListHeader.setVisibility(View.VISIBLE);
                    // 如果SSID未在Set中，添加到Set和List中
                    uniqueSSIDs.add(result.SSID);
                    wifiList.add(result.SSID);

                    if (!isConnectedToTarget)
                    {
                        if (result.SSID.equals(TARGET_SSID))
                        {
                            Toast.makeText(this, "正在进行目标Wi-Fi连接！", Toast.LENGTH_SHORT).show();
                            connectToWifi(result.SSID, TARGET_PASSWORD);
                            isConnectedToTarget = true;
                            forgetWifiButton.setEnabled(true);
                        }
                        else
                        {
                            Toast.makeText(this, "未找到目标Wi-Fi！", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            }
            adapter.notifyDataSetChanged();  //通知适配器数据已更改，刷新ListView显示

        }
        catch (SecurityException e)
        {
            Toast.makeText(this, "无法访问Wi-Fi，权限被拒绝！", Toast.LENGTH_SHORT).show();
        }
    }


    // 配置网络连接
    private void connectToWifi(String ssid, String password)
    {
        WifiConfiguration wifiConfig = new WifiConfiguration();
        wifiConfig.SSID = "\"" + ssid + "\"";
        // String password = generateSHA256Password(ssid);
        wifiConfig.preSharedKey = "\"" + password + "\"";

        // 添加网络配置并连接
        int netId = wifiManager.addNetwork(wifiConfig);  // 将配置的网络添加到Wi-Fi管理器中，并获取网络ID
        wifiManager.disconnect();
        wifiManager.enableNetwork(netId, true);
        wifiManager.reconnect();

        Toast.makeText(this, "正在连接到..." + ssid, Toast.LENGTH_SHORT).show();
    }

    // 处理权限请求的结果。
    // 如果用户授予了位置权限，继续执行扫描；否则，显示权限要求的提示。
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
    {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);  // 处理权限请求结果的回调方法
        // 检查请求代码是否与位置权限请求代码匹配。
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE)
        {
            // 检查是否授予了位置权限，如果已授予，则继续执行扫描和连接操作。
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
            {
                startContinuousScan();  // 授权后开始循环扫描
            }
            else
            {
                Toast.makeText(this, "需要位置权限才能扫描WiFi网络!", Toast.LENGTH_SHORT).show();
            }
        }
    }
}