package org.example;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.MulticastSocket;
import java.util.Arrays;

public class MulticastMessageWorker extends AbstractMessageWorker {
    ChatClient client;
    MulticastSocket socket;

    public MulticastMessageWorker(ChatClient client, MulticastSocket socket) {
        this.client = client;
        this.socket = socket;
    }

    @Override
    String getMessageString() throws IOException {
        byte[] receiveBuffer = new byte[1024];
        Arrays.fill(receiveBuffer, (byte)0);
        DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
        socket.receive(receivePacket);
        String str = new String(receivePacket.getData());

        //remove \0 chars from the end of string, needed for UDP
        return str.replaceAll("\0+$", "");
    }
}
