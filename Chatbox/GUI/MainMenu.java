package Chatbox.GUI;

import java.util.*;
import java.util.stream.Collectors;
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

    private Map<String, List<javafx.scene.Node>> privateHistory = new HashMap<>(); 
    private Map<String, Integer> friendPorts = new HashMap<>(); 
    private Map<String, List<Integer>> myGroups = new HashMap<>(); 
    
    private String username = "Guest";
    private int currentUdpPort = 6000;
    private java.net.DatagramSocket udpSocket;

    @Override
    public void start(Stage primaryStage) {
        askUsername();
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #f0f2f5;");
        root.setCenter(createCenterPanel(primaryStage));
        root.setLeft(createLeftPanel());
        
        Scene scene = new Scene(root, 1100, 750);
        primaryStage.setTitle("CHAT BOX - " + username);
        primaryStage.setScene(scene);
        primaryStage.show();

        autoStartConnection();
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
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); setStyle("-fx-background-color: transparent;"); }
                else {
                    HBox cell = new HBox(10); cell.setAlignment(Pos.CENTER_LEFT); cell.setPadding(new Insets(10, 8, 10, 8));
                    if (item.equals("Phòng chat chung")) {
                        Label lbl = new Label(item); lbl.setStyle("-fx-font-weight: 800; -fx-text-fill: #1877f2; -fx-font-size: 15px;");
                        cell.getChildren().add(lbl);
                    } else { cell.getChildren().addAll(createAvatar(item), new Label(item)); }
                    setGraphic(cell);
                    selectedProperty().addListener((o, old, val) -> setStyle(val ? "-fx-background-color: #e7f3ff; -fx-background-radius: 12;" : "-fx-background-color: transparent;"));
                }
            }
        });
        friendList.getItems().add("Phòng chat chung");
        friendList.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                if (oldV != null) privateHistory.put(oldV, new ArrayList<>(messageContainer.getChildren()));
                updateHeaderInfo(newV);
                messageContainer.getChildren().clear();
                if (privateHistory.containsKey(newV)) messageContainer.getChildren().addAll(privateHistory.get(newV));
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
                messageContainer.getChildren().add(hb);
                scrollToBottom();
            } else {
                privateHistory.computeIfAbsent(chatRoom, k -> new ArrayList<>()).add(hb);
            }
        });
    }

    private HBox createMsgNode(String sender, String content, boolean isMe) {
        Label lbl = new Label(content); lbl.setWrapText(true); lbl.setMaxWidth(400);
        lbl.setStyle(isMe ? "-fx-background-color: #0084ff; -fx-text-fill: white; -fx-background-radius: 18 18 2 18; -fx-padding: 10 15;" 
                          : "-fx-background-color: #e4e6eb; -fx-text-fill: black; -fx-background-radius: 18 18 18 2; -fx-padding: 10 15;");
        HBox hb = isMe ? new HBox(lbl) : new HBox(8, createAvatar(sender), lbl);
        if (!isMe) ((Circle)((StackPane)hb.getChildren().get(0)).getChildren().get(0)).setRadius(15);
        hb.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        return hb;
    }

    private void updateHeaderInfo(String name) {
        headerBox.getChildren().clear();
        chatHeaderName.setText(name);
        if (!name.equals("Phòng chat chung")) {
            StackPane avt = createAvatar(name); ((Circle)avt.getChildren().get(0)).setRadius(18);
            headerBox.getChildren().add(avt);
        }
        headerBox.getChildren().add(chatHeaderName);
    }

    private BorderPane createCenterPanel(Stage stage) {
        BorderPane center = new BorderPane();
        chatHeaderName = new Label("Phòng chat chung");
        chatHeaderName.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        headerBox = new HBox(10, chatHeaderName); headerBox.setAlignment(Pos.CENTER_LEFT);
        HBox top = new HBox(headerBox); top.setPadding(new Insets(15, 20, 15, 20));
        top.setStyle("-fx-background-color: white; -fx-border-color: transparent transparent #d9dce1 transparent;");
        messageContainer = new VBox(12); messageContainer.setPadding(new Insets(20));
        messageScroll = new ScrollPane(messageContainer); messageScroll.setFitToWidth(true);
        messageScroll.setStyle("-fx-background: #f0f2f5; -fx-background-color: #f0f2f5;");
        HBox bottom = new HBox(10); bottom.setPadding(new Insets(15, 20, 15, 20));
        bottom.setStyle("-fx-background-color: white;");
        messageField = new TextField(); messageField.setPromptText("Nhập tin nhắn...");
        messageField.setStyle("-fx-background-color: #f0f2f5; -fx-background-radius: 20; -fx-padding: 10;");
        HBox.setHgrow(messageField, Priority.ALWAYS);
        Button btnSend = new Button("➤"); btnSend.setStyle("-fx-text-fill: #1877f2; -fx-font-size: 20px; -fx-background-color: transparent;");
        btnSend.setOnAction(e -> sendMessage());
        messageField.setOnAction(e -> sendMessage());
        bottom.getChildren().addAll(messageField, btnSend);
        center.setTop(top); center.setCenter(messageScroll); center.setBottom(bottom);
        return center;
    }

    // --- GIAO TIẾP UDP ---
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
                    if (data.startsWith("LOGIN:")) {
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
                    } else if (data.startsWith("GROUP_INVITE:")) {
                        String[] p = data.split(":");
                        List<Integer> ports = Arrays.stream(p[2].split(",")).map(Integer::parseInt).collect(Collectors.toList());
                        myGroups.put(p[1], ports);
                        updateFriendList(p[1] + " (Nhóm)");
                    } else if (data.startsWith("GROUP_MSG:")) {
                        String[] p = data.split(":", 4);
                        handleIncomingMsg(p[1].equals("CHAT_1_1") ? p[2] : p[1] + " (Nhóm)", p[2], p[3]);
                    }
                }
            } catch (Exception e) {}
        }).start();
    }

    private void sendMessage() {
        String text = messageField.getText().trim();
        if (text.isEmpty()) return;
        String target = chatHeaderName.getText();
        if (target.endsWith("(Nhóm)")) {
            String gName = target.replace(" (Nhóm)", "");
            for (Integer p : myGroups.get(gName)) if (p != currentUdpPort) sendUdpRaw("GROUP_MSG:" + gName + ":" + username + ":" + text, p);
            messageContainer.getChildren().add(createMsgNode(username, text, true));
        } else {
            Integer p = friendPorts.get(target);
            if (p != null) {
                sendUdpRaw("GROUP_MSG:CHAT_1_1:" + username + ":" + text, p);
                messageContainer.getChildren().add(createMsgNode(username, text, true));
            }
        }
        messageField.clear(); scrollToBottom();
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

    private void updateFriendList(String name) {
        Platform.runLater(() -> { if (!friendList.getItems().contains(name)) friendList.getItems().add(name); });
    }

    private void broadcastLogin() {
        for (int p = 6000; p <= 6010; p++) if (p != currentUdpPort) sendUdpRaw("LOGIN:" + username + ":" + currentUdpPort, p);
    }

    private void autoStartConnection() {
        new Thread(() -> { startUdpReceiver(); try { Thread.sleep(500); broadcastLogin(); } catch (Exception e) {} }).start();
    }

    private void askUsername() {
        TextInputDialog dialog = new TextInputDialog("Nam");
        dialog.setHeaderText("Chào Nam, nhập tên để bắt đầu:");
        username = dialog.showAndWait().orElse("Guest");
    }

    private void scrollToBottom() { Platform.runLater(() -> messageScroll.setVvalue(1.0)); }
    public static void main(String[] args) { launch(args); }
}