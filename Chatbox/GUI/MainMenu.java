package Chatbox.GUI;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

public class MainMenu extends Application {
    private VBox messageContainer;
    private TextField messageField;
    private ScrollPane messageScroll;
    private ListView<String> friendList;
    private HBox headerBox; 
    private Label chatHeaderName;
    private Label typingLabel; // Hiển thị trạng thái đang gõ
    private Map<String, List<javafx.scene.Node>> privateHistory = new HashMap<>(); 
    private Map<String, Integer> friendPorts = new HashMap<>(); 
    private Map<String, List<Integer>> myGroups = new HashMap<>(); 
    private String username = "Guest";
    private int currentUdpPort = 6000;
    private java.net.DatagramSocket udpSocket;
    
    private final String HISTORY_DIR = "chat_history/"; // Thư mục lưu lịch sử

    @Override
    public void start(Stage primaryStage) {
        new File(HISTORY_DIR).mkdirs(); // Tự động tạo thư mục lưu trữ
        showDetailedLoginScreen(primaryStage);
    }

    private void showDetailedLoginScreen(Stage stage) {
        VBox root = new VBox();
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-background-color: linear-gradient(to bottom right, #1d3557, #457b9d, #1d3557);");

        VBox loginCard = new VBox(25);
        loginCard.setAlignment(Pos.CENTER);
        loginCard.setPadding(new Insets(45));
        loginCard.setMaxWidth(390);
        loginCard.setStyle("-fx-background-color: rgba(255, 255, 255, 0.1); -fx-background-radius: 25; -fx-border-color: rgba(255, 255, 255, 0.2); -fx-border-radius: 25; -fx-border-width: 1.5; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.4), 30, 0.5, 0, 15);");

        TextField nameField = new TextField();
        nameField.setPromptText("Tên nhân viên / Username");
        nameField.setPrefHeight(52);
        nameField.setStyle("-fx-background-color: #f1faee; -fx-background-radius: 14; -fx-padding: 0 18; -fx-font-size: 14px;");

        Button btnLogin = new Button("KẾT NỐI HỆ THỐNG");
        btnLogin.setMaxWidth(Double.MAX_VALUE);
        btnLogin.setPrefHeight(52);
        btnLogin.setStyle("-fx-background-color: linear-gradient(to right, #1d3557, #457b9d); -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 14;");

        btnLogin.setOnAction(e -> {
            if (!nameField.getText().trim().isEmpty()) {
                username = nameField.getText().trim();
                switchToMainChat(stage);
            }
        });

        loginCard.getChildren().addAll(new Label("ĐĂNG NHẬP"), nameField, btnLogin);
        root.getChildren().add(loginCard);
        stage.setScene(new Scene(root, 1100, 750));
        stage.setTitle("VẠN TÍN MESSENGER");
        stage.show();
    }

    private void switchToMainChat(Stage stage) {
        BorderPane root = new BorderPane();
        root.setCenter(createCenterPanel(stage));
        root.setLeft(createLeftPanel());
        stage.getScene().setRoot(root);
        autoStartConnection();
    }

    private BorderPane createCenterPanel(Stage stage) {
        BorderPane center = new BorderPane();
        chatHeaderName = new Label("Phòng chat chung");
        typingLabel = new Label(""); // Label thông báo đang gõ
        typingLabel.setStyle("-fx-text-fill: #31a24c; -fx-font-style: italic; -fx-font-size: 11px;");

        VBox headerInfo = new VBox(2, chatHeaderName, typingLabel);
        headerBox = new HBox(10, headerInfo);
        headerBox.setPadding(new Insets(15, 20, 15, 20));
        headerBox.setStyle("-fx-background-color: white; -fx-border-color: transparent transparent #d9dce1 transparent;");

        messageContainer = new VBox(12);
        messageContainer.setPadding(new Insets(20));
        messageScroll = new ScrollPane(messageContainer);
        messageScroll.setFitToWidth(true);

        // Ô nhập liệu và nút chức năng
        HBox bottom = new HBox(10);
        bottom.setPadding(new Insets(15, 20, 15, 20));
        bottom.setAlignment(Pos.CENTER_LEFT);

        Button btnImage = new Button("📷"); // CHỨC NĂNG 1: GỬI ẢNH
        btnImage.setStyle("-fx-background-color: transparent; -fx-font-size: 18px; -fx-cursor: hand;");
        btnImage.setOnAction(e -> sendImage(stage));

        messageField = new TextField();
        messageField.setPromptText("Nhập tin nhắn...");
        messageField.setStyle("-fx-background-radius: 20; -fx-padding: 10;");
        HBox.setHgrow(messageField, Priority.ALWAYS);

        // CHỨC NĂNG 2: TYPING INDICATOR (Đang gõ...)
        messageField.setOnKeyTyped(e -> broadcastTyping(true));
        
        Button btnSend = new Button("➤");
        btnSend.setOnAction(e -> sendMessage());
        messageField.setOnAction(e -> sendMessage());

        bottom.getChildren().addAll(btnImage, messageField, btnSend);
        center.setTop(headerBox);
        center.setCenter(messageScroll);
        center.setBottom(bottom);
        return center;
    }

    // --- XỬ LÝ GỬI ẢNH ---
    private void sendImage(Stage stage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.gif"));
        File file = fileChooser.showOpenDialog(stage);
        if (file != null) {
            try {
                byte[] imageBytes = Files.readAllBytes(file.toPath());
                String base64Image = Base64.getEncoder().encodeToString(imageBytes);
                String target = chatHeaderName.getText();
                String msg = "IMG_MSG:" + username + ":" + base64Image;
                
                if (target.equals("Phòng chat chung")) {
                    broadcastUdp(msg);
                } else {
                    Integer p = friendPorts.get(target);
                    if (p != null) sendUdpRaw(msg, p);
                }
                displayMessage(username, file.toURI().toString(), true, true);
                saveHistory(target, username, "[Hình ảnh]");
            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    // --- XỬ LÝ LƯU LỊCH SỬ (CHỨC NĂNG 3) ---
    private void saveHistory(String chatRoom, String sender, String content) {
        try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(HISTORY_DIR + chatRoom + ".txt", true)))) {
            out.println(sender + ": " + content);
        } catch (IOException e) { e.printStackTrace(); }
    }

    private void loadHistory(String chatRoom) {
        Path path = Paths.get(HISTORY_DIR + chatRoom + ".txt");
        if (Files.exists(path)) {
            try {
                List<String> lines = Files.readAllLines(path);
                for (String line : lines) {
                    String[] parts = line.split(": ", 2);
                    if (parts.length == 2) {
                        displayMessage(parts[0], parts[1], parts[0].equals(username), false);
                    }
                }
            } catch (IOException e) { e.printStackTrace(); }
        }
    }

    private void broadcastTyping(boolean isTyping) {
        String target = chatHeaderName.getText();
        if (!target.equals("Phòng chat chung")) {
            Integer p = friendPorts.get(target);
            if (p != null) sendUdpRaw("TYPING:" + username + ":" + isTyping, p);
        }
    }

    private void displayMessage(String sender, String content, boolean isMe, boolean isImage) {
        Platform.runLater(() -> {
            javafx.scene.Node node;
            if (isImage) {
                ImageView iv = new ImageView(new Image(content));
                iv.setFitWidth(200); iv.setPreserveRatio(true);
                node = iv;
            } else {
                Label lbl = new Label(content);
                lbl.setWrapText(true); lbl.setMaxWidth(400);
                lbl.setStyle(isMe ? "-fx-background-color: #0084ff; -fx-text-fill: white; -fx-background-radius: 18; -fx-padding: 10;" : "-fx-background-color: #e4e6eb; -fx-padding: 10; -fx-background-radius: 18;");
                node = lbl;
            }
            HBox hb = new HBox(10, isMe ? node : new HBox(5, createAvatar(sender), node));
            hb.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
            messageContainer.getChildren().add(hb);
            scrollToBottom();
        });
    }

    private void sendMessage() {
        String text = messageField.getText().trim();
        if (text.isEmpty()) return;
        String target = chatHeaderName.getText();
        String msg = "TEXT_MSG:" + username + ":" + text;
        
        if (target.equals("Phòng chat chung")) broadcastUdp(msg);
        else {
            Integer p = friendPorts.get(target);
            if (p != null) sendUdpRaw(msg, p);
        }
        
        displayMessage(username, text, true, false);
        saveHistory(target, username, text);
        messageField.clear();
        broadcastTyping(false);
    }

    // --- UDP RECEIVER CẬP NHẬT ---
    private void startUdpReceiver() {
        new Thread(() -> {
            try {
                while (udpSocket == null) {
                    try { udpSocket = new java.net.DatagramSocket(currentUdpPort); } catch (Exception e) { currentUdpPort++; }
                }
                byte[] buffer = new byte[65507];
                while (true) {
                    java.net.DatagramPacket packet = new java.net.DatagramPacket(buffer, buffer.length);
                    udpSocket.receive(packet);
                    String data = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                    
                    if (data.startsWith("TEXT_MSG:")) {
                        String[] p = data.split(":", 3);
                        displayMessage(p[1], p[2], false, false);
                        saveHistory(p[1], p[1], p[2]);
                    } else if (data.startsWith("IMG_MSG:")) {
                        String[] p = data.split(":", 3);
                        byte[] imgBytes = Base64.getDecoder().decode(p[2]);
                        File tempFile = File.createTempFile("chat_img", ".png");
                        Files.write(tempFile.toPath(), imgBytes);
                        displayMessage(p[1], tempFile.toURI().toString(), false, true);
                    } else if (data.startsWith("TYPING:")) {
                        String[] p = data.split(":");
                        Platform.runLater(() -> typingLabel.setText(p[2].equals("true") ? p[1] + " đang soạn tin..." : ""));
                    } else if (data.startsWith("LOGIN:")) {
                        String[] p = data.split(":");
                        if (!p[1].equals(username)) {
                            friendPorts.put(p[1], Integer.parseInt(p[2]));
                            updateFriendList(p[1]);
                            sendUdpRaw("REPLY_LOGIN:" + username + ":" + currentUdpPort, Integer.parseInt(p[2]));
                        }
                    } else if (data.startsWith("REPLY_LOGIN:")) {
                        String[] p = data.split(":");
                        friendPorts.put(p[1], Integer.parseInt(p[2]));
                        updateFriendList(p[1]);
                    }
                }
            } catch (Exception e) {}
        }).start();
    }

    // --- CÁC HÀM HỖ TRỢ KHÁC ---
    private void broadcastUdp(String msg) {
        for (int p : friendPorts.values()) sendUdpRaw(msg, p);
    }

    private void sendUdpRaw(String msg, int port) {
        try {
            byte[] data = msg.getBytes(StandardCharsets.UTF_8);
            udpSocket.send(new java.net.DatagramPacket(data, data.length, java.net.InetAddress.getByName("127.0.0.1"), port));
        } catch (Exception e) {}
    }

    private VBox createLeftPanel() {
        VBox left = new VBox(15);
        left.setPadding(new Insets(20));
        left.setPrefWidth(300);
        left.setStyle("-fx-background-color: white;");
        
        friendList = new ListView<>();
        friendList.getItems().add("Phòng chat chung");
        friendList.getSelectionModel().selectedItemProperty().addListener((obs, old, newV) -> {
            if (newV != null) {
                chatHeaderName.setText(newV);
                messageContainer.getChildren().clear();
                loadHistory(newV); // Tải lại lịch sử khi đổi phòng
            }
        });
        
        left.getChildren().addAll(new Label("BẠN BÈ"), friendList);
        return left;
    }

    private StackPane createAvatar(String name) {
        Circle circle = new Circle(15, Color.web("#1877f2"));
        Label lbl = new Label(name.substring(0, 1).toUpperCase());
        lbl.setTextFill(Color.WHITE);
        return new StackPane(circle, lbl);
    }

    private void updateFriendList(String name) {
        Platform.runLater(() -> { if (!friendList.getItems().contains(name)) friendList.getItems().add(name); });
    }

    private void autoStartConnection() {
        new Thread(() -> { startUdpReceiver(); try { Thread.sleep(500); for(int p=6000;p<=6010;p++) sendUdpRaw("LOGIN:"+username+":"+currentUdpPort, p); } catch(Exception e){} }).start();
    }

    private void scrollToBottom() { Platform.runLater(() -> messageScroll.setVvalue(1.0)); }
    public static void main(String[] args) { launch(args); }
}