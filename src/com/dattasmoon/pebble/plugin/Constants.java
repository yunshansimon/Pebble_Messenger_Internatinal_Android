/* 
Copyright (c) 2013 Dattas Moonchaser

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.dattasmoon.pebble.plugin;

import android.content.Context;
import android.util.Log;

public final class Constants {

    public static final String  LOG_TAG                               = "com.dattasmoon.pebble.plugin";
    public static final boolean IS_LOGGABLE                           = true;
    public static final String  DONATION_URL                          = "http://yangtsao.wordpress.com";
    public static final String  WATCHAPP_URL                          = "pebble://appstore/5377035eaa542fc1d20000f2";
    public static final String  CALL_APP_URL                          = "pebble://appstore/538d6c9bc48cf5c229000075";

    // bundle extras
    public static final String  BUNDLE_EXTRA_INT_VERSION_CODE         = LOG_TAG + ".INT_VERSION_CODE";
    public static final String  BUNDLE_EXTRA_STRING_TITLE             = LOG_TAG + ".STRING_TITLE";
    public static final String  BUNDLE_EXTRA_STRING_BODY              = LOG_TAG + ".STRING_BODY";
    public static final String  BUNDLE_EXTRA_INT_TYPE                 = LOG_TAG + ".INT_TYPE";
    public static final String  BUNDLE_EXTRA_INT_MODE                 = LOG_TAG + ".INT_MODE";
    public static final String  BUNDLE_EXTRA_BOOL_NOTIFICATIONS_ONLY  = LOG_TAG + ".BOOL_NOTIFICATIONS_ONLY";
    public static final String  BUNDLE_EXTRA_STRING_PACKAGE_LIST      = LOG_TAG + ".STRING_PACKAGE_LIST";
    public static final String  BUNDLE_EXTRA_BOOL_NOTIFICATION_EXTRAS = LOG_TAG + ".BOOL_NOTIFICATION_EXTAS";

    // Tasker bundle extras
    public static final String  BUNDLE_EXTRA_REPLACE_KEY              = "net.dinglisch.android.tasker.extras.VARIABLE_REPLACE_KEYS";
    public static final String  BUNDLE_EXTRA_REPLACE_VALUE            = BUNDLE_EXTRA_STRING_TITLE + " "
                                                                              + BUNDLE_EXTRA_STRING_BODY;

    // Shared preferences
    public static final String  PREFERENCE_EXCLUDE_MODE               = "excludeMode";
    public static final String  PREFERENCE_MODE                       = "pref_mode";
    public static final String  PREFERENCE_NOTIFICATIONS_ONLY         = "pref_notif_only";
    public static final String  PREFERENCE_NO_ONGOING_NOTIF           = "pref_no_ongoing_notif";
    public static final String  PREFERENCE_PACKAGE_LIST               = "pref_package_list";
    public static final String  PREFERENCE_MIN_NOTIFICATION_WAIT      = "minNotificationWait";
    public static final String  PREFERENCE_TASKER_SET                 = "pref_tasker_set";
    public static final String  PREFERENCE_NOTIFICATION_EXTRA         = "pref_fetch_notif_extras";
    public static final String  PREFERENCE_NOTIF_SCREEN_ON            = "pref_notif_screen_on";
    public static final String  PREFERENCE_QUIET_HOURS                = "pref_dnd_time_enabled";
    public static final String  PREFERENCE_QUIET_HOURS_BEFORE         = "pref_dnd_time_before";
    public static final String  PREFERENCE_QUIET_HOURS_AFTER          = "pref_dnd_time_after";

    public static final String  PREFERENCE_CALL_ENABLE                = "pref_call_enable";
    public static final String  PREFERENCE_CALL_QUIET                 = "pref_call_quiet";
    public static final String  PREFERENCE_CALL_SMS_SHORT             = "pref_call_sms_short";
    public static final String  PREFERENCE_CALL_SMS_LONG              = "pref_call_sms_long";

    // Tiago: added for unicode notifications
    public static final String  PREFERENCE_UNICODE                    = "pref_notif_unicode";
    public static final String  PREFERENCE_NOTIFICATION_TIMEOUT       = "pref_notif_timeout";

    public static final String  DATABASE_READY                        = "status_database_ready";

    // Intents
    public static final String  INTENT_SEND_PEBBLE_NOTIFICATION       = "com.getpebble.action.SEND_NOTIFICATION";

    // Pebble specific items
    public static final String  PEBBLE_MESSAGE_TYPE_ALERT             = "PEBBLE_ALERT";

    // Accessibility specific items
    public static final String  ACCESSIBILITY_SERVICE                 = "com.yangtsaosoftware.pebblemessenger/com.dattasmoon.pebble.plugin.NotificationService";

    public static enum Type {
        NOTIFICATION, SETTINGS
    };

    public static enum Mode {
        OFF, EXCLUDE, INCLUDE
    }

    public static int getVersionCode(final Context context) {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode;
        } catch (final UnsupportedOperationException e) {
            return 1;
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void log(String tag, String message) {
        if (Constants.IS_LOGGABLE) {
            Log.d(tag, message);
        }
    }

    public static void logw(String tag, String message, Throwable tr) {
        if (Constants.IS_LOGGABLE) {
            Log.w(tag, message, tr);
        }
    }

    private Constants() {
        throw new UnsupportedOperationException("This class is non-instantiable, so stop trying!");
    }
}
