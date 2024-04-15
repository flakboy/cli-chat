package org.example;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;

public class TcpMessageWorker extends AbstractMessageWorker {

    private final BufferedReader in;

    public TcpMessageWorker(ChatClient client, Socket socket) throws IOException {
        this.client = client;
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    }

    @Override
    String getMessageString() throws IOException {
        return in.readLine();
    }

    //run() inherited from AbstractMessageWorker
}
