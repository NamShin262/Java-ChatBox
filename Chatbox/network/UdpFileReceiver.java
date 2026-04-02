package Chatbox.network;

import java.io.ByteArrayOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class UdpFileReceiver {

    private boolean running = false;
    private DatagramSocket socket;

    // Interface mới: nhận vào (Tên file, Dữ liệu byte)
    public void start(int port, BiConsumer<String, byte[]> onFileReceived, Consumer<String> onLog) {
        if (running) return;
        running = true;

        Thread thread = new Thread(() -> {
            ByteArrayOutputStream baos = null;
            String currentFileName = null;

            try {
                socket = new DatagramSocket(port);
                byte[] buffer = new byte[65507];

                if (onLog != null) {
                    onLog.accept("UDP Receiver đang lắng nghe ở cổng " + port);
                }

                while (running) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    String msg = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);

                    if (msg.startsWith("FILE|")) {
                        String[] parts = msg.split("\\|", 3);
                        currentFileName = parts[1];
                        
                        // Khởi tạo bộ nhớ đệm để chứa dữ liệu file
                        baos = new ByteArrayOutputStream();

                        if (onLog != null) {
                            onLog.accept("Đang nhận file: " + currentFileName + " (chờ xác nhận tải)");
                        }
                    } else if (msg.startsWith("DATA|")) {
                        if (baos == null) continue;

                        String[] parts = msg.split("\\|", 3);
                        byte[] data = Base64.getDecoder().decode(parts[2]);
                        baos.write(data);
                        
                    } else if ("END".equals(msg)) {
                        if (baos != null && currentFileName != null) {
                            // Gửi dữ liệu về giao diện thông qua Callback
                            if (onFileReceived != null) {
                                onFileReceived.accept(currentFileName, baos.toByteArray());
                            }
                            
                            if (onLog != null) {
                                onLog.accept("Đã nhận xong " + currentFileName + ". Chờ bạn nhấn 'Tải về'.");
                            }
                            
                            baos.close();
                            baos = null;
                        }
                        currentFileName = null;
                    }
                }
            } catch (Exception e) {
                if (running && onLog != null) {
                    onLog.accept("Lỗi UDP Receiver: " + e.getMessage());
                }
            }
        });

        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }
}