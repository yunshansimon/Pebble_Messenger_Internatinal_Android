package com.espinhasoftware.wechatpebble.pebblecomm;

import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

import com.espinhasoftware.wechatpebble.model.CharacterMatrix;

public class PebbleCall implements Serializable {
    /**
     * 
     */

    /**
     * 
     */

    /**
     * 
     */
    private static final long      serialVersionUID    = -4432642272402397731L;
    /**
     * 
     */

    public static final UUID       WECHATPEBBLE_UUID   = UUID.fromString("628b21fb-4294-4689-bd8d-0ea30960bf85");
    public static final int        PBL_ASCMSG          = 1;
    public static final int        PBL_PHONE_NUM       = 2;
    public static final int        PBL_FINAL           = 3;
    public static final int        PBL_UNIPOS          = 4;
    public static final int        PBL_UNIWIDTH        = 5;
    public static final int        PBL_UNICHAR         = 6;
    public static final int        PBL_CALL            = 7;
    public static final int        CALL_ANSWER         = 1;
    public static final int        CALL_END            = 2;
    public static final int        CALL_END_SMS_SHORT  = 3;
    public static final int        CALL_ANSWER_SPEAKER = 4;
    public static final int        CALL_END_SMS_LONG   = 5;

    private Deque<CharacterMatrix> _characterQueue;
    /* store the unicode character's imgs and their positions */
    private String                 _ascmsg;
    private String                 _phonenum;

    public String getAscMsg() {
        return _ascmsg;
    }

    public void setAscMsg(String msg) {
        this._ascmsg = msg;
    }

    public String getPhoneNum() {
        return _phonenum;
    }

    public void setPhoneNum(String num) {
        this._phonenum = num;
    }

    public void AddCharToAscMsg(char c) {
        this._ascmsg += String.valueOf(c);

    }

    public void AddStringToAscMsg(String s) {
        this._ascmsg += s;
    }

    public PebbleCall(Deque<CharacterMatrix> characterQueue) {
        this._characterQueue = characterQueue;
        if (this._ascmsg != null) {

        } else {
            this._ascmsg = new String();
            this._phonenum = new String();
        }
    }

    public PebbleCall() {
        this._characterQueue = new ArrayDeque<CharacterMatrix>();
        this._ascmsg = new String();
        this._phonenum = new String();
    }

    public Deque<CharacterMatrix> getCharacterQueue() {
        return _characterQueue;
    }

    public void setCharacterQueue(Deque<CharacterMatrix> characterQueue) {
        this._characterQueue = characterQueue;
    }

    public boolean hasMore() {
        if (_characterQueue.isEmpty() && (_ascmsg.length() == 0)) {
            return false;
        }

        return true;
    }
}
