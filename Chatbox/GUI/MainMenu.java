package Chatbox.GUI;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
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
    private Label typingLabel; 
    private Map<String, Integer> friendPorts = new HashMap<>(); 
    private String username = "Guest";
    private int currentUdpPort = 6000;
    private int currentTcpPort = 7000; 
    private DatagramSocket udpSocket;
    private final String HISTORY_DIR = "chat_history/";

    @Override
    public void start(Stage primaryStage) {
        try { Files.createDirectories(Paths.get(HISTORY_DIR)); } catch (IOException e) {}
        showDetailedLoginScreen(primaryStage);
        // Khi tắt app bằng nút X đỏ, xóa sạch lịch sử theo đúng ý Nam
        primaryStage.setOnCloseRequest(event -> cleanupHistory());
    }

    private void cleanupHistory() {
        try {
            File folder = new File(HISTORY_DIR);
            File[] files = folder.listFiles();
            if (files != null) { for (File f : files) f.delete(); }
        } catch (Exception e) {}
    }

    private void showDetailedLoginScreen(Stage stage) {
        VBox root = new VBox();
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-background-color: linear-gradient(to bottom right, #1d3557, #457b9d, #1d3557);");

        VBox loginCard = new VBox(25);
        loginCard.setAlignment(Pos.CENTER);
        loginCard.setPadding(new Insets(45));
        loginCard.setMaxWidth(390);
        loginCard.setStyle("-fx-background-color: rgba(255, 255, 255, 0.1); -fx-background-radius: 25; -fx-border-color: rgba(255, 255, 255, 0.2); -fx-border-radius: 25;");

        StackPane logo = new StackPane(new Circle(38, Color.web("#f1faee")), new Label("N"));
        ((Label)logo.getChildren().get(1)).setStyle("-fx-text-fill: #1d3557; -fx-font-size: 36px; -fx-font-weight: 900;");

        TextField nameField = new TextField();
        nameField.setPromptText("Tên nhân viên...");
        nameField.setPrefHeight(52);
        nameField.setStyle("-fx-background-radius: 14;");

        Button btnLogin = new Button("KẾT NỐI HỆ THỐNG");
        btnLogin.setMaxWidth(Double.MAX_VALUE);
        btnLogin.setPrefHeight(52);
        btnLogin.setStyle("-fx-background-color: #1877f2; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 14;");

        btnLogin.setOnAction(e -> {
            if (!nameField.getText().trim().isEmpty()) {
                username = nameField.getText().trim();
                switchToMainChat(stage);
            }
        });

        loginCard.getChildren().addAll(logo, new Label("HỆ THỐNG VẠN TÍN"), nameField, btnLogin);
        root.getChildren().add(loginCard);
        stage.setScene(new Scene(root, 1100, 750));
        stage.setTitle("VẠN TÍN MESSENGER - LOGIN");
        stage.show();
    }

    private void switchToMainChat(Stage stage) {
        BorderPane root = new BorderPane();
        root.setCenter(createCenterPanel(stage));
        root.setLeft(createLeftPanel());
        stage.getScene().setRoot(root);
        autoStartConnection();
    }

    private VBox createLeftPanel() {
        VBox left = new VBox(15);
        left.setPadding(new Insets(20, 15, 15, 15));
        left.setPrefWidth(320);
        left.setStyle("-fx-background-color: white; -fx-border-color: transparent #d9dce1 transparent transparent;");

        HBox profile = new HBox(12, createAvatar(username), new VBox(new Label(username), new Label("Đang hoạt động")));
        ((Label)((VBox)profile.getChildren().get(1)).getChildren().get(1)).setStyle("-fx-text-fill: #31a24c; -fx-font-size: 12px;");
        ((Label)((VBox)profile.getChildren().get(1)).getChildren().get(0)).setStyle("-fx-font-weight: bold; -fx-font-size: 15px;");

        Button btnCreateGroup = new Button("+ Tạo Nhóm Mới");
        btnCreateGroup.setMaxWidth(Double.MAX_VALUE);
        btnCreateGroup.setPrefHeight(40);
        btnCreateGroup.setStyle("-fx-background-color: #1877f2; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8;");

        Label sectionTitle = new Label("HỘI THOẠI");
        sectionTitle.setStyle("-fx-text-fill: #65676b; -fx-font-weight: bold; -fx-font-size: 13px;");

        friendList = new ListView<>();
        friendList.getItems().add("Phòng chat chung");
        friendList.setCellFactory(lv -> new ListCell<String>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); }
                else {
                    HBox hb = new HBox(10); hb.setAlignment(Pos.CENTER_LEFT); hb.setPadding(new Insets(8, 5, 8, 5));
                    if (item.equals("Phòng chat chung")) {
                        Label lbl = new Label(item); lbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #1877f2;");
                        hb.getChildren().add(lbl);
                    } else { hb.getChildren().addAll(createAvatar(item), new Label(item)); }
                    setGraphic(hb);
                }
            }
        });

        friendList.getSelectionModel().selectedItemProperty().addListener((obs, old, newV) -> {
            if (newV != null) {
                updateHeaderInfo(newV);
                messageContainer.getChildren().clear();
                loadHistory(newV);
            }
        });

        left.getChildren().addAll(profile, btnCreateGroup, sectionTitle, friendList);
        return left;
    }

    private BorderPane createCenterPanel(Stage stage) {
        BorderPane center = new BorderPane();
        chatHeaderName = new Label("Phòng chat chung");
        chatHeaderName.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        typingLabel = new Label(""); 
        typingLabel.setStyle("-fx-text-fill: #31a24c; -fx-font-style: italic;");

        headerBox = new HBox(15, new VBox(2, chatHeaderName, typingLabel));
        headerBox.setAlignment(Pos.CENTER_LEFT);
        headerBox.setPadding(new Insets(15, 25, 15, 25));
        headerBox.setStyle("-fx-background-color: white; -fx-border-color: transparent transparent #d9dce1 transparent;");

        messageContainer = new VBox(15); messageContainer.setPadding(new Insets(20));
        messageScroll = new ScrollPane(messageContainer); messageScroll.setFitToWidth(true);
        messageScroll.setStyle("-fx-background: #f0f2f5; -fx-background-color: #f0f2f5;");

        HBox bottom = new HBox(12); bottom.setPadding(new Insets(15, 25, 15, 25));
        bottom.setStyle("-fx-background-color: white;");

        Button btnFile = new Button("📎"); 
        btnFile.setStyle("-fx-font-size: 20px; -fx-background-color: transparent; -fx-cursor: hand;");
        btnFile.setOnAction(e -> sendFileViaTcp(stage));

        messageField = new TextField();
        messageField.setPromptText("Nhập tin nhắn...");
        messageField.setStyle("-fx-background-radius: 20; -fx-padding: 10 15; -fx-background-color: #f0f2f5;");
        HBox.setHgrow(messageField, Priority.ALWAYS);
        messageField.setOnKeyTyped(e -> broadcastTyping(true));

        Button btnSend = new Button("➤"); 
        btnSend.setStyle("-fx-text-fill: #1877f2; -fx-font-size: 22px; -fx-background-color: transparent;");
        btnSend.setOnAction(e -> sendMessage());
        messageField.setOnAction(e -> sendMessage());

        bottom.getChildren().addAll(btnFile, messageField, btnSend);
        center.setTop(headerBox); center.setCenter(messageScroll); center.setBottom(bottom);
        return center;
    }

    // ==========================================
    // MODULE UDP: Chat & Bắt tay (Handshake)
    // ==========================================
    private void startUdpReceiver() {
        new Thread(() -> {
            try {
                while (udpSocket == null) { 
                    try { udpSocket = new DatagramSocket(currentUdpPort); } 
                    catch (Exception e) { currentUdpPort++; currentTcpPort++; } 
                }
                startTcpFileServer(); 
                byte[] buf = new byte[65507];
                while (true) {
                    DatagramPacket p = new DatagramPacket(buf, buf.length);
                    udpSocket.receive(p);
                    String data = new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8);
                    
                    if (data.startsWith("TEXT_MSG:")) {
                        String[] pts = data.split(":", 3);
                        updateFriendList(pts[1]); handleIncoming(pts[1], pts[1], pts[2], false);
                    } else if (data.startsWith("LOGIN:")) {
                        String[] pts = data.split(":");
                        if (!pts[1].equals(username)) {
                            friendPorts.put(pts[1], Integer.parseInt(pts[2]));
                            updateFriendList(pts[1]); // Nam thấy Hùng
                            sendUdpRaw("REPLY_LOGIN:"+username+":"+currentUdpPort, Integer.parseInt(pts[2])); // Báo cho Hùng
                        }
                    } else if (data.startsWith("REPLY_LOGIN:")) {
                        String[] pts = data.split(":"); 
                        friendPorts.put(pts[1], Integer.parseInt(pts[2]));
                        updateFriendList(pts[1]); // Hùng thấy Nam
                    } else if (data.startsWith("TYPING:")) {
                        String[] pts = data.split(":");
                        Platform.runLater(() -> typingLabel.setText(pts[2].equals("true") && chatHeaderName.getText().equals(pts[1]) ? pts[1]+" đang soạn tin..." : ""));
                    }
                }
            } catch (Exception e) {}
        }).start();
    }

    // ==========================================
    // MODULE TCP: Truyền File (Max 1đ)
    // ==========================================
    private void startTcpFileServer() {
        new Thread(() -> {
            try (ServerSocket server = new ServerSocket(currentTcpPort)) {
                while (true) {
                    try (Socket s = server.accept(); DataInputStream dis = new DataInputStream(s.getInputStream())) {
                        String sender = dis.readUTF();
                        String fileName = dis.readUTF();
                        long fileSize = dis.readLong();
                        File fld = new File("downloads"); if (!fld.exists()) fld.mkdir();
                        File file = new File(fld, fileName);
                        try (FileOutputStream fos = new FileOutputStream(file)) {
                            byte[] b = new byte[4096]; int read;
                            while (fileSize > 0 && (read = dis.read(b, 0, (int)Math.min(b.length, fileSize))) != -1) {
                                fos.write(b, 0, read); fileSize -= read;
                            }
                        }
                        handleIncoming(sender, sender, "[Đã nhận file: " + fileName + "]", false);
                    }
                }
            } catch (Exception e) {}
        }).start();
    }

    private void sendFileViaTcp(Stage stage) {
        String target = chatHeaderName.getText();
        if (target.equals("Phòng chat chung") || !friendPorts.containsKey(target)) return;
        FileChooser fc = new FileChooser(); File f = fc.showOpenDialog(stage);
        if (f != null) {
            new Thread(() -> {
                try (Socket s = new Socket("127.0.0.1", friendPorts.get(target) + 1000); 
                     DataOutputStream dos = new DataOutputStream(s.getOutputStream())) {
                    dos.writeUTF(username); dos.writeUTF(f.getName()); dos.writeLong(f.length());
                    Files.copy(f.toPath(), dos);
                    handleIncoming(target, username, "[Đã gửi file: " + f.getName() + "]", true);
                } catch (Exception e) {}
            }).start();
        }
    }

    private void handleIncoming(String room, String sender, String msg, boolean isMe) {
        Platform.runLater(() -> {
            saveHistory(room, sender, msg);
            if (chatHeaderName.getText().equals(room)) displayMessage(sender, msg, isMe);
        });
    }

    private void displayMessage(String sender, String content, boolean isMe) {
        Label lbl = new Label(content); lbl.setWrapText(true); lbl.setMaxWidth(400);
        lbl.setStyle(isMe ? "-fx-background-color: #0084ff; -fx-text-fill: white; -fx-background-radius: 18 18 2 18; -fx-padding: 10 15;" 
                          : "-fx-background-color: #e4e6eb; -fx-text-fill: black; -fx-background-radius: 18 18 18 2; -fx-padding: 10 15;");
        StackPane avt = createAvatar(sender); ((Circle)avt.getChildren().get(0)).setRadius(15);
        HBox hb = isMe ? new HBox(10, lbl, avt) : new HBox(10, avt, lbl);
        hb.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        messageContainer.getChildren().add(hb);
        Platform.runLater(() -> messageScroll.setVvalue(1.0));
    }

    private void saveHistory(String room, String sender, String msg) {
        try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(HISTORY_DIR + room + ".txt", true)))) {
            out.println(sender + "|SEP|" + msg);
        } catch (IOException e) {}
    }

    private void loadHistory(String room) {
        Path p = Paths.get(HISTORY_DIR + room + ".txt");
        if (Files.exists(p)) {
            try { Files.readAllLines(p).forEach(l -> {
                String[] pts = l.split("\\|SEP\\|", 2);
                if (pts.length == 2) displayMessage(pts[0], pts[1], pts[0].equals(username));
            }); } catch (IOException e) {}
        }
    }

    private void sendMessage() {
        String t = messageField.getText().trim(); if (t.isEmpty()) return;
        String target = chatHeaderName.getText();
        if (!target.equals("Phòng chat chung")) {
            Integer p = friendPorts.get(target);
            if (p != null) sendUdpRaw("TEXT_MSG:" + username + ":" + t, p);
        } else { friendPorts.values().forEach(p -> sendUdpRaw("TEXT_MSG:" + username + ":" + t, p)); }
        displayMessage(username, t, true);
        saveHistory(target, username, t);
        messageField.clear(); broadcastTyping(false);
    }

    private void updateHeaderInfo(String name) {
        headerBox.getChildren().clear();
        chatHeaderName.setText(name);
        VBox v = new VBox(2, chatHeaderName, typingLabel);
        if (!name.equals("Phòng chat chung")) {
            StackPane avt = createAvatar(name); ((Circle)avt.getChildren().get(0)).setRadius(20);
            headerBox.getChildren().addAll(avt, v);
        } else { headerBox.getChildren().add(v); }
    }

    private StackPane createAvatar(String name) {
        Circle circle = new Circle(18);
        String[] colors = {"#f44336", "#9c27b0", "#3f51b5", "#4caf50", "#1877f2", "#ff9800"};
        circle.setFill(Color.web(colors[Math.abs(name.hashCode()) % colors.length]));
        Label letter = new Label(name.substring(0,1).toUpperCase());
        letter.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
        return new StackPane(circle, letter);
    }

    private void sendUdpRaw(String m, int p) {
        try { byte[] d = m.getBytes(StandardCharsets.UTF_8); udpSocket.send(new DatagramPacket(d, d.length, InetAddress.getByName("127.0.0.1"), p)); } catch (Exception e) {}
    }
    
    private void updateFriendList(String n) { 
        Platform.runLater(() -> { 
            if (!friendList.getItems().contains(n) && !n.equals(username)) friendList.getItems().add(n); 
        }); 
    }
    
    private void broadcastTyping(boolean is) {
        String target = chatHeaderName.getText();
        if (friendPorts.containsKey(target)) sendUdpRaw("TYPING:" + username + ":" + is, friendPorts.get(target));
    }
    
    private void autoStartConnection() {
        new Thread(() -> { 
            startUdpReceiver(); 
            try { 
                Thread.sleep(800); 
                for(int p=6000; p<=6010; p++) sendUdpRaw("LOGIN:"+username+":"+currentUdpPort, p); 
            } catch(Exception e){} 
        }).start();
    }

    public static void main(String[] args) { launch(args); }
}