package Chatbox.GUI;

import java.awt.Desktop;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
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
import javafx.stage.Stage;
import javafx.stage.FileChooser;

public class MainMenu extends Application {
    private VBox messageContainer;
    private TextField messageField;
    private ScrollPane messageScroll;
    private ListView<String> friendList;
    private HBox headerBox; 
    private Label chatHeaderName;
    private Label typingLabel; 
    private Button groupInfoButton;
    private Stage mainStage;
    private HBox bottomInputBar;
    private StackPane chatCenterStack;
    private VBox emptyStatePane;
    
    private Map<String, Integer> friendPorts = new HashMap<>(); 
    private Map<String, InetAddress> friendAddresses = new HashMap<>();
    private Map<String, String> lastMessages = new HashMap<>(); 
    private Map<String, Integer> unreadCounts = new HashMap<>(); 
    private Map<String, List<String>> groupMembers = new HashMap<>();

    private String username = "Guest";
    private int currentUdpPort = 6000;
    private DatagramSocket udpSocket;
    private final String HISTORY_DIR = "chat_history/";
    private final String ATTACHMENTS_DIR = "chat_history/attachments/";
    private final String RECEIVED_DIR = "chat_history/received/";
    private final int FILE_CHUNK_SIZE = 48000;

    private final Map<String, IncomingTransfer> incomingTransfers = new ConcurrentHashMap<>();
    private final Set<String> completedTransferIds = ConcurrentHashMap.newKeySet();
    private final AtomicInteger transferCounter = new AtomicInteger(1);

    @Override
    public void start(Stage primaryStage) {
        mainStage = primaryStage;
        try {
            Files.createDirectories(Paths.get(HISTORY_DIR));
            Files.createDirectories(Paths.get(ATTACHMENTS_DIR));
            Files.createDirectories(Paths.get(RECEIVED_DIR));
        } catch (IOException e) {}
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

        Button btnAddFriend = new Button("+ Thêm Bạn");
        btnAddFriend.setMaxWidth(Double.MAX_VALUE);
        btnAddFriend.setPrefHeight(40);
        btnAddFriend.setStyle("-fx-background-color: #22a45d; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8;");
        btnAddFriend.setOnAction(e -> showAddFriendDialog());

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
                            sendToUser(m, "NEW_GROUP:" + gName + ":" + memberListStr);
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

                    if (item.startsWith("GROUP:")) {
                        int memberCount = groupMembers.getOrDefault(item, Collections.emptyList()).size();
                        Label memberInfo = new Label(memberCount + " thành viên");
                        memberInfo.setStyle("-fx-text-fill: #5f6368; -fx-font-size: 11px;");
                        info.getChildren().add(memberInfo);
                    }

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
                setChatSelectedState(true);
                messageContainer.getChildren().clear();
                loadHistory(newV); 
            } else {
                updateHeaderInfo("Vạn Tín Messenger");
                setChatSelectedState(false);
            }
        });

        ContextMenu friendContextMenu = new ContextMenu();
        MenuItem deleteFriendItem = new MenuItem("Xóa bạn");
        deleteFriendItem.setOnAction(e -> removeSelectedFriend());
        friendContextMenu.getItems().add(deleteFriendItem);

        friendList.setOnContextMenuRequested(e -> {
            String selected = friendList.getSelectionModel().getSelectedItem();
            if (selected == null || selected.startsWith("GROUP:")) {
                deleteFriendItem.setDisable(true);
            } else {
                deleteFriendItem.setDisable(false);
            }
        });
        friendList.setContextMenu(friendContextMenu);

        left.getChildren().addAll(profile, btnAddFriend, btnCreateGroup, new Label("HỘI THOẠI"), friendList);
        return left;
    }

    private void showAddFriendDialog() {
        TextInputDialog dialog = new TextInputDialog("");
        dialog.setTitle("Thêm bạn");
        dialog.setHeaderText("Nhập username bạn muốn thêm");
        dialog.setContentText("Username:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(input -> {
            String friend = input.trim().toLowerCase();
            if (friend.isEmpty() || friend.equalsIgnoreCase(username) || friend.startsWith("group:")) return;

            if (!friendList.getItems().contains(friend)) {
                friendList.getItems().add(0, friend);
                friendList.getSelectionModel().select(friend);
            }
        });
    }

    private void removeSelectedFriend() {
        String selected = friendList.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        if (selected.startsWith("GROUP:")) {
            Alert info = new Alert(Alert.AlertType.INFORMATION);
            info.setTitle("Không thể xóa");
            info.setHeaderText("Mục đang chọn là nhóm");
            info.setContentText("Chức năng Xóa bạn chỉ áp dụng cho bạn bè cá nhân, không áp dụng cho nhóm.");
            info.showAndWait();
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Xóa bạn");
        confirm.setHeaderText("Bạn có chắc muốn xóa " + selected + " không?");
        confirm.setContentText("Sau khi xóa, cuộc trò chuyện với người này sẽ bị gỡ khỏi danh sách.");

        Optional<ButtonType> choice = confirm.showAndWait();
        if (choice.isPresent() && choice.get() == ButtonType.OK) {
            friendList.getItems().remove(selected);
            friendPorts.remove(selected.toLowerCase());
            friendAddresses.remove(selected.toLowerCase());
            unreadCounts.remove(selected);
            lastMessages.remove(selected);

            if (selected.equalsIgnoreCase(chatHeaderName.getText())) {
                chatHeaderName.setText("Vạn Tín Messenger");
                messageContainer.getChildren().clear();
            }
        }
    }

    private void showGroupInfoDialog() {
        String groupId = friendList.getSelectionModel().getSelectedItem();
        if (groupId == null || !groupId.startsWith("GROUP:")) return;

        List<String> members = groupMembers.computeIfAbsent(groupId, k -> new ArrayList<>());

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Thông tin nhóm");
        dialog.setHeaderText(groupId.substring(6));
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        VBox root = new VBox(12);
        root.setPadding(new Insets(10));

        Label lblCount = new Label("Số lượng thành viên: " + members.size());
        lblCount.setStyle("-fx-font-weight: bold; -fx-text-fill: #1877f2;");

        Label lblMembers = new Label("Thành viên:");
        ListView<String> memberView = new ListView<>();
        memberView.getItems().setAll(members);
        memberView.setPrefHeight(180);

        TextField txtSearchMember = new TextField();
        txtSearchMember.setPromptText("Tìm bạn bè để thêm vào nhóm...");

        ListView<String> friendSuggestView = new ListView<>();
        friendSuggestView.setPrefHeight(120);

        Runnable reloadFriendSuggestions = () -> {
            String keyword = txtSearchMember.getText() == null ? "" : txtSearchMember.getText().trim().toLowerCase();
            List<String> suggestions = new ArrayList<>();
            for (String f : friendList.getItems()) {
                if (f == null || f.startsWith("GROUP:")) continue;
                if (members.contains(f)) continue;
                if (!keyword.isEmpty() && !f.toLowerCase().contains(keyword)) continue;
                suggestions.add(f);
            }
            friendSuggestView.getItems().setAll(suggestions);
        };
        reloadFriendSuggestions.run();

        txtSearchMember.textProperty().addListener((obs, oldV, newV) -> reloadFriendSuggestions.run());

        Button btnAddMember = new Button("Thêm thành viên");
        btnAddMember.setOnAction(e -> {
            String picked = friendSuggestView.getSelectionModel().getSelectedItem();
            if (picked == null || picked.isEmpty() || members.contains(picked)) return;
            members.add(picked);
            memberView.getItems().setAll(members);
            lblCount.setText("Số lượng thành viên: " + members.size());
            friendList.refresh();
            txtSearchMember.clear();
            reloadFriendSuggestions.run();
        });

        friendSuggestView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                String picked = friendSuggestView.getSelectionModel().getSelectedItem();
                if (picked == null || members.contains(picked)) return;
                members.add(picked);
                memberView.getItems().setAll(members);
                lblCount.setText("Số lượng thành viên: " + members.size());
                friendList.refresh();
                txtSearchMember.clear();
                reloadFriendSuggestions.run();
            }
        });

        Button btnRemoveMember = new Button("Xóa thành viên đã chọn");
        btnRemoveMember.setOnAction(e -> {
            String selected = memberView.getSelectionModel().getSelectedItem();
            if (selected == null) return;
            if (selected.equalsIgnoreCase(username)) {
                Alert alert = new Alert(Alert.AlertType.INFORMATION, "Không thể xóa chính bạn khỏi nhóm ở bản cơ bản này.", ButtonType.OK);
                alert.setHeaderText("Không thể xóa");
                alert.showAndWait();
                return;
            }
            members.remove(selected);
            memberView.getItems().setAll(members);
            lblCount.setText("Số lượng thành viên: " + members.size());
            friendList.refresh();
            reloadFriendSuggestions.run();
        });

        Button btnFileHistory = new Button("Xem lịch sử file gửi");
        btnFileHistory.setOnAction(e -> showGroupFileHistory(groupId));

        HBox actionRow = new HBox(10, btnAddMember, btnRemoveMember, btnFileHistory);
        actionRow.setAlignment(Pos.CENTER_LEFT);

        Label lblSearch = new Label("Tìm bạn bè để thêm:");
        root.getChildren().addAll(lblCount, lblMembers, memberView, lblSearch, txtSearchMember, friendSuggestView, actionRow);
        dialog.getDialogPane().setContent(root);
        dialog.showAndWait();
    }

    private void showGroupFileHistory(String groupId) {
        List<String> fileEntries = new ArrayList<>();
        Path history = Paths.get(HISTORY_DIR + getChatFileName(groupId));

        if (Files.exists(history)) {
            try {
                for (String line : Files.readAllLines(history)) {
                    String[] pts = line.split("\\|SEP\\|", 2);
                    if (pts.length != 2) continue;
                    String sender = pts[0];
                    String msg = pts[1];
                    if (!isAttachmentMessage(msg)) continue;

                    String[] p = msg.split("\\|", 4);
                    if (p.length < 4) continue;
                    String type = "IMG".equalsIgnoreCase(p[1]) ? "[Ảnh]" : "[File]";
                    fileEntries.add(type + " " + p[2] + " - gửi bởi " + sender + "|PATH|" + p[3]);
                }
            } catch (IOException e) {
                // ignore
            }
        }

        Dialog<Void> dlg = new Dialog<>();
        dlg.setTitle("Lịch sử file đã gửi");
        dlg.setHeaderText(groupId.substring(6));
        dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        VBox box = new VBox(10);
        box.setPadding(new Insets(10));

        ListView<String> lv = new ListView<>();
        for (String entry : fileEntries) {
            String[] p = entry.split("\\|PATH\\|", 2);
            lv.getItems().add(p[0]);
        }
        lv.setPrefHeight(260);

        Button btnOpen = new Button("Mở file đã chọn");
        btnOpen.setOnAction(e -> {
            int idx = lv.getSelectionModel().getSelectedIndex();
            if (idx < 0 || idx >= fileEntries.size()) return;
            String[] p = fileEntries.get(idx).split("\\|PATH\\|", 2);
            if (p.length == 2) openFile(Paths.get(p[1]));
        });

        box.getChildren().addAll(lv, btnOpen);
        dlg.getDialogPane().setContent(box);
        dlg.showAndWait();
    }

    private BorderPane createCenterPanel(Stage stage) {
        BorderPane center = new BorderPane();
        chatHeaderName = new Label("Vạn Tín Messenger");
        chatHeaderName.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        typingLabel = new Label(""); 
        groupInfoButton = new Button("Thông tin nhóm");
        groupInfoButton.setStyle("-fx-background-color: #eef3ff; -fx-text-fill: #1877f2; -fx-font-weight: bold; -fx-background-radius: 8;");
        groupInfoButton.setDisable(true);
        groupInfoButton.setVisible(false);
        groupInfoButton.setManaged(false);
        groupInfoButton.setOnAction(e -> showGroupInfoDialog());

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        headerBox = new HBox(15, new VBox(2, chatHeaderName, typingLabel), headerSpacer, groupInfoButton);
        headerBox.setAlignment(Pos.CENTER_LEFT);
        headerBox.setPadding(new Insets(15, 25, 15, 25));
        headerBox.setStyle("-fx-background-color: white; -fx-border-color: transparent transparent #d9dce1 transparent;");

        messageContainer = new VBox(15); messageContainer.setPadding(new Insets(20));
        messageScroll = new ScrollPane(messageContainer); messageScroll.setFitToWidth(true);
        messageScroll.setStyle("-fx-background: #f0f2f5; -fx-background-color: #f0f2f5;");

        emptyStatePane = new VBox(16);
        emptyStatePane.setAlignment(Pos.CENTER);
        emptyStatePane.setStyle("-fx-background-color: linear-gradient(to bottom, #f7f9fc, #eef2f7);");

        Label welcomeTitle = new Label("Chào mừng đến với Vạn Tín Messenger");
        welcomeTitle.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #1d3557;");
        Label welcomeSub = new Label("Chọn một bạn bè hoặc nhóm ở bên trái để bắt đầu trò chuyện");
        welcomeSub.setStyle("-fx-font-size: 14px; -fx-text-fill: #5f6368;");
        Label welcomeHint = new Label("Mẹo demo: Gửi ảnh/file để hiển thị preview đẹp trong bong bóng chat");
        welcomeHint.setStyle("-fx-font-size: 13px; -fx-text-fill: #7a8087;");
        emptyStatePane.getChildren().addAll(createAvatar(username), welcomeTitle, welcomeSub, welcomeHint);

        chatCenterStack = new StackPane(emptyStatePane, messageScroll);

        bottomInputBar = new HBox(12); bottomInputBar.setPadding(new Insets(15, 25, 15, 25));
        bottomInputBar.setStyle("-fx-background-color: white;");

        messageField = new TextField();
        messageField.setPromptText("Nhập tin nhắn...");
        messageField.setStyle("-fx-background-radius: 20; -fx-padding: 10 15; -fx-background-color: #f0f2f5;");
        HBox.setHgrow(messageField, Priority.ALWAYS);

        Button btnSend = new Button("➤"); 
        btnSend.setStyle("-fx-text-fill: #1877f2; -fx-font-size: 22px; -fx-background-color: transparent;");
        btnSend.setOnAction(e -> sendMessage());
        messageField.setOnAction(e -> sendMessage());

        Button btnAttach = new Button("📎");
        btnAttach.setStyle("-fx-font-size: 18px; -fx-background-color: transparent;");
        btnAttach.setOnAction(e -> sendAttachment());

        bottomInputBar.getChildren().addAll(btnAttach, messageField, btnSend);
        center.setTop(headerBox); center.setCenter(chatCenterStack); center.setBottom(bottomInputBar);
        setChatSelectedState(false);
        return center;
    }

    private void handleIncoming(String room, String sender, String msg, boolean isMe) {
        Platform.runLater(() -> {
            saveHistory(room, sender, msg); 
            lastMessages.put(room, (isMe ? "Bạn: " : sender + ": ") + summarizeMessageForList(msg));
            
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
                    sendToUser(m, "GROUP_MSG:" + username + ":" + target + ":" + t);
                }
            }
        } else {
            sendToUser(target, "TEXT_MSG:" + username + ":" + target + ":" + t);
        }
        handleIncoming(target, username, t, true);
        messageField.clear();
    }

    private void sendAttachment() {
        String target = friendList.getSelectionModel().getSelectedItem();
        if (target == null || target.equals("Vạn Tín Messenger")) return;

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Chọn ảnh hoặc file để gửi");
        FileChooser.ExtensionFilter allFilter = new FileChooser.ExtensionFilter("Tất cả file", "*.*");
        FileChooser.ExtensionFilter imgFilter = new FileChooser.ExtensionFilter("Ảnh", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp", "*.webp");
        chooser.getExtensionFilters().addAll(allFilter, imgFilter);
        chooser.setSelectedExtensionFilter(allFilter);

        File selected = chooser.showOpenDialog(mainStage);
        if (selected == null || !selected.exists()) return;

        String ext = "";
        String name = selected.getName();
        int idx = name.lastIndexOf('.');
        if (idx >= 0) ext = name.substring(idx).toLowerCase();
        boolean isImage = Arrays.asList(".png", ".jpg", ".jpeg", ".gif", ".bmp", ".webp").contains(ext);

        String safeFileName = name.replace("|", "_").replace(":", "_");
        String transferId = username + "-" + System.currentTimeMillis() + "-" + transferCounter.getAndIncrement();
        try {
            byte[] data = Files.readAllBytes(selected.toPath());
            String localSaved = saveAttachmentLocally(data, username, safeFileName, "sent");
            sendAttachmentTransfer(target, transferId, safeFileName, isImage ? "IMG" : "FILE", data);
            String localPayload = buildAttachmentPayload(isImage ? "IMG" : "FILE", safeFileName, localSaved);
            handleIncoming(target, username, localPayload, true);
        } catch (IOException e) {
            // ignore
        }
    }

    private void sendAttachmentTransfer(String target, String transferId, String fileName, String fileType, byte[] data) {
        String encoded = Base64.getEncoder().encodeToString(data);
        int totalChunks = (encoded.length() + FILE_CHUNK_SIZE - 1) / FILE_CHUNK_SIZE;
        String fileSize = String.valueOf(data.length);

        if (target.startsWith("GROUP:")) {
            List<String> members = groupMembers.get(target);
            if (members == null) return;
            for (String m : members) {
                if (m.equalsIgnoreCase(username)) continue;

                sendToUser(m, "FILE_META_G:" + transferId + ":" + username + ":" + target + ":" + fileType + ":" + fileName + ":" + fileSize + ":" + totalChunks);
                for (int i = 0; i < totalChunks; i++) {
                    int from = i * FILE_CHUNK_SIZE;
                    int to = Math.min(encoded.length(), from + FILE_CHUNK_SIZE);
                    String chunk = encoded.substring(from, to);
                    sendToUser(m, "FILE_CHUNK_G:" + transferId + ":" + username + ":" + target + ":" + i + ":" + totalChunks + ":" + chunk);
                }
                sendToUser(m, "FILE_END_G:" + transferId + ":" + username + ":" + target);
            }
        } else {
            sendToUser(target, "FILE_META:" + transferId + ":" + username + ":" + target + ":" + fileType + ":" + fileName + ":" + fileSize + ":" + totalChunks);
            for (int i = 0; i < totalChunks; i++) {
                int from = i * FILE_CHUNK_SIZE;
                int to = Math.min(encoded.length(), from + FILE_CHUNK_SIZE);
                String chunk = encoded.substring(from, to);
                sendToUser(target, "FILE_CHUNK:" + transferId + ":" + username + ":" + target + ":" + i + ":" + totalChunks + ":" + chunk);
            }
            sendToUser(target, "FILE_END:" + transferId + ":" + username + ":" + target);
        }
    }

    private void startUdpReceiver() {
        new Thread(() -> {
            try {
                while (udpSocket == null) { 
                    try {
                        udpSocket = new DatagramSocket(currentUdpPort);
                        udpSocket.setBroadcast(true);
                    } catch (Exception e) { currentUdpPort++; } 
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
                            friendAddresses.put(sender.toLowerCase(), p.getAddress());
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
                    } else if (rawData.startsWith("FILE_META:")) {
                        String[] pts = rawData.split(":", 8);
                        if (pts.length >= 8 && pts[3].trim().equalsIgnoreCase(username)) {
                            String transferId = pts[1].trim();
                            if (completedTransferIds.contains(transferId) || incomingTransfers.containsKey(transferId)) continue;

                            String sender = pts[2].trim();
                            String fileType = pts[4].trim();
                            String fileName = pts[5].trim();
                            int totalChunks = Integer.parseInt(pts[7].trim());
                            incomingTransfers.put(transferId, new IncomingTransfer(sender, sender, fileType, fileName, totalChunks));
                            friendPorts.put(sender.toLowerCase(), p.getPort());
                            friendAddresses.put(sender.toLowerCase(), p.getAddress());
                            updateFriendList(sender);
                        }
                    } else if (rawData.startsWith("FILE_META_G:")) {
                        String[] pts = rawData.split(":", 8);
                        if (pts.length >= 8) {
                            String transferId = pts[1].trim();
                            if (completedTransferIds.contains(transferId) || incomingTransfers.containsKey(transferId)) continue;

                            String sender = pts[2].trim();
                            String groupID = pts[3].trim();
                            String fileType = pts[4].trim();
                            String fileName = pts[5].trim();
                            int totalChunks = Integer.parseInt(pts[7].trim());
                            incomingTransfers.put(transferId, new IncomingTransfer(sender, groupID, fileType, fileName, totalChunks));
                            Platform.runLater(() -> {
                                if(!friendList.getItems().contains(groupID)) {
                                    friendList.getItems().add(0, groupID);
                                }
                            });
                        }
                    } else if (rawData.startsWith("FILE_CHUNK:")) {
                        String[] pts = rawData.split(":", 7);
                        if (pts.length >= 7 && pts[3].trim().equalsIgnoreCase(username)) {
                            String transferId = pts[1].trim();
                            int chunkIndex = Integer.parseInt(pts[4].trim());
                            appendChunk(transferId, chunkIndex, pts[6]);
                        }
                    } else if (rawData.startsWith("FILE_CHUNK_G:")) {
                        String[] pts = rawData.split(":", 7);
                        if (pts.length >= 7) {
                            String transferId = pts[1].trim();
                            int chunkIndex = Integer.parseInt(pts[4].trim());
                            appendChunk(transferId, chunkIndex, pts[6]);
                        }
                    } else if (rawData.startsWith("FILE_END:")) {
                        String[] pts = rawData.split(":", 4);
                        if (pts.length >= 4 && pts[3].trim().equalsIgnoreCase(username)) {
                            finalizeIncomingTransfer(pts[1].trim());
                        }
                    } else if (rawData.startsWith("FILE_END_G:")) {
                        String[] pts = rawData.split(":", 4);
                        if (pts.length >= 4) {
                            finalizeIncomingTransfer(pts[1].trim());
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
                                friendAddresses.put(sender.toLowerCase(), p.getAddress());
                                updateFriendList(sender);
                                sendUdpRawTo("REPLY_LOGIN:"+username+":"+currentUdpPort, Integer.parseInt(pts[2]), p.getAddress());
                            }
                        }
                    } else if (rawData.startsWith("REPLY_LOGIN:")) {
                        String[] pts = rawData.split(":"); 
                        if(pts.length >= 3) {
                            String sender = pts[1].trim();
                            friendPorts.put(sender.toLowerCase(), Integer.parseInt(pts[2]));
                            friendAddresses.put(sender.toLowerCase(), p.getAddress());
                            updateFriendList(sender);
                        }
                    }
                }
            } catch (Exception e) {}
        }).start();
    }

    private void appendChunk(String transferId, int chunkIndex, String chunkData) {
        if (completedTransferIds.contains(transferId)) return;
        IncomingTransfer transfer = incomingTransfers.get(transferId);
        if (transfer == null) return;
        transfer.chunks.put(chunkIndex, chunkData);
    }

    private void finalizeIncomingTransfer(String transferId) {
        if (completedTransferIds.contains(transferId)) return;

        IncomingTransfer transfer = incomingTransfers.remove(transferId);
        if (transfer == null) return;

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < transfer.totalChunks; i++) {
            String c = transfer.chunks.get(i);
            if (c == null) return;
            sb.append(c);
        }

        try {
            byte[] raw = Base64.getDecoder().decode(sb.toString());
            String cleanName = transfer.fileName.replace("/", "_").replace("\\", "_").replace(":", "_");
            String savedName = System.currentTimeMillis() + "_" + transfer.sender + "_" + cleanName;
            Path out = Paths.get(RECEIVED_DIR, savedName);
            Files.write(out, raw);

            String payload = buildAttachmentPayload(transfer.fileType, cleanName, out.toAbsolutePath().toString());
            handleIncoming(transfer.roomId, transfer.sender, payload, false);
            completedTransferIds.add(transferId);
        } catch (Exception e) {
            // ignore
        }
    }

    private void displayMessage(String sender, String content, boolean isMe) {
        Region bubbleContent;
        if (isAttachmentMessage(content)) {
            bubbleContent = createAttachmentBubble(content, isMe);
        } else {
            Label lbl = new Label(content);
            lbl.setWrapText(true);
            lbl.setMaxWidth(400);
            lbl.setStyle(isMe
                ? "-fx-background-color: #0084ff; -fx-text-fill: white; -fx-background-radius: 18 18 2 18; -fx-padding: 10 15;"
                : "-fx-background-color: #e4e6eb; -fx-text-fill: black; -fx-background-radius: 18 18 18 2; -fx-padding: 10 15;");
            bubbleContent = lbl;
        }

        StackPane avt = createAvatar(sender);
        ((Circle)avt.getChildren().get(0)).setRadius(15);
        HBox hb = isMe ? new HBox(10, bubbleContent, avt) : new HBox(10, avt, bubbleContent);
        hb.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        messageContainer.getChildren().add(hb);
        Platform.runLater(() -> messageScroll.setVvalue(1.0));
    }

    private boolean isAttachmentMessage(String msg) {
        return msg != null && msg.startsWith("ATTACH|");
    }

    private String buildAttachmentPayload(String type, String name, String path) {
        return "ATTACH|" + type + "|" + name.replace("|", "_") + "|" + path.replace("|", "_");
    }

    private String summarizeMessageForList(String msg) {
        if (!isAttachmentMessage(msg)) return msg;
        String[] p = msg.split("\\|", 4);
        if (p.length < 4) return "[Tệp đính kèm]";
        return ("IMG".equalsIgnoreCase(p[1]) ? "[Ảnh] " : "[File] ") + p[2];
    }

    private String saveAttachmentLocally(byte[] data, String owner, String fileName, String folderType) throws IOException {
        Path folder = "sent".equals(folderType) ? Paths.get(ATTACHMENTS_DIR) : Paths.get(RECEIVED_DIR);
        Files.createDirectories(folder);
        String cleanName = fileName.replace("/", "_").replace("\\", "_").replace(":", "_");
        String savedName = System.currentTimeMillis() + "_" + owner + "_" + cleanName;
        Path out = folder.resolve(savedName);
        Files.write(out, data);
        return out.toAbsolutePath().toString();
    }

    private Region createAttachmentBubble(String payload, boolean isMe) {
        String[] p = payload.split("\\|", 4);
        if (p.length < 4) {
            Label fallback = new Label(payload);
            fallback.setWrapText(true);
            fallback.setStyle(isMe
                ? "-fx-background-color: #0084ff; -fx-text-fill: white; -fx-background-radius: 18 18 2 18; -fx-padding: 10 15;"
                : "-fx-background-color: #e4e6eb; -fx-text-fill: black; -fx-background-radius: 18 18 18 2; -fx-padding: 10 15;");
            return fallback;
        }

        String type = p[1];
        String fileName = p[2];
        String filePath = p[3];

        VBox box = new VBox(8);
        box.setPadding(new Insets(10, 12, 10, 12));
        box.setMaxWidth(420);
        box.setStyle(isMe
            ? "-fx-background-color: #0084ff; -fx-background-radius: 18 18 2 18;"
            : "-fx-background-color: #e4e6eb; -fx-background-radius: 18 18 18 2;");

        Label title = new Label(("IMG".equalsIgnoreCase(type) ? "Ảnh: " : "File: ") + fileName);
        title.setWrapText(true);
        title.setStyle(isMe ? "-fx-text-fill: white; -fx-font-weight: bold;" : "-fx-text-fill: black; -fx-font-weight: bold;");
        box.getChildren().add(title);

        Path path = Paths.get(filePath);
        if ("IMG".equalsIgnoreCase(type) && Files.exists(path)) {
            try {
                Image img = new Image(path.toUri().toString(), 240, 240, true, true);
                if (!img.isError()) {
                    ImageView iv = new ImageView(img);
                    iv.setPreserveRatio(true);
                    iv.setFitWidth(220);
                    iv.setOnMouseClicked(e -> openFile(path));
                    box.getChildren().add(iv);
                }
            } catch (Exception e) {
                // ignore preview load
            }
        }

        HBox actions = new HBox(8);
        Button btnView = new Button("Xem");
        Button btnDownload = new Button("Tải về");
        btnView.setOnAction(e -> openFile(path));
        btnDownload.setOnAction(e -> saveCopyAs(path, fileName));
        actions.getChildren().addAll(btnView, btnDownload);
        box.getChildren().add(actions);

        return box;
    }

    private void openFile(Path path) {
        try {
            if (path != null && Files.exists(path) && Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(path.toFile());
            }
        } catch (Exception e) {
            // ignore
        }
    }

    private void saveCopyAs(Path source, String defaultName) {
        try {
            if (source == null || !Files.exists(source)) return;
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Chọn nơi lưu file");
            chooser.setInitialFileName(defaultName);
            File out = chooser.showSaveDialog(mainStage);
            if (out == null) return;
            Files.copy(source, out.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            // ignore
        }
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
        // --- XÓA CHỮ GROUP: KHI HIỆN TRÊN HEADER ---
        String displayName = name.startsWith("GROUP:") ? name.substring(6) : name;
        chatHeaderName.setText(displayName);

        if (name.startsWith("GROUP:")) {
            int memberCount = groupMembers.getOrDefault(name, Collections.emptyList()).size();
            typingLabel.setText(memberCount + " thành viên");
            typingLabel.setStyle("-fx-text-fill: #5f6368; -fx-font-size: 12px;");
        } else {
            typingLabel.setText("");
        }

        if (groupInfoButton != null) {
            boolean isGroup = name.startsWith("GROUP:");
            groupInfoButton.setDisable(!isGroup);
            groupInfoButton.setVisible(isGroup);
            groupInfoButton.setManaged(isGroup);
        }
    }

    private void setChatSelectedState(boolean selected) {
        if (messageScroll != null) {
            messageScroll.setVisible(selected);
            messageScroll.setManaged(selected);
        }
        if (emptyStatePane != null) {
            emptyStatePane.setVisible(!selected);
            emptyStatePane.setManaged(!selected);
        }
        if (bottomInputBar != null) {
            bottomInputBar.setVisible(selected);
            bottomInputBar.setManaged(selected);
        }
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
        try {
            sendUdpRawTo(m, p, InetAddress.getByName("127.0.0.1"));
        } catch (Exception e) {}
    }

    private void sendUdpRawTo(String m, int p, InetAddress addr) {
        try {
            byte[] d = m.getBytes(StandardCharsets.UTF_8);
            udpSocket.send(new DatagramPacket(d, d.length, addr, p));
        } catch (Exception e) {}
    }

    private void sendToUser(String user, String message) {
        Integer p = friendPorts.get(user.toLowerCase());
        if (p == null) return;
        InetAddress addr = friendAddresses.get(user.toLowerCase());
        if (addr != null) sendUdpRawTo(message, p, addr);
        else sendUdpRaw(message, p);
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
            try {
                Thread.sleep(800);
                for(int p=6000; p<=6015; p++) {
                    sendUdpRaw("LOGIN:"+username+":"+currentUdpPort, p);
                    sendUdpRawTo("LOGIN:"+username+":"+currentUdpPort, p, InetAddress.getByName("255.255.255.255"));
                }
            } catch(Exception e){} 
        }).start();
    }

    private static class IncomingTransfer {
        final String sender;
        final String roomId;
        final String fileType;
        final String fileName;
        final int totalChunks;
        final Map<Integer, String> chunks = new ConcurrentHashMap<>();

        IncomingTransfer(String sender, String roomId, String fileType, String fileName, int totalChunks) {
            this.sender = sender;
            this.roomId = roomId;
            this.fileType = fileType;
            this.fileName = fileName;
            this.totalChunks = totalChunks;
        }
    }

    public static void main(String[] args) { launch(args); }
}