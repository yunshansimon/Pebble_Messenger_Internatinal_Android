/* 
Copyright (c) 2014 Tiago Espinha & Yang Tsao

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.espinhasoftware.wechatpebble.service;

import java.util.ArrayDeque;
import java.util.Deque;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;

import com.dattasmoon.pebble.plugin.Constants;
import com.espinhasoftware.wechatpebble.db.DatabaseHandler;
import com.espinhasoftware.wechatpebble.model.CharacterMatrix;
import com.espinhasoftware.wechatpebble.model.Font;
import com.espinhasoftware.wechatpebble.pebblecomm.PebbleCall;
import com.espinhasoftware.wechatpebble.pebblecomm.PebbleMessage;

public class MessageProcessingService extends Service {
    public static final String     KEY_ORIGINAL_MSG         = "KEY_ORIGINAL_MSG";
    public static final String     KEY_ORIGINAL_TITLE       = "KEY_ORIGINAL_TITLE";
    public static final String     KEY_CALL_PHONE           = "KEY_CALL_PHONE";
    public static final String     KEY_CALL_NAME            = "KEY_CALL_NAME";

    public static final String     KEY_RPL_PBL_MSG          = "KEY_RPL_PBL_MSG";
    public static final String     KEY_RPL_PBL_CALL         = "KEY_RPL_PBL_CALL";
    public static final String     KEY_RPL_STR              = "KEY_RPL_STR";
    public static final String     KEY_RPL_TITLE            = "KEY_RPL_TITLE";

    public static final int        MSG_SEND_ORIGINAL_MSG    = 1;
    public static final int        MSG_REPLY_PROCESSED_MSG  = 2;
    public static final int        MSG_SEND_CALL_MSG        = 3;
    public static final int        MSG_REPLY_PROCESSED_CALL = 4;

    public static final int        PROCESS_UNIFONT          = 1;
    // I'm disabling this until I can find a better pinyin library
    // public static final int PROCESS_PINYIN = 2;
    public static final int        PROCESS_NO_PINYIN        = 3;

    private static DatabaseHandler db;

    private static Context         _context;

    /**
     * Handler of incoming messages from PebbleCommService.
     */
    static class HandleWeChatIncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MessageProcessingService.MSG_SEND_ORIGINAL_MSG:
                Constants.log("MessageProcessing", "Got message");

                String originalMessage = msg.getData().getString(MessageProcessingService.KEY_ORIGINAL_MSG);
                String title = msg.getData().getString(MessageProcessingService.KEY_ORIGINAL_TITLE);

                Messenger replyChannel = msg.replyTo;
                Message reply = Message.obtain();

                reply.what = MessageProcessingService.MSG_REPLY_PROCESSED_MSG;

                if (msg.arg1 == MessageProcessingService.PROCESS_UNIFONT) {
                    PebbleMessage pb = processMessage(originalMessage.replaceAll("\\n+", "\n"));

                    Bundle b = new Bundle();
                    b.putSerializable(MessageProcessingService.KEY_RPL_PBL_MSG, pb);

                    reply.setData(b);

                    try {
                        Constants.log("MessageProcessing", "Replied to HandleWeChat with Unifont");
                        replyChannel.send(reply);
                    } catch (RemoteException e) {
                        Constants.log("MessageProcessing", "Exception replying to HandleWeChat with Unifont");
                    }
                    // } else if (msg.arg1 ==
                    // MessageProcessingService.PROCESS_PINYIN) {
                    // // pass through
                    // Bundle b = new Bundle();
                    // b.putSerializable(MessageProcessingService.KEY_RPL_STR,
                    // processMessageString(originalMessage));
                    //
                    // reply.setData(b);
                    //
                    // try {
                    // Log.d("MessageProcessing",
                    // "Replied to HandleWeChat with PinYin");
                    // replyChannel.send(reply);
                    // } catch (RemoteException e) {
                    // Log.d("MessageProcessing",
                    // "Exception replying to HandleWeChat with PinYin");
                    // }
                } else if (msg.arg1 == MessageProcessingService.PROCESS_NO_PINYIN) {
                    // pass through
                    Bundle b = new Bundle();
                    b.putSerializable(MessageProcessingService.KEY_RPL_TITLE, title);
                    b.putSerializable(MessageProcessingService.KEY_RPL_STR, originalMessage);

                    reply.setData(b);

                    try {
                        Constants.log("MessageProcessing", "Replied to HandleWeChat without PinYin");
                        replyChannel.send(reply);
                    } catch (RemoteException e) {
                        Constants.log("MessageProcessing", "Exception replying to HandleWeChat without PinYin");
                    }
                }
                break;
            case MessageProcessingService.MSG_SEND_CALL_MSG:
                String phone = msg.getData().getString(MessageProcessingService.KEY_CALL_PHONE);
                String name = msg.getData().getString(MessageProcessingService.KEY_CALL_NAME);

                Messenger replyCallChannel = msg.replyTo;
                Message replyCall = Message.obtain();

                replyCall.what = MessageProcessingService.MSG_REPLY_PROCESSED_CALL;
                PebbleCall pc = processCall(phone, name);
                Bundle b = new Bundle();
                b.putSerializable(MessageProcessingService.KEY_RPL_PBL_CALL, pc);

                replyCall.setData(b);
                try {
                    Constants.log("MessageProcessing", "Replied to HandleWeChat with Unifont");
                    replyCallChannel.send(replyCall);
                } catch (RemoteException e) {
                    Constants.log("MessageProcessing", "Exception replying to HandleWeChat with Unifont");
                }

            default:
                super.handleMessage(msg);
            }
        }

        private boolean isDatabaseReady() {
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(_context);

            if (sharedPref.getBoolean(Constants.DATABASE_READY, false)) {
                return true;
            }

            int maxWaitMilis = 15000;
            int waited = 0;

            while (waited < maxWaitMilis) {
                try {
                    Thread.currentThread().sleep(500);
                } catch (InterruptedException e) {
                }

                if (sharedPref.getBoolean(Constants.DATABASE_READY, false)) {
                    return true;
                }
            }

            return false;
        }

        // /**
        // * Sends alerts to the Pebble watch, as per the Pebble app's intents
        // * @param alert Alert which to send to the watch.
        // */
        // private static String processMessageString(String originalMessage) {
        // // This is the traditional Pebble alert which does not show Unicode
        // characters
        // HanyuPinyinOutputFormat format = new HanyuPinyinOutputFormat();
        // format.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        // format.setToneType(HanyuPinyinToneType.WITH_TONE_NUMBER);
        // format.setVCharType(HanyuPinyinVCharType.WITH_V);
        //
        // try {
        // // I know this is deprecated but there's no viable alternative...
        // return PinyinHelper.toHanyuPinyinString(originalMessage, format ,
        // "");
        // } catch (BadHanyuPinyinOutputFormatCombination e) {
        // Log.e("Pinyin", "Failed to convert pinyin");
        // }
        //
        // return "";
        // }

        private PebbleMessage processMessage(String originalMessage) {
            // This method not only polls the database but waits for it to be
            // ready
            // in the event it is loading.
            if (!isDatabaseReady()) {
                Log.e("MessageProcessing", "Database not ready after waiting!");
            }

            PebbleMessage message = new PebbleMessage();

            // Clear the characterQueue, just in case
            Deque<CharacterMatrix> characterQueue = new ArrayDeque<CharacterMatrix>();
            int row = 1;
            int col = 0;

            while (originalMessage.length() > 0) {

                int codepoint = originalMessage.codePointAt(0);
                if (codepoint == 0) {
                    break;
                }
                Constants.log("codepoint", "char='" + (char) codepoint + "' code=" + String.valueOf(codepoint));
                if (codepoint <= 127) {
                    if (codepoint == 10) {
                        row++;
                        col = 0;
                        message.AddCharToAscMsg(originalMessage.charAt(0));
                    } else {
                        if (col < 16) {
                            if (col == 15) {
                                if (message.getAscMsg().matches("\\w\\z") && originalMessage.matches("\\A\\w")) {
                                    message.AddStringToAscMsg("-\n");
                                    row++;
                                    col = 0;
                                }
                            }

                            col++;
                            message.AddCharToAscMsg(originalMessage.charAt(0));
                        } else {
                            message.AddCharToAscMsg('\n');
                            message.AddCharToAscMsg(originalMessage.charAt(0));
                            row++;
                            col = 1;
                        }
                    }

                } else {
                    String originalHex;
                    String codepointStr = Integer.toHexString(codepoint).toUpperCase();
                    // Constants.log("codepoint", "codepoint=" +
                    // String.valueOf(codepoint) + " codeStr=" + codepointStr);
                    if (codepointStr.length() < 4) {
                        codepointStr = ("0000" + codepointStr).substring(codepointStr.length());
                    }
                    Constants.log("codepoint", "codepoint=" + String.valueOf(codepoint) + " codeStr=" + codepointStr);
                    Font font = db.getFont(codepointStr);
                    if (font == null) {
                        Log.i("MessageProcessing", "font is null! codepoint=[" + String.valueOf(codepoint) + "] char=["
                                + (char) codepoint + "]");
                        originalMessage = originalMessage.substring(1);
                        continue;
                        // originalHex =
                        // "004000E001F003F8071C0C061E473FE77FE7FFC7FF8F7F1F3F3F1FFF0FBE071C";
                    } else {
                        originalHex = font.getHex();
                    }
                    // Constants.log("codepoint", "char='" + (char) codepoint +
                    // "' hex=" + originalHex);
                    CharacterMatrix c = new CharacterMatrix(originalHex);

                    if (c.getWidthBytes() == 2) {
                        if (col < 15) {
                            c.setPos(row, col + 1);
                            message.AddStringToAscMsg("  ");
                            col += 2;
                        } else {
                            message.AddCharToAscMsg('\n');
                            message.AddStringToAscMsg("  ");
                            row++;
                            col = 0;
                            c.setPos(row, col + 1);
                            col += 2;
                        }

                    } else {
                        if (col < 16) {
                            c.setPos(row, col + 1);
                            message.AddCharToAscMsg(' ');
                            col++;
                        } else {
                            message.AddCharToAscMsg('\n');
                            message.AddCharToAscMsg(' ');
                            row++;
                            col = 0;
                            c.setPos(row, col + 1);
                            col++;
                        }

                    }

                    characterQueue.add(c);

                }

                if (row > 8 && (col > 10 || originalMessage.charAt(0) == '\n')) {
                    Constants.log("codepoint", "too many chars!the end char='" + (char) codepoint + "'");
                    message.AddStringToAscMsg("...");

                    break;
                }
                originalMessage = originalMessage.substring(1);
            }

            message.setCharacterQueue(characterQueue);

            return message;
        }

        private PebbleCall processCall(String phone, String originalMessage) {
            // This method not only polls the database but waits for it to be
            // ready
            // in the event it is loading.
            if (!isDatabaseReady()) {
                Log.e("MessagProcessing", "Database not ready after waiting!");
            }

            PebbleCall message = new PebbleCall();
            message.setPhoneNum(phone);
            // Clear the characterQueue, just in case
            Deque<CharacterMatrix> characterQueue = new ArrayDeque<CharacterMatrix>();
            int row = 1;
            int col = 0;

            while (originalMessage.length() > 0) {

                int codepoint = originalMessage.codePointAt(0);
                if (codepoint == 0) {
                    break;
                }
                Constants.log("codepoint", "char='" + (char) codepoint + "' code=" + String.valueOf(codepoint));
                if (codepoint <= 127) {
                    if (codepoint == 10) {
                        row++;
                        col = 0;
                        message.AddCharToAscMsg(originalMessage.charAt(0));
                    } else {
                        if (col < 8) {
                            if (col == 7) {
                                if (message.getAscMsg().matches("\\w\\z") && originalMessage.matches("\\A\\w")) {
                                    message.AddStringToAscMsg("-\n");
                                    row++;
                                    col = 0;
                                }
                            }

                            col++;
                            message.AddCharToAscMsg(originalMessage.charAt(0));
                        } else {
                            message.AddCharToAscMsg('\n');
                            message.AddCharToAscMsg(originalMessage.charAt(0));
                            row++;
                            col = 1;
                        }
                    }

                } else {
                    String originalHex;
                    String codepointStr = Integer.toHexString(codepoint).toUpperCase();
                    // Constants.log("codepoint", "codepoint=" +
                    // String.valueOf(codepoint) + " codeStr=" + codepointStr);
                    if (codepointStr.length() < 4) {
                        codepointStr = ("0000" + codepointStr).substring(codepointStr.length());
                    }
                    Constants.log("codepoint", "codepoint=" + String.valueOf(codepoint) + " codeStr=" + codepointStr);
                    Font font = db.getFont(codepointStr);
                    if (font == null) {
                        Log.i("MessageProcessing", "font is null! codepoint=[" + String.valueOf(codepoint) + "] char=["
                                + (char) codepoint + "]");
                        originalMessage = originalMessage.substring(1);
                        continue;
                        // originalHex =
                        // "004000E001F003F8071C0C061E473FE77FE7FFC7FF8F7F1F3F3F1FFF0FBE071C";
                    } else {
                        originalHex = font.getHex();
                    }

                    CharacterMatrix c = new CharacterMatrix(originalHex);

                    if (c.getWidthBytes() == 2) {
                        if (col < 7) {
                            c.setPos(row, col + 1);
                            message.AddStringToAscMsg("  ");
                            col += 2;
                        } else {
                            message.AddCharToAscMsg('\n');
                            message.AddStringToAscMsg("  ");
                            row++;
                            col = 0;
                            c.setPos(row, col + 1);
                            col += 2;
                        }

                    } else {
                        if (col < 8) {
                            c.setPos(row, col + 1);
                            message.AddCharToAscMsg(' ');
                            col++;
                        } else {
                            message.AddCharToAscMsg('\n');
                            message.AddCharToAscMsg(' ');
                            row++;
                            col = 0;
                            c.setPos(row, col + 1);
                            col++;
                        }

                    }

                    characterQueue.add(c);

                }

                if (row == 3 && (col > 4 || originalMessage.charAt(0) == '\n')) {
                    Constants.log("codepoint", "too many chars!the end char='" + (char) codepoint + "'");
                    message.AddStringToAscMsg("...");

                    break;
                }
                originalMessage = originalMessage.substring(1);
            }

            message.setCharacterQueue(characterQueue);

            return message;
        }
    }

    /**
     * Target we publish for clients to send messages to IncomingHandler.
     */
    final Messenger mMessengerHandleWeChat = new Messenger(new HandleWeChatIncomingHandler());

    @Override
    public void onCreate() {
        MessageProcessingService._context = getApplicationContext();

        db = new DatabaseHandler(getApplicationContext());

        db.open();
    }

    @Override
    public IBinder onBind(Intent arg0) {
        return mMessengerHandleWeChat.getBinder();
    }

}
