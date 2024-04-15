package org.example;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.Socket;
import java.util.Arrays;

public abstract class AbstractMessageWorker implements Runnable {
    protected ChatClient client;

    abstract String getMessageString() throws IOException;

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                String received = getMessageString();
                //This fragment repeats in many places, maybe it could be moved somewhere else
                String[] parts = received.split("\\|", 2);         //TWO backslashes are needed!
                String author = parts[0];
                String content = parts[1];

                client.displayMessage(author, content);
            } catch (IOException e) {
                System.out.println("Couldn't get data from server. Disconnecting..." + e.getMessage());
                System.exit(1);
            }
        }
    }
}
