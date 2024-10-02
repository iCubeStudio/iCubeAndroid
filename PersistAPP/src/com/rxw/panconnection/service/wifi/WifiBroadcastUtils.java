package com.rxw.panconnection.service.wifi;

import android.annotation.NonNull;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.TetheringManager;
import android.net.wifi.SoftApConfiguration;
import android.net.wifi.SoftApCapability;
import android.net.wifi.SoftApInfo;
import android.net.wifi.WifiClient;
import android.net.wifi.WifiManager;
import android.content.Intent;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import android.net.MacAddress;
import android.util.Log;

import com.android.internal.util.ConcurrentUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class WifiBroadcastUtils {

    private static final String TAG = "WifiBroadcastUtils";

}