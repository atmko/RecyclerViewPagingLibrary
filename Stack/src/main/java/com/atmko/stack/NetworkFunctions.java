/*
 * Copyright (C) 2019 Aayat Mimiko
 */

package com.atmko.stack;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;

class NetworkFunctions {
    private static final String SOCKET_ADDRESS = "8.8.8.8";
    private static final int PORT_NUMBER = 53;
    private static final int TIMEOUT_MILLIS = 1500;

    //source: https://stackoverflow.com/questions/1560788/how-to-check-internet-access-on-android-inetaddress-never-times-out
    //user: Levit
    //date: Dec 5 '14
    static boolean isOnline() {
        try {
            Socket sock = new Socket();
            SocketAddress socketAddress = new InetSocketAddress(SOCKET_ADDRESS, PORT_NUMBER);

            sock.connect(socketAddress, TIMEOUT_MILLIS);
            sock.close();

            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
