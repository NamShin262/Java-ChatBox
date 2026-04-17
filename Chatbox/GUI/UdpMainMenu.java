package Chatbox.GUI;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import Chatbox.network.UdpFileReceiver;
import Chatbox.network.UdpFileSender;

public class UdpMainMenu extends Application {
    public static void main(String[] args) {
        launch(args);
    }

    private final int startPort = 6000;
    private final int endPort = 6010;
    private int localPort = startPort;

    private VBox messageBox;
    private TextField nameField;
    private TextField peerPortField;
    private TextField messageField;
    private Label statusLabel;
    private Stage stage;
    private java.net.DatagramSocket socket;

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        localPort = findFreePort();

        nameField = new TextField("User");
        peerPortField = new TextField(String.valueOf(localPort == startPort ? startPort + 1 : startPort));
        messageField = new TextField();
        messageField.setPromptText("Nhập tin nhắn...");

        statusLabel = new Label("UDP local port: " + localPort);
        messageBox = new VBox(8);
        messageBox.setPadding(new Insets(10));

        ScrollPane scrollPane = new ScrollPane(messageBox);
        scrollPane.setFitToWidth(true);

        Button sendButton = new Button("Gửi");
        sendButton.setOnAction(e -> sendMessage());
        messageField.setOnAction(e -> sendMessage());

        Button fileButton = new Button("Gửi file/ảnh");
        fileButton.setOnAction(e -> sendFile());

        HBox top = new HBox(10,
                new Label("Tên:"), nameField,
                new Label("Peer port:"), peerPortField,
                statusLabel);
        top.setAlignment(Pos.CENTER_LEFT);
        top.setPadding(new Insets(10));

        HBox bottom = new HBox(10, messageField, sendButton, fileButton);
        HBox.setHgrow(messageField, Priority.ALWAYS);
        bottom.setPadding(new Insets(10));

        BorderPane root = new BorderPane();
        root.setTop(top);
        root.setCenter(scrollPane);
        root.setBottom(bottom);
        root.setPadding(new Insets(10));

        primaryStage.setTitle("UDP Chat");
        primaryStage.setScene(new Scene(root, 900, 600));
        primaryStage.show();

        startReceiver();
    }

    private int findFreePort() {
        for (int p = startPort; p <= endPort; p++) {
            java.net.DatagramSocket socketCheck = null;
            try {
                socketCheck = new java.net.DatagramSocket(p);
                return p;
            } catch (Exception ignored) {
            } finally {
                if (socketCheck != null) socketCheck.close();
            }
        }
        return startPort;
    }

    private void startReceiver() {
        new Thread(() -> {
            try {
                socket = new java.net.DatagramSocket(localPort);
                byte[] buffer = new byte[65507];
                while (true) {
                    java.net.DatagramPacket packet = new java.net.DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    String line = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                    handleIncoming(line);
                }
            } catch (Exception e) {
                Platform.runLater(() -> showAlert("Lỗi UDP receiver: " + e.getMessage()));
            }
        }, "udp-receiver").start();
    }

    private void handleIncoming(String line) {
        String[] parts = line.split("\\|", 4);
        if (parts.length < 3) return;
        Platform.runLater(() -> {
            switch (parts[0]) {
                case "MSG" -> addBubble(parts[1] + ": " + decode(parts[2]), false);
                case "FILE" -> {
                    String name = UdpFileReceiver.getFileName(line);
                    String payload = UdpFileReceiver.getEncodedData(line);
                    if (name == null || payload == null) return;
                    boolean image = name.toLowerCase().matches(".*\\.(png|jpg|jpeg|gif|webp)$");
                    addFileBubble(name, payload, image, false);
                }
            }
        });
    }

    private void sendMessage() {
        String text = messageField.getText().trim();
        if (text.isEmpty()) return;
        addBubble(nameField.getText().trim() + ": " + text, true);
        sendRaw("MSG|" + encode(nameField.getText().trim()) + "|" + encode(text));
        messageField.clear();
    }

    private void sendFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Chọn file hoặc ảnh");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Text/Images", "*.txt", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.webp")
        );
        File file = chooser.showOpenDialog(stage);
        if (file == null) return;
        try {
            String payload = UdpFileSender.encodeFile(file);
            String name = file.getName();
            boolean image = name.toLowerCase().matches(".*\\.(png|jpg|jpeg|gif|webp)$");
            addFileBubble(name, Base64.getEncoder().encodeToString(java.nio.file.Files.readAllBytes(file.toPath())), image, true);
            sendRaw(payload);
        } catch (Exception ex) {
            showAlert("Không thể gửi file: " + ex.getMessage());
        }
    }

    private void sendRaw(String payload) {
        int peerPort;
        try {
            peerPort = Integer.parseInt(peerPortField.getText().trim());
        } catch (Exception e) {
            showAlert("Peer port không hợp lệ");
            return;
        }
        try {
            byte[] data = payload.getBytes(StandardCharsets.UTF_8);
            if (socket == null) socket = new java.net.DatagramSocket();
            socket.send(new java.net.DatagramPacket(data, data.length, java.net.InetAddress.getByName("127.0.0.1"), peerPort));
        } catch (Exception e) {
            showAlert("Không gửi được qua UDP: " + e.getMessage());
        }
    }

    private void addBubble(String text, boolean isMe) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setStyle(isMe
                ? "-fx-background-color:#10b981;-fx-text-fill:white;-fx-padding:10 14;-fx-background-radius:18;"
                : "-fx-background-color:#e4e6eb;-fx-text-fill:black;-fx-padding:10 14;-fx-background-radius:18;");
        HBox row = new HBox(label);
        row.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        messageBox.getChildren().add(row);
    }

    private void addFileBubble(String name, String encodedBytes, boolean image, boolean isMe) {
        VBox content = new VBox(6);
        content.getChildren().add(new Label((image ? "[Ảnh] " : "[File] ") + name));
        try {
            if (image) {
                byte[] bytes = Base64.getDecoder().decode(encodedBytes);
                ImageView iv = new ImageView(new Image(new ByteArrayInputStream(bytes)));
                iv.setFitWidth(220);
                iv.setPreserveRatio(true);
                content.getChildren().add(iv);
            } else {
                byte[] bytes = Base64.getDecoder().decode(encodedBytes);
                String text = new String(bytes, StandardCharsets.UTF_8);
                TextArea ta = new TextArea(text);
                ta.setEditable(false);
                ta.setWrapText(true);
                ta.setPrefRowCount(4);
                ta.setPrefWidth(320);
                content.getChildren().add(ta);
            }
        } catch (Exception ex) {
            content.getChildren().add(new Label("[Không mở được nội dung]"));
        }

        VBox bubble = new VBox(content);
        bubble.setStyle(isMe
                ? "-fx-background-color:#10b981;-fx-text-fill:white;-fx-padding:10;-fx-background-radius:18;"
                : "-fx-background-color:#e4e6eb;-fx-text-fill:black;-fx-padding:10;-fx-background-radius:18;");
        HBox row = new HBox(bubble);
        row.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        messageBox.getChildren().add(row);
    }

    private String encode(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private String decode(String base64) {
        return new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
    }

    private void showAlert(String msg) {
        Platform.runLater(() -> new Alert(Alert.AlertType.INFORMATION, msg).show());
    }
}
