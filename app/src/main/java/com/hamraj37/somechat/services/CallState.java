package com.hamraj37.somechat.services;

public class CallState {
    public static boolean isCallActive = false;
    public static String activeCallId = null;
    public static String activeCallName = null;
    public static String activeCallAvatar = null;
    public static boolean isActiveCallVideo = false;
    public static boolean isActiveCallIncoming = false;
    public static long callStartTime = 0;

    public static void reset() {
        isCallActive = false;
        activeCallId = null;
        activeCallName = null;
        activeCallAvatar = null;
        isActiveCallVideo = false;
        isActiveCallIncoming = false;
        callStartTime = 0;
    }
}
