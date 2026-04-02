package Chatbox.network;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import Chatbox.core.Protocol;

public class TcpChatServer {

    private final int port;
    private ServerSocket serverSocket;
    private boolean running = false;

    private final Set<ClientHandler> clients = ConcurrentHashMap.newKeySet();

    public TcpChatServer(int port) {
        this.port = port;
    }

    public void start() {
        if (running) return;

        running = true;

        Thread serverThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                System.out.println("TCP Server started at port " + port);

                while (running) {
                    Socket socket = serverSocket.accept();
                    ClientHandler handler = new ClientHandler(socket);
                    clients.add(handler);
                    handler.start();
                }
            } catch (Exception e) {
                if (running) {
                    e.printStackTrace();
                }
            }
        });

        serverThread.setDaemon(true);
        serverThread.start();
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (Exception ignored) {
        }
    }

    private void broadcast(String sender, String message) {
        for (ClientHandler client : clients) {
            client.send("MSG|" + Protocol.b64(sender) + "|" + Protocol.b64(message));
        }
    }

    private class ClientHandler extends Thread {
        private final Socket socket;
        private PrintWriter out;
        private String username = "Unknown";

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))
            ) {
                out = new PrintWriter(socket.getOutputStream(), true);

                String line;
                while ((line = in.readLine()) != null) {
                    String[] parts = line.split("\\|", 2);
                    String type = parts[0];

                    if ("LOGIN".equals(type) && parts.length == 2) {
                        username = Protocol.fromB64(parts[1]);
                        broadcast("SYSTEM", username + " đã tham gia phòng chat");
                    } else if ("CHAT".equals(type) && parts.length == 2) {
                        String content = Protocol.fromB64(parts[1]);
                        broadcast(username, content);
                    }
                }
            } catch (Exception ignored) {
            } finally {
                clients.remove(this);
                broadcast("SYSTEM", username + " đã rời phòng chat");
                try {
                    socket.close();
                } catch (Exception ignored) {
                }
            }
        }

        public void send(String line) {
            if (out != null) {
                out.println(line);
            }
        }
    }
}