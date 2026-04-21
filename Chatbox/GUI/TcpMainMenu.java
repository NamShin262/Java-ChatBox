package Chatbox.GUI;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.MouseButton;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
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
import java.nio.file.Files;
import java.util.Base64;
import java.util.Optional;

import Chatbox.network.TcpFileReceiver;
import Chatbox.network.TcpFileSender;

public class TcpMainMenu extends Application {
    private static final int CARO_SIZE = 3;

    private final int startPort = 7000;
    private final int endPort = 7010;
    private int localPort = startPort;

    private VBox messageBox;
    private TextField nameField;
    private TextField peerPortField;
    private TextField messageField;
    private Label statusLabel;
    private Stage stage;

    private String[][] caroBoard;
    private Button[][] caroButtons;
    private Label caroStatusLabel;
    private String caroMySymbol;
    private String caroOpponentSymbol;
    private boolean caroMyTurn;
    private boolean caroGameActive;

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        localPort = findFreePort();

        nameField = new TextField("User");
        peerPortField = new TextField();
        peerPortField.setPromptText("Nhập peer port");
        messageField = new TextField();
        messageField.setPromptText("Nhập tin nhắn...");

        statusLabel = new Label("TCP local port: " + localPort + " | Reliable mode");
        messageBox = new VBox(8);
        messageBox.setPadding(new Insets(10));

        ScrollPane scrollPane = new ScrollPane(messageBox);
        scrollPane.setFitToWidth(true);

        Button sendButton = new Button("Gửi");
        sendButton.setOnAction(e -> sendMessage());
        messageField.setOnAction(e -> sendMessage());

        Button fileButton = new Button("Gửi file/ảnh");
        fileButton.setOnAction(e -> sendFile());

        Button quadraticButton = new Button("PT bậc 2");
        quadraticButton.setOnAction(e -> sendQuadraticProblem());

        Button caroButton = new Button("Caro chung");
        caroButton.setOnAction(e -> sendCaroInvite());

        Button rpsButton = new Button("Oẳn tù tì TCP");
        rpsButton.setOnAction(e -> sendRpsInvite());

        HBox top = new HBox(10,
                new Label("Tên:"), nameField,
                new Label("Peer port:"), peerPortField,
                statusLabel);
        top.setAlignment(Pos.CENTER_LEFT);
        top.setPadding(new Insets(10));

        HBox bottom = new HBox(10, messageField, sendButton, fileButton, quadraticButton, caroButton, rpsButton);
        HBox.setHgrow(messageField, Priority.ALWAYS);
        bottom.setPadding(new Insets(10));

        BorderPane root = new BorderPane();
        root.setTop(top);
        root.setCenter(scrollPane);
        root.setBottom(bottom);
        root.setPadding(new Insets(10));

        primaryStage.setTitle("TCP Chat + Ứng dụng");
        primaryStage.setScene(new Scene(root, 980, 620));
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
                    try {
                        serverSocketCheck.close();
                    } catch (Exception ignored) {
                    }
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
        String[] parts = line.split("\\|");
        if (parts.length < 1) {
            return;
        }
        Platform.runLater(() -> {
            switch (parts[0]) {
                case "MSG" -> {
                    if (parts.length >= 3) {
                        addLine(decode(parts[1]) + ": " + decode(parts[2]), false);
                    }
                }
                case "FILE" -> {
                    String name = TcpFileReceiver.getFileName(line);
                    String payload = TcpFileReceiver.getEncodedData(line);
                    if (name == null || payload == null) {
                        return;
                    }
                    boolean image = name.toLowerCase().matches(".*\\.(png|jpg|jpeg|gif|webp)$");
                    addFileContent(name, payload, image, false);
                }
                case "QUAD" -> {
                    if (parts.length >= 5) {
                        showQuadraticResult(decode(parts[1]), decode(parts[2]), decode(parts[3]), decode(parts[4]));
                    }
                }
                case "CARO_INVITE" -> {
                    if (parts.length >= 2) {
                        startCaroGame(decode(parts[1]), false);
                    }
                }
                case "CARO_MOVE" -> {
                    if (parts.length >= 4) {
                        applyOpponentMove(decode(parts[1]), Integer.parseInt(decode(parts[2])), Integer.parseInt(decode(parts[3])));
                    }
                }
                case "CARO_RESULT" -> {
                    if (parts.length >= 3) {
                        addSystemLine("Caro TCP: " + decode(parts[1]) + " -> " + decode(parts[2]));
                    }
                }
                case "RPS_INVITE" -> {
                    if (parts.length >= 3) {
                        showRpsCard(decode(parts[1]), decode(parts[2]));
                    }
                }
                case "RPS_CHOICE" -> {
                    if (parts.length >= 4) {
                        handleRpsChoiceFromPeer(decode(parts[1]), decode(parts[2]), decode(parts[3]));
                    }
                }
                case "RPS_RESULT" -> {
                    if (parts.length >= 6) {
                        showRpsResultFromPeer(
                                decode(parts[1]),
                                decode(parts[2]),
                                decode(parts[3]),
                                decode(parts[4]),
                                decode(parts[5])
                        );
                    }
                }
                default -> {
                }
            }
        });
    }

    private void sendMessage() {
        String text = messageField.getText().trim();
        if (text.isEmpty()) {
            return;
        }
        addLine(nameField.getText().trim() + ": " + text, true);
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
        if (file == null) {
            return;
        }
        try {
            String name = file.getName();
            boolean image = name.toLowerCase().matches(".*\\.(png|jpg|jpeg|gif|webp)$");
            String payload = TcpFileSender.encodeFile(file);
            String encodedBytes = TcpFileReceiver.getEncodedData(payload);
            addFileContent(name, encodedBytes, image, true);
            sendRaw(payload);
        } catch (Exception ex) {
            showAlert("Không thể gửi file: " + ex.getMessage());
        }
    }

    private void sendQuadraticProblem() {
        Optional<String> aInput = askInput("Phương trình bậc 2", "Nhập hệ số a:", "");
        if (aInput.isEmpty()) {
            return;
        }
        Optional<String> bInput = askInput("Phương trình bậc 2", "Nhập hệ số b:", "");
        if (bInput.isEmpty()) {
            return;
        }
        Optional<String> cInput = askInput("Phương trình bậc 2", "Nhập hệ số c:", "");
        if (cInput.isEmpty()) {
            return;
        }

        String a = aInput.get().trim();
        String b = bInput.get().trim();
        String c = cInput.get().trim();
        if (!isDouble(a) || !isDouble(b) || !isDouble(c)) {
            showAlert("Các hệ số phải là số hợp lệ.");
            return;
        }

        addSystemLine("Bạn đã gửi bài toán bậc 2: " + formatEquation(a, b, c));
        sendRaw("QUAD|" + encode(nameField.getText().trim()) + "|" + encode(a) + "|" + encode(b) + "|" + encode(c));
    }

    private void showQuadraticResult(String sender, String aText, String bText, String cText) {
        double a = Double.parseDouble(aText);
        double b = Double.parseDouble(bText);
        double c = Double.parseDouble(cText);

        String result;
        if (a == 0) {
            if (b == 0) {
                result = c == 0 ? "Vô số nghiệm" : "Vô nghiệm";
            } else {
                result = "Phương trình bậc nhất, nghiệm x = " + formatNumber(-c / b);
            }
        } else {
            double delta = b * b - 4 * a * c;
            if (delta < 0) {
                result = "Vô nghiệm";
            } else if (delta == 0) {
                result = "Nghiệm kép x = " + formatNumber(-b / (2 * a));
            } else {
                double sqrtDelta = Math.sqrt(delta);
                double x1 = (-b + sqrtDelta) / (2 * a);
                double x2 = (-b - sqrtDelta) / (2 * a);
                result = "x1 = " + formatNumber(x1) + ", x2 = " + formatNumber(x2);
            }
        }

        addSystemLine(sender + " gửi bài toán: " + formatEquation(aText, bText, cText));
        addSystemLine("Kết quả tính nhanh qua TCP: " + result);
    }

    private void sendCaroInvite() {
        startCaroGame(nameField.getText().trim(), true);
        addSystemLine("Bạn đã tạo game caro chung qua TCP. Bạn đi trước với X.");
        sendRaw("CARO_INVITE|" + encode(nameField.getText().trim()));
    }

    private void startCaroGame(String opponentName, boolean iStart) {
        caroMySymbol = iStart ? "X" : "O";
        caroOpponentSymbol = iStart ? "O" : "X";
        caroMyTurn = iStart;
        caroGameActive = true;
        caroBoard = new String[CARO_SIZE][CARO_SIZE];
        caroButtons = new Button[CARO_SIZE][CARO_SIZE];

        VBox card = new VBox(8);
        card.setPadding(new Insets(10));
        card.setStyle("-fx-background-color: #eef6ff; -fx-border-color: #3b82f6; -fx-border-radius: 6; -fx-background-radius: 6;");

        Label title = new Label("Game caro TCP với " + opponentName);
        title.setStyle("-fx-font-weight: bold;");
        Label symbolLabel = new Label("Bạn là " + caroMySymbol + ", đối thủ là " + caroOpponentSymbol + ".");
        caroStatusLabel = new Label(iStart ? "Đến lượt bạn." : "Đối thủ đi trước, vui lòng chờ.");

        GridPane boardPane = new GridPane();
        boardPane.setHgap(6);
        boardPane.setVgap(6);

        for (int row = 0; row < CARO_SIZE; row++) {
            for (int col = 0; col < CARO_SIZE; col++) {
                Button cellButton = new Button(" ");
                cellButton.setPrefSize(60, 60);
                final int currentRow = row;
                final int currentCol = col;
                cellButton.setOnAction(e -> makeCaroMove(currentRow, currentCol));
                caroButtons[row][col] = cellButton;
                boardPane.add(cellButton, col, row);
            }
        }

        card.getChildren().addAll(title, symbolLabel, caroStatusLabel, boardPane);
        messageBox.getChildren().add(card);
        updateCaroBoardUi();
    }

    private void makeCaroMove(int row, int col) {
        if (!caroGameActive) {
            showAlert("Game caro chưa bắt đầu.");
            return;
        }
        if (!caroMyTurn) {
            showAlert("Chưa đến lượt bạn.");
            return;
        }
        if (!caroBoard[row][col].isEmpty()) {
            return;
        }

        caroBoard[row][col] = caroMySymbol;
        updateCaroBoardUi();

        if (hasCaroWinner(caroMySymbol)) {
            caroGameActive = false;
            caroStatusLabel.setText("Bạn thắng!");
            disableCaroBoard();
            sendRaw("CARO_MOVE|" + encode(nameField.getText().trim()) + "|" + encode(String.valueOf(row)) + "|" + encode(String.valueOf(col)));
            sendRaw("CARO_RESULT|" + encode(nameField.getText().trim()) + "|" + encode("Bạn thua"));
            return;
        }

        if (isCaroDraw()) {
            caroGameActive = false;
            caroStatusLabel.setText("Hòa.");
            disableCaroBoard();
            sendRaw("CARO_MOVE|" + encode(nameField.getText().trim()) + "|" + encode(String.valueOf(row)) + "|" + encode(String.valueOf(col)));
            sendRaw("CARO_RESULT|" + encode(nameField.getText().trim()) + "|" + encode("Hòa"));
            return;
        }

        caroMyTurn = false;
        caroStatusLabel.setText("Đã gửi nước đi. Đang chờ đối thủ...");
        updateCaroBoardUi();
        sendRaw("CARO_MOVE|" + encode(nameField.getText().trim()) + "|" + encode(String.valueOf(row)) + "|" + encode(String.valueOf(col)));
    }

    private void applyOpponentMove(String sender, int row, int col) {
        if (caroBoard == null || !caroGameActive) {
            startCaroGame(sender, false);
        }
        if (caroBoard[row][col] == null || caroBoard[row][col].isEmpty()) {
            caroBoard[row][col] = caroOpponentSymbol;
        }

        if (hasCaroWinner(caroOpponentSymbol)) {
            updateCaroBoardUi();
            caroGameActive = false;
            caroStatusLabel.setText("Bạn thua.");
            disableCaroBoard();
            return;
        }

        if (isCaroDraw()) {
            updateCaroBoardUi();
            caroGameActive = false;
            caroStatusLabel.setText("Hòa.");
            disableCaroBoard();
            return;
        }

        caroMyTurn = true;
        caroStatusLabel.setText("Đến lượt bạn.");
        updateCaroBoardUi();
    }

    private void updateCaroBoardUi() {
        if (caroBoard == null || caroButtons == null) {
            return;
        }
        for (int row = 0; row < CARO_SIZE; row++) {
            for (int col = 0; col < CARO_SIZE; col++) {
                if (caroBoard[row][col] == null) {
                    caroBoard[row][col] = "";
                }
                caroButtons[row][col].setText(caroBoard[row][col].isEmpty() ? " " : caroBoard[row][col]);
                caroButtons[row][col].setDisable(!caroGameActive || !caroMyTurn || !caroBoard[row][col].isEmpty());
            }
        }
    }

    private void disableCaroBoard() {
        if (caroButtons == null) {
            return;
        }
        for (int row = 0; row < CARO_SIZE; row++) {
            for (int col = 0; col < CARO_SIZE; col++) {
                caroButtons[row][col].setDisable(true);
            }
        }
    }

    private boolean hasCaroWinner(String symbol) {
        for (int i = 0; i < CARO_SIZE; i++) {
            if (symbol.equals(caroBoard[i][0]) && symbol.equals(caroBoard[i][1]) && symbol.equals(caroBoard[i][2])) {
                return true;
            }
            if (symbol.equals(caroBoard[0][i]) && symbol.equals(caroBoard[1][i]) && symbol.equals(caroBoard[2][i])) {
                return true;
            }
        }
        return (symbol.equals(caroBoard[0][0]) && symbol.equals(caroBoard[1][1]) && symbol.equals(caroBoard[2][2]))
                || (symbol.equals(caroBoard[0][2]) && symbol.equals(caroBoard[1][1]) && symbol.equals(caroBoard[2][0]));
    }

    private boolean isCaroDraw() {
        for (int row = 0; row < CARO_SIZE; row++) {
            for (int col = 0; col < CARO_SIZE; col++) {
                if (caroBoard[row][col] == null || caroBoard[row][col].isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    private void sendRpsInvite() {
        String[] options = {"Kéo", "Búa", "Bao"};
        ChoiceDialog<String> dialog = new ChoiceDialog<>(options[0], options);
        dialog.setTitle("Oẳn tù tì TCP");
        dialog.setHeaderText("Chọn nước đi của bạn trước khi gửi lời mời");
        dialog.setContentText("Lựa chọn:");
        Optional<String> choice = dialog.showAndWait();
        if (choice.isEmpty()) {
            return;
        }

        String myChoice = choice.get();
        addSystemLine("Bạn đã gửi lời mời chơi oẳn tù tì qua TCP. Nước đi của bạn đã được khóa.");
        sendRaw("RPS_INVITE|" + encode(nameField.getText().trim()) + "|" + encode(myChoice));
    }

    private void showRpsCard(String sender, String inviterChoice) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(10));
        card.setStyle("-fx-background-color: #f4f8ec; -fx-border-color: #7cb342; -fx-border-radius: 6; -fx-background-radius: 6;");

        Label title = new Label(sender + " mời bạn chơi oẳn tù tì TCP:");
        title.setStyle("-fx-font-weight: bold;");
        Label info = new Label("Chọn một trong ba đáp án bên dưới. Kết quả sẽ dựa trên lựa chọn thật của cả hai bên.");
        info.setWrapText(true);

        HBox choices = new HBox(8);
        Label resultLabel = new Label();
        String[] options = {"Kéo", "Búa", "Bao"};

        for (String option : options) {
            Button optionButton = new Button(option);
            optionButton.setOnAction(e -> {
                String result = compareRps(option, inviterChoice);
                resultLabel.setText("Bạn chọn " + option + ", đối thủ chọn " + inviterChoice + ": " + result);
                sendRaw("RPS_CHOICE|" + encode(nameField.getText().trim())
                        + "|" + encode(option)
                        + "|" + encode(inviterChoice));
                choices.getChildren().forEach(node -> node.setDisable(true));
            });
            choices.getChildren().add(optionButton);
        }

        card.getChildren().addAll(title, info, choices, resultLabel);
        messageBox.getChildren().add(card);
    }

    private void handleRpsChoiceFromPeer(String responderName, String responderChoice, String myChoice) {
        String resultForInviter = compareRps(myChoice, responderChoice);
        addSystemLine("Oẳn tù tì với " + responderName + ": bạn chọn " + myChoice + ", đối thủ chọn "
                + responderChoice + " -> " + resultForInviter);

        String resultForResponder = invertRpsResult(resultForInviter);
        sendRaw("RPS_RESULT|" + encode(nameField.getText().trim())
                + "|" + encode(myChoice)
                + "|" + encode(responderChoice)
                + "|" + encode(resultForResponder)
                + "|" + encode(responderName));
    }

    private void showRpsResultFromPeer(String inviterName, String inviterChoice, String myChoice, String result, String playerName) {
        addSystemLine("Kết quả oẳn tù tì với " + inviterName + ": bạn chọn " + myChoice + ", đối thủ chọn "
                + inviterChoice + " -> " + result);
    }

    private String compareRps(String playerChoice, String opponentChoice) {
        if (playerChoice.equals(opponentChoice)) {
            return "Hòa";
        }
        if ((playerChoice.equals("Kéo") && opponentChoice.equals("Bao"))
                || (playerChoice.equals("Búa") && opponentChoice.equals("Kéo"))
                || (playerChoice.equals("Bao") && opponentChoice.equals("Búa"))) {
            return "Bạn thắng";
        }
        return "Bạn thua";
    }

    private String invertRpsResult(String result) {
        if (result.equals("Bạn thắng")) {
            return "Bạn thua";
        }
        if (result.equals("Bạn thua")) {
            return "Bạn thắng";
        }
        return "Hòa";
    }

    private Optional<String> askInput(String title, String header, String defaultValue) {
        TextInputDialog dialog = new TextInputDialog(defaultValue);
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        dialog.setContentText("Giá trị:");
        return dialog.showAndWait();
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

    private void addLine(String text, boolean ownMessage) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMaxWidth(420);
        label.setStyle(ownMessage
                ? "-fx-padding: 8 12 8 12; -fx-text-fill: black; -fx-background-color: #d9fdd3; -fx-background-radius: 14;"
                : "-fx-padding: 8 12 8 12; -fx-text-fill: black; -fx-background-color: #f1f0f0; -fx-background-radius: 14;");

        HBox wrapper = new HBox(label);
        wrapper.setFillHeight(false);
        wrapper.setAlignment(ownMessage ? Pos.CENTER_LEFT : Pos.CENTER_RIGHT);
        wrapper.setPadding(new Insets(2, 0, 2, 0));
        messageBox.getChildren().add(wrapper);
    }

    private void addSystemLine(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setStyle("-fx-padding: 4 0 4 0; -fx-text-fill: #006400; -fx-font-style: italic;");
        messageBox.getChildren().add(label);
    }

    private void addFileContent(String fileName, String encodedBytes, boolean image, boolean ownMessage) {
        try {
            byte[] bytes = Base64.getDecoder().decode(encodedBytes);
            if (image) {
                ImageView iv = new ImageView(new Image(new ByteArrayInputStream(bytes)));
                iv.setFitWidth(220);
                iv.setPreserveRatio(true);
                iv.setStyle("-fx-cursor: hand;");
                iv.setOnMouseClicked(event -> {
                    if (event.getButton() == MouseButton.PRIMARY) {
                        openImageViewer(fileName, bytes);
                    }
                });

                Button saveButton = new Button("Lưu ảnh");
                saveButton.setOnAction(e -> saveBytesToFile(fileName, bytes));
                Label hintLabel = new Label("Nhấp vào ảnh để xem lớn");
                hintLabel.setStyle("-fx-font-size: 11; -fx-text-fill: #666666;");

                VBox content = new VBox(6, iv, new HBox(8, saveButton), hintLabel);
                addContentBubble(content, ownMessage);
            } else {
                Label fileLabel = new Label(fileName);
                fileLabel.setStyle("-fx-font-weight: bold;");
                Label subLabel = new Label("Tệp văn bản - nhấn Xem để mở hoặc Lưu để tải về");
                subLabel.setStyle("-fx-text-fill: #666666;");

                Button viewButton = new Button("Xem");
                viewButton.setOnAction(e -> openTextViewer(fileName, bytes));
                Button saveButton = new Button("Lưu");
                saveButton.setOnAction(e -> saveBytesToFile(fileName, bytes));

                VBox content = new VBox(6, fileLabel, subLabel, new HBox(8, viewButton, saveButton));
                addContentBubble(content, ownMessage);
            }
        } catch (Exception ex) {
            addLine("[Không mở được nội dung]", ownMessage);
        }
    }

    private void addContentBubble(javafx.scene.Node content, boolean ownMessage) {
        VBox bubble = new VBox(content);
        bubble.setMaxWidth(440);
        bubble.setStyle(ownMessage
                ? "-fx-padding: 8 12 8 12; -fx-background-color: #d9fdd3; -fx-background-radius: 14;"
                : "-fx-padding: 8 12 8 12; -fx-background-color: #f1f0f0; -fx-background-radius: 14;");

        HBox wrapper = new HBox(bubble);
        wrapper.setAlignment(ownMessage ? Pos.CENTER_LEFT : Pos.CENTER_RIGHT);
        wrapper.setPadding(new Insets(2, 0, 2, 0));
        messageBox.getChildren().add(wrapper);
    }

    private void openImageViewer(String fileName, byte[] bytes) {
        Stage imageStage = new Stage();
        imageStage.setTitle(fileName);

        ImageView imageView = new ImageView(new Image(new ByteArrayInputStream(bytes)));
        imageView.setPreserveRatio(true);
        imageView.setFitWidth(900);
        imageView.setFitHeight(700);

        ScrollPane pane = new ScrollPane(imageView);
        pane.setFitToWidth(true);
        pane.setFitToHeight(true);

        BorderPane root = new BorderPane(pane);
        root.setPadding(new Insets(10));
        imageStage.setScene(new Scene(root, 950, 750));
        imageStage.show();
    }

    private void openTextViewer(String fileName, byte[] bytes) {
        Stage textStage = new Stage();
        textStage.setTitle(fileName);

        TextArea textArea = new TextArea(new String(bytes, StandardCharsets.UTF_8));
        textArea.setEditable(false);
        textArea.setWrapText(true);

        BorderPane root = new BorderPane(textArea);
        root.setPadding(new Insets(10));
        textStage.setScene(new Scene(root, 700, 500));
        textStage.show();
    }

    private void saveBytesToFile(String defaultFileName, byte[] bytes) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Lưu tệp");
        chooser.setInitialFileName(defaultFileName);
        File file = chooser.showSaveDialog(stage);
        if (file == null) {
            return;
        }
        try {
            Files.write(file.toPath(), bytes);
            showAlert("Đã lưu tệp: " + file.getAbsolutePath());
        } catch (Exception ex) {
            showAlert("Không thể lưu tệp: " + ex.getMessage());
        }
    }

    private String encode(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private String decode(String base64) {
        return new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
    }

    private boolean isDouble(String value) {
        try {
            Double.parseDouble(value);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String formatEquation(String a, String b, String c) {
        return a + "x² + " + b + "x + " + c + " = 0";
    }

    private String formatNumber(double value) {
        if (value == (long) value) {
            return String.valueOf((long) value);
        }
        return String.format("%.2f", value);
    }

    private void showAlert(String msg) {
        Platform.runLater(() -> new Alert(Alert.AlertType.INFORMATION, msg).show());
    }
}
