package Chatbox.network;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.function.Consumer;

import Chatbox.core.ChatMessage;
import Chatbox.core.Protocol;

public class TcpChatClient {

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String username;

    private Consumer<ChatMessage> onMessage;
    private Consumer<String> onSystem;

    public void setOnMessage(Consumer<ChatMessage> onMessage) {
        this.onMessage = onMessage;
    }

    public void setOnSystem(Consumer<String> onSystem) {
        this.onSystem = onSystem;
    }

    public boolean connect(String host, int port, String username) {
        try {
            this.username = username;
            socket = new Socket(host, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            out.println(Protocol.encodeLogin(username));

            Thread readerThread = new Thread(() -> {
                try {
                    String line;
                    while ((line = in.readLine()) != null) {
                        String[] parts = line.split("\\|", 3);
                        if (parts.length < 3) continue;

                        if ("MSG".equals(parts[0])) {
                            String from = Protocol.fromB64(parts[1]);
                            String content = Protocol.fromB64(parts[2]);

                            if (onMessage != null) {
                                onMessage.accept(new ChatMessage(from, content));
                            }
                        }
                    }
                } catch (Exception e) {
                    if (onSystem != null) {
                        onSystem.accept("Mất kết nối tới server.");
                    }
                }
            });

            readerThread.setDaemon(true);
            readerThread.start();

            return true;
        } catch (Exception e) {
            if (onSystem != null) {
                onSystem.accept("Không kết nối được tới server.");
            }
            return false;
        }
    }

    public void sendMessage(String content) {
        if (out != null) {
            out.println(Protocol.encodeChat(content));
        }
    }

    public String getUsername() {
        return username;
    }

    public void disconnect() {
        try {
            if (socket != null) socket.close();
        } catch (Exception ignored) {
        }
    }
}