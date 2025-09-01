package com.jojjer.companiondevicepairing;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.app.Notification;
import android.util.Log;

import java.util.List;
import java.util.ArrayList;


public class NLService extends NotificationListenerService {
    private static final String logTag = "NTL";

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        Notification notification = sbn.getNotification();
        if (notification == null) return;

        CharSequence title = notification.extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence text = notification.extras.getCharSequence(Notification.EXTRA_TEXT);

        if (title == null || text == null) return;

        // TODO: Set up storing of "allowed" app notifications and only allow those past here
        List<String> listOfAllowedPackages = new ArrayList<>();
        listOfAllowedPackages.add("discord");
        listOfAllowedPackages.add("outlook");
        listOfAllowedPackages.add("gmail");
        listOfAllowedPackages.add("messaging");
        listOfAllowedPackages.add("calendar");

        String pkgName = "";

        Boolean allowedNotification = false;
        for (String allowedPkg : listOfAllowedPackages) {
            if (sbn.getPackageName().toString().toLowerCase().contains(allowedPkg.toLowerCase())) {
                pkgName = allowedPkg;
                allowedNotification = true;
                break;
            }
        }

        if (!allowedNotification) return;
        if ((sbn.getNotification().flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;

        Log.d(logTag, "Notification posted, ID: " + sbn.getId() + ", key: " + sbn.getKey());
        Log.d(logTag, "Notification from " + sbn.getPackageName() + " title=" + title + " text=" + text);

        NotificationForwarder.getInstance().forwardNotification(
            sbn.getPostTime(),
            pkgName,
            title.toString(),
            text.toString());
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        Log.d(logTag, "Notification removed: " + sbn.getPackageName());
        // TODO: Remove notifications on the watch if they still exist on it
    }
}

