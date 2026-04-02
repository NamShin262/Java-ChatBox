package Chatbox.network;

import java.io.File;
import java.io.FileInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class UdpFileSender {

    public void sendFile(File file, String host, int port) throws Exception {
        try (DatagramSocket socket = new DatagramSocket();
             FileInputStream fis = new FileInputStream(file)) {

            String header = "FILE|" + file.getName() + "|" + file.length();
            byte[] headerBytes = header.getBytes(StandardCharsets.UTF_8);
            DatagramPacket headerPacket = new DatagramPacket(
                headerBytes, headerBytes.length, InetAddress.getByName(host), port
            );
            socket.send(headerPacket);

            byte[] buffer = new byte[4096];
            int read;
            int seq = 0;

            while ((read = fis.read(buffer)) != -1) {
                byte[] data = new byte[read];
                System.arraycopy(buffer, 0, data, 0, read);

                String payload = "DATA|" + seq + "|" + Base64.getEncoder().encodeToString(data);
                byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);

                DatagramPacket dataPacket = new DatagramPacket(
                    payloadBytes, payloadBytes.length, InetAddress.getByName(host), port
                );
                socket.send(dataPacket);
                seq++;
                Thread.sleep(2);
            }

            byte[] endBytes = "END".getBytes(StandardCharsets.UTF_8);
            DatagramPacket endPacket = new DatagramPacket(
                endBytes, endBytes.length, InetAddress.getByName(host), port
            );
            socket.send(endPacket);
        }
    }
}