package org.example;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.*;


@SuppressWarnings("deprecation")    //ignore the .joinGroup and .leaveGroup deprecation
public class ChatClient {
    /*
        MESSAGE PREFIXES
     */
    private static final String PREFIX_EXIT = "EXIT";
    private static final String PREFIX_TCP = "T ";
    private static final String PREFIX_UDP = "U";
    private static final String PREFIX_MULTICAST = "M";

    /*
        SPECIAL MESSAGES
     */

    private final static String MSG_USER_ACCEPT = "accepted";
    private final static String MSG_USER_DUPLICATE = "duplicate";



    /*
        NETWORK CONSTANTS
     */
    private static final int SERVER_PORT = 2604;
    private static final String DEFAULT_HOSTNAME = "localhost";

    // 239.255.0.0 with /16 mask is preferred for custom group
    private static final String MULTICAST_ADDR = "239.255.19.86";

    private final BufferedReader consoleReader;
    private Socket tcpSocket;
    private BufferedReader in;
    private PrintWriter out;

    private Thread tcpWorkerThread;
    private DatagramSocket udpSocket;
    private Thread udpWorkerThread;
    private InetAddress serverAddress;

    private MulticastSocket multicastSocket;
    private InetAddress multicastGroup;
    private String username = "";

    private static final String ASCII_ART = """

                      /"*._         _\r
                  .-*'`    `*-.._.-'/\r
                < * ))     ,       ( \r
                  `*-._`._(__.--*"`.\\\
            """;


    private final Thread shutdownHook = new Thread(() -> {
        System.out.println("Shutting down the application...");
        if (tcpSocket != null) {
            try {
                System.out.println("Closing TCP socket...");
                tcpSocket.close();
            } catch (IOException e) { System.out.println("Failed to close the socket."); }
        }


        System.out.println("Closing UDP socket...");
        if (udpSocket != null) udpSocket.close();

        if (multicastSocket != null) {
            if (multicastGroup != null) {
                try {
                    multicastSocket.leaveGroup(multicastGroup);
                } catch (IOException e) { System.out.println("Failed to leave multicast group"); }
            }
            System.out.println("Closing multicast socket...");
            multicastSocket.close();
        }
    });


    public ChatClient() {
        consoleReader = new BufferedReader(new InputStreamReader(System.in));
    }

    private final static String USERNAME_PATTERN = "^[a-zA-Z0-9]*$";
    private String promptUsername() {
        String username = null;

        while (username == null || !username.matches(USERNAME_PATTERN)) {
            try {
                username = consoleReader.readLine();
            } catch (IOException e) {
                System.out.println("Couldn't retrieve the input (" + e.getMessage() + ")");
                return null;
            }
        }
//        if (!username.matches(USERNAME_PATTERN)) {
//            do {
//                System.out.println("The given username is invalid. Try another one:");
//                //it repeats the same actions as above, we can move it to separate method or class
//                try {
//                    username = consoleReader.readLine();
//                } catch (IOException e) {
//                    System.out.println("Couldn't retrieve the input.");
//                    e.printStackTrace();
//                    return null;
//                }
//            } while (!username.matches(USERNAME_PATTERN));
//        }

        return username;
    }

    private boolean validateUser(String username) {
        boolean status = false;
        //send username to server
        out.println(username);
        System.out.println("Awaiting server's response...");

        try {
            //wait for response from the server, if the user already exists we should try picking other username
            String response = in.readLine();
            if (response.equals(MSG_USER_ACCEPT)) {
                status = true;
            } else if (response.equals(MSG_USER_DUPLICATE)) {
                System.out.println("This username is already taken, try other one.");
            }
        } catch (IOException e) {
            System.out.println("Failed to get server response!");
            System.exit(0);
        }

        return status;
    }

    private boolean init() {
        //try to get server's hostname from user
        String hostName;
        try {
            System.out.println("Input the server's hostname:");
            hostName = consoleReader.readLine();

            if (hostName.isBlank()) {
                hostName = DEFAULT_HOSTNAME;
            }

            serverAddress = InetAddress.getByName(hostName);
        } catch (IOException e) {
            System.out.println("Couldn't retrieve the input (" + e.getMessage() + ")");
            return false;
        }


        //trying to connect with server
        try {
            tcpSocket = new Socket(hostName, SERVER_PORT);
            out = new PrintWriter(tcpSocket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(tcpSocket.getInputStream()));

            udpSocket = new DatagramSocket(tcpSocket.getLocalPort());

            multicastSocket = new MulticastSocket();
            multicastGroup = InetAddress.getByName(MULTICAST_ADDR);
            multicastSocket.joinGroup(multicastGroup);

            Runtime.getRuntime().addShutdownHook(shutdownHook);
        } catch (IOException e) {
            System.out.println("Failed to initialize socket (" + e.getMessage() + ")");
            return false;
        }

        //trying to set valid username
        System.out.println("Successfully connected with server.");
        System.out.println("Input your username:");
        boolean status = false;
        while (!status) {
            username = promptUsername();
            if (username == null) { return false; }
            status = validateUser(username);
        }


        try {
            startWorkers();
        } catch (IOException e) {
            System.out.println("Failed to initialize socket input reader. Terminating.");
            System.exit(1);
        }

        return true;
    }

    private void startWorkers() throws IOException {
        TcpMessageWorker tcpWorker = new TcpMessageWorker(this, tcpSocket);
        tcpWorkerThread = new Thread(tcpWorker);
        tcpWorkerThread.start();
        UdpMessageWorker udpWorker = new UdpMessageWorker(this, udpSocket);
        udpWorkerThread = new Thread(udpWorker);
        udpWorkerThread.start();
    }

    public void displayMessage(String author, String content) {
        String builder = '[' + author + "] " + content;
        System.out.println(builder);
    }
    public void start() {
        if (!init()) {
            System.out.println("Init failed");
            return;
        }

        System.out.println("Joined the chat as " + username);
        while (true) {
            try {
                String command = consoleReader.readLine();

                if (command.toUpperCase().startsWith(PREFIX_TCP)){
                    String[] parts = command.split(" ", 2);
                    String msg = parts[1];

                    out.println(username + "|" + msg);
                    displayMessage(username, msg);
                } else if (command.toUpperCase().startsWith(PREFIX_UDP)) {
                    String msgPrefix = username + "|";
                    String msg = ASCII_ART;
                    byte[] sendBuffer = (msgPrefix + msg).getBytes();
                    DatagramPacket sendPacket = new DatagramPacket(sendBuffer, sendBuffer.length, serverAddress, SERVER_PORT);

                    udpSocket.send(sendPacket);
                    displayMessage(username, msg);
                } else if (command.toUpperCase().startsWith(PREFIX_MULTICAST)) {
                    String msgPrefix = username + "|";
                    String msg = ASCII_ART;
                    byte[] sendBuffer = (msgPrefix + msg).getBytes();
                    DatagramPacket sendPacket = new DatagramPacket(sendBuffer, sendBuffer.length, serverAddress, SERVER_PORT);

                    multicastSocket.send(sendPacket);
                    //TODO:
                    // -implement multicast
                    displayMessage(username, msg);
                } else if (command.startsWith(PREFIX_EXIT)) {
                    tcpSocket.close();
                    break;
                } else {
                    System.out.println("Invalid command: " + command);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

    }
}
