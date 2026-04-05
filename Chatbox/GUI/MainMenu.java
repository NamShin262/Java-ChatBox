package Chatbox.GUI;

import java.io.File;
import java.util.Optional;

import Chatbox.network.TcpChatClient;
import Chatbox.network.TcpChatServer;
import Chatbox.network.UdpFileReceiver;
import Chatbox.network.UdpFileSender;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;


public class MainMenu extends Application {

    private VBox messageContainer;
    private TextField messageField;
    private Label chatHeaderName;
    private ScrollPane messageScroll;
    private java.net.DatagramSocket udpBroadcaster;

    private TcpChatServer tcpServer;
    private TcpChatClient tcpClient;
    private UdpFileReceiver udpReceiver;
    private ListView<String> friendList;

    private String username = "Guest";
    private final int TCP_PORT = 5000;
    private final int UDP_PORT = 6000;

    @Override
    public void start(Stage primaryStage) {
        askUsername();

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #f0f2f5;");
        root.setCenter(createCenterPanel(primaryStage));
        root.setLeft(createLeftPanel());

        Scene scene = new Scene(root, 950, 800);

        primaryStage.setTitle("CHAT BOX");
        primaryStage.setScene(scene);
        primaryStage.show();
        autoStartConnection();
    }

    private void autoStartConnection() {
        new Thread(() -> {
            try {
                startTcpServer();
            } catch (Exception e) {
                System.out.println("Server đã chạy, đang đóng vai trò Client.");
            }
        }).start();
        startUdpReceiver();

        Platform.runLater(() -> {
            connectAuto("127.0.0.1");
            broadcastLogin();
        });
    }

    private void connectAuto(String host) {
        tcpClient = new TcpChatClient();
        tcpClient.setOnMessage(msg -> Platform.runLater(() -> {
            String senderName = msg.getFrom();
            String content = msg.getContent();

            if ("SYSTEM".equals(senderName)) {
                if (content.contains("đã tham gia phòng chat")) {
                    String newUser = content.split(" ")[0];
                    if (!newUser.equals(username) && !friendList.getItems().contains(newUser)) {
                        friendList.getItems().add(newUser);
                    }
                }
            } else {
                if (senderName.equals(username)) {
                    addSentMessage(content);
                } else {
                    addReceivedMessage(senderName + ": " + content);
                    if (!friendList.getItems().contains(senderName)) {
                        friendList.getItems().add(senderName);
                    }
                }
            }
        }));
        tcpClient.connect(host, TCP_PORT, username);
    }

    private void askUsername() {
        TextInputDialog dialog = new TextInputDialog("User");
        dialog.setTitle("Tên người dùng");
        dialog.setHeaderText("Nhập username");
        dialog.setContentText("Username:");

        Optional<String> result = dialog.showAndWait();
        if (result.isPresent() && !result.get().trim().isEmpty()) {
            username = result.get().trim();
        }
    }

    private StackPane createAvatar(String name) {
        String displayLetter = "?";
        if (name != null && !name.trim().isEmpty()) {
            String[] parts = name.trim().split("\\s+");
            String lastWord = parts[parts.length - 1];
            if (!lastWord.isEmpty()) {
                displayLetter = lastWord.substring(0, 1).toUpperCase();
            }
        }

        Circle circle = new Circle(20);
        String[] colors = {"#f44336", "#9c27b0", "#3f51b5", "#00bcd4", "#4caf50", "#ff9800", "#1877f2"};
        int colorIndex = Math.abs(name.hashCode()) % colors.length;
        circle.setFill(Color.web(colors[colorIndex]));

        Label letter = new Label(displayLetter);
        letter.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 16px;");

        StackPane avatar = new StackPane();
        avatar.getChildren().addAll(circle, letter);
        return avatar;
    }

    private VBox createLeftPanel() {
        VBox leftPanel = new VBox(10);
        leftPanel.setPadding(new Insets(10));
        leftPanel.setPrefWidth(280);
        leftPanel.setStyle("-fx-background-color: white; -fx-border-color: transparent #d9dce1 transparent transparent;");

        // Profile cá nhân
        HBox profileBox = new HBox(12);
        profileBox.setAlignment(Pos.CENTER_LEFT);
        profileBox.setPadding(new Insets(5, 5, 15, 5));
        profileBox.setStyle("-fx-border-color: transparent transparent #f0f2f5 transparent; -fx-border-width: 1;");

        StackPane myAvatar = createAvatar(username);
        VBox nameInfo = new VBox(0, new Label(username), new Label("Đang hoạt động"));
        ((Label)nameInfo.getChildren().get(0)).setStyle("-fx-font-weight: bold; -fx-font-size: 15px;");
        ((Label)nameInfo.getChildren().get(1)).setStyle("-fx-text-fill: #31a24c; -fx-font-size: 11px;");
        profileBox.getChildren().addAll(myAvatar, nameInfo);

        Label title = new Label("Đoạn chat");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: 800; -fx-padding: 5 0 5 5;");

        friendList = new ListView<>();
        // CSS cho ListView để mất viền mặc định và tạo hiệu ứng chọn
        friendList.setStyle("-fx-background-color: transparent; -fx-background-insets: 0; -fx-padding: 0;");
        
        friendList.setCellFactory(param -> new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                    setStyle("-fx-background-color: transparent;");
                } else {
                    HBox cell = new HBox(10);
                    cell.setAlignment(Pos.CENTER_LEFT);
                    cell.setPadding(new Insets(8, 10, 8, 10));
                    
                    StackPane avt = createAvatar(item);
                    ((Circle)avt.getChildren().get(0)).setRadius(18);
                    Label name = new Label(item);
                    name.setStyle("-fx-font-weight: 500; -fx-font-size: 14px;");
                    
                    cell.getChildren().addAll(avt, name);
                    setGraphic(cell);
                    
                    // Hiệu ứng khi được chọn hoặc di chuột qua
                    selectedProperty().addListener((obs, wasSelected, isNowSelected) -> {
                        if (isNowSelected) setStyle("-fx-background-color: #e7f3ff; -fx-background-radius: 10;");
                        else setStyle("-fx-background-color: transparent;");
                    });
                }
            }
        });

        friendList.getItems().add("Phòng chat chung");
        VBox.setVgrow(friendList, Priority.ALWAYS);
        leftPanel.getChildren().addAll(profileBox, title, friendList);
        return leftPanel;
    }

    private BorderPane createCenterPanel(Stage stage) {
        BorderPane centerPanel = new BorderPane();
        centerPanel.setTop(createTopBar());
        centerPanel.setCenter(createMessageArea());
        centerPanel.setBottom(createBottomBar(stage));
        return centerPanel;
    }

    private HBox createTopBar() {
        if (chatHeaderName == null) {
            chatHeaderName = new Label("Phòng chat chung");
        }

        HBox topBar = new HBox(12);
        topBar.setPadding(new Insets(10, 15, 10, 15));
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setStyle("-fx-background-color: white; -fx-border-color: transparent transparent #d9dce1 transparent;");

        StackPane headerAvatarContainer = new StackPane();

        // Logic hiển thị Avatar thông minh
        Runnable updateHeaderAvatar = () -> {
            headerAvatarContainer.getChildren().clear();
            String name = chatHeaderName.getText();

            // NẾU LÀ PHÒNG CHAT CHUNG -> KHÔNG HIỆN AVATAR
            if (name.equals("Phòng chat chung")) {
                headerAvatarContainer.setManaged(false);
                headerAvatarContainer.setVisible(false);
            } else {
                // NẾU LÀ TÊN NGƯỜI -> HIỆN AVATAR
                headerAvatarContainer.setManaged(true);
                headerAvatarContainer.setVisible(true);
                
                StackPane avatar = createAvatar(name);
                ((Circle) avatar.getChildren().get(0)).setRadius(18);
                ((Label) avatar.getChildren().get(1)).setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px;");
                headerAvatarContainer.getChildren().add(avatar);
            }
        };

        updateHeaderAvatar.run();

        // Cập nhật lại mỗi khi chọn người khác trong danh sách
        chatHeaderName.textProperty().addListener((obs, oldVal, newVal) -> {
            Platform.runLater(updateHeaderAvatar);
        });

        chatHeaderName.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        topBar.getChildren().addAll(headerAvatarContainer, chatHeaderName);
        return topBar;
    }
    private ScrollPane createMessageArea() {
        messageContainer = new VBox(10);
        messageContainer.setPadding(new Insets(15));
        messageContainer.setStyle("-fx-background-color: #f0f2f5;");

        messageScroll = new ScrollPane(messageContainer);
        messageScroll.setFitToWidth(true);
        messageScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        messageScroll.setStyle("-fx-background: #f0f2f5; -fx-background-color: #f0f2f5;");

        return messageScroll;
    }

    private HBox createBottomBar(Stage stage) {
        HBox bottomBar = new HBox(8);
        bottomBar.setPadding(new Insets(15));
        bottomBar.setAlignment(Pos.CENTER_LEFT);
        bottomBar.setStyle("-fx-background-color: white;");

        Button fileBtn = new Button("⊕"); // Dùng ký tự đặc biệt nhìn sang hơn
        fileBtn.setStyle("-fx-background-color: #f0f2f5; -fx-background-radius: 50; -fx-font-size: 16px; -fx-text-fill: #1877f2; -fx-min-width: 35; -fx-min-height: 35;");

        messageField = new TextField();
        messageField.setPromptText("Nhập tin nhắn...");
        messageField.setStyle("-fx-background-color: #f0f2f5; -fx-background-radius: 25; -fx-padding: 10 20; -fx-font-size: 14px;");
        HBox.setHgrow(messageField, Priority.ALWAYS);

        Button sendBtn = new Button("➤");
        sendBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #1877f2; -fx-font-size: 20px; -fx-cursor: hand;");

        fileBtn.setOnAction(e -> sendUdpFile(stage));
        sendBtn.setOnAction(e -> sendMessage());
        messageField.setOnAction(e -> sendMessage());

        bottomBar.getChildren().addAll(fileBtn, messageField, sendBtn);
        return bottomBar;
    }
    
    private void startTcpServer() {
        if (tcpServer != null) return;
        tcpServer = new TcpChatServer(TCP_PORT);
        tcpServer.start();
        addSystemMessage("TCP Server đã khởi động ở port " + TCP_PORT);
    }

    private void startUdpReceiver() {
        new Thread(() -> {
            try {
                java.net.DatagramSocket socket = new java.net.DatagramSocket(null);
                socket.setReuseAddress(true);
                socket.bind(new java.net.InetSocketAddress(UDP_PORT));

                byte[] buffer = new byte[65507];
                while (true) {
                    java.net.DatagramPacket packet = new java.net.DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    byte[] data = java.util.Arrays.copyOf(packet.getData(), packet.getLength());
                    String msg = new String(data, java.nio.charset.StandardCharsets.UTF_8).trim();

                    if (msg.startsWith("LOGIN:")) {
                        updateFriendList(msg.substring(6));
                    } else {
                        // QUAN TRỌNG: Phải dùng Platform.runLater để vẽ lên giao diện
                        Platform.runLater(() -> {
                            // Hiển thị khung tải file bên máy người nhận (isIncoming = true)
                            addFileTransferMessage("File_Nhan_Duoc.dat", data, true);
                            addSystemMessage("Bạn nhận được một file mới!");
                        });
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
        
    private void updateFriendList(String name) {
        if (name == null || name.equals(username)) return;
        
        Platform.runLater(() -> {
            if (!friendList.getItems().contains(name)) {
                friendList.getItems().add(name);
                System.out.println("Đã thêm " + name + " vào danh sách online.");
            }
        });
    }

    private void broadcastLogin() {
        new Thread(() -> {
            try { Thread.sleep(1000); } catch (InterruptedException e) {}

            try (java.net.DatagramSocket socket = new java.net.DatagramSocket()) {
                socket.setBroadcast(true);
                String loginMsg = "LOGIN:" + username;
                byte[] sendData = loginMsg.getBytes();
                
                java.net.DatagramPacket sendPacket = new java.net.DatagramPacket(
                    sendData, sendData.length, 
                    java.net.InetAddress.getByName("255.255.255.255"), UDP_PORT
                );
                socket.send(sendPacket);
                System.out.println("Đã gửi Broadcast chào hỏi: " + loginMsg);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void sendUdpFile(Stage stage) {
        TextInputDialog hostDialog = new TextInputDialog("127.0.0.1");
        hostDialog.setTitle("Gửi file UDP");
        hostDialog.setHeaderText("Nhập địa chỉ máy nhận");
        hostDialog.setContentText("Host:");

        Optional<String> hostResult = hostDialog.showAndWait();
        if (!hostResult.isPresent() || hostResult.get().trim().isEmpty()) return;

        String host = hostResult.get().trim();
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Chọn file gửi qua UDP");
        File file = chooser.showOpenDialog(stage);

        if (file == null) return;

        new Thread(() -> {
            try {
                UdpFileSender sender = new UdpFileSender();
                sender.sendFile(file, host, UDP_PORT);
                byte[] data = java.nio.file.Files.readAllBytes(file.toPath());
                Platform.runLater(() -> {
                    addFileTransferMessage(file.getName(), data, false);
                    addSystemMessage("Đã gửi file UDP: " + file.getName());
                });
            } catch (Exception e) {
                Platform.runLater(() -> addSystemMessage("Lỗi gửi file: " + e.getMessage()));
            }
        }).start();
    }

    private void sendMessage() {
        String text = messageField.getText().trim();
        if (text.isEmpty()) return;
        if (tcpClient == null) {
            showInfo("Chưa kết nối", "Bạn phải Connect TCP Client trước.");
            return;
        }
        tcpClient.sendMessage(text);
        messageField.clear();
    }

    private void styleIconButton(Button button) {
        button.setStyle("-fx-background-color: #e4e6eb; -fx-background-radius: 50; -fx-padding: 8 10;");
    }

    private void addSentMessage(String text) {
        MessageBubble bubble = new MessageBubble(text, true);
        // Tinh chỉnh bubble: Xanh dương gradient, chữ trắng, bo góc mạnh
        bubble.setStyle("-fx-background-color: linear-gradient(to bottom right, #0084ff, #0072ff);" +
                        "-fx-background-radius: 18 18 2 18; -fx-padding: 8 12; -fx-text-fill: white;");

        StackPane myAvatar = createAvatar(username);
        myAvatar.setScaleX(0.7); myAvatar.setScaleY(0.7);

        HBox container = new HBox(8, bubble, myAvatar);
        container.setAlignment(Pos.CENTER_RIGHT);
        container.setPadding(new Insets(5, 10, 5, 10));
        
        messageContainer.getChildren().add(container);
        scrollToBottom();
    }

    private void addReceivedMessage(String text) {
        String senderName = "User";
        String content = text;
        if (text.contains(": ")) {
            senderName = text.substring(0, text.indexOf(": "));
            content = text.substring(text.indexOf(": ") + 2);
        }

        MessageBubble bubble = new MessageBubble(content, false);
        // Tinh chỉnh bubble nhận: Xám nhạt, bo góc mềm
        bubble.setStyle("-fx-background-color: #e4e6eb; -fx-background-radius: 18 18 18 2; " +
                        "-fx-padding: 8 12; -fx-text-fill: black;");

        StackPane senderAvatar = createAvatar(senderName);
        senderAvatar.setScaleX(0.7); senderAvatar.setScaleY(0.7);

        HBox container = new HBox(8, senderAvatar, bubble);
        container.setAlignment(Pos.CENTER_LEFT);
        container.setPadding(new Insets(5, 10, 5, 10));
        
        messageContainer.getChildren().add(container);
        scrollToBottom();
    }

    private void addFileTransferMessage(String fileName, byte[] fileData, boolean isIncoming) {
        HBox wrapper = new HBox();
        wrapper.setAlignment(isIncoming ? Pos.CENTER_LEFT : Pos.CENTER_RIGHT);
        wrapper.setPadding(new Insets(5, 10, 5, 10));

        HBox fileBox = new HBox(10);
        String bgColor = isIncoming ? "#ffffff" : "#e7f3ff";
        fileBox.setStyle("-fx-background-color: " + bgColor + "; -fx-padding: 10; -fx-background-radius: 10; -fx-border-color: #ddd;");
        fileBox.setAlignment(Pos.CENTER_LEFT);
        fileBox.setMaxWidth(350);

        Label icon = new Label("📄");
        Label nameLabel = new Label(fileName);
        nameLabel.setWrapText(true);

        Button downloadBtn = new Button(isIncoming ? "Tải về" : "Đã gửi");
        downloadBtn.setDisable(!isIncoming);
        downloadBtn.setStyle("-fx-background-color: #31a24c; -fx-text-fill: white;");

        downloadBtn.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setInitialFileName(fileName);
            File saveFile = fileChooser.showSaveDialog(null);
            if (saveFile != null) {
                try {
                    java.nio.file.Files.write(saveFile.toPath(), fileData);
                    showInfo("Thành công", "Đã lưu file!");
                    downloadBtn.setDisable(true);
                } catch (Exception ex) {
                    showInfo("Lỗi", "Lỗi: " + ex.getMessage());
                }
            }
        });

        fileBox.getChildren().addAll(icon, nameLabel, downloadBtn);
        wrapper.getChildren().add(fileBox);
        messageContainer.getChildren().add(wrapper);
        scrollToBottom();
    }

    private void addSystemMessage(String text) {
        Label sysLabel = new Label(text);
        sysLabel.setStyle("-fx-text-fill: gray; -fx-font-style: italic; -fx-font-size: 11px;");
        HBox container = new HBox(sysLabel);
        container.setAlignment(Pos.CENTER);
        container.setPadding(new Insets(5, 0, 5, 0));
        messageContainer.getChildren().add(container);
        scrollToBottom();
    }

    private void scrollToBottom() {
        Platform.runLater(() -> messageScroll.setVvalue(1.0));
    }

    private void showInfo(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, content, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}