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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import Chatbox.network.TcpFileReceiver;
import Chatbox.network.TcpFileSender;

public class TcpMainMenu extends Application {
    public static void main(String[] args) {
        launch(args);
    }

    private final int startPort = 7000;
    private final int endPort = 7010;
    private int localPort = startPort;

    private VBox messageBox;
    private TextField nameField;
    private TextField peerPortField;
    private TextField messageField;
    private Label statusLabel;
    private Stage stage;

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        localPort = findFreePort();

        nameField = new TextField("User");
        peerPortField = new TextField(String.valueOf(localPort == startPort ? startPort + 1 : startPort));
        messageField = new TextField();
        messageField.setPromptText("Nhập tin nhắn...");

        statusLabel = new Label("TCP local port: " + localPort);
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

        primaryStage.setTitle("TCP Chat");
        primaryStage.setScene(new Scene(root, 900, 600));
        primaryStage.show();

        startReceiver();
    }

    private int findFreePort() {
        for (int p = startPort; p <= endPort; p++) {
            ServerSocket serverSocketCheck = null;
            try {
                serverSocketCheck = new ServerSocket(p);
                return p;
            } catch (Exception ex) {
            } finally {
                if (serverSocketCheck != null) {
                    try { serverSocketCheck.close(); } catch (Exception ignored) {}
                }
            }
        }
        return startPort;
    }

    private void startReceiver() {
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(localPort)) {
                while (true) {
                    Socket socket = serverSocket.accept();
                    new Thread(() -> handleConnection(socket)).start();
                }
            } catch (Exception e) {
                Platform.runLater(() -> showAlert("Lỗi TCP receiver: " + e.getMessage()));
            }
        }, "tcp-receiver").start();
    }

    private void handleConnection(Socket socket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                handleIncoming(line);
            }
        } catch (Exception ignored) {
        }
    }

    private void handleIncoming(String line) {
        String[] parts = line.split("\\|", 4);
        if (parts.length < 3) return;
        Platform.runLater(() -> {
            switch (parts[0]) {
                case "MSG" -> addBubble(parts[1] + ": " + decode(parts[2]), false);
                case "FILE" -> {
                    String name = TcpFileReceiver.getFileName(line);
                    String payload = TcpFileReceiver.getEncodedData(line);
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
            String name = file.getName();
            boolean image = name.toLowerCase().matches(".*\\.(png|jpg|jpeg|gif|webp)$");
            String payload = TcpFileSender.encodeFile(file);
            String encodedBytes = TcpFileReceiver.getEncodedData(payload);
            addFileBubble(name, encodedBytes, image, true);
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
        try (Socket socket = new Socket("127.0.0.1", peerPort);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8)) {
            out.println(payload);
        } catch (Exception e) {
            showAlert("Không gửi được qua TCP: " + e.getMessage());
        }
    }

    private void addBubble(String text, boolean isMe) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setStyle("-fx-padding: 2 0 2 0; -fx-text-fill: black;");
        messageBox.getChildren().add(label);
    }

    private void addFileBubble(String name, String encodedBytes, boolean image, boolean isMe) {
        Label title = new Label((isMe ? nameField.getText().trim() : "") + ": " + (image ? "[Ảnh] " : "[File] ") + name);
        title.setStyle("-fx-padding: 2 0 2 0; -fx-text-fill: black;");
        messageBox.getChildren().add(title);
        try {
            if (image) {
                byte[] bytes = Base64.getDecoder().decode(encodedBytes);
                ImageView iv = new ImageView(new Image(new ByteArrayInputStream(bytes)));
                iv.setFitWidth(220);
                iv.setPreserveRatio(true);
                messageBox.getChildren().add(iv);
            } else {
                byte[] bytes = Base64.getDecoder().decode(encodedBytes);
                String text = new String(bytes, StandardCharsets.UTF_8);
                TextArea ta = new TextArea(text);
                ta.setEditable(false);
                ta.setWrapText(true);
                ta.setPrefRowCount(4);
                ta.setPrefWidth(320);
                messageBox.getChildren().add(ta);
            }
        } catch (Exception ex) {
            messageBox.getChildren().add(new Label("[Không mở được nội dung]"));
        }
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
