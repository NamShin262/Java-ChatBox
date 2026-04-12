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
    private Map<String, String> lastMessages = new HashMap<>(); 
    private Map<String, Integer> unreadCounts = new HashMap<>(); 
    private Map<String, List<String>> groupMembers = new HashMap<>();

    private String username = "Guest";
    private int currentUdpPort = 6000;
    private DatagramSocket udpSocket;
    private final String HISTORY_DIR = "chat_history/";

    @Override
    public void start(Stage primaryStage) {
        try { Files.createDirectories(Paths.get(HISTORY_DIR)); } catch (IOException e) {}
        showDetailedLoginScreen(primaryStage);
        primaryStage.setOnCloseRequest(event -> Platform.exit());
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

        StackPane logo = new StackPane(new Circle(38, Color.web("#f1faee")), new Label("V"));
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
                username = nameField.getText().trim().toLowerCase();
                switchToMainChat(stage);
            }
        });

        loginCard.getChildren().addAll(logo, new Label("VẠN TÍN MESSENGER"), nameField, btnLogin);
        root.getChildren().add(loginCard);
        stage.setScene(new Scene(root, 1100, 750));
        stage.setTitle("Vạn Tín Messenger - " + username);
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
        
        btnCreateGroup.setOnAction(e -> {
            TextInputDialog nameDialog = new TextInputDialog("");
            nameDialog.setTitle("Tạo Nhóm Mới");
            nameDialog.setHeaderText("Bước 1: Nhập tên nhóm");
            Optional<String> nameResult = nameDialog.showAndWait();

            nameResult.ifPresent(groupName -> {
                String gName = groupName.trim();
                if (gName.isEmpty()) return;

                Dialog<List<String>> memberDialog = new Dialog<>();
                memberDialog.setTitle("Chọn thành viên cho " + gName);
                ButtonType okBtn = new ButtonType("Tạo Nhóm", ButtonBar.ButtonData.OK_DONE);
                memberDialog.getDialogPane().getButtonTypes().addAll(okBtn, ButtonType.CANCEL);

                VBox memberBox = new VBox(10);
                memberBox.setPadding(new Insets(10));
                List<CheckBox> checkBoxes = new ArrayList<>();
                for (String friend : friendList.getItems()) {
                    if (!friend.startsWith("GROUP:")) {
                        CheckBox cb = new CheckBox(friend);
                        checkBoxes.add(cb);
                        memberBox.getChildren().add(cb);
                    }
                }
                memberDialog.getDialogPane().setContent(new ScrollPane(memberBox));

                memberDialog.setResultConverter(btn -> {
                    if (btn == okBtn) {
                        List<String> selected = new ArrayList<>();
                        for (CheckBox cb : checkBoxes) if (cb.isSelected()) selected.add(cb.getText());
                        return selected;
                    }
                    return null;
                });

                Optional<List<String>> membersResult = memberDialog.showAndWait();
                membersResult.ifPresent(selected -> {
                    String fullGroupID = "GROUP:" + gName;
                    if (!friendList.getItems().contains(fullGroupID)) {
                        List<String> allMembers = new ArrayList<>(selected);
                        if(!allMembers.contains(username)) allMembers.add(username);
                        
                        groupMembers.put(fullGroupID, allMembers);
                        friendList.getItems().add(0, fullGroupID);
                        
                        String memberListStr = String.join(",", allMembers);
                        for (String m : allMembers) {
                            if (m.equalsIgnoreCase(username)) continue;
                            Integer p = friendPorts.get(m.toLowerCase());
                            if (p != null) sendUdpRaw("NEW_GROUP:" + gName + ":" + memberListStr, p);
                        }
                    }
                });
            });
        });

        friendList = new ListView<>();
        friendList.setCellFactory(lv -> new ListCell<String>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); }
                else {
                    HBox cell = new HBox(12); cell.setAlignment(Pos.CENTER_LEFT); cell.setPadding(new Insets(8, 5, 8, 5));
                    StackPane avt = createAvatar(item);
                    VBox info = new VBox(2);
                    
                    // --- XÓA CHỮ GROUP: KHI HIỂN THỊ TRÊN LIST ---
                    String displayName = item.startsWith("GROUP:") ? item.substring(6) : item;
                    Label name = new Label(displayName); 
                    name.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
                    
                    info.getChildren().add(name);
                    String last = lastMessages.get(item);
                    if (last != null) {
                        Label msg = new Label(last); msg.setStyle("-fx-text-fill: #65676b; -fx-font-size: 12px;");
                        msg.setPrefWidth(150); info.getChildren().add(msg);
                    }
                    Region s = new Region(); HBox.setHgrow(s, Priority.ALWAYS);
                    StackPane badge = new StackPane();
                    int count = unreadCounts.getOrDefault(item, 0);
                    if (count > 0) {
                        Circle c = new Circle(10, Color.RED);
                        Label l = new Label(String.valueOf(count));
                        l.setStyle("-fx-text-fill: white; -fx-font-size: 10px; -fx-font-weight: bold;");
                        badge.getChildren().addAll(c, l);
                    }
                    cell.getChildren().addAll(avt, info, s, badge);
                    setGraphic(cell);
                }
            }
        });

        friendList.getSelectionModel().selectedItemProperty().addListener((obs, old, newV) -> {
            if (newV != null) {
                unreadCounts.put(newV, 0); friendList.refresh();
                updateHeaderInfo(newV);
                messageContainer.getChildren().clear();
                loadHistory(newV); 
            }
        });

        left.getChildren().addAll(profile, btnCreateGroup, new Label("HỘI THOẠI"), friendList);
        return left;
    }

    private BorderPane createCenterPanel(Stage stage) {
        BorderPane center = new BorderPane();
        chatHeaderName = new Label("Vạn Tín Messenger");
        chatHeaderName.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        typingLabel = new Label(""); 
        headerBox = new HBox(15, new VBox(2, chatHeaderName, typingLabel));
        headerBox.setAlignment(Pos.CENTER_LEFT);
        headerBox.setPadding(new Insets(15, 25, 15, 25));
        headerBox.setStyle("-fx-background-color: white; -fx-border-color: transparent transparent #d9dce1 transparent;");
        
        messageContainer = new VBox(15); messageContainer.setPadding(new Insets(20));
        messageScroll = new ScrollPane(messageContainer); messageScroll.setFitToWidth(true);
        messageScroll.setStyle("-fx-background: #f0f2f5; -fx-background-color: #f0f2f5;");
        
        HBox bottom = new HBox(12); bottom.setPadding(new Insets(15, 25, 15, 25));
        bottom.setStyle("-fx-background-color: white;");
        
        messageField = new TextField();
        messageField.setPromptText("Nhập tin nhắn...");
        messageField.setStyle("-fx-background-radius: 20; -fx-padding: 10 15; -fx-background-color: #f0f2f5;");
        HBox.setHgrow(messageField, Priority.ALWAYS);

        Button btnSend = new Button("➤"); 
        btnSend.setStyle("-fx-text-fill: #1877f2; -fx-font-size: 22px; -fx-background-color: transparent;");
        btnSend.setOnAction(e -> sendMessage());
        messageField.setOnAction(e -> sendMessage());

        bottom.getChildren().addAll(new Button("📎"), messageField, btnSend);
        center.setTop(headerBox); center.setCenter(messageScroll); center.setBottom(bottom);
        return center;
    }

    private void handleIncoming(String room, String sender, String msg, boolean isMe) {
        Platform.runLater(() -> {
            saveHistory(room, sender, msg); 
            lastMessages.put(room, (isMe ? "Bạn: " : sender + ": ") + msg);
            
            // Tìm tên hiển thị hiện tại ở Header để so sánh logic
            String currentDisplay = chatHeaderName.getText();
            String roomDisplay = room.startsWith("GROUP:") ? room.substring(6) : room;

            if (!isMe && !currentDisplay.equalsIgnoreCase(roomDisplay)) {
                unreadCounts.put(room, unreadCounts.getOrDefault(room, 0) + 1);
            }
            friendList.refresh();
            if (currentDisplay.equalsIgnoreCase(roomDisplay)) displayMessage(sender, msg, isMe);
        });
    }

    private void sendMessage() {
        String t = messageField.getText().trim(); if (t.isEmpty()) return;
        
        // Lấy lại ID thật (có GROUP:) từ danh sách đang chọn
        String target = friendList.getSelectionModel().getSelectedItem();
        if (target == null || target.equals("Vạn Tín Messenger")) return;
        
        if (target.startsWith("GROUP:")) {
            List<String> members = groupMembers.get(target);
            if (members != null) {
                for (String m : members) {
                    if (m.equalsIgnoreCase(username)) continue;
                    Integer p = friendPorts.get(m.toLowerCase());
                    if (p != null) sendUdpRaw("GROUP_MSG:" + username + ":" + target + ":" + t, p);
                }
            }
        } else {
            Integer p = friendPorts.get(target.toLowerCase());
            if (p != null) sendUdpRaw("TEXT_MSG:" + username + ":" + target + ":" + t, p);
        }
        handleIncoming(target, username, t, true);
        messageField.clear();
    }

    private void startUdpReceiver() {
        new Thread(() -> {
            try {
                while (udpSocket == null) { 
                    try { udpSocket = new DatagramSocket(currentUdpPort); } 
                    catch (Exception e) { currentUdpPort++; } 
                }
                while (true) {
                    byte[] buf = new byte[65507];
                    DatagramPacket p = new DatagramPacket(buf, buf.length);
                    udpSocket.receive(p);
                    String rawData = new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8).trim();
                    
                    if (rawData.startsWith("TEXT_MSG:")) {
                        String[] pts = rawData.split(":", 4);
                        if(pts.length >= 4 && pts[2].trim().equalsIgnoreCase(username)) {
                            String sender = pts[1].trim();
                            friendPorts.put(sender.toLowerCase(), p.getPort());
                            updateFriendList(sender);
                            handleIncoming(sender, sender, pts[3], false);
                        }
                    } else if (rawData.startsWith("GROUP_MSG:")) {
                        String[] pts = rawData.split(":", 4);
                        if(pts.length >= 4) {
                            String sender = pts[1].trim();
                            String groupID = pts[2].trim();
                            
                            // Tự động thêm vào list nếu chưa có
                            Platform.runLater(() -> {
                                if(!friendList.getItems().contains(groupID)) {
                                    friendList.getItems().add(0, groupID);
                                }
                            });
                            
                            handleIncoming(groupID, sender, pts[3], false);
                        }
                    } else if (rawData.startsWith("NEW_GROUP:")) {
                        String[] pts = rawData.split(":", 3);
                        if (pts.length >= 3) {
                            String gName = "GROUP:" + pts[1];
                            List<String> mList = Arrays.asList(pts[2].split(","));
                            groupMembers.put(gName, mList);
                            Platform.runLater(() -> { 
                                if(!friendList.getItems().contains(gName)) {
                                    friendList.getItems().add(0, gName);
                                }
                            });
                        }
                    } else if (rawData.startsWith("LOGIN:")) {
                        String[] pts = rawData.split(":");
                        if (pts.length >= 3) {
                            String sender = pts[1].trim();
                            if (!sender.equalsIgnoreCase(username)) {
                                friendPorts.put(sender.toLowerCase(), Integer.parseInt(pts[2]));
                                updateFriendList(sender);
                                sendUdpRaw("REPLY_LOGIN:"+username+":"+currentUdpPort, Integer.parseInt(pts[2]));
                            }
                        }
                    } else if (rawData.startsWith("REPLY_LOGIN:")) {
                        String[] pts = rawData.split(":"); 
                        if(pts.length >= 3) {
                            String sender = pts[1].trim();
                            friendPorts.put(sender.toLowerCase(), Integer.parseInt(pts[2]));
                            updateFriendList(sender);
                        }
                    }
                }
            } catch (Exception e) {}
        }).start();
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
        String fileName = getChatFileName(room);
        try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(HISTORY_DIR + fileName, true)))) {
            out.println(sender + "|SEP|" + msg);
        } catch (IOException e) {}
    }

    private void loadHistory(String room) {
        String fileName = getChatFileName(room);
        Path p = Paths.get(HISTORY_DIR + fileName);
        if (Files.exists(p)) {
            try { Files.readAllLines(p).forEach(l -> {
                String[] pts = l.split("\\|SEP\\|", 2);
                if (pts.length == 2) displayMessage(pts[0], pts[1], pts[0].equalsIgnoreCase(username));
            }); } catch (IOException e) {}
        }
    }

    private String getChatFileName(String target) {
        if (target.startsWith("GROUP:")) return target.replace(":", "_") + ".txt";
        String u1 = username.toLowerCase().trim();
        String u2 = target.toLowerCase().trim();
        return (u1.compareTo(u2) < 0) ? u1 + "_" + u2 + ".txt" : u2 + "_" + u1 + ".txt";
    }

    private void updateHeaderInfo(String name) {
        headerBox.getChildren().clear(); 
        // --- XÓA CHỮ GROUP: KHI HIỆN TRÊN HEADER ---
        String displayName = name.startsWith("GROUP:") ? name.substring(6) : name;
        chatHeaderName.setText(displayName);
        
        VBox v = new VBox(2, chatHeaderName, typingLabel);
        StackPane avt = createAvatar(name); ((Circle)avt.getChildren().get(0)).setRadius(20);
        headerBox.getChildren().addAll(avt, v);
    }

    private StackPane createAvatar(String name) {
        Circle circle = new Circle(18);
        String[] colors = {"#f44336", "#9c27b0", "#3f51b5", "#4caf50", "#1877f2", "#ff9800"};
        circle.setFill(Color.web(colors[Math.abs(name.toLowerCase().hashCode()) % colors.length]));
        Label letter = new Label(name.length() > 0 ? (name.startsWith("GROUP:") ? "G" : name.substring(0,1).toUpperCase()) : "?");
        letter.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
        return new StackPane(circle, letter);
    }

    private void sendUdpRaw(String m, int p) {
        try { byte[] d = m.getBytes(StandardCharsets.UTF_8); udpSocket.send(new DatagramPacket(d, d.length, InetAddress.getByName("127.0.0.1"), p)); } catch (Exception e) {}
    }
    
    private void updateFriendList(String n) { 
        Platform.runLater(() -> { 
            String lowerN = n.toLowerCase();
            if (!friendList.getItems().contains(lowerN) && !lowerN.equals(username)) {
                friendList.getItems().add(lowerN); 
            }
        }); 
    }
    
    private void autoStartConnection() {
        new Thread(() -> { 
            startUdpReceiver(); 
            try { Thread.sleep(800); for(int p=6000; p<=6015; p++) sendUdpRaw("LOGIN:"+username+":"+currentUdpPort, p); } catch(Exception e){} 
        }).start();
    }

    public static void main(String[] args) { launch(args); }
}