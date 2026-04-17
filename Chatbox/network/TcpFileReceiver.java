package Chatbox.network;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class TcpFileReceiver {

    public static boolean isFileMessage(String line) {
        return line != null && line.startsWith("FILE|");
    }

    public static String getFileName(String line) {
        if (!isFileMessage(line)) return null;
        String[] parts = line.split("\\|", 3);
        if (parts.length < 3) return null;
        return decode(parts[1]);
    }

    public static String getEncodedData(String line) {
        if (!isFileMessage(line)) return null;
        String[] parts = line.split("\\|", 3);
        if (parts.length < 3) return null;
        return parts[2];
    }

    public static byte[] decodeData(String encodedData) {
        return Base64.getDecoder().decode(encodedData);
    }

    private static String decode(String base64) {
        return new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
    }
}
