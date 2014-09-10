/* 
Copyright (c) 2013 Dattas Moonchaser
Parts Copyright (c) 2013 Robin Sheat
Parts Copyright (c) 2013 Tiago Espinha (modifications)

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.dattasmoon.pebble.plugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.annotation.TargetApi;
import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.Parcelable;
import android.os.PowerManager;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.provider.ContactsContract.PhoneLookup;
import android.telephony.PhoneNumberUtils;
import android.telephony.PhoneStateListener;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.RemoteViews;
import android.widget.TextView;

import com.android.internal.telephony.ITelephony;
import com.dattasmoon.pebble.plugin.Constants.Mode;
import com.espinhasoftware.wechatpebble.pebblecomm.PebbleCall;
import com.espinhasoftware.wechatpebble.pebblecomm.PebbleMessage;
import com.espinhasoftware.wechatpebble.service.MessageProcessingService;
import com.espinhasoftware.wechatpebble.service.PebbleCommService;
import com.yangtsaosoftware.pebblemessenger.R;

public class NotificationService extends AccessibilityService {
    private class queueItem {
        public String title;
        public String body;

        public queueItem(String title, String body) {
            this.title = title;
            this.body = body;
        }
    }

    private Mode      mode                  = Mode.EXCLUDE;
    private boolean   notifications_only    = false;
    private boolean   no_ongoing_notifs     = false;
    private boolean   notification_extras   = false;
    private boolean   quiet_hours           = false;
    private boolean   notifScreenOn         = true;
    private boolean   unicode_notifications = false;
    private int       notification_timeout  = 10000;
    private long      min_notification_wait = 0 * 1000;
    // private final long notification_last_sent = 0;
    private Date      quiet_hours_before    = null;
    private Date      quiet_hours_after     = null;
    private String[]  packages              = null;
    // private Handler mHandler;
    private File      watchFile;
    private Long      lastChange;

    Queue<queueItem>  queue;
    private boolean   isMessengerBusy       = false;
    private boolean   isCallBusy            = false;
    private Timer     myTimer;
    private int       timerCounter          = 0;
    private final int buzyClearTrigger      = 40;
    private String    smsPhoneNum;
    // private String smsBody;

    private boolean   callMessengerEnable   = false;
    private boolean   callQuietEnable       = false;
    private String    smsShort;
    private String    smsLong;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // handle the prefs changing, because of how accessibility services
        // work, sharedprefsonchange listeners don't work
        if (watchFile.lastModified() > lastChange) {
            loadPrefs();
        }
        Constants.log(Constants.LOG_TAG, "Service: Mode is: " + String.valueOf(mode.ordinal()));

        // if we are off, don't do anything.
        if (mode == Mode.OFF) {
            Constants.log(Constants.LOG_TAG, "Service: Mode is off, not sending any notifications");

            return;
        }

        // handle quiet hours
        if (quiet_hours) {

            Calendar c = Calendar.getInstance();
            Date now = new Date(0, 0, 0, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
            Constants.log(Constants.LOG_TAG, "Checking quiet hours. Now: " + now.toString() + " vs "
                    + quiet_hours_before.toString() + " and " + quiet_hours_after.toString());
            if (now.before(quiet_hours_before) || now.after(quiet_hours_after)) {
                Constants.log(Constants.LOG_TAG, "Time is before or after the quiet hours time. Returning.");

                return;
            }

        }

        // handle if they only want notifications
        if (notifications_only) {
            if (event != null) {
                Parcelable parcelable = event.getParcelableData();
                if (!(parcelable instanceof Notification)) {

                    Constants.log(Constants.LOG_TAG,
                            "Event is not a notification and notifications only is enabled. Returning.");
                    return;
                }
            }
        }
        if (no_ongoing_notifs) {
            Parcelable parcelable = event.getParcelableData();
            if (parcelable instanceof Notification) {
                Notification notif = (Notification) parcelable;
                Constants.log(
                        Constants.LOG_TAG,
                        "Looking at " + String.valueOf(notif.flags) + " vs "
                                + String.valueOf(Notification.FLAG_ONGOING_EVENT));
                if ((notif.flags & Notification.FLAG_ONGOING_EVENT) == Notification.FLAG_ONGOING_EVENT) {
                    Constants
                            .log(Constants.LOG_TAG,
                                    "Event is a notification, notification flag contains ongoing, and no ongoing notification is true. Returning.");
                    return;
                }
            } else {
                Constants.log(Constants.LOG_TAG, "Event is not a notification.");

            }
        }

        // Handle the do not disturb screen on settings
        PowerManager powMan = (PowerManager) this.getSystemService(Context.POWER_SERVICE);
        if (Constants.IS_LOGGABLE) {
            Constants.log(Constants.LOG_TAG, "NotificationService.onAccessibilityEvent: notifScreenOn=" + notifScreenOn
                    + "  screen=" + powMan.isScreenOn());
        }
        if (!notifScreenOn && powMan.isScreenOn()) {
            return;
        }

        if (event == null) {
            Constants.log(Constants.LOG_TAG, "Event is null. Returning.");

            return;
        }
        Constants.log(Constants.LOG_TAG, "Event: " + event.toString());

        // main logic
        PackageManager pm = getPackageManager();

        String eventPackageName;
        if (event.getPackageName() != null) {
            eventPackageName = event.getPackageName().toString();
        } else {
            eventPackageName = "";
        }
        if (Constants.IS_LOGGABLE) {
            Constants.log(Constants.LOG_TAG, "Service package list is: ");
            for (String strPackage : packages) {
                Constants.log(Constants.LOG_TAG, strPackage);
            }
            Constants.log(Constants.LOG_TAG, "End Service package list");
        }

        switch (mode) {
        case EXCLUDE:
            // exclude functionality
            Constants.log(Constants.LOG_TAG, "Mode is set to exclude");

            for (String packageName : packages) {
                if (packageName.equalsIgnoreCase(eventPackageName)) {
                    Constants.log(Constants.LOG_TAG, packageName + " == " + eventPackageName
                            + " which is on the exclude list. Returning.");
                    return;
                }
            }
            break;
        case INCLUDE:
            // include only functionality
            Constants.log(Constants.LOG_TAG, "Mode is set to include only");

            boolean found = false;
            for (String packageName : packages) {
                if (packageName.equalsIgnoreCase(eventPackageName)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                Constants.log(Constants.LOG_TAG, eventPackageName + " was not found in the include list. Returning.");
                return;
            }
            break;
        }

        // get the title
        String title = "";
        try {
            title = pm.getApplicationLabel(pm.getApplicationInfo(eventPackageName, 0)).toString();
        } catch (NameNotFoundException e) {
            title = eventPackageName;
        }

        // get the notification text
        String notificationText = event.getText().toString();
        // strip the first and last characters which are [ and ]
        notificationText = notificationText.substring(1, notificationText.length() - 1);

        if (notification_extras && !eventPackageName.contentEquals("com.android.mms")) {

            Constants.log(Constants.LOG_TAG, "Fetching extras from notification");
            Parcelable parcelable = event.getParcelableData();
            if (parcelable instanceof Notification) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                    String str = getExtraBigData((Notification) parcelable, notificationText.trim());
                    if (notificationText.contains(str)) {

                    } else {
                        notificationText += "\n" + str;
                    }

                } else {
                    String str = getExtraData((Notification) parcelable, notificationText.trim());
                    if (notificationText.contains(str)) {

                    } else {
                        notificationText += "\n" + str;
                    }
                }

            }
        }
        if (notificationText.length() == 0) {
            return;
        }

        // Send the alert to Pebble
        queue.add(new queueItem(title, notificationText));
        checkandsend();

        if (Constants.IS_LOGGABLE) {
            Constants.log(Constants.LOG_TAG, event.toString());
            Constants.log(Constants.LOG_TAG, event.getPackageName().toString());
        }
    }

    private void checkandsend() {
        if (queue.size() == 0) {
            return;
        }
        if (isMessengerBusy) {

            timerCounter++;
            if (timerCounter > buzyClearTrigger) {
                while (isMessengerBusy) {
                    sendStopPebbleMessengerToPebble();
                    try {
                        Thread.sleep(500);

                    } catch (InterruptedException e) {
                        Constants.log("HandleWeChat", "Problem while stop the pebble app");
                    }
                }
                checkandsend();
            }

        } else {
            isMessengerBusy = true;
            if (queue.size() > 2) {
                sendToPebble("Messagebrief", String.valueOf(queue.size()) + this.getString(R.string.messages_brief));
                queue.clear();
            } else {
                queueItem item = queue.poll();
                sendToPebble(item.title, item.body);

            }
        }

    }

    /*
     * private void sendToPebble(String title, String notificationText) { title
     * = title.trim(); notificationText = notificationText.trim(); if
     * (title.trim().isEmpty() || notificationText.isEmpty()) { if
     * (Constants.IS_LOGGABLE) { Log.i(Constants.LOG_TAG,
     * "Detected empty title or notification text, skipping"); } return; } //
     * Create json object to be sent to Pebble final Map<String, Object> data =
     * new HashMap<String, Object>();
     * 
     * data.put("title", title);
     * 
     * data.put("body", notificationText); final JSONObject jsonData = new
     * JSONObject(data); final String notificationData = new
     * JSONArray().put(jsonData).toString();
     * 
     * // Create the intent to house the Pebble notification final Intent i =
     * new Intent(Constants.INTENT_SEND_PEBBLE_NOTIFICATION);
     * i.putExtra("messageType", Constants.PEBBLE_MESSAGE_TYPE_ALERT);
     * i.putExtra("sender", getString(R.string.app_name));
     * i.putExtra("notificationData", notificationData);
     * 
     * // Send the alert to Pebble if (Constants.IS_LOGGABLE) {
     * Log.d(Constants.LOG_TAG, "About to send a modal alert to Pebble: " +
     * notificationData); } sendBroadcast(i); notification_last_sent =
     * System.currentTimeMillis();
     * 
     * }
     */

    /**
     * Handler of incoming messages from PebbleCommService.
     */
    class PebbleCommIncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            Constants.log("nc-service", "what is:" + String.valueOf(msg.what));
            switch (msg.what) {

            case PebbleCommService.MSG_SEND_FINISHED:

                Constants.log("nc-service", "MSG send finished");
                isMessengerBusy = false;
                timerCounter = 0;
                break;
            case PebbleCommService.MSG_SEND_CALL_FINISHED:
                isCallBusy = false;
                break;
            case PebbleCommService.MSG_SEND_CALL_ANSWER:
                answerCall(false);
                Constants.log("nc-service", "answer call");
                // sendStopCallMessengerToPebble();
                break;
            case PebbleCommService.MSG_SEND_CALL_ANSWER_WITHSPEAKER:
                answerCall(true);
                Constants.log("nc-service", "answer call with sperker on.");

                break;
            case PebbleCommService.MSG_SEND_CALL_END:
                endCall();
                Constants.log("nc-service", "end call");
                break;
            case PebbleCommService.MSG_SEND_CALL_END_SMS_SHORT:
                Constants.log("nc-service", "end call and send sms");
                endCall();
                if (smsPhoneNum != "") {
                    doSendSMSTo(smsPhoneNum, smsShort);
                }
                // sendStopCallMessengerToPebble();
                break;
            case PebbleCommService.MSG_SEND_CALL_END_SMS_LONG:
                Constants.log("nc-service", "end call and send sms long");
                endCall();
                if (smsPhoneNum != "") {
                    doSendSMSTo(smsPhoneNum, smsLong);
                }
                // sendStopCallMessengerToPebble();
                break;
            default:
                super.handleMessage(msg);
            }
        }
    }

    /**
     * Handler of incoming messages from PebbleCommService.
     */
    class MessageProcessingIncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {

            switch (msg.what) {
            case MessageProcessingService.MSG_REPLY_PROCESSED_MSG:
                Bundle b = msg.getData();

                // The MessageProcessingService can reply with one of two
                // objects
                // - An object of PebbleMessage
                // - A string with pinyin
                if (b.containsKey(MessageProcessingService.KEY_RPL_PBL_MSG)) {
                    PebbleMessage message = (PebbleMessage) b.getSerializable(MessageProcessingService.KEY_RPL_PBL_MSG);

                    sendMessageToPebbleComm(message, notification_timeout);
                } else if (b.containsKey(MessageProcessingService.KEY_RPL_STR)) {
                    String title = b.getString(MessageProcessingService.KEY_RPL_TITLE);
                    String message = b.getString(MessageProcessingService.KEY_RPL_STR);

                    sendMessageToPebbleComm(title, message, notification_timeout);
                }
                break;
            case MessageProcessingService.MSG_REPLY_PROCESSED_CALL:
                Bundle bc = msg.getData();
                PebbleCall msgcall = (PebbleCall) bc.getSerializable(MessageProcessingService.KEY_RPL_PBL_CALL);
                sendCallToPebbleComm(msgcall);
                break;
            default:
                super.handleMessage(msg);
            }
        }

        private void sendMessageToPebbleComm(PebbleMessage message, int timeout) {
            try {

                Message msg = Message.obtain(null, PebbleCommService.MSG_SEND_DATA_TO_PEBBLE);
                msg.replyTo = mMessengerPebbleComm;

                msg.arg1 = PebbleCommService.TYPE_DATA_PBL_MSG;

                msg.arg2 = timeout;

                Bundle b = new Bundle();
                b.putSerializable(PebbleCommService.KEY_MESSAGE, message);
                Constants.log("nc-service", "msg will send to PebbleComm");

                msg.setData(b);

                mServicePebbleComm.send(msg);
            } catch (RemoteException e) {
                // In this case the service has crashed before we could even
                // do anything with it; we can count on soon being
                // disconnected (and then reconnected if it can be restarted)
                // so there is no need to do anything here.
            }
        }

        private void sendMessageToPebbleComm(String title, String message, int timeout) {
            try {

                Message msg = Message.obtain(null, PebbleCommService.MSG_SEND_DATA_TO_PEBBLE);
                msg.replyTo = mMessengerPebbleComm;

                msg.arg1 = PebbleCommService.TYPE_DATA_STR;

                msg.arg2 = timeout;

                Bundle b = new Bundle();
                b.putString(PebbleCommService.KEY_MESSAGE, message);
                b.putString(PebbleCommService.KEY_TITLE, title);

                msg.setData(b);

                mServicePebbleComm.send(msg);
            } catch (RemoteException e) {
                // In this case the service has crashed before we could even
                // do anything with it; we can count on soon being
                // disconnected (and then reconnected if it can be restarted)
                // so there is no need to do anything here.
            }
        }
    }

    /**
     * Target we publish for clients to send messages to IncomingHandler.
     */
    final Messenger                 mMessengerPebbleComm         = new Messenger(new PebbleCommIncomingHandler());
    final Messenger                 mMessengerMessageProcessing  = new Messenger(new MessageProcessingIncomingHandler());

    /** Messenger for communicating with PebbleCommService. */
    Messenger                       mServicePebbleComm           = null;
    /** Messenger for communicating with PebbleCommService. */
    Messenger                       mServiceMessageProcessing    = null;

    /** Flag indicating whether we have called bind on the service. */
    boolean                         mPebbleCommIsBound;

    boolean                         mMessageProcessingIsBound;

    /**
     * Class for interacting with the main interface of the service.
     */
    private final ServiceConnection mConnectionPebbleComm        = new ServiceConnection() {
                                                                     @Override
                                                                     public void onServiceConnected(
                                                                             ComponentName className, IBinder service) {

                                                                         mServicePebbleComm = new Messenger(service);

                                                                         mPebbleCommIsBound = true;

                                                                     }

                                                                     @Override
                                                                     public void onServiceDisconnected(
                                                                             ComponentName className) {

                                                                         mServicePebbleComm = null;

                                                                         mPebbleCommIsBound = false;
                                                                     }
                                                                 };

    /**
     * Class for interacting with the main interface of the service.
     */
    private final ServiceConnection mConnectionMessageProcessing = new ServiceConnection() {
                                                                     @Override
                                                                     public void onServiceConnected(
                                                                             ComponentName className, IBinder service) {

                                                                         mServiceMessageProcessing = new Messenger(
                                                                                 service);

                                                                         mMessageProcessingIsBound = true;

                                                                     }

                                                                     @Override
                                                                     public void onServiceDisconnected(
                                                                             ComponentName className) {

                                                                         mServiceMessageProcessing = null;

                                                                         mMessageProcessingIsBound = false;
                                                                     }
                                                                 };

    private void sendToPebble(String title, String notificationText) {
        if (!mPebbleCommIsBound) {
            Constants.log("PBL_HandleWeChat", "Comm Service not bound! Can't send message.");
            return;
        }

        // Notification notification = (Notification) event.getParcelableData();

        // String originalMsg = notification.tickerText.toString();

        Message msg = Message.obtain(null, MessageProcessingService.MSG_SEND_ORIGINAL_MSG);
        msg.replyTo = mMessengerMessageProcessing;

        if (unicode_notifications) {
            msg.arg1 = MessageProcessingService.PROCESS_UNIFONT;
        } else {
            msg.arg1 = MessageProcessingService.PROCESS_NO_PINYIN;
        }

        Bundle b = new Bundle();
        b.putString(MessageProcessingService.KEY_ORIGINAL_TITLE, title);
        b.putString(MessageProcessingService.KEY_ORIGINAL_MSG, notificationText);

        msg.setData(b);

        try {
            Constants.log("nc-service", "Sending message to message processing service");
            mServiceMessageProcessing.send(msg);
        } catch (RemoteException e) {
            Constants.log("nc-service", "Exception while sending data to the MessageProcessing");
        }
    }

    @Override
    public void onInterrupt() {

    }

    @Override
    protected void onServiceConnected() {
        // get inital preferences

        watchFile = new File(getFilesDir() + "PrefsChanged.none");
        if (!watchFile.exists()) {
            try {
                watchFile.createNewFile();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            watchFile.setLastModified(System.currentTimeMillis());
        }
        loadPrefs();

        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_VISUAL;
        info.eventTypes = AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED;
        info.notificationTimeout = 100;
        setServiceInfo(info);

        bindService(new Intent(NotificationService.this, PebbleCommService.class), mConnectionPebbleComm,
                Context.BIND_AUTO_CREATE);
        bindService(new Intent(NotificationService.this, MessageProcessingService.class), mConnectionMessageProcessing,
                Context.BIND_AUTO_CREATE);

        queue = new LinkedList<queueItem>();
        myTimer = new Timer();
        myTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                checkandsend();
            }
        }, 1000, 1000);

        MyPhoneListener phoneListener = new MyPhoneListener();
        TelephonyManager telephonyManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        telephonyManager.listen(phoneListener, PhoneStateListener.LISTEN_CALL_STATE);

    }

    @Override
    public void onDestroy() {
        myTimer.cancel();
        unbindService(mConnectionMessageProcessing);
        unbindService(mConnectionPebbleComm);

    }

    private void loadPrefs() {
        Constants.log(Constants.LOG_TAG, "I am loading preferences");

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);

        mode = Mode.values()[sharedPref.getInt(Constants.PREFERENCE_MODE, Mode.OFF.ordinal())];

        Constants.log(Constants.LOG_TAG,
                "Service package list is: " + sharedPref.getString(Constants.PREFERENCE_PACKAGE_LIST, ""));

        packages = sharedPref.getString(Constants.PREFERENCE_PACKAGE_LIST, "").split(",");
        notifications_only = sharedPref.getBoolean(Constants.PREFERENCE_NOTIFICATIONS_ONLY, true);
        no_ongoing_notifs = sharedPref.getBoolean(Constants.PREFERENCE_NO_ONGOING_NOTIF, false);
        min_notification_wait = sharedPref.getInt(Constants.PREFERENCE_MIN_NOTIFICATION_WAIT, 0) * 1000;
        notification_extras = sharedPref.getBoolean(Constants.PREFERENCE_NOTIFICATION_EXTRA, false);
        notifScreenOn = sharedPref.getBoolean(Constants.PREFERENCE_NOTIF_SCREEN_ON, true);
        quiet_hours = sharedPref.getBoolean(Constants.PREFERENCE_QUIET_HOURS, false);

        callMessengerEnable = sharedPref.getBoolean(Constants.PREFERENCE_CALL_ENABLE, true);
        callQuietEnable = sharedPref.getBoolean(Constants.PREFERENCE_CALL_QUIET, false);

        // we only need to pull this if quiet hours are enabled. Save the cycles
        // for the cpu! (haha)
        if (quiet_hours) {
            String[] pieces = sharedPref.getString(Constants.PREFERENCE_QUIET_HOURS_BEFORE, "00:00").split(":");
            quiet_hours_before = new Date(0, 0, 0, Integer.parseInt(pieces[0]), Integer.parseInt(pieces[1]));
            pieces = sharedPref.getString(Constants.PREFERENCE_QUIET_HOURS_AFTER, "23:59").split(":");
            quiet_hours_after = new Date(0, 0, 0, Integer.parseInt(pieces[0]), Integer.parseInt(pieces[1]));
        }

        unicode_notifications = sharedPref.getBoolean(Constants.PREFERENCE_UNICODE, false);
        if (unicode_notifications) {
            notification_timeout = Integer.valueOf(sharedPref.getString(Constants.PREFERENCE_NOTIFICATION_TIMEOUT,
                    "10000"));
        }

        if (callMessengerEnable) {
            smsShort = sharedPref.getString(Constants.PREFERENCE_CALL_SMS_SHORT,
                    getString(R.string.pref_call_sms_short_default));
            smsLong = sharedPref.getString(Constants.PREFERENCE_CALL_SMS_LONG,
                    getString(R.string.pref_call_sms_short_default));
        }

        lastChange = watchFile.lastModified();
    }

    private String getExtraData(Notification notification, String existing_text) {
        Constants.log(Constants.LOG_TAG, "I am running extra data");

        RemoteViews views = notification.contentView;
        if (views == null) {
            Constants.log(Constants.LOG_TAG, "ContentView was empty, returning a blank string");

            return "";
        }

        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        try {
            ViewGroup localView = (ViewGroup) inflater.inflate(views.getLayoutId(), null);
            views.reapply(getApplicationContext(), localView);
            return dumpViewGroup(0, localView, existing_text);
        } catch (android.content.res.Resources.NotFoundException e) {
            return "";
        } catch (RemoteViews.ActionException e) {
            return "";
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private String getExtraBigData(Notification notification, String existing_text) {
        Constants.log(Constants.LOG_TAG, "I am running extra big data");

        RemoteViews views = null;
        try {
            views = notification.bigContentView;
        } catch (NoSuchFieldError e) {
            return getExtraData(notification, existing_text);
        }
        if (views == null) {
            Constants.log(Constants.LOG_TAG, "bigContentView was empty, running normal");

            return getExtraData(notification, existing_text);
        }
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        try {
            ViewGroup localView = (ViewGroup) inflater.inflate(views.getLayoutId(), null);
            views.reapply(getApplicationContext(), localView);
            return dumpViewGroup(0, localView, existing_text);
        } catch (android.content.res.Resources.NotFoundException e) {
            return "";
        }
    }

    private String dumpViewGroup(int depth, ViewGroup vg, String existing_text) {
        String text = "";
        Constants.log(Constants.LOG_TAG, "root view, depth:" + depth + "; view: " + vg);
        for (int i = 0; i < vg.getChildCount(); ++i) {

            View v = vg.getChildAt(i);
            Constants.log(Constants.LOG_TAG, "depth: " + depth + "; " + v.getClass().toString() + "; view: " + v);

            if (v.getId() == android.R.id.title || v instanceof android.widget.Button
                    || v.getClass().toString().contains("android.widget.DateTimeView")) {
                if (Constants.IS_LOGGABLE) {
                    Constants.log(Constants.LOG_TAG, "I am going to skip this, but if I didn't, the text would be: "
                            + ((TextView) v).getText().toString());
                }
                if (existing_text.isEmpty() && v.getId() == android.R.id.title) {
                    if (Constants.IS_LOGGABLE) {
                        Constants.log(Constants.LOG_TAG,
                                "I was going to skip this, but the existing text was empty, and I need something.");
                    }
                } else {
                    continue;
                }
            }

            if (v instanceof TextView) {
                TextView tv = (TextView) v;
                if (tv.getText().toString() == "..." || tv.getText().toString() == "�"
                        || isInteger(tv.getText().toString()) || existing_text.contains(tv.getText().toString().trim())) {
                    if (Constants.IS_LOGGABLE) {
                        Constants.log(Constants.LOG_TAG, "Text is: " + tv.getText().toString()
                                + " but I am going to skip this");
                    }
                    continue;
                }

                text += tv.getText().toString() + "\n";
                Constants.log(Constants.LOG_TAG, tv.getText().toString());

            }
            if (v instanceof ViewGroup) {
                text += dumpViewGroup(depth + 1, (ViewGroup) v, existing_text);
            }
        }
        return text;
    }

    public boolean isInteger(String input) {
        try {
            Integer.parseInt(input);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private class MyPhoneListener extends PhoneStateListener {
        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            if (!callMessengerEnable) {
                return;
            }
            if (quiet_hours && callQuietEnable) {

                Calendar c = Calendar.getInstance();
                Date now = new Date(0, 0, 0, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
                Constants.log(Constants.LOG_TAG, "Checking quiet hours. Now: " + now.toString() + " vs "
                        + quiet_hours_before.toString() + " and " + quiet_hours_after.toString());
                if (now.before(quiet_hours_before) || now.after(quiet_hours_after)) {
                    Constants.log(Constants.LOG_TAG, "Time is before or after the quiet hours time. Returning.");

                    return;
                }

            }
            switch (state) {

            case TelephonyManager.CALL_STATE_RINGING:
                if (incomingNumber != null) {
                    smsPhoneNum = incomingNumber;
                } else {

                    smsPhoneNum = "";
                }
                sendCallToPebble(smsPhoneNum, queryNameByNum(smsPhoneNum, getBaseContext()));
                Constants.log("phone", "phone is comming:" + smsPhoneNum);
                break;
            case TelephonyManager.CALL_STATE_IDLE:
                if (isCallBusy) {
                    sendStopCallMessengerToPebble();
                }

                break;

            case TelephonyManager.CALL_STATE_OFFHOOK:

                break;
            }
            ;
        }
    }

    private void sendCallToPebble(String phone, String name) {
        if (!mPebbleCommIsBound) {
            Constants.log("PBL_HandleWeChat", "Comm Service not bound! Can't send message.");
            return;
        }

        // Notification notification = (Notification) event.getParcelableData();

        // String originalMsg = notification.tickerText.toString();
        if (isMessengerBusy) {
            sendStopPebbleMessengerToPebble();
        }
        while (isCallBusy) {
            sendStopCallMessengerToPebble();
            try {
                Thread.sleep(500);

            } catch (InterruptedException e) {
                Constants.log("HandleWeChat", "Problem while stop the pebble app");
            }
        }

        isCallBusy = true;
        Message msg = Message.obtain(null, MessageProcessingService.MSG_SEND_CALL_MSG);
        msg.replyTo = mMessengerMessageProcessing;

        msg.arg1 = MessageProcessingService.PROCESS_UNIFONT;

        Bundle b = new Bundle();
        b.putString(MessageProcessingService.KEY_CALL_PHONE, phone);
        b.putString(MessageProcessingService.KEY_CALL_NAME, name);

        msg.setData(b);

        try {
            Constants.log("NotificationService", "Sending message to message processing service");
            mServiceMessageProcessing.send(msg);
        } catch (RemoteException e) {
            Constants.log("NotificationService", "Exception while sending data to the MessageProcessing");
        }
    }

    private void sendStopCallMessengerToPebble() {
        try {

            Message msg = Message.obtain(null, PebbleCommService.MSG_SEND_CALL_STOP);
            msg.replyTo = mMessengerPebbleComm;

            mServicePebbleComm.send(msg);

        } catch (RemoteException e) {

        }
    }

    private void sendStopPebbleMessengerToPebble() {
        try {

            Message msg = Message.obtain(null, PebbleCommService.MSG_SEND_MESSENGER_STOP);
            msg.replyTo = mMessengerPebbleComm;

            mServicePebbleComm.send(msg);

        } catch (RemoteException e) {

        }
    }

    private void sendCallToPebbleComm(PebbleCall msgcall) {
        try {

            Message msg = Message.obtain(null, PebbleCommService.MSG_SEND_CALL_TO_PEBBLE);
            msg.replyTo = mMessengerPebbleComm;
            Bundle b = new Bundle();
            b.putSerializable(PebbleCommService.KEY_MESSAGE, msgcall);
            msg.setData(b);
            mServicePebbleComm.send(msg);

        } catch (RemoteException e) {

        }
    }

    public static String queryNameByNum(String num, Context context) {
        // String findNum = rawNumber(num);
        Uri uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(num));

        Cursor cursorOriginal = context.getContentResolver().query(uri, new String[] {
            PhoneLookup.DISPLAY_NAME
        }, null, null, null);
        // Constants
        // .log("queryNameByNum",
        // ContactsContract.CommonDataKinds.Phone.NUMBER + " LIKE '%" + num +
        // "'" + " OR "
        // + ContactsContract.CommonDataKinds.Phone.NUMBER + " LIKE '%"
        // + PhoneNumberUtils.formatNumber(num) + "'");
        String nameString = context.getString(R.string.notificationservice_unknownperson);
        if (null != cursorOriginal) {
            if (cursorOriginal.getCount() > 0) {
                int columnNumberId = cursorOriginal.getColumnIndex(PhoneLookup.DISPLAY_NAME);
                if (cursorOriginal.moveToFirst()) {
                    nameString = cursorOriginal.getString(columnNumberId);
                }
            }
            cursorOriginal.close();
        }
        return nameString;
    }

    private void answerCall(boolean isSpeakon) {

        Context context = getApplicationContext();
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        // 判断是否插上了耳机
        if (!audioManager.isWiredHeadsetOn()) {
            // 4.1以上系统限制了部分权限， 使用三星4.1版本测试提示警告：Permission Denial: not allowed to
            // send broadcast android.intent.action.HEADSET_PLUG from pid=1324,
            // uid=10017
            // 这里需要注意一点，发送广播时加了权限“android.permission.CALL_PRIVLEGED”，则接受该广播时也需要增加该权限。但是4.1以上版本貌似这个权限只能系统应用才可以得到。测试的时候，自定义的接收器无法接受到此广播，后来去掉了这个权限，设为NULL便可以监听到了。

            if (android.os.Build.VERSION.SDK_INT >= 15) {
                Constants.log("speakerset", "AudioManager before mode:" + audioManager.getMode() + " speaker mod:"
                        + String.valueOf(audioManager.isSpeakerphoneOn()));
                Intent meidaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
                KeyEvent keyEvent = new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HEADSETHOOK);
                meidaButtonIntent.putExtra(Intent.EXTRA_KEY_EVENT, keyEvent);
                context.sendOrderedBroadcast(meidaButtonIntent, null);
                if (isSpeakon) {
                    try {
                        Thread.sleep(500);

                    } catch (InterruptedException e) {
                        Constants.log("speakerset", "Problem while sleeping");
                    }
                    Constants.log("speakerset", "AudioManager answer mode:" + audioManager.getMode() + " speaker mod:"
                            + String.valueOf(audioManager.isSpeakerphoneOn()));
                    while (audioManager.getMode() != AudioManager.MODE_IN_CALL) {
                        try {
                            Thread.sleep(300);

                        } catch (InterruptedException e) {
                            Constants.log("speakerset", "Problem while sleeping");
                        }
                    }
                    // audioManager.setMicrophoneMute(true);
                    audioManager.setSpeakerphoneOn(true);
                    // audioManager.setMode(AudioManager.MODE_IN_CALL);
                    Constants.log("speakerset", "AudioManager set mode:" + audioManager.getMode() + " speaker mod:"
                            + String.valueOf(audioManager.isSpeakerphoneOn()));

                }

            }
        } else {
            Constants.log(
                    "speakerset",
                    "AudioManager before mode:" + audioManager.getMode() + " speaker mod:"
                            + String.valueOf(audioManager.isSpeakerphoneOn()));

            Intent meidaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
            KeyEvent keyEvent = new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HEADSETHOOK);
            meidaButtonIntent.putExtra(Intent.EXTRA_KEY_EVENT, keyEvent);
            context.sendOrderedBroadcast(meidaButtonIntent, null);

        }
    }

    private void endCall() {
        TelephonyManager telMag = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        Class<TelephonyManager> c = TelephonyManager.class;
        Method mthEndCall = null;
        try {
            mthEndCall = c.getDeclaredMethod("getITelephony", (Class[]) null);
            mthEndCall.setAccessible(true);
            ITelephony iTel = (ITelephony) mthEndCall.invoke(telMag, (Object[]) null);
            iTel.endCall();

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public void doSendSMSTo(String phoneNumber, String message) {
        if (PhoneNumberUtils.isGlobalPhoneNumber(phoneNumber)) {
            SmsManager smsmanager = SmsManager.getDefault();
            ArrayList<String> smsList = smsmanager.divideMessage(message);
            for (String sms : smsList) {
                smsmanager.sendTextMessage(phoneNumber, null, sms, null, null);
            }
            Constants.log("sendsms", "send:[" + message + "] to number:" + phoneNumber);
        }
    }

    // private static int currVolume;

    /*
     * public void OpenSpeaker() {
     * 
     * try { AudioManager audioManager = (AudioManager)
     * getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
     * audioManager.setMode(AudioManager.MODE_IN_CALL); currVolume =
     * audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL);
     * 
     * if (!audioManager.isSpeakerphoneOn()) {
     * audioManager.setSpeakerphoneOn(true);
     * 
     * audioManager .setStreamVolume(AudioManager.STREAM_VOICE_CALL,
     * audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL),
     * AudioManager.STREAM_VOICE_CALL); } } catch (Exception e) {
     * e.printStackTrace(); } }
     * 
     * public void CloseSpeaker() {
     * 
     * try { AudioManager audioManager = (AudioManager)
     * getApplicationContext().getSystemService(Context.AUDIO_SERVICE); if
     * (audioManager != null) { if (audioManager.isSpeakerphoneOn()) {
     * audioManager.setSpeakerphoneOn(false);
     * audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, currVolume,
     * AudioManager.STREAM_VOICE_CALL); } } } catch (Exception e) {
     * e.printStackTrace(); } //
     * Toast.makeText(context,"揚聲器已經關閉",Toast.LENGTH_SHORT).show(); }
     */
}
