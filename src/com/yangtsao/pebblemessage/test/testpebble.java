package com.yangtsao.pebblemessage.test;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.dattasmoon.pebble.plugin.Constants;
import com.espinhasoftware.wechatpebble.pebblecomm.PebbleCall;
import com.espinhasoftware.wechatpebble.pebblecomm.PebbleMessage;
import com.espinhasoftware.wechatpebble.service.MessageProcessingService;
import com.espinhasoftware.wechatpebble.service.PebbleCommService;
import com.yangtsaosoftware.pebblemessenger.R;

//import com.dattasmoon.pebble.plugin.AbstractPluginActivity;

public class testpebble extends Activity {
    EditText textToSend;
    EditText textPhone;
    EditText textName;
    Button   testBut;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.test_pebble);
        bindService(new Intent(testpebble.this, PebbleCommService.class), mConnectionPebbleComm,
                Context.BIND_AUTO_CREATE);
        bindService(new Intent(testpebble.this, MessageProcessingService.class), mConnectionMessageProcessing,
                Context.BIND_AUTO_CREATE);
        textToSend = (EditText) findViewById(R.id.sendText);
        findViewById(R.id.send).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast toast = Toast.makeText(testpebble.this, getResources().getString(R.string.test_pebble_sendbegin),
                        Toast.LENGTH_SHORT);
                toast.show();
                sendToPebble("PebbleMessager", textToSend.getText().toString());
            }
        });
        textPhone = (EditText) findViewById(R.id.testPhoneNum);
        textPhone.setText("1234567890");
        textName = (EditText) findViewById(R.id.testName);
        textName.setText("Kitty");
        testBut = (Button) findViewById(R.id.butTest);

        testBut.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                if (testBut.getText().toString() == getResources().getString(R.string.test_pebble_phone_test)) {
                    testBut.setText(R.string.test_pebble_phone_test_stop);
                    sendCallToPebble(textPhone.getText().toString(), textName.getText().toString());
                } else {
                    testBut.setText(R.string.test_pebble_phone_test);
                    sendStopToPebble();
                }
            }
        });

    }

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

                    sendMessageToPebbleComm(message, 10000);
                } else if (b.containsKey(MessageProcessingService.KEY_RPL_STR)) {
                    String title = b.getString(MessageProcessingService.KEY_RPL_TITLE);
                    String message = b.getString(MessageProcessingService.KEY_RPL_STR);

                    sendMessageToPebbleComm(title, message, 10000);
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

                msg.setData(b);

                mServicePebbleComm.send(msg);
            } catch (RemoteException e) {
                // In this case the service has crashed before we could even
                // do anything with it; we can count on soon being
                // disconnected (and then reconnected if it can be restarted)
                // so there is no need to do anything here.
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

    class PebbleCommIncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case PebbleCommService.MSG_SEND_FINISHED:
                Toast toast = Toast.makeText(testpebble.this, getResources().getString(R.string.test_pebble_sendend),
                        Toast.LENGTH_SHORT);
                toast.show();
                Constants.log("HandleWeChat", "Hooray! Message sent!");
                break;
            default:
                super.handleMessage(msg);
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

        msg.arg1 = MessageProcessingService.PROCESS_UNIFONT;

        Bundle b = new Bundle();
        b.putString(MessageProcessingService.KEY_ORIGINAL_TITLE, title);
        b.putString(MessageProcessingService.KEY_ORIGINAL_MSG, notificationText);

        msg.setData(b);

        try {
            Constants.log("NotificationService", "Sending message to message processing service");
            mServiceMessageProcessing.send(msg);
        } catch (RemoteException e) {
            Constants.log("NotificationService", "Exception while sending data to the MessageProcessing");
        }
    }

    private void sendCallToPebble(String phone, String name) {
        if (!mPebbleCommIsBound) {
            Constants.log("PBL_HandleWeChat", "Comm Service not bound! Can't send message.");
            return;
        }

        // Notification notification = (Notification) event.getParcelableData();

        // String originalMsg = notification.tickerText.toString();

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

    private void sendStopToPebble() {
        try {
            Message msg = Message.obtain(null, PebbleCommService.MSG_SEND_CALL_STOP);
            msg.replyTo = mMessengerPebbleComm;

            mServicePebbleComm.send(msg);

        } catch (RemoteException e) {

        }
    }

    protected void onServiceConnected() {
        // get inital preferences

        // queue = new LinkedList<queueItem>();
    }

    @Override
    public void finish() {
        unbindService(mConnectionMessageProcessing);
        unbindService(mConnectionPebbleComm);
        super.finish();
    }
}
