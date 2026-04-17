package Chatbox.network;

public class UdpFileReceiver {
    public static String getFileName(String line) {
        String[] parts = line.split("\\|", 4);
        if (parts.length < 4) return null;
        return new String(java.util.Base64.getDecoder().decode(parts[2]), java.nio.charset.StandardCharsets.UTF_8);
    }

    public static String getEncodedData(String line) {
        String[] parts = line.split("\\|", 4);
        if (parts.length < 4) return null;
        return parts[3];
    }
}
