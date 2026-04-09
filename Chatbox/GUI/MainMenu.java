package Chatbox.GUI;

import java.io.File;
import java.util.Optional;
import Chatbox.network.TcpChatClient;
import Chatbox.network.TcpChatServer;
import Chatbox.network.UdpFileSender;
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
    private Label chatHeaderName;
    private ScrollPane messageScroll;
    private ListView<String> friendList;
    private java.util.Map<String, java.util.List<javafx.scene.Node>> privateHistory = new java.util.HashMap<>(); 

    private TcpChatServer tcpServer;
    private TcpChatClient tcpClient;
    private String username = "Guest";
    private final int TCP_PORT = 5000;
    private int currentUdpPort = 6000; 

    @Override
    public void start(Stage primaryStage) {
        askUsername();

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #f0f2f5;");
        root.setCenter(createCenterPanel(primaryStage));
        root.setLeft(createLeftPanel());

        Scene scene = new Scene(root, 1000, 750);

        primaryStage.setTitle("CHAT BOX - " + username);
        primaryStage.setScene(scene);
        primaryStage.show();
        autoStartConnection();
    }

    private void autoStartConnection() {
        new Thread(() -> {
            try {
                tcpServer = new TcpChatServer(TCP_PORT);
                tcpServer.start();
                Platform.runLater(() -> addSystemMessage("Server TCP khởi tạo tại port " + TCP_PORT));
            } catch (Exception e) {
                System.out.println("Server đã tồn tại, chạy chế độ Client.");
            }
        }).start();

        // Khởi động nhận UDP với cơ chế tự tìm Port trống
        startUdpReceiver();

        Platform.runLater(() -> {
            connectAuto("127.0.0.1");
            broadcastLogin();
        });
    }

    private void startUdpReceiver() {
        new Thread(() -> {
            boolean bound = false;
            java.net.DatagramSocket socket = null;
            
            // Thêm một chút delay nhỏ để tránh 2 tiến trình tranh nhau cùng 1 miligiây
            try { Thread.sleep((long)(Math.random() * 200)); } catch(Exception e) {}

            while (!bound && currentUdpPort < 6100) {
                try {
                    // Constructor này sẽ báo lỗi ngay nếu port 6000 đã có người dùng
                    socket = new java.net.DatagramSocket(currentUdpPort); 
                    bound = true;
                } catch (Exception e) {
                    currentUdpPort++; // Port bị chiếm -> tăng lên 6001
                }
            }
            
            if (socket == null) return;
            
            final int finalPort = currentUdpPort;
            Platform.runLater(() -> addSystemMessage("Bạn đang nhận file tại Port: " + finalPort));

            // ... các code nhận dữ liệu bên dưới giữ nguyên như của bạn ...
            try {
                byte[] buffer = new byte[65507];
                while (true) {
                    java.net.DatagramPacket packet = new java.net.DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    // Xử lý data... (giữ nguyên code của bạn)
                }
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    // --- CÁC HÀM GIAO DIỆN GIỮ NGUYÊN PHONG CÁCH CŨ CỦA BẠN ---

    private void connectAuto(String host) {
        tcpClient = new TcpChatClient();
        tcpClient.setOnMessage(msg -> Platform.runLater(() -> {
            String senderName = msg.getFrom();
            String content = msg.getContent();
            if ("SYSTEM".equals(senderName)) {
                if (content.contains("đã tham gia")) updateFriendList(content.split(" ")[0]);
            } else {
                if (senderName.equals(username)) addSentMessage(content);
                else {
                    addReceivedMessage(senderName + ": " + content);
                    updateFriendList(senderName);
                }
            }
        }));
        tcpClient.connect(host, TCP_PORT, username);
    }

    private void askUsername() {
        TextInputDialog dialog = new TextInputDialog("User");
        dialog.setTitle("Tên người dùng");
        dialog.setHeaderText("Chào mừng bạn đến với Chat Box");
        dialog.setContentText("Nhập username của bạn:");
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(name -> username = name.trim().isEmpty() ? "Guest" : name.trim());
    }

    private StackPane createAvatar(String name) {
        String displayLetter = (name != null && !name.isEmpty()) ? name.substring(0, 1).toUpperCase() : "?";
        Circle circle = new Circle(20);
        String[] colors = {"#f44336", "#9c27b0", "#3f51b5", "#00bcd4", "#4caf50", "#ff9800", "#1877f2"};
        int colorIndex = Math.abs(name.hashCode()) % colors.length;
        circle.setFill(Color.web(colors[colorIndex]));
        Label letter = new Label(displayLetter);
        letter.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 16px;");
        return new StackPane(circle, letter);
    }

    private VBox createLeftPanel() {
        VBox leftPanel = new VBox(10);
        leftPanel.setPadding(new Insets(15, 10, 10, 10));
        leftPanel.setPrefWidth(280);
        leftPanel.setStyle("-fx-background-color: white; -fx-border-color: transparent #d9dce1 transparent transparent;");

        HBox profileBox = new HBox(12);
        profileBox.setAlignment(Pos.CENTER_LEFT);
        profileBox.setPadding(new Insets(5, 5, 15, 5));
        VBox nameInfo = new VBox(0, new Label(username), new Label("Đang hoạt động"));
        ((Label)nameInfo.getChildren().get(0)).setStyle("-fx-font-weight: bold; -fx-font-size: 15px;");
        ((Label)nameInfo.getChildren().get(1)).setStyle("-fx-text-fill: #31a24c; -fx-font-size: 11px;");
        profileBox.getChildren().addAll(createAvatar(username), nameInfo);

        Label title = new Label("Đoạn chat");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: 800; -fx-padding: 10 0 5 5;");

        friendList = new ListView<>();
        friendList.setCellFactory(param -> new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); setText(null); setStyle("-fx-background-color: transparent;"); }
                else {
                    HBox cell = new HBox(10); cell.setAlignment(Pos.CENTER_LEFT); cell.setPadding(new Insets(10));
                    if (!item.equals("Phòng chat chung")) {
                        StackPane avt = createAvatar(item);
                        ((Circle)avt.getChildren().get(0)).setRadius(18);
                        cell.getChildren().add(avt);
                    }
                    Label name = new Label(item);
                    name.setStyle(item.equals("Phòng chat chung") ? "-fx-font-weight: bold; -fx-font-size: 15px; -fx-text-fill: #1877f2;" : "-fx-font-weight: 500;");
                    cell.getChildren().add(name);
                    setGraphic(cell);
                    selectedProperty().addListener((obs, old, val) -> setStyle(val ? "-fx-background-color: #e7f3ff; -fx-background-radius: 12;" : "-fx-background-color: transparent;"));
                }
            }
        });

        friendList.getItems().add("Phòng chat chung");
        VBox.setVgrow(friendList, Priority.ALWAYS);
        leftPanel.getChildren().addAll(profileBox, title, friendList);
        friendList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
        if (newVal != null) {
            Platform.runLater(() -> {
                // 1. Lưu lại những gì đang hiện trên màn hình vào kho của người cũ (oldVal)
                if (oldVal != null) {
                    privateHistory.put(oldVal, new java.util.ArrayList<>(messageContainer.getChildren()));
                }

                // 2. Đổi tên tiêu đề phòng chat
                chatHeaderName.setText(newVal);

                // 3. Xóa màn hình cũ và nạp tin nhắn từ kho của người mới (newVal)
                messageContainer.getChildren().clear();
                if (privateHistory.containsKey(newVal)) {
                    messageContainer.getChildren().addAll(privateHistory.get(newVal));
                }
                scrollToBottom();
            });
        }
    });
        return leftPanel;
    }

    private BorderPane createCenterPanel(Stage stage) {
        BorderPane centerPanel = new BorderPane();
        chatHeaderName = new Label("Phòng chat chung");
        chatHeaderName.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        HBox topBar = new HBox(chatHeaderName);
        topBar.setPadding(new Insets(15, 20, 15, 20));
        topBar.setStyle("-fx-background-color: white; -fx-border-color: transparent transparent #d9dce1 transparent;");
        
        messageContainer = new VBox(12);
        messageContainer.setPadding(new Insets(20));
        messageScroll = new ScrollPane(messageContainer);
        messageScroll.setFitToWidth(true);
        messageScroll.setStyle("-fx-background: #f0f2f5; -fx-background-color: #f0f2f5;");

        centerPanel.setTop(topBar);
        centerPanel.setCenter(messageScroll);
        centerPanel.setBottom(createBottomBar(stage));
        return centerPanel;
    }

    private HBox createBottomBar(Stage stage) {
        HBox bottomBar = new HBox(10);
        bottomBar.setPadding(new Insets(15, 20, 15, 20));
        bottomBar.setAlignment(Pos.CENTER_LEFT);
        bottomBar.setStyle("-fx-background-color: white;");

        Button fileBtn = new Button("⊕");
        fileBtn.setStyle("-fx-background-color: #f0f2f5; -fx-background-radius: 50; -fx-font-size: 18px; -fx-text-fill: #1877f2; -fx-min-width: 40; -fx-min-height: 40; -fx-cursor: hand;");

        messageField = new TextField();
        messageField.setPromptText("Nhập tin nhắn...");
        messageField.setStyle("-fx-background-color: #f0f2f5; -fx-background-radius: 25; -fx-padding: 10 20;");
        HBox.setHgrow(messageField, Priority.ALWAYS);

        Button sendBtn = new Button("➤");
        sendBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #1877f2; -fx-font-size: 22px; -fx-cursor: hand;");

        fileBtn.setOnAction(e -> sendUdpFile(stage));
        sendBtn.setOnAction(e -> sendMessage());
        messageField.setOnAction(e -> sendMessage());

        bottomBar.getChildren().addAll(fileBtn, messageField, sendBtn);
        return bottomBar;
    }

    // --- CÁC HÀM XỬ LÝ TIN NHẮN & FILE ---

    private void sendMessage() {
        String text = messageField.getText().trim();
        if (!text.isEmpty() && tcpClient != null) {
            tcpClient.sendMessage(text);
            messageField.clear();
        }
    }

    private void addSentMessage(String text) {
        Label lbl = new Label(text); lbl.setWrapText(true);
        lbl.setStyle("-fx-background-color: #0084ff; -fx-text-fill: white; -fx-background-radius: 18 18 2 18; -fx-padding: 10 15;");
        HBox hb = new HBox(lbl); hb.setAlignment(Pos.CENTER_RIGHT);
        messageContainer.getChildren().add(hb);
        scrollToBottom();
    }

    private void addReceivedMessage(String text) {
        // 1. Lấy tên người gửi từ nội dung tin nhắn (Ví dụ: "nam: alo hùng ơi")
        String sender = text.contains(": ") ? text.substring(0, text.indexOf(": ")) : "Hệ thống";
        String content = text.contains(": ") ? text.substring(text.indexOf(": ") + 2) : text;

        // --- GIỮ NGUYÊN PHẦN CODE HIỂN THỊ TIN NHẮN CỦA BẠN ---
        Label lbl = new Label(content); lbl.setWrapText(true);
        lbl.setStyle("-fx-background-color: #e4e6eb; -fx-text-fill: black; -fx-background-radius: 18 18 18 2; -fx-padding: 10 15;");
        HBox hb = new HBox(lbl); hb.setAlignment(Pos.CENTER_LEFT);

        // 2. THÊM DÒNG NÀY: Giúp bên Hùng tự hiện tên Nam vào danh sách bên trái
        updateFriendList(sender); 

        // --- PHẦN LOGIC PHÂN LOẠI TIN NHẮN RIÊNG/CHUNG ĐÃ CÓ ---
        if (chatHeaderName.getText().equals(sender) || chatHeaderName.getText().equals("Phòng chat chung")) {
            messageContainer.getChildren().add(hb);
            scrollToBottom();
        } else {
            privateHistory.computeIfAbsent(sender, k -> new java.util.ArrayList<>()).add(hb);
        }
    }

    // Cải tiến hàm này để hiển thị Ảnh trực tiếp nếu là file ảnh
    private void addFileTransferMessage(String fileName, byte[] data, boolean isIncoming) {
        VBox box = new VBox(5);
        box.setAlignment(isIncoming ? Pos.CENTER_LEFT : Pos.CENTER_RIGHT);
        
        // Hiển thị ảnh nếu đuôi file là ảnh
        String ext = fileName.toLowerCase();
        if (ext.endsWith(".png") || ext.endsWith(".jpg") || ext.endsWith(".jpeg") || ext.endsWith(".gif")) {
            try {
                javafx.scene.image.Image img = new javafx.scene.image.Image(new java.io.ByteArrayInputStream(data));
                javafx.scene.image.ImageView iv = new javafx.scene.image.ImageView(img);
                iv.setFitWidth(200); iv.setPreserveRatio(true);
                iv.setStyle("-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 10, 0, 0, 0); -fx-border-radius: 10;");
                box.getChildren().add(iv);
            } catch (Exception e) {}
        }

        Button btn = new Button((isIncoming ? "Tải: " : "Đã gửi: ") + fileName);
        btn.setStyle("-fx-background-color: #31a24c; -fx-text-fill: white; -fx-background-radius: 10;");
        btn.setOnAction(e -> {
            FileChooser fc = new FileChooser(); fc.setInitialFileName(fileName);
            File f = fc.showSaveDialog(null);
            if(f != null) try { java.nio.file.Files.write(f.toPath(), data); } catch(Exception ex) { ex.printStackTrace(); }
        });
        
        box.getChildren().add(btn);
        messageContainer.getChildren().add(box);
        scrollToBottom();
    }

    private void sendUdpFile(Stage stage) {
        // Nhập IP:Port để gửi đúng cửa sổ đang mở trên cùng máy
        TextInputDialog id = new TextInputDialog("127.0.0.1:" + currentUdpPort);
        id.setHeaderText("Gửi file đến IP và Port máy nhận:");
        id.setContentText("Ví dụ: 127.0.0.1:6000 hoặc 127.0.0.1:6001");
        
        id.showAndWait().ifPresent(input -> {
            try {
                String host = input.split(":")[0];
                int port = input.contains(":") ? Integer.parseInt(input.split(":")[1]) : 6000;
                
                FileChooser fc = new FileChooser();
                File f = fc.showOpenDialog(stage);
                if (f != null) new Thread(() -> {
                    try {
                        new UdpFileSender().sendFile(f, host, port);
                        byte[] d = java.nio.file.Files.readAllBytes(f.toPath());
                        Platform.runLater(() -> addFileTransferMessage(f.getName(), d, false));
                    } catch (Exception ex) { ex.printStackTrace(); }
                }).start();
            } catch (Exception e) { addSystemMessage("Lỗi định dạng IP:Port"); }
        });
    }

    private void updateFriendList(String name) {
        if (name == null || name.equals(username)) return;
        Platform.runLater(() -> {
            if (!friendList.getItems().contains(name)) friendList.getItems().add(name);
        });
    }

    private void broadcastLogin() {
        new Thread(() -> {
            try {
                Thread.sleep(1000); // Đợi app khởi động xong
                try (java.net.DatagramSocket socket = new java.net.DatagramSocket()) {
                    socket.setBroadcast(true);
                    byte[] sendData = ("LOGIN:" + username).getBytes();
                    
                    // Gửi lời chào đến tất cả các Port có thể có (từ 6000 đến 6010)
                    for (int p = 6000; p <= 6010; p++) {
                        java.net.DatagramPacket pack = new java.net.DatagramPacket(
                            sendData, sendData.length, 
                            java.net.InetAddress.getByName("127.0.0.1"), p
                        );
                        socket.send(pack);
                    }
                }
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }
    
    private void addSystemMessage(String text) {
        Label l = new Label(text); l.setStyle("-fx-text-fill: gray; -fx-font-style: italic; -fx-font-size: 11px;");
        HBox hb = new HBox(l); hb.setAlignment(Pos.CENTER);
        messageContainer.getChildren().add(hb);
    }

    private void scrollToBottom() { Platform.runLater(() -> messageScroll.setVvalue(1.0)); }

    public static void main(String[] args) { launch(args); }
}