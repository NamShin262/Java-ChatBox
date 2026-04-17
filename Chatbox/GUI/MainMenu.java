package Chatbox.GUI;

import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Base64;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.collections.ObservableList;
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
    private HBox inputBar;
    private Button attachButton;
    private VBox emptyStatePane;
    private ListView<String> friendList;
    private HBox headerBox; 
    private Label chatHeaderName;
    private VBox groupInfoPane;
    private Label groupNameLabel;
    private Label groupMemberCountLabel;
    private ListView<String> groupMemberList;
    private Button addMemberButton;
    private Button removeMemberButton;
    private Button viewMediaHistoryButton;
    private Button groupInfoToggleButton;

    private Map<String, List<javafx.scene.Node>> privateHistory = new HashMap<>(); 
    private Map<String, Integer> friendPorts = new HashMap<>(); 
    private Map<String, List<Integer>> myGroups = new HashMap<>(); 
    private Map<String, Integer> unreadCounts = new HashMap<>();
    private Map<String, String> latestIncomingPreview = new HashMap<>();
    private String currentChatRoom;
    
    public static String launchProtocol = "SELECT";
    private String username = "Guest";
    private String selectedProtocol = "UDP";
    private static final String FILE_PREFIX = "FILE:";
    private static final String IMAGE_PREFIX = "IMAGE:";
    private int currentUdpPort = 6000;
    private int currentTcpPort = 7000;
    private java.net.DatagramSocket udpSocket;
    private java.net.ServerSocket tcpServerSocket;

    @Override
    public void start(Stage primaryStage) {
        if ("TCP".equalsIgnoreCase(launchProtocol)) {
            selectedProtocol = "TCP";
        } else if ("UDP".equalsIgnoreCase(launchProtocol)) {
            selectedProtocol = "UDP";
        }
        showLoginScreen(primaryStage);
    }

    // --- LOGIC CHỌN BẠN BÈ VÀO NHÓM (GIỮ NGUYÊN UI HIỆN CÓ) ---
    private void createNewGroup() {
        if (friendPorts.isEmpty()) {
            new Alert(Alert.AlertType.INFORMATION, "Không có bạn bè online để tạo nhóm!").show();
            return;
        }

        TextInputDialog nameDialog = new TextInputDialog("Nhóm Mới");
        nameDialog.setTitle("Tạo Nhóm");
        nameDialog.setHeaderText("Đặt tên cho nhóm:");
        
        nameDialog.showAndWait().ifPresent(gName -> {
            Stage subStage = new Stage();
            VBox layout = new VBox(15);
            layout.setPadding(new Insets(20));
            layout.setStyle("-fx-background-color: white;");

            Label label = new Label("Mời thành viên:");
            label.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

            VBox memberSelection = new VBox(10);
            Map<String, CheckBox> boxes = new HashMap<>();
            for (String friend : friendPorts.keySet()) {
                CheckBox cb = new CheckBox(friend);
                boxes.put(friend, cb);
                memberSelection.getChildren().add(cb);
            }

            Button btnCreate = new Button("Xác nhận tạo");
            btnCreate.setMaxWidth(Double.MAX_VALUE);
            btnCreate.setStyle("-fx-background-color: #1877f2; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8;");
            
            btnCreate.setOnAction(e -> {
                List<Integer> selectedPorts = new ArrayList<>();
                selectedPorts.add(currentUdpPort);
                for (String f : boxes.keySet()) {
                    if (boxes.get(f).isSelected()) selectedPorts.add(friendPorts.get(f));
                }

                if (selectedPorts.size() < 2) {
                    new Alert(Alert.AlertType.WARNING, "Hãy chọn ít nhất 1 người bạn!").show();
                    return;
                }

                String portsStr = selectedPorts.stream().map(String::valueOf).collect(Collectors.joining(","));
                for (Integer p : selectedPorts) {
                    if (p != currentUdpPort) sendUdpRaw("GROUP_INVITE:" + gName + ":" + portsStr, p);
                }
                myGroups.put(gName, selectedPorts);
                updateFriendList(gName + " (Nhóm)");
                updateGroupInfo(gName);
                subStage.close();
            });

            layout.getChildren().addAll(label, new ScrollPane(memberSelection), btnCreate);
            subStage.setScene(new Scene(layout, 280, 400));
            subStage.show();
        });
    }

    // --- GIỮ NGUYÊN UI GỐC THEO YÊU CẦU ---
    private VBox createLeftPanel() {
        VBox left = new VBox(15);
        left.setPadding(new Insets(20, 10, 10, 10));
        left.setPrefWidth(300);
        left.setStyle("-fx-background-color: white; -fx-border-color: transparent #d9dce1 transparent transparent;");

        HBox profile = new HBox(12);
        profile.setAlignment(Pos.CENTER_LEFT);
        VBox info = new VBox(0, new Label(username), new Label("Đang hoạt động"));
        ((Label)info.getChildren().get(0)).setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");
        ((Label)info.getChildren().get(1)).setStyle("-fx-text-fill: #31a24c; -fx-font-size: 12px;");
        profile.getChildren().addAll(createAvatar(username), info);

        Button btnAddGroup = new Button("+ Tạo Nhóm Mới");
        btnAddGroup.setMaxWidth(Double.MAX_VALUE);
        btnAddGroup.setStyle("-fx-background-color: #1877f2; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 10; -fx-padding: 10;");
        btnAddGroup.setOnAction(e -> createNewGroup());

        Label title = new Label("DANH SÁCH BẠN BÈ");
        title.setStyle("-fx-font-size: 15px; -fx-font-weight: 900; -fx-text-fill: #1c1e21; -fx-letter-spacing: 1px; -fx-padding: 10 0 0 5;");

        friendList = new ListView<>();
        friendList.setCellFactory(p -> new ListCell<String>() {
            private final Label unreadBadge = new Label();
            private final VBox textBox = new VBox(2);
            private final HBox cell = new HBox(10);
            private final Region spacer = new Region();
            private final Label nameLabel = new Label();
            private final Label previewLabel = new Label();

            {
                cell.setAlignment(Pos.CENTER_LEFT);
                cell.setPadding(new Insets(10, 8, 10, 8));
                HBox.setHgrow(spacer, Priority.ALWAYS);
                unreadBadge.setMinSize(22, 22);
                unreadBadge.setAlignment(Pos.CENTER);
                unreadBadge.setStyle("-fx-background-color: #e53935; -fx-text-fill: white; -fx-font-size: 11px; -fx-font-weight: bold; -fx-background-radius: 999;");
                nameLabel.setStyle("-fx-font-weight: 600; -fx-font-size: 13px;");
                previewLabel.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 11px;");
            }

            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); setStyle("-fx-background-color: transparent;"); }
                else {
                    cell.getChildren().setAll(createAvatar(item));
                    textBox.getChildren().setAll(nameLabel, previewLabel);
                    cell.getChildren().addAll(textBox, spacer, unreadBadge);
                    nameLabel.setText(item);
                    String preview = latestIncomingPreview.get(item);
                    previewLabel.setText(preview == null || preview.isBlank() ? "" : preview);
                    int unread = unreadCounts.getOrDefault(item, 0);
                    unreadBadge.setText(unread > 0 ? String.valueOf(unread) : "");
                    unreadBadge.setVisible(unread > 0);
                    unreadBadge.setManaged(unread > 0);
                    setGraphic(cell);
                    selectedProperty().addListener((o, old, val) -> setStyle(val ? "-fx-background-color: #e7f3ff; -fx-background-radius: 12;" : "-fx-background-color: transparent;"));
                }
            }
        });
        friendList.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                if (oldV != null) privateHistory.put(oldV, new ArrayList<>(messageContainer.getChildren()));
                currentChatRoom = newV;
                unreadCounts.put(newV, 0);
                latestIncomingPreview.remove(newV);
                updateFriendList(newV);
                updateHeaderInfo(newV);
                messageContainer.getChildren().clear();
                if (privateHistory.containsKey(newV)) {
                    messageContainer.getChildren().addAll(privateHistory.get(newV));
                }
                emptyStatePane.setVisible(false);
                emptyStatePane.setManaged(false);
                messageScroll.setVisible(true);
                messageScroll.setManaged(true);
                inputBar.setVisible(true);
                inputBar.setManaged(true);
                messageScroll.setStyle("-fx-background-color: #ffffff; -fx-background: #ffffff; -fx-border-color: transparent; -fx-background-radius: 18;");
                scrollToBottom();
            }
        });
        left.getChildren().addAll(profile, btnAddGroup, title, friendList);
        return left;
    }

    // --- CÁC PHƯƠNG THỨC XỬ LÝ TIN NHẮN (ĐÃ FIX LỖI "UNDEFINED") ---
    private void handleIncomingMsg(String chatRoom, String sender, String content) {
        Platform.runLater(() -> {
            HBox hb = createMsgNode(sender, content, false);
            if (chatHeaderName.getText().equals(chatRoom)) {
                if (emptyStatePane != null) {
                    emptyStatePane.setVisible(false);
                    emptyStatePane.setManaged(false);
                }
                if (messageScroll != null) {
                    messageScroll.setVisible(true);
                    messageScroll.setManaged(true);
                    messageScroll.setStyle("-fx-background-color: #ffffff; -fx-background: #ffffff; -fx-border-color: transparent; -fx-background-radius: 18;");
                }
                if (inputBar != null) {
                    inputBar.setVisible(true);
                    inputBar.setManaged(true);
                }
                messageContainer.getChildren().add(hb);
                scrollToBottom();
            } else {
                privateHistory.computeIfAbsent(chatRoom, k -> new ArrayList<>()).add(hb);
                unreadCounts.put(chatRoom, unreadCounts.getOrDefault(chatRoom, 0) + 1);
                latestIncomingPreview.put(chatRoom, content);
                updateFriendList(chatRoom);
            }
        });
    }

    private HBox createMsgNode(String sender, String content, boolean isMe) {
        Label nameLabel = new Label(isMe ? "Bạn:" : "");
        nameLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #6b7280;");

        Label lbl = new Label(content);
        lbl.setWrapText(true);
        lbl.setMaxWidth(400);
        lbl.setStyle(isMe ? "-fx-background-color: #0084ff; -fx-text-fill: white; -fx-background-radius: 18 18 2 18; -fx-padding: 10 15;" 
                          : "-fx-background-color: #e4e6eb; -fx-text-fill: black; -fx-background-radius: 18 18 18 2; -fx-padding: 10 15;");

        VBox bubble = isMe ? new VBox(3, nameLabel, lbl) : new VBox(lbl);
        bubble.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        HBox hb = isMe ? new HBox(bubble) : new HBox(8, createAvatar(sender), bubble);
        if (!isMe) ((Circle)((StackPane)hb.getChildren().get(0)).getChildren().get(0)).setRadius(15);
        hb.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        return hb;
    }


    private void updateHeaderInfo(String name) {
        headerBox.getChildren().clear();
        chatHeaderName.setText(name);
        if (name != null && !name.isBlank() && !name.equals("Nam Tùng Chat")) {
            StackPane avt = createAvatar(name); ((Circle)avt.getChildren().get(0)).setRadius(18);
            headerBox.getChildren().add(avt);
        }
        headerBox.getChildren().add(chatHeaderName);
        if (groupInfoPane != null) {
            String groupName = name != null && name.endsWith(" (Nhóm)") ? name.replace(" (Nhóm)", "") : null;
            updateGroupInfo(groupName);
            boolean isGroup = groupName != null;
            groupInfoToggleButton.setVisible(isGroup);
            groupInfoToggleButton.setManaged(isGroup);
            if (!isGroup) {
                groupInfoPane.setVisible(false);
                groupInfoPane.setManaged(false);
            }
        }
    }

    private BorderPane createCenterPanel(Stage stage) {
        BorderPane center = new BorderPane();
        chatHeaderName = new Label("Nam Tùng Chat");
        chatHeaderName.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        headerBox = new HBox(10, chatHeaderName); headerBox.setAlignment(Pos.CENTER_LEFT);
        groupInfoToggleButton = new Button("i");
        groupInfoToggleButton.setStyle("-fx-background-color: #e7f3ff; -fx-text-fill: #1877f2; -fx-font-weight: bold; -fx-background-radius: 8; -fx-min-width: 34; -fx-min-height: 34;");
        groupInfoToggleButton.setVisible(false);
        groupInfoToggleButton.setManaged(false);
        groupInfoToggleButton.setOnAction(e -> toggleGroupInfoPane());

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        HBox top = new HBox(12, headerBox, headerSpacer, groupInfoToggleButton); top.setPadding(new Insets(15, 20, 15, 20));
        top.setStyle("-fx-background-color: white; -fx-border-color: transparent transparent #d9dce1 transparent;");

        messageContainer = new VBox(12); 
        messageContainer.setPadding(new Insets(20));
        messageScroll = new ScrollPane(messageContainer); 
        messageScroll.setFitToWidth(true);
        messageScroll.setStyle("-fx-background-color: #ffffff; -fx-background: #ffffff; -fx-border-color: transparent; -fx-background-radius: 18;");
        messageScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        messageScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        messageScroll.setPannable(true);

        emptyStatePane = new VBox(16);
        emptyStatePane.setAlignment(Pos.CENTER);
        emptyStatePane.setStyle("-fx-background-color: transparent;");
        Circle bigAvatar = new Circle(38, Color.web("#1d3557"));
        Label bigText = new Label("NT");
        bigText.setStyle("-fx-text-fill: white; -fx-font-size: 28px; -fx-font-weight: 900;");
        StackPane emptyLogo = new StackPane(bigAvatar, bigText);
        Label welcomeTitle = new Label("Chào mừng đến với Công Ty TNHH Nam Tùng");
        welcomeTitle.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #1d3557;");
        Label welcomeSub = new Label("Chọn một bạn bè hoặc nhóm ở bên trái để bắt đầu trò chuyện");
        welcomeSub.setStyle("-fx-font-size: 14px; -fx-text-fill: #5f6368;");
        emptyStatePane.getChildren().addAll(emptyLogo, welcomeTitle, welcomeSub);

        StackPane centerStack = new StackPane(emptyStatePane, messageScroll);
        messageScroll.setVisible(false);
        messageScroll.setManaged(false);

        inputBar = new HBox(10); 
        inputBar.setPadding(new Insets(15, 20, 15, 20));
        inputBar.setStyle("-fx-background-color: transparent;");
        messageField = new TextField();
        messageField.setPromptText("Nhập tin nhắn...");
        messageField.setStyle("-fx-background-color: #f0f2f5; -fx-background-radius: 20; -fx-padding: 10;");
        HBox.setHgrow(messageField, Priority.ALWAYS);
        attachButton = new Button("＋");
        attachButton.setStyle("-fx-text-fill: #1877f2; -fx-font-size: 18px; -fx-background-color: transparent;");
        Button btnSend = new Button("➤");
        btnSend.setStyle("-fx-text-fill: #1877f2; -fx-font-size: 20px; -fx-background-color: transparent;");
        btnSend.setOnAction(e -> sendMessage());
        messageField.setOnAction(e -> sendMessage());
        attachButton.setOnAction(e -> sendAttachment());
        inputBar.getChildren().addAll(attachButton, messageField, btnSend);

        groupInfoPane = createGroupInfoPane();
        groupInfoPane.setVisible(false);
        groupInfoPane.setManaged(false);
        groupInfoPane.setMinWidth(0);
        groupInfoPane.setPrefWidth(280);

        center.setTop(top);
        center.setCenter(centerStack);
        center.setBottom(inputBar);
        center.setStyle("-fx-background-color: #f6f8fc;");
        return center;
    }

    private String getCurrentGroupName() {
        if (chatHeaderName == null) return null;
        String title = chatHeaderName.getText();
        return title != null && title.endsWith(" (Nhóm)") ? title.replace(" (Nhóm)", "") : null;
    }

    private void toggleGroupInfoPane() {
        if (groupInfoPane == null || chatHeaderName == null) return;
        String title = chatHeaderName.getText();
        boolean isGroup = title != null && title.endsWith(" (Nhóm)");
        if (!isGroup) return;
        boolean show = !groupInfoPane.isVisible();
        groupInfoPane.setVisible(show);
        groupInfoPane.setManaged(show);
    }

    // --- GIAO TIẾP UDP ---
    private void startNetworkReceiver() {
        if ("TCP".equalsIgnoreCase(selectedProtocol)) {
            startTcpReceiver();
        } else {
            startUdpReceiver();
        }
    }

    private void startUdpReceiver() {
        new Thread(() -> {
            try {
                while (udpSocket == null && currentUdpPort < 6100) {
                    try { udpSocket = new java.net.DatagramSocket(currentUdpPort); } catch (Exception e) { currentUdpPort++; }
                }
                byte[] buffer = new byte[65507];
                while (true) {
                    java.net.DatagramPacket packet = new java.net.DatagramPacket(buffer, buffer.length);
                    udpSocket.receive(packet);
                    String data = new String(packet.getData(), 0, packet.getLength());
                    handleIncomingProtocolData(data);
                }
            } catch (Exception e) {}
        }).start();
    }

    private void startTcpReceiver() {
        new Thread(() -> {
            try {
                while (tcpServerSocket == null && currentTcpPort < 7010) {
                    try { tcpServerSocket = new java.net.ServerSocket(currentTcpPort); } catch (Exception e) { currentTcpPort++; }
                }
                while (true) {
                    java.net.Socket socket = tcpServerSocket.accept();
                    new Thread(() -> {
                        try (java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream()))) {
                            String line;
                            while ((line = in.readLine()) != null) handleIncomingProtocolData(line);
                        } catch (Exception ignored) {}
                    }).start();
                }
            } catch (Exception e) {}
        }).start();
    }

    private void handleIncomingProtocolData(String data) {
        if (data.startsWith("LOGIN:")) {
            String[] p = data.split(":");
            if (!p[1].equals(username)) {
                friendPorts.put(p[1], Integer.parseInt(p[2]));
                updateFriendList(p[1]);
                sendProtocolRaw("REPLY_LOGIN:" + username + ":" + currentPort(), Integer.parseInt(p[2]));
            }
        } else if (data.startsWith("REPLY_LOGIN:")) {
            String[] p = data.split(":");
            friendPorts.put(p[1], Integer.parseInt(p[2]));
            updateFriendList(p[1]);
        } else if (data.startsWith("GROUP_INVITE:")) {
            String[] p = data.split(":");
            List<Integer> ports = Arrays.stream(p[2].split(",")).map(Integer::parseInt).collect(Collectors.toList());
            myGroups.put(p[1], ports);
            updateFriendList(p[1] + " (Nhóm)");
            updateGroupInfo(p[1]);
        } else if (data.startsWith("GROUP_MSG:")) {
            String[] p = data.split(":", 4);
            String room = p[1].equals("CHAT_1_1") ? p[2] : p[1] + " (Nhóm)";
            String sender = p[2];
            String content = p[3];
            if (content.startsWith(FILE_PREFIX) || content.startsWith(IMAGE_PREFIX)) {
                handleAttachmentIncoming(room, sender, content);
            } else {
                handleIncomingMsg(room, sender, content);
            }
        }
    }

    private void handleAttachmentIncoming(String chatRoom, String sender, String content) {
        Platform.runLater(() -> {
            String[] head = content.split(":", 3);
            if (head.length < 3) return;
            boolean image = content.startsWith(IMAGE_PREFIX);
            String fileName = head[1];
            String label = (image ? "[Ảnh] " : "[File] ") + fileName;
            HBox hb = createMsgNode(sender, label, false);
            privateHistory.computeIfAbsent(chatRoom, k -> new ArrayList<>()).add(hb);
            if (chatHeaderName.getText().equals(chatRoom)) {
                messageContainer.getChildren().add(hb);
                scrollToBottom();
            } else {
                unreadCounts.put(chatRoom, unreadCounts.getOrDefault(chatRoom, 0) + 1);
                latestIncomingPreview.put(chatRoom, label);
                updateFriendList(chatRoom);
            }
        });
    }

    private void sendMessage() {
        String text = messageField.getText().trim();
        if (text.isEmpty()) return;
        String target = chatHeaderName.getText();
        if (target.endsWith("(Nhóm)")) {
            String gName = target.replace(" (Nhóm)", "");
            for (Integer p : myGroups.get(gName)) if (p != currentPort()) sendProtocolRaw("GROUP_MSG:" + gName + ":" + username + ":" + text, p);
            messageContainer.getChildren().add(createMsgNode(username, text, true));
        } else {
            Integer p = friendPorts.get(target);
            if (p != null) {
                sendProtocolRaw("GROUP_MSG:CHAT_1_1:" + username + ":" + text, p);
                messageContainer.getChildren().add(createMsgNode(username, text, true));
            }
        }
        messageField.clear();
        unreadCounts.put(target, 0);
        latestIncomingPreview.remove(target);
        if (currentChatRoom != null) unreadCounts.put(currentChatRoom, 0);
        updateFriendList(target);
        updateFriendList(currentChatRoom);
        scrollToBottom();
    }

    private void sendAttachment() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Chọn file hoặc ảnh để gửi");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Tất cả hỗ trợ", "*.txt", "*.png", "*.jpg", "*.jpeg"),
                new FileChooser.ExtensionFilter("Text files", "*.txt"),
                new FileChooser.ExtensionFilter("Image files", "*.png", "*.jpg", "*.jpeg")
        );
        File file = chooser.showOpenDialog(null);
        if (file == null) return;

        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            boolean image = file.getName().toLowerCase().matches(".*\\.(png|jpg|jpeg)$");
            String encoded = Base64.getEncoder().encodeToString(bytes);
            String payload = (image ? IMAGE_PREFIX : FILE_PREFIX) + file.getName() + ":" + encoded;
            sendAttachmentPayload(payload);

            String label = image ? "[Ảnh] " + file.getName() : "[File] " + file.getName();
            messageContainer.getChildren().add(createMsgNode(username, label, true));
            scrollToBottom();
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "Không thể gửi file/ảnh.").show();
        }
    }

    private void sendAttachmentPayload(String payload) {
        String target = chatHeaderName.getText();
        if (target.endsWith("(Nhóm)")) {
            String gName = target.replace(" (Nhóm)", "");
            for (Integer p : myGroups.get(gName)) if (p != currentPort()) sendProtocolRaw("GROUP_MSG:" + gName + ":" + username + ":" + payload, p);
        } else {
            Integer p = friendPorts.get(target);
            if (p != null) sendProtocolRaw("GROUP_MSG:CHAT_1_1:" + username + ":" + payload, p);
        }
    }

    private void sendProtocolRaw(String msg, int port) {
        if ("TCP".equalsIgnoreCase(selectedProtocol)) {
            try (java.net.Socket socket = new java.net.Socket("127.0.0.1", port);
                 java.io.PrintWriter out = new java.io.PrintWriter(socket.getOutputStream(), true)) {
                out.println(msg);
            } catch (Exception e) {}
        } else {
            sendUdpRaw(msg, port);
        }
    }

    private int currentPort() {
        return "TCP".equalsIgnoreCase(selectedProtocol) ? currentTcpPort : currentUdpPort;
    }

    private void sendUdpRaw(String msg, int port) {
        try {
            byte[] data = msg.getBytes();
            udpSocket.send(new java.net.DatagramPacket(data, data.length, java.net.InetAddress.getByName("127.0.0.1"), port));
        } catch (Exception e) {}
    }

    private StackPane createAvatar(String name) {
        String display = (name != null && !name.isEmpty()) ? name.substring(0, 1).toUpperCase() : "?";
        Circle circle = new Circle(20);
        String[] colors = {"#f44336", "#9c27b0", "#3f51b5", "#00bcd4", "#4caf50", "#ff9800", "#1877f2"};
        circle.setFill(Color.web(colors[Math.abs(name.hashCode()) % colors.length]));
        Label letter = new Label(display);
        letter.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 16px;");
        return new StackPane(circle, letter);
    }

    private VBox createGroupInfoPane() {
        groupNameLabel = new Label("Thông tin nhóm");
        groupNameLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        groupMemberCountLabel = new Label("0 thành viên");
        groupMemberCountLabel.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 13px;");

        groupMemberList = new ListView<>();
        groupMemberList.setPrefHeight(250);
        groupMemberList.setCellFactory(lv -> new ListCell<String>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item);
            }
        });
        groupMemberList.setStyle("-fx-background-color: white; -fx-border-color: #d9dce1; -fx-border-radius: 10; -fx-background-radius: 10;");

        addMemberButton = new Button("+ Thêm bạn bè");
        addMemberButton.setMaxWidth(Double.MAX_VALUE);
        addMemberButton.setStyle("-fx-background-color: #1877f2; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 10; -fx-padding: 10;");
        addMemberButton.setOnAction(e -> showAddMemberDialog());

        removeMemberButton = new Button("- Xóa bạn bè");
        removeMemberButton.setMaxWidth(Double.MAX_VALUE);
        removeMemberButton.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 10; -fx-padding: 10;");
        removeMemberButton.setOnAction(e -> showRemoveMemberDialog());

        viewMediaHistoryButton = new Button("Xem lịch sử file/ảnh");
        viewMediaHistoryButton.setMaxWidth(Double.MAX_VALUE);
        viewMediaHistoryButton.setStyle("-fx-background-color: #10b981; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 10; -fx-padding: 10;");
        viewMediaHistoryButton.setOnAction(e -> showMediaHistoryDialog());

        VBox actions = new VBox(8, addMemberButton, removeMemberButton, viewMediaHistoryButton);
        VBox pane = new VBox(12, groupNameLabel, groupMemberCountLabel, new Label("Thành viên"), groupMemberList, actions);
        pane.setPrefWidth(260);
        pane.setPadding(new Insets(16));
        pane.setStyle("-fx-background-color: white; -fx-border-color: transparent transparent transparent #d9dce1;");
        return pane;
    }

    private void updateGroupInfo(String groupName) {
        if (groupInfoPane == null) return;
        boolean isGroup = groupName != null && myGroups.containsKey(groupName);
        groupInfoPane.setVisible(isGroup);
        groupInfoPane.setManaged(isGroup);
        if (!isGroup) return;

        List<Integer> ports = myGroups.getOrDefault(groupName, Collections.emptyList());
        groupNameLabel.setText(groupName);
        groupMemberCountLabel.setText(ports.size() + " thành viên");

        ObservableList<String> members = javafx.collections.FXCollections.observableArrayList();
        for (Integer p : ports) {
            if (p == currentUdpPort) members.add(username + " (Bạn)");
            else {
                String friend = friendPorts.entrySet().stream()
                        .filter(e -> Objects.equals(e.getValue(), p))
                        .map(Map.Entry::getKey)
                        .findFirst().orElse("Thành viên " + p);
                members.add(friend);
            }
        }
        groupMemberList.setItems(members);
    }

    private void showAddMemberDialog() {
        String groupName = getCurrentGroupName();
        if (groupName == null || !myGroups.containsKey(groupName)) return;

        List<Integer> currentMembers = myGroups.get(groupName);
        Dialog<List<String>> dialog = new Dialog<>();
        dialog.setTitle("Thêm bạn bè vào nhóm");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        VBox box = new VBox(8);
        Map<String, CheckBox> checks = new HashMap<>();
        for (String friend : friendPorts.keySet()) {
            Integer port = friendPorts.get(friend);
            if (!currentMembers.contains(port)) {
                CheckBox cb = new CheckBox(friend);
                checks.put(friend, cb);
                box.getChildren().add(cb);
            }
        }
        if (box.getChildren().isEmpty()) box.getChildren().add(new Label("Không còn bạn bè nào để thêm."));
        dialog.getDialogPane().setContent(box);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                List<String> selected = new ArrayList<>();
                for (Map.Entry<String, CheckBox> entry : checks.entrySet()) if (entry.getValue().isSelected()) selected.add(entry.getKey());
                return selected;
            }
            return null;
        });

        dialog.showAndWait().ifPresent(selectedFriends -> {
            if (selectedFriends == null || selectedFriends.isEmpty()) return;
            List<Integer> updatedMembers = new ArrayList<>(currentMembers);
            for (String friend : selectedFriends) {
                Integer p = friendPorts.get(friend);
                if (p != null && !updatedMembers.contains(p)) {
                    updatedMembers.add(p);
                }
            }
            myGroups.put(groupName, updatedMembers);
            updateGroupInfo(groupName);
        });
    }

    private void showRemoveMemberDialog() {
        String groupName = getCurrentGroupName();
        if (groupName == null || !myGroups.containsKey(groupName)) return;

        List<Integer> members = new ArrayList<>(myGroups.get(groupName));
        Dialog<List<String>> dialog = new Dialog<>();
        dialog.setTitle("Xóa bạn bè khỏi nhóm");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        VBox box = new VBox(8);
        Map<String, CheckBox> checks = new HashMap<>();
        for (Integer port : members) {
            if (port == currentUdpPort) continue;
            String friend = friendPorts.entrySet().stream()
                    .filter(e -> Objects.equals(e.getValue(), port))
                    .map(Map.Entry::getKey)
                    .findFirst().orElse(null);
            if (friend != null) {
                CheckBox cb = new CheckBox(friend);
                checks.put(friend, cb);
                box.getChildren().add(cb);
            }
        }
        if (box.getChildren().isEmpty()) box.getChildren().add(new Label("Không có thành viên nào để xóa."));
        dialog.getDialogPane().setContent(box);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                List<String> selected = new ArrayList<>();
                for (Map.Entry<String, CheckBox> entry : checks.entrySet()) if (entry.getValue().isSelected()) selected.add(entry.getKey());
                return selected;
            }
            return null;
        });

        dialog.showAndWait().ifPresent(selectedFriends -> {
            if (selectedFriends == null || selectedFriends.isEmpty()) return;
            List<Integer> updatedMembers = new ArrayList<>(members);
            for (String friend : selectedFriends) {
                Integer p = friendPorts.get(friend);
                updatedMembers.remove(p);
            }
            myGroups.put(groupName, updatedMembers);
            updateGroupInfo(groupName);
        });
    }

    private void showMediaHistoryDialog() {
        String groupName = getCurrentGroupName();
        if (groupName == null) return;
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Lịch sử file/ảnh");
        alert.setHeaderText("Chức năng này chưa có dữ liệu file/ảnh trong code hiện tại");
        alert.setContentText("Nếu bạn muốn, mình sẽ thêm lưu lịch sử file/ảnh đã gửi của nhóm vào đây.");
        alert.showAndWait();
    }

    private void updateFriendList(String name) {
        Platform.runLater(() -> {
            if (!friendList.getItems().contains(name)) friendList.getItems().add(name);
            friendList.refresh();
        });
    }

    private void broadcastLogin() {
        for (int p = 6000; p <= 6010; p++) {
            if (p != currentPort()) sendProtocolRaw("LOGIN:" + username + ":" + currentPort(), p);
        }
    }

    private void autoStartConnection() {
        new Thread(() -> {
            startNetworkReceiver();
            try { Thread.sleep(500); broadcastLogin(); } catch (Exception e) {}
        }).start();
    }

    private void showLoginScreen(Stage stage) {
        VBox root = new VBox();
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-background-color: linear-gradient(to bottom right, #1d3557, #457b9d, #1d3557);");

        VBox loginCard = new VBox(25);
        loginCard.setAlignment(Pos.CENTER);
        loginCard.setPadding(new Insets(45));
        loginCard.setMaxWidth(390);
        loginCard.setStyle("-fx-background-color: rgba(255, 255, 255, 0.1); -fx-background-radius: 25; -fx-border-color: rgba(255, 255, 255, 0.2); -fx-border-radius: 25;");

        StackPane logo = new StackPane(new Circle(40, Color.web("#1d3557")), new Label("NT"));
        ((Label)logo.getChildren().get(1)).setStyle("-fx-text-fill: white; -fx-font-size: 28px; -fx-font-weight: 900;");

        Label appTitle = new Label("CÔNG TY TNHH NAM TÙNG");
        appTitle.setStyle("-fx-text-fill: white; -fx-font-size: 20px; -fx-font-weight: 900;");

        TextField nameField = new TextField();
        nameField.setPromptText("Tên nhân viên...");
        nameField.setPrefHeight(52);
        nameField.setStyle("-fx-background-radius: 14;");

        Button btnLogin = new Button("KẾT NỐI HỆ THỐNG");
        btnLogin.setMaxWidth(Double.MAX_VALUE);
        btnLogin.setPrefHeight(52);
        btnLogin.setStyle("-fx-background-color: #1877f2; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 14;");

        btnLogin.setOnAction(e -> {
            String input = nameField.getText().trim();
            if (!input.isEmpty()) {
                username = input.toLowerCase();
                BorderPane rootChat = new BorderPane();
                rootChat.setStyle("-fx-background-color: #f6f8fc;");
                rootChat.setCenter(createCenterPanel(stage));
                rootChat.setLeft(createLeftPanel());
                if (emptyStatePane != null) {
                    emptyStatePane.setVisible(true);
                    emptyStatePane.setManaged(true);
                }
                if (messageScroll != null) {
                    messageScroll.setVisible(false);
                    messageScroll.setManaged(false);
                }
                if (inputBar != null) {
                    inputBar.setVisible(false);
                    inputBar.setManaged(false);
                }
                stage.setScene(new Scene(rootChat, 1100, 750));
                stage.setTitle("Công Ty TNHH Nam Tùng - " + selectedProtocol);
                autoStartConnection();
            }
        });

        loginCard.getChildren().addAll(logo, appTitle, nameField, btnLogin);
        root.getChildren().add(loginCard);
        stage.setScene(new Scene(root, 1100, 750));
        stage.setTitle("Công Ty TNHH Nam Tùng");
        stage.show();
    }

    private void scrollToBottom() { Platform.runLater(() -> messageScroll.setVvalue(1.0)); }
    public static void main(String[] args) { launch(args); }
}