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
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

public class MainMenu extends Application {

    private VBox messageContainer;
    private TextField messageField;
    private Label chatHeaderName;
    private Label chatHeaderStatus;
    private ScrollPane messageScroll;

    private TcpChatServer tcpServer;
    private TcpChatClient tcpClient;
    private UdpFileReceiver udpReceiver;

    private String username = "Guest";
    private final int TCP_PORT = 5000;
    private final int UDP_PORT = 6000;

    @Override
    public void start(Stage primaryStage) {
        askUsername();

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #f0f2f5;");

        root.setLeft(createLeftPanel());
        root.setCenter(createCenterPanel(primaryStage));
        root.setRight(createRightPanel(primaryStage));

        Scene scene = new Scene(root, 1200, 700);

        primaryStage.setTitle("Messenger JavaFX - TCP Chat + UDP File");
        primaryStage.setScene(scene);
        primaryStage.show();

        loadSampleMessages();

        primaryStage.setOnCloseRequest(e -> {
            if (tcpClient != null) tcpClient.disconnect();
            if (udpReceiver != null) udpReceiver.stop();
            if (tcpServer != null) tcpServer.stop();
        });
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

    private VBox createLeftPanel() {
        VBox leftPanel = new VBox(12);
        leftPanel.setPadding(new Insets(15));
        leftPanel.setPrefWidth(260);
        leftPanel.setStyle("-fx-background-color: white; -fx-border-color: #d9dce1;");

        Label title = new Label("Messenger");
        title.setStyle("-fx-font-size: 26px; -fx-font-weight: bold; -fx-text-fill: #1877f2;");

        TextField searchField = new TextField();
        searchField.setPromptText("Tìm kiếm...");
        searchField.setStyle(
            "-fx-background-radius: 20;" +
            "-fx-padding: 10 14;" +
            "-fx-background-color: #f0f2f5;"
        );

        ListView<String> conversationList = new ListView<>();
        conversationList.getItems().addAll("Phòng chat chung", "UDP File Transfer", "TCP Chat Room");
        conversationList.getSelectionModel().selectFirst();

        leftPanel.getChildren().addAll(title, searchField, conversationList);
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
        HBox topBar = new HBox();
        topBar.setPadding(new Insets(15));
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setStyle("-fx-background-color: white; -fx-border-color: #d9dce1;");

        VBox infoBox = new VBox(3);

        chatHeaderName = new Label("Phòng chat chung");
        chatHeaderName.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        chatHeaderStatus = new Label("Sẵn sàng");
        chatHeaderStatus.setStyle("-fx-text-fill: #31a24c; -fx-font-size: 12px;");

        infoBox.getChildren().addAll(chatHeaderName, chatHeaderStatus);
        topBar.getChildren().add(infoBox);

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
        HBox bottomBar = new HBox(10);
        bottomBar.setPadding(new Insets(12));
        bottomBar.setAlignment(Pos.CENTER_LEFT);
        bottomBar.setStyle("-fx-background-color: white; -fx-border-color: #d9dce1;");

        Button emojiBtn = new Button("😊");
        Button fileBtn = new Button("📎");
        Button sendBtn = new Button("Gửi");

        styleIconButton(emojiBtn);
        styleIconButton(fileBtn);

        sendBtn.setStyle(
            "-fx-background-color: #1877f2;" +
            "-fx-text-fill: white;" +
            "-fx-font-weight: bold;" +
            "-fx-background-radius: 20;" +
            "-fx-padding: 10 18;"
        );

        messageField = new TextField();
        messageField.setPromptText("Nhập tin nhắn...");
        messageField.setStyle(
            "-fx-background-color: #f0f2f5;" +
            "-fx-background-radius: 20;" +
            "-fx-padding: 10 15;"
        );
        HBox.setHgrow(messageField, Priority.ALWAYS);

        emojiBtn.setOnAction(e -> messageField.appendText("😊"));

        fileBtn.setOnAction(e -> sendUdpFile(stage));

        sendBtn.setOnAction(e -> sendMessage());
        messageField.setOnAction(e -> sendMessage());

        bottomBar.getChildren().addAll(emojiBtn, fileBtn, messageField, sendBtn);
        return bottomBar;
    }

    private VBox createRightPanel(Stage stage) {
        VBox rightPanel = new VBox(12);
        rightPanel.setPadding(new Insets(15));
        rightPanel.setPrefWidth(240);
        rightPanel.setStyle("-fx-background-color: white; -fx-border-color: #d9dce1;");

        Label title = new Label("Chức năng");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        Button tcpServerBtn = new Button("Start TCP Server");
        Button tcpClientBtn = new Button("Connect TCP Client");
        Button udpReceiverBtn = new Button("Start UDP Receiver");
        Button clearChatBtn = new Button("Xóa màn hình");

        styleRightButton(tcpServerBtn);
        styleRightButton(tcpClientBtn);
        styleRightButton(udpReceiverBtn);
        styleRightButton(clearChatBtn);

        tcpServerBtn.setOnAction(e -> startTcpServer());
        tcpClientBtn.setOnAction(e -> connectTcpClient());
        clearChatBtn.setOnAction(e -> messageContainer.getChildren().clear());
        udpReceiverBtn.setOnAction(e -> startUdpReceiver());

        rightPanel.getChildren().addAll(
            title,
            tcpServerBtn,
            tcpClientBtn,
            udpReceiverBtn,
            clearChatBtn
        );

        return rightPanel;
    }

    private void startTcpServer() {
        if (tcpServer != null) {
            showInfo("Server", "TCP Server đã chạy rồi.");
            return;
        }

        tcpServer = new TcpChatServer(TCP_PORT);
        tcpServer.start();
        addSystemMessage("TCP Server đã khởi động ở port " + TCP_PORT);
    }

    private void connectTcpClient() {
        if (tcpClient != null) {
            showInfo("TCP Client", "Client đã kết nối rồi.");
            return;
        }

        TextInputDialog hostDialog = new TextInputDialog("127.0.0.1");
        hostDialog.setTitle("Kết nối TCP");
        hostDialog.setHeaderText("Nhập địa chỉ server");
        hostDialog.setContentText("Host:");

        Optional<String> hostResult = hostDialog.showAndWait();
        if (!hostResult.isPresent() || hostResult.get().trim().isEmpty()) {
            return;
        }

        String host = hostResult.get().trim();

        tcpClient = new TcpChatClient();
        tcpClient.setOnSystem(msg -> Platform.runLater(() -> addSystemMessage(msg)));
        
        tcpClient.setOnMessage(msg -> Platform.runLater(() -> {
            // Lấy tên người gửi từ gói tin
            String senderName = msg.getFrom();
            // So sánh với username của chính máy này
            boolean isSelf = senderName.equals(username);

            if ("SYSTEM".equals(senderName)) {
                addSystemMessage(msg.getContent());
            } else if (isSelf) {
                // LÀ MÌNH GỬI: Chỉ hiện nội dung, hàm addSentMessage sẽ đẩy sang PHẢI
                addSentMessage(msg.getContent());
            } else {
                // NGƯỜI KHÁC GỬI: Hiện "Tên: Nội dung", hàm addReceivedMessage sẽ đẩy sang TRÁI
                String formattedMsg = senderName + ": " + msg.getContent();
                addReceivedMessage(formattedMsg);
            }
        }));

        boolean ok = tcpClient.connect(host, TCP_PORT, username);
        if (ok) {
            addSystemMessage("Đã kết nối TCP tới " + host + ":" + TCP_PORT + " với username: " + username);
            chatHeaderStatus.setText("Đã kết nối TCP");
        }
    }

    
    private void startUdpReceiver() {
        if (udpReceiver == null) {
            udpReceiver = new UdpFileReceiver();
        }
        
        udpReceiver.start(UDP_PORT, (fileName, fileData) -> {
            Platform.runLater(() -> addFileTransferMessage(fileName, fileData,true));
            }, (log) -> {
            Platform.runLater(() -> {
                addSystemMessage(log);
                // Cập nhật trạng thái để người dùng biết máy đã sẵn sàng nhận
                chatHeaderStatus.setText("UDP Ready (Port " + UDP_PORT + ")");
            });
    });
}

    private void sendUdpFile(Stage stage) {
        TextInputDialog hostDialog = new TextInputDialog("127.0.0.1");
        hostDialog.setTitle("Gửi file UDP");
        hostDialog.setHeaderText("Nhập địa chỉ máy nhận");
        hostDialog.setContentText("Host:");

        Optional<String> hostResult = hostDialog.showAndWait();
        if (!hostResult.isPresent() || hostResult.get().trim().isEmpty()) {
            return;
        }

        String host = hostResult.get().trim();

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Chọn file gửi qua UDP");
        File file = chooser.showOpenDialog(stage);

        if (file == null) return;

        new Thread(() -> {
            try {
                System.out.println("--- Bắt đầu gửi file qua UDP tới " + host + ":" + UDP_PORT + " ---"); // Thêm dòng này
                UdpFileSender sender = new UdpFileSender();
                sender.sendFile(file, host, UDP_PORT);
                byte[] data = java.nio.file.Files.readAllBytes(file.toPath());
                Platform.runLater(() -> addFileTransferMessage(file.getName(), data, false)); // false vì mình là người gửi

                Platform.runLater(() ->
                    addSystemMessage("Đã gửi file UDP: " + file.getName() + " tới " + host + ":" + UDP_PORT));
                System.out.println("--- Hoàn tất gửi file! ---"); // Thêm dòng này
            } catch (Exception e) {
                e.printStackTrace(); // Rất quan trọng: In lỗi chi tiết ra Terminal
                Platform.runLater(() ->
                    addSystemMessage("Lỗi gửi file UDP: " + e.getMessage()));
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
        button.setStyle(
            "-fx-background-color: #e4e6eb;" +
            "-fx-background-radius: 50;" +
            "-fx-padding: 8 10;"
        );
    }

    private void styleRightButton(Button button) {
        button.setMaxWidth(Double.MAX_VALUE);
        button.setStyle(
            "-fx-background-color: #f0f2f5;" +
            "-fx-background-radius: 12;" +
            "-fx-padding: 12;"
        );
    }

    private void addSentMessage(String text) {
        MessageBubble bubble = new MessageBubble(text, true); // true để hiện màu xanh
        HBox container = new HBox(bubble);
        container.setAlignment(Pos.CENTER_RIGHT); // Ép về bên PHẢI
        container.setPadding(new Insets(2, 10, 2, 10));
        messageContainer.getChildren().add(container);
        scrollToBottom();
    }
    
    private void addFileTransferMessage(String fileName, byte[] fileData, boolean isIncoming) {
        HBox wrapper = new HBox();
        // Căn lề: true (nhận) -> Trái, false (gửi) -> Phải
        wrapper.setAlignment(isIncoming ? Pos.CENTER_LEFT : Pos.CENTER_RIGHT);
        wrapper.setPadding(new Insets(5, 10, 5, 10));

        HBox fileBox = new HBox(10);
        String bgColor = isIncoming ? "#ffffff" : "#e7f3ff";
        fileBox.setStyle("-fx-background-color: " + bgColor + "; -fx-padding: 10; -fx-background-radius: 10; -fx-border-color: #ddd;");
        fileBox.setAlignment(Pos.CENTER_LEFT);
        fileBox.setMaxWidth(350);

        // PHẢI CÓ CÁC DÒNG NÀY:
        Label icon = new Label("📄");
        Label nameLabel = new Label(fileName);
        nameLabel.setWrapText(true);

        Button downloadBtn = new Button(isIncoming ? "Tải về" : "Đã gửi");
        downloadBtn.setDisable(!isIncoming); 
        downloadBtn.setStyle("-fx-background-color: #31a24c; -fx-text-fill: white;");

        // Logic nút tải về (chỉ chạy nếu isIncoming = true)
        downloadBtn.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setInitialFileName(fileName);
            File saveFile = fileChooser.showSaveDialog(null);
            if (saveFile != null) {
                try {
                    java.nio.file.Files.write(saveFile.toPath(), fileData);
                    showInfo("Thành công", "Đã lưu file tại: " + saveFile.getAbsolutePath());
                    downloadBtn.setDisable(true);
                    downloadBtn.setText("Đã tải");
                } catch (Exception ex) {
                    showInfo("Lỗi", "Không thể lưu file: " + ex.getMessage());
                }
            }
        });

        fileBox.getChildren().addAll(icon, nameLabel, downloadBtn);
        wrapper.getChildren().add(fileBox);

        Platform.runLater(() -> {
            messageContainer.getChildren().add(wrapper);
            scrollToBottom();
        });
    }

    private void addReceivedMessage(String text) {
        MessageBubble bubble = new MessageBubble(text, false); // false để hiện màu xám/trắng
        HBox container = new HBox(bubble);
        container.setAlignment(Pos.CENTER_LEFT); // Ép về bên TRÁI
        container.setPadding(new Insets(2, 10, 2, 10));
        messageContainer.getChildren().add(container);
        scrollToBottom();
    }

    private void addSystemMessage(String text) {
        HBox box = new HBox();
        box.setAlignment(Pos.CENTER);

        Label label = new Label(text);
        label.setStyle(
            "-fx-text-fill: #666666;" +
            "-fx-font-size: 12px;" +
            "-fx-background-color: #eaeaea;" +
            "-fx-background-radius: 12;" +
            "-fx-padding: 6 10;"
        );

        box.getChildren().add(label);
        messageContainer.getChildren().add(box);
        scrollToBottom();
    }

    private void scrollToBottom() {
        Platform.runLater(() -> messageScroll.setVvalue(1.0));
    }

    private void loadSampleMessages() {
        addSystemMessage("Xin chào " + username);
        addSystemMessage("Bấm Start TCP Server ở 1 máy.");
        addSystemMessage("Bấm Connect TCP Client ở các máy còn lại để chat.");
        addSystemMessage("Bấm Start UDP Receiver ở máy nhận, rồi dùng nút 📎 để gửi file.");
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