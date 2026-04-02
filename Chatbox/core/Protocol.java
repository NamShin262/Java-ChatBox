package Chatbox.core;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class Protocol {

    public static String encodeLogin(String username) {
        return "LOGIN|" + b64(username);
    }

    public static String encodeChat(String content) {
        return "CHAT|" + b64(content);
    }

    public static String encodeSystem(String content) {
        return "SYSTEM|" + b64(content);
    }

    public static String[] parse(String line) {
        return line.split("\\|", 2);
    }

    public static String b64(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    public static String fromB64(String base64) {
        return new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
    }
}