/* 
Copyright (c) 2014 Tiago Espinha & Yang Tsao

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.espinhasoftware.wechatpebble.service;

import java.util.HashMap;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.provider.Settings.Secure;

import com.dattasmoon.pebble.plugin.Constants;
import com.espinhasoftware.wechatpebble.model.CharacterMatrix;
import com.espinhasoftware.wechatpebble.pebblecomm.PebbleCall;
import com.espinhasoftware.wechatpebble.pebblecomm.PebbleMessage;
import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.PebbleKit.PebbleAckReceiver;
import com.getpebble.android.kit.PebbleKit.PebbleDataReceiver;
import com.getpebble.android.kit.PebbleKit.PebbleNackReceiver;
import com.getpebble.android.kit.util.PebbleDictionary;
import com.google.android.vending.licensing.AESObfuscator;
import com.google.android.vending.licensing.LicenseChecker;
import com.google.android.vending.licensing.LicenseCheckerCallback;
import com.google.android.vending.licensing.ServerManagedPolicy;

public class PebbleCommService extends Service {
    private static PebbleMessage   message;
    private static PebbleCall      call;
    private static int             timeout;
    private static Messenger       replychannel;
    private static int             totalunichars;
    private static int             licensing                        = 1;
    /**
     * Command to the service to register a client, receiving callbacks from the
     * service. The Message's replyTo field must be a Messenger of the client
     * where callbacks should be sent.
     */
    public static final int        MSG_SEND_DATA_TO_PEBBLE          = 1;
    public static final int        MSG_SEND_FINISHED                = 2;
    public static final int        MSG_SEND_CALL_TO_PEBBLE          = 3;
    public static final int        MSG_SEND_CALL_STOP               = 4;
    public static final int        MSG_SEND_CALL_ANSWER             = 5;
    public static final int        MSG_SEND_CALL_END                = 6;
    public static final int        MSG_SEND_CALL_END_SMS_SHORT      = 7;
    public static final int        MSG_SEND_CALL_ANSWER_WITHSPEAKER = 8;
    public static final int        MSG_SEND_MESSENGER_STOP          = 9;
    public static final int        MSG_SEND_CALL_FINISHED           = 10;
    public static final int        MSG_SEND_CALL_END_SMS_LONG       = 11;

    public static final int        TYPE_DATA_PBL_MSG                = 1;
    public static final int        TYPE_DATA_STR                    = 2;

    public static final String     KEY_MESSAGE                      = "KEY_MESSAGE";
    public static final String     KEY_TITLE                        = "KEY_TITLE";

    private static final String    BASE64_PUBLIC_KEY                = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAyNijJODeoCsFY3g7F+i1WyNk1q0MbhXQJy9bjxxN2xErv4qLwcKDDlSIRb1ps2tHV7fEFQikaF8IGyhodecNcWNLvqe7muHhNHRiCXMPBp47N0G8o8KmE+lGfzQ/cXU0ZveI8kwkgiL8zEPfuHFVHs4eivXuZj7CaE5aDpRNRCMtNtnRiCD4dXHHoQnWpw8jBfw+/6+C5H447LIHlZ30zAaiQU1hCt8aNgMq7zmilnxYC+0yoorosR5nbHzWftgm3tKmwrT5vIR+CBeUFDPSBcSExQpjnXJ6vx/AO+a19QJ7IF0s5Y6FJlu70yp/lU1qm7WvFPBxTqq7UjapuQS7EQIDAQAB";

    // Generate your own 20 random bytes, and put them here.
    private static final byte[]    SALT                             = new byte[] {
            -46, 65, 30, -118, -103, -57, 74, -64, 51, 33, -95, -45, 77, -115, -36, 113, -11, 32, -64, 89
                                                                    };
    private LicenseCheckerCallback mLicenseCheckerCallback;
    private LicenseChecker         mChecker;

    /**
     * Handler of incoming messages from clients.
     */
    static class HandleWeChatIncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            replychannel = msg.replyTo;

            if (licensing == 0) {
                sendAlertToPebble("WARNNING",
                        "The Pebble messager didn't passed the license test, please download it from google play!");
                return;
            }
            switch (msg.what) {
            case PebbleCommService.MSG_SEND_DATA_TO_PEBBLE:
                if (msg.arg1 == PebbleCommService.TYPE_DATA_PBL_MSG) {
                    PebbleMessage pb = (PebbleMessage) msg.getData().getSerializable(PebbleCommService.KEY_MESSAGE);

                    PebbleCommService.timeout = msg.arg2;
                    totalunichars = pb.getCharacterQueue().size();
                    sendAlertToPebble(pb, true);
                } else if (msg.arg1 == PebbleCommService.TYPE_DATA_STR) {
                    String s = msg.getData().getString(PebbleCommService.KEY_MESSAGE);
                    String t = msg.getData().getString(PebbleCommService.KEY_TITLE);

                    sendAlertToPebble(t, s);
                }
                break;
            case PebbleCommService.MSG_SEND_CALL_TO_PEBBLE:
                PebbleCall pc = (PebbleCall) msg.getData().getSerializable(PebbleCommService.KEY_MESSAGE);
                sendAlertToPebble(pc, true);
                break;
            case PebbleCommService.MSG_SEND_CALL_STOP:
                sendStopPebble(500);
                break;
            case PebbleCommService.MSG_SEND_MESSENGER_STOP:
                sendStopMessenger();
                break;
            default:
                super.handleMessage(msg);
                break;
            }
        }

    }

    /**
     * Target we publish for clients to send messages to IncomingHandler.
     */
    final Messenger mMessengerHandleWeChat = new Messenger(new HandleWeChatIncomingHandler());
    static Context  _context;

    @Override
    public void onCreate() {
        String deviceId = Secure.getString(getContentResolver(), Secure.ANDROID_ID);
        mLicenseCheckerCallback = new MyLicenseCheckerCallback();
        mChecker = new LicenseChecker(this, new ServerManagedPolicy(this, new AESObfuscator(SALT, getPackageName(),
                deviceId)), BASE64_PUBLIC_KEY);
        mChecker.checkAccess(mLicenseCheckerCallback);

        message = new PebbleMessage();
        _context = getApplicationContext();

        PebbleKit.registerReceivedDataHandler(getApplicationContext(), new PebbleDataReceiver(
                PebbleMessage.WECHATPEBBLE_UUID) {
            @Override
            public void receiveData(Context arg0, int arg1, PebbleDictionary arg2) {
                Constants.log("PB_RECEIVE", "Got data from Pebble");
            }
        });

        PebbleAckReceiver ack = new PebbleAckReceiver(PebbleMessage.WECHATPEBBLE_UUID) {
            @Override
            public void receiveAck(Context arg0, int arg1) {
                Constants.log("PB_ACK", "Pebble sent an ACK. Transaction ID: " + arg1);

                if (arg1 != 1) {
                    return;
                }

                PebbleKit.sendAckToPebble(getApplicationContext(), arg1);

                if (!message.hasMore()) {
                    System.out.println("Sending finish signal");
                    PebbleDictionary data = new PebbleDictionary();

                    data.addInt8(PebbleMessage.PBL_FINAL, (byte) 1);

                    PebbleKit.sendDataToPebbleWithTransactionId(getApplicationContext(),
                            PebbleMessage.WECHATPEBBLE_UUID, data, 2);

                    try {
                        Thread.sleep(PebbleCommService.timeout);

                    } catch (InterruptedException e) {
                        Constants.log("HandleWeChat", "Problem while sleeping");
                    }
                    PebbleKit.closeAppOnPebble(getApplicationContext(), PebbleMessage.WECHATPEBBLE_UUID);
                    try {
                        Thread.sleep(500);

                    } catch (InterruptedException e) {
                        Constants.log("HandleWeChat", "Problem while sleeping");
                    }
                    Message reply = Message.obtain();
                    reply.what = PebbleCommService.MSG_SEND_FINISHED;
                    try {
                        Constants.log("MessageProcessing", "Send finish to notificationserver!");
                        if (replychannel == null) {
                            Constants.log("MessageProcessing", "bad replychannel!");
                            return;
                        }
                        replychannel.send(reply);
                    } catch (RemoteException e) {
                        Constants.log("MessageProcessing", "Exception replying to HandleWeChat with Unifont");
                    }

                    return;
                }

                sendChunk(false);
            }
        };

        PebbleNackReceiver nack = new PebbleNackReceiver(PebbleMessage.WECHATPEBBLE_UUID) {

            @Override
            public void receiveNack(Context arg0, int arg1) {
                Constants.log("PB_NACK", "Pebble sent an NACK. Transaction ID:" + arg1);
            }
        };
        PebbleKit.registerReceivedAckHandler(getApplicationContext(), ack);
        PebbleKit.registerReceivedNackHandler(getApplicationContext(), nack);

        call = new PebbleCall();
        PebbleKit.registerReceivedDataHandler(getApplicationContext(), new PebbleDataReceiver(
                PebbleCall.WECHATPEBBLE_UUID) {
            @Override
            public void receiveData(Context arg0, int arg1, PebbleDictionary arg2) {

                Constants.log("PB_RECEIVE", "Got data from Pebble tid:" + String.valueOf(arg1));

                PebbleKit.sendAckToPebble(getApplicationContext(), arg1);
                int c = arg2.getInteger(PebbleCall.PBL_CALL).intValue();
                Message reply = Message.obtain();
                switch (c) {
                case PebbleCall.CALL_ANSWER: {
                    reply.what = PebbleCommService.MSG_SEND_CALL_ANSWER;
                    Constants.log("MessageProcessing", "Replied to Handle call_answer");
                }
                    break;
                case PebbleCall.CALL_ANSWER_SPEAKER: {
                    reply.what = PebbleCommService.MSG_SEND_CALL_ANSWER_WITHSPEAKER;
                    Constants.log("MessageProcessing", "Replied to Handle call_answer_withspeaker");
                }
                    break;
                case PebbleCall.CALL_END: {
                    reply.what = PebbleCommService.MSG_SEND_CALL_END;
                    Constants.log("MessageProcessing", "Replied to Handle call_end");

                    sendStopPebble(3000);

                }
                    break;
                case PebbleCall.CALL_END_SMS_SHORT: {
                    reply.what = PebbleCommService.MSG_SEND_CALL_END_SMS_SHORT;
                    sendStopPebble(3000);
                    Constants.log("MessageProcessing", "Replied to Handle call_end_sms_short");

                }
                    break;
                case PebbleCall.CALL_END_SMS_LONG: {
                    reply.what = PebbleCommService.MSG_SEND_CALL_END_SMS_LONG;
                    sendStopPebble(3000);
                    Constants.log("MessageProcessing", "Replied to Handle call_end_sms_long");

                }
                    break;
                default:
                    Constants.log("MessageProcessing", "Unknows c:" + String.valueOf(c));
                    return;

                }
                try {
                    if (replychannel != null) {
                        replychannel.send(reply);
                    }
                } catch (RemoteException e) {
                    Constants.log("MessageProcessing", "Exception replying to call_answer");
                }
            }
        });
        PebbleAckReceiver callack = new PebbleAckReceiver(PebbleCall.WECHATPEBBLE_UUID) {
            @Override
            public void receiveAck(Context arg0, int arg1) {
                Constants.log("PB_ACK", "Pebble sent an ACK. Transaction ID: " + arg1);
                if (arg1 != 3) {
                    return;
                }
                PebbleKit.sendAckToPebble(_context, arg1);

                if (!call.hasMore()) {
                    Constants.log("sendchar", "send final!");
                    PebbleDictionary data = new PebbleDictionary();

                    data.addInt8(PebbleCall.PBL_FINAL, (byte) 1);

                    PebbleKit.sendDataToPebbleWithTransactionId(_context, PebbleCall.WECHATPEBBLE_UUID, data, 4);

                } else {

                    sendCallChunk(false);
                }
            }
        };
        PebbleNackReceiver callnack = new PebbleNackReceiver(PebbleCall.WECHATPEBBLE_UUID) {

            @Override
            public void receiveNack(Context arg0, int arg1) {
                Constants.log("PB_NACK", "Pebble sent an NACK. Transaction ID:" + arg1);
            }
        };
        PebbleKit.registerReceivedAckHandler(getApplicationContext(), callack);
        PebbleKit.registerReceivedNackHandler(getApplicationContext(), callnack);
    }

    @Override
    public void onDestroy() {

    }

    /**
     * When binding to the service, we return an interface to our messenger for
     * sending messages to the service.
     */
    @Override
    public IBinder onBind(Intent intent) {
        return mMessengerHandleWeChat.getBinder();
    }

    private static void sendAlertToPebble(String title, String alert) {
        final Intent i = new Intent("com.getpebble.action.SEND_NOTIFICATION");

        final Map<String, String> data = new HashMap<String, String>();
        data.put("title", title);
        data.put("body", alert);
        final JSONObject jsonData = new JSONObject(data);
        final String notificationData = new JSONArray().put(jsonData).toString();

        i.putExtra("messageType", "PEBBLE_ALERT");
        i.putExtra("sender", "MyAndroidApp");
        i.putExtra("notificationData", notificationData);

        _context.sendBroadcast(i);
    }

    private static void sendAlertToPebble(PebbleMessage pm, boolean reset) {
        PebbleKit.startAppOnPebble(_context, PebbleMessage.WECHATPEBBLE_UUID);
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        message = pm;
        sendChunk(reset);
    }

    private static void sendAlertToPebble(PebbleCall pc, boolean reset) {
        PebbleKit.startAppOnPebble(_context, PebbleCall.WECHATPEBBLE_UUID);
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        call = pc;
        sendCallChunk(reset);
    }

    public static void sendStopPebble(int millsec) {
        if (_context == null) {
            Constants.log("nc-service", "_context became null!");
        }
        try {
            Thread.sleep(millsec);

        } catch (InterruptedException e) {
            Constants.log("HandleWeChat", "Problem while sleeping");
        }
        PebbleKit.closeAppOnPebble(_context, PebbleCall.WECHATPEBBLE_UUID);
        call = new PebbleCall();

        try {
            Thread.sleep(500);

        } catch (InterruptedException e) {
            Constants.log("HandleWeChat", "Problem while sleeping");
        }
        if (replychannel == null) {
            return;
        }
        Message reply = Message.obtain();
        reply.what = PebbleCommService.MSG_SEND_CALL_FINISHED;
        try {
            Constants.log("MessageProcessing", "finished call and close the app");
            replychannel.send(reply);
        } catch (RemoteException e) {
            Constants.log("MessageProcessing", "Exception replying to HandleWeChat with Unifont");
        }
        Constants.log("nc-service", "Pebblecomm Send Stop to Pebble!");

    }

    private static void sendStopMessenger() {

        PebbleKit.closeAppOnPebble(_context, PebbleMessage.WECHATPEBBLE_UUID);
        message = new PebbleMessage();

        try {
            Thread.sleep(500);

        } catch (InterruptedException e) {
            Constants.log("HandleWeChat", "Problem while sleeping");
        }

        Constants.log("nc-service", "Pebblecomm Send Stop to Pebble!");

        Message reply = Message.obtain();
        reply.what = PebbleCommService.MSG_SEND_FINISHED;
        try {
            if (replychannel == null) {
                return;
            }
            Constants.log("MessageProcessing", "Replied to HandleWeChat with Unifont");
            replychannel.send(reply);
        } catch (RemoteException e) {
            Constants.log("MessageProcessing", "Exception replying to HandleWeChat with Unifont");
        }
    }

    public static boolean sendChunk(boolean reset) {

        PebbleDictionary data = new PebbleDictionary();

        if (reset) {

            data.addInt8(PebbleMessage.PBL_RESET, (byte) 1);

        } else {
            if (message.getAscMsg().length() > 0) {
                if (message.getAscMsg().length() > 80) {
                    data.addString(PebbleMessage.PBL_ASCMSG, message.getAscMsg().substring(0, 80));
                    Constants.log("sendchar", "String =" + message.getAscMsg());
                    Constants.log("sendchar", "The Size of String is " + String.valueOf(message.getAscMsg().length()));
                    message.setAscMsg(message.getAscMsg().substring(80));
                } else {
                    data.addString(PebbleMessage.PBL_ASCMSG, message.getAscMsg());

                    Constants.log("sendchar", "String =" + message.getAscMsg());
                    Constants.log("sendchar", "The Size of String is " + String.valueOf(message.getAscMsg().length()));
                    message.setAscMsg("");
                }
                data.addInt8(PebbleMessage.PBL_RESET, (byte) 0);
            } else {
                CharacterMatrix cm = message.getCharacterQueue().pollFirst();
                data.addInt8(PebbleMessage.PBL_RESET, (byte) 0);
                data.addBytes(PebbleMessage.PBL_UNIPOS, cm.getPos());
                data.addInt8(PebbleMessage.PBL_UNIWIDTH, (byte) cm.getWidthBytes());
                data.addInt8(PebbleMessage.PBL_PROGRESS, (byte) ((1 - ((double) message.getCharacterQueue().size())
                        / ((double) totalunichars)) * 10));
                int size = cm.getByteList().size();
                byte[] b2 = new byte[size];
                cm.getbyteArray(b2, size);
                data.addBytes(PebbleMessage.PBL_UNICHAR, b2);
                Constants.log("sendchar", "index=" + String.valueOf(message.getCharacterQueue().size()) + " code='"
                        + String.valueOf(b2));

            }

        }

        PebbleKit.sendDataToPebbleWithTransactionId(_context, PebbleMessage.WECHATPEBBLE_UUID, data, 1);

        return message.hasMore();
    }

    public static boolean sendCallChunk(boolean reset) {
        if (!call.hasMore()) {
            return false;
        }
        PebbleDictionary data = new PebbleDictionary();

        if (reset) {

            data.addString(PebbleCall.PBL_PHONE_NUM, call.getPhoneNum());

        } else {
            if (call.getAscMsg().length() > 0) {

                data.addString(PebbleCall.PBL_ASCMSG, call.getAscMsg());
                Constants.log("sendchar", "String =" + call.getAscMsg());
                Constants.log("sendchar", "The Size of String is " + String.valueOf(call.getAscMsg().length()));
                call.setAscMsg("");

            } else {
                CharacterMatrix cm = call.getCharacterQueue().pollFirst();

                data.addBytes(PebbleCall.PBL_UNIPOS, cm.getPos());
                data.addInt8(PebbleCall.PBL_UNIWIDTH, (byte) cm.getWidthBytes());
                int size = cm.getByteList().size();
                byte[] b2 = new byte[size];
                cm.getbyteArray(b2, size);
                data.addBytes(PebbleCall.PBL_UNICHAR, b2);
                Constants.log("sendchar", "index=" + String.valueOf(call.getCharacterQueue().size()) + " code='"
                        + String.valueOf(b2));
                Constants.log("sendchar", "call queue length" + String.valueOf(call.getCharacterQueue().size()));
                Constants.log("sendchar", "has more?" + String.valueOf(call.hasMore()));

            }

        }

        PebbleKit.sendDataToPebbleWithTransactionId(_context, PebbleCall.WECHATPEBBLE_UUID, data, 3);

        return call.hasMore();
    }

    // //////
    private class MyLicenseCheckerCallback implements LicenseCheckerCallback {
        @Override
        public void allow(int policyReason) {
            licensing = 1;
            Constants.log("license", "License is OK!");
        }

        @Override
        public void dontAllow(int policyReason) {
            // licensing = 0;
            Constants.log("license", "License is not allowed!");
        }

        @Override
        public void applicationError(int errorCode) {

            // This is a polite way of saying the developer made a mistake
            // while setting up or calling the license checker library.
            // Please examine the error code and fix the error.

            Constants.log("license", "the license test occur error!");
        }
    }

}
