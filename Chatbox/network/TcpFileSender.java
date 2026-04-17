package Chatbox.network;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;

public class TcpFileSender {

    public static String encodeFile(File file) throws IOException {
        byte[] bytes = Files.readAllBytes(file.toPath());
        String encodedBytes = Base64.getEncoder().encodeToString(bytes);
        return "FILE|" + encode(file.getName()) + "|" + encodedBytes;
    }

    private static String encode(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }
}
