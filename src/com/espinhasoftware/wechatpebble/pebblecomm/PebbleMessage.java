/* 
Copyright (c) 2014 Tiago Espinha & Yang Tsao

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.espinhasoftware.wechatpebble.pebblecomm;

import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

import com.espinhasoftware.wechatpebble.model.CharacterMatrix;

public class PebbleMessage implements Serializable {

    /**
     * 
     */
    private static final long      serialVersionUID  = -8720164527356388785L;

    /**
     * 
     */

    public static final UUID       WECHATPEBBLE_UUID = UUID.fromString("e711a268-8464-406c-a8f6-a4f580b6a2be");

    public static final int        PBL_ASCMSG        = 1;
    public static final int        PBL_RESET         = 2;
    public static final int        PBL_FINAL         = 3;
    public static final int        PBL_UNIPOS        = 4;
    public static final int        PBL_UNIWIDTH      = 5;
    public static final int        PBL_UNICHAR       = 6;
    public static final int        PBL_PROGRESS      = 7;

    private Deque<CharacterMatrix> _characterQueue;
    /* store the unicode character's imgs and their positions */
    private String                 _ascmsg;

    /*
     * store the ascii String and blank space where should display unicode
     * character
     */

    public String getAscMsg() {
        return _ascmsg;
    }

    public void setAscMsg(String msg) {
        this._ascmsg = msg;
    }

    public void AddCharToAscMsg(char c) {
        this._ascmsg += String.valueOf(c);

    }

    public void AddStringToAscMsg(String s) {
        this._ascmsg += s;
    }

    public PebbleMessage(Deque<CharacterMatrix> characterQueue) {
        this._characterQueue = characterQueue;
        if (this._ascmsg != null) {

        } else {
            this._ascmsg = new String();
        }
    }

    public PebbleMessage() {
        this._characterQueue = new ArrayDeque<CharacterMatrix>();
        this._ascmsg = new String();
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

    public static String toBinary(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * Byte.SIZE);
        for (int i = 0; i < Byte.SIZE * bytes.length; i++) {
            sb.append((bytes[i / Byte.SIZE] << i % Byte.SIZE & 0x80) == 0 ? '-' : '#');
        }
        return sb.toString();
    }

    public static String toBinary(Byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * Byte.SIZE);
        for (int i = 0; i < Byte.SIZE * bytes.length; i++) {
            sb.append((bytes[i / Byte.SIZE] << i % Byte.SIZE & 0x80) == 0 ? '-' : '#');
        }
        return sb.toString();
    }
}
