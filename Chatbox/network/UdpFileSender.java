package Chatbox.network;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;

public class UdpFileSender {
    public static String encodeFile(File file) throws java.io.IOException {
        byte[] bytes = Files.readAllBytes(file.toPath());
        String encodedBytes = Base64.getEncoder().encodeToString(bytes);
        String fileName = Base64.getEncoder().encodeToString(file.getName().getBytes(StandardCharsets.UTF_8));
        return "FILE|" + fileName + "|" + encodedBytes;
    }
}
