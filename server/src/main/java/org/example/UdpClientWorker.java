package org.example;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.Arrays;

public class UdpClientWorker implements Runnable {
    private final ChatServer server;
    private final DatagramSocket socket;

    byte[] receiveBuffer = new byte[1024];

    public UdpClientWorker(ChatServer server, DatagramSocket clientSocket) {
        if (clientSocket == null) {
            throw new IllegalArgumentException("The client socket mustn't be null!");
        }

        this.server = server;
        this.socket = clientSocket;
    }

    String getMessageString() throws IOException {
        Arrays.fill(receiveBuffer, (byte)0);
        DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
        socket.receive(receivePacket);

        String str = new String(receivePacket.getData());

        //remove \0 chars from the end of string, needed for UDP
        return str.replaceAll("\0+$", "");
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                String received = getMessageString();
//                System.out.println(received);
                String[] parts = received.split("\\|", 2);         //TWO backslashes are needed!
                String author = parts[0];
                String content = parts[1];

                server.sendToAllUdp(author, content);
            } catch (IOException e) {
                System.out.println("Failed to read UDP datagram (" + e.getCause() + ")");
            }
        }
    }
}
