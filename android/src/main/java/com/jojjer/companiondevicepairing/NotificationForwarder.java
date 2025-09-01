package com.jojjer.companiondevicepairing;

import android.util.Log;

public class NotificationForwarder {
    private static NotificationForwarder instance;
    private BleManager bleManager;

    private NotificationForwarder() {}

    public static synchronized NotificationForwarder getInstance() {
        if (instance == null) instance = new NotificationForwarder();
        return instance;
    }

    public void setBleManager(BleManager manager) {
        this.bleManager = manager;
    }

    public void forwardNotification(Long ts, String pkg, String title, String text) {
        if (bleManager != null) {
                bleManager.sendNotificationToWatch(ts, pkg, title, text);
        }
    }
}

