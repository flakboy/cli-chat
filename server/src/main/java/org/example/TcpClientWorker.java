package org.example;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketException;

public class TcpClientWorker implements Runnable {
    private final ChatServer server;
    private String username;
    private final Socket clientSocket;
    private final PrintWriter out;
    private final BufferedReader in;


    private final static String MSG_USER_ACCEPT = "accepted";
    private final static String MSG_USER_DUPLICATE = "duplicate";
//    private final static String MSG_USER_INVALID = "invalid";

    /*
      TODO:
    - maybe split stages of creating connection so the thread creation fails early?

    - detect if the client disconnects
    - if client sends TCP FIN then close connection and thread
    - do it too, if you try to pass the value from other client and it fails
    */
    public TcpClientWorker(ChatServer server, Socket clientSocket) throws IOException {
        if (clientSocket == null) {
            throw new IllegalArgumentException("The client socket mustn't be null!");
        }

        this.server = server;
        this.clientSocket = clientSocket;

        out = new PrintWriter(clientSocket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
    }

    private boolean initChatConnection() {
        String username = null;
        try {
            boolean isUserValid = false;
            try {
                do {
                    username = in.readLine();
                    if (username == null) {
                        clientSocket.close();
                        return false;
                    } else if (!server.usernameExists(username)) {
                        this.username = username;
                        out.println(MSG_USER_ACCEPT);
                        server.putUser(username, clientSocket, Thread.currentThread());
                        System.out.println("User " + username + " joined the chat.");
                        isUserValid = true;
                    } else {
                        System.out.println("DUPLIKAT");
                        out.println(MSG_USER_DUPLICATE);
                    }
                } while (!isUserValid);
            } catch (IOException e) {
                System.out.println("Failed to get valid username from client.");
                clientSocket.close();
            }

            //explanation: https://stackoverflow.com/questions/31810668/java-net-socket-tcp-keep-alive-usage
            clientSocket.setKeepAlive(true);
            return true;

        } catch (SocketException e) {
            System.out.println("A problem occurred when initializing user. Removing user.");
            server.deleteUser(username);
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public void handleDisconnection() {
        server.deleteUser(username);
    }

    @Override
    public void run() {
        if (initChatConnection()) {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    String received = in.readLine();
                    if (received == null) {
                        System.out.println("User \"" + username + "\" disconnected.");
                        handleDisconnection();
                    } else {
                        String[] parts = received.split("\\|", 2);         //TWO backslashes are needed!
                        String author = parts[0];
                        String content = parts[1];

                        server.sendToAllTcp(author, content);
                    }
                }

                clientSocket.close();
            } catch (IOException e) {
                System.out.println("Lost connection with client \"" + username + "\" (" + e.getMessage() + ")");
                handleDisconnection();
            }
        }
    }

}
