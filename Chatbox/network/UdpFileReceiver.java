package Chatbox.network;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class UdpFileReceiver {
    public static String getFileName(String line) {
        String[] parts = line.split("\\|", 3);
        if (parts.length < 3) return null;
        return new String(Base64.getDecoder().decode(parts[1]), StandardCharsets.UTF_8);
    }

    public static String getEncodedData(String line) {
        String[] parts = line.split("\\|", 3);
        if (parts.length < 3) return null;
        return parts[2];
    }
}
