package com.example.jian;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.os.Handler;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

public class AutoClickService extends AccessibilityService
{

    private static final String TAG = "AutoClickService";
    private Handler handler;
    private Runnable runnable;
    private boolean taskStopped = false;


    @Override
    public void onAccessibilityEvent(AccessibilityEvent event)
    {
        // 处理无障碍事件的逻辑可以在这里实现!
    }

    @Override
    public void onInterrupt()
    {
        Log.d(TAG, "AutoClickService interrupted!");
    }

    @Override
    protected void onServiceConnected()
    {
        super.onServiceConnected();
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;

        setServiceInfo(info);
        Log.d(TAG, "AutoClickService connected!");

        startAutoClickTask();

    }

    private void performAutoClick()
    {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null)
        {
            return;
        }

        List<AccessibilityNodeInfo> allowButton = rootNode.findAccessibilityNodeInfosByText("允许");
        // 查找带有"允许"文本的按钮
        String appName = getApplicationContext().getPackageManager().getApplicationLabel(getApplicationInfo()).toString();
        List<AccessibilityNodeInfo> allowLabel = rootNode.findAccessibilityNodeInfosByText(appName + "正在开启/关闭WLAN");
        if (allowLabel!= null && !allowLabel.isEmpty() && allowButton != null && !allowButton.isEmpty())
        {
            for (AccessibilityNodeInfo button : allowButton)
            {
                if (button.isClickable())
                {
                    button.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    Log.d(TAG, "执行点击！");

                    stopAutoClickTask();
                    return;
                }
            }
        }
        else
        {
            Log.d(TAG, "没有发现“允许”按钮！");
        }
    }

    public void startAutoClickTask()
    {
        // 启动定时器
        handler = new Handler();
        runnable = new Runnable()
        {
            @Override
            public void run()
            {
                if (taskStopped)
                {
                    return;
                }
                // 检查窗口内容并执行点击操作
                Log.d(TAG, "自动点击任务开始！");
                performAutoClick();
                handler.postDelayed(this, 5000); // 每隔5秒检查一次
            }
        };
        handler.postDelayed(runnable, 1000); // 1秒后开始第一次执行
    }

    public void stopAutoClickTask()
    {
        if (handler != null && runnable != null)
        {
            handler.removeCallbacks(runnable); // 停止定时任务
            taskStopped = true;
            Log.d(TAG, "自动点击任务停止！");
        }
    }

}
