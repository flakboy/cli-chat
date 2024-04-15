package org.example;

import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.Map;

public class ChatServer {

    private static final int SERVER_PORT = 2604;

    /*
            TCP
     */
    private ServerSocket tcpServerSocket;  //for creating TCP connections with clients

    private final HashMap<String, Socket> sockets = new HashMap<>();
    private final HashMap<String, Thread> clientThreads = new HashMap<>();

    private final Map<String, PrintWriter> clientOutputs = new HashMap<>();

    /*
            UDP
     */

    //We only use one UDP socket, because UDP allows one-to-many/many-to-one communication
    private DatagramSocket udpServerSocket;


    private final Thread shutdownHook = new Thread(() -> {
        System.out.println("Shutting down the application...");
        if (tcpServerSocket != null) {
            try {
                tcpServerSocket.close();
                System.out.println("Server socket closed.");
            } catch (IOException e) {
                System.out.println("Failed to close the socket.");
            }
        }

        if (udpServerSocket != null) {
            udpServerSocket.close();
            System.out.println("UDP socket closed.");
        }

        for (String username : sockets.keySet()) {
            try {
                sockets.get(username).close();
                clientOutputs.get(username).close();
            } catch (IOException | NullPointerException e) {
                System.out.println("Failed to close the socket for client \"" + username + "\"");
            }
        }


    });

    public boolean usernameExists(String username) {
        return sockets.containsKey(username);
    }

    public void start() {
        Runtime.getRuntime().addShutdownHook(shutdownHook);
        try {
            tcpServerSocket = new ServerSocket(SERVER_PORT);
            udpServerSocket = new DatagramSocket(SERVER_PORT);
            System.out.println("The server is listening on port " + SERVER_PORT);
        } catch (IOException e) {
            System.out.println("An error occurred when creating sockets. (" +  e.getCause() + ") Terminating... ");
            System.exit(1);
        }

        while (!Thread.currentThread().isInterrupted()) {
            try {
                Socket clientSocket = tcpServerSocket.accept();
                TcpClientWorker tcpWorker = new TcpClientWorker(this, clientSocket);
                UdpClientWorker udpWorker = new UdpClientWorker(this, udpServerSocket);
                Thread tcpThr = new Thread(tcpWorker);
                tcpThr.start();
                Thread udpThread = new Thread(udpWorker);
                udpThread.start();

            } catch (IOException e) {
                System.out.println("Failed to accept connection (" + e.getMessage() + ")");
            }
        }

    }

    public void putUser(String username, Socket clientSocket, Thread thr) throws IOException{
        sockets.put(username, clientSocket);
        clientThreads.put(username, thr);
        clientOutputs.put(username, new PrintWriter(clientSocket.getOutputStream(), true));
    }

    public void deleteUser(String username) {
        if (username == null) {
            return;
        }
        Thread thr = clientThreads.get(username);
        //thread can be null, if the client haven't logged in as user with specific name
        if (thr != null) {
            thr.interrupt();
        }
        clientThreads.remove(username);

        PrintWriter out = clientOutputs.get(username);
        Socket socket = sockets.get(username);
        try {
            if (clientOutputs.get(username) != null) out.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            clientOutputs.remove(username);
            sockets.remove(username);
        }
    }


    public void sendToAllTcp(String username, String msg) {
        for (String nick : clientOutputs.keySet()) {
            if (!nick.equals(username)) {
                //send message
//                System.out.println("Sent to " + nick);
                clientOutputs.get(nick).println(username + "|" + msg);
            }
        }
    }

    public void sendToAllUdp(String author, String msg) {
        for (String nick : clientOutputs.keySet()) {
            if (!nick.equals(author)) {
                InetAddress hostAddr = sockets.get(nick).getInetAddress();
                int port = sockets.get(nick).getPort();

                byte[] sendBuffer = (author + "|" + msg).getBytes();
                DatagramPacket sendPacket = new DatagramPacket(sendBuffer, sendBuffer.length, hostAddr, port);

                try {
                    udpServerSocket.send(sendPacket);
                } catch (IOException e) {
                    System.out.println("Failed to send UDP packet to " + nick + ".");
                }

            }
        }
    }

}