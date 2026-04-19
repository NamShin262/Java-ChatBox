package Chatbox.GUI;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
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
import java.util.Base64;
import java.util.Optional;

import Chatbox.network.TcpFileReceiver;
import Chatbox.network.TcpFileSender;

public class TcpMainMenu extends Application {
    public static void main(String[] args) {
        launch(args);
    }

    private final int startPort = 7000;
    private final int endPort = 7010;
    private int localPort = startPort;

    private VBox messageBox;
    private TextField nameField;
    private TextField peerPortField;
    private TextField messageField;
    private Label statusLabel;
    private Stage stage;

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        localPort = findFreePort();

        nameField = new TextField("User");
        peerPortField = new TextField(String.valueOf(localPort == startPort ? startPort + 1 : startPort));
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

        Button quizButton = new Button("Tạo câu đố");
        quizButton.setOnAction(e -> sendQuiz());

        HBox top = new HBox(10,
                new Label("Tên:"), nameField,
                new Label("Peer port:"), peerPortField,
                statusLabel);
        top.setAlignment(Pos.CENTER_LEFT);
        top.setPadding(new Insets(10));

        HBox bottom = new HBox(10, messageField, sendButton, fileButton, quadraticButton, quizButton);
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

        addSystemLine("TCP demo: chat, gửi file, giải phương trình bậc 2 và trò chơi đố chữ.");
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
        String[] parts = line.split("\\|", 6);
        if (parts.length < 1) {
            return;
        }
        Platform.runLater(() -> {
            switch (parts[0]) {
                case "MSG" -> {
                    if (parts.length >= 3) {
                        addLine(decode(parts[1]) + ": " + decode(parts[2]));
                    }
                }
                case "FILE" -> {
                    String name = TcpFileReceiver.getFileName(line);
                    String payload = TcpFileReceiver.getEncodedData(line);
                    if (name == null || payload == null) {
                        return;
                    }
                    boolean image = name.toLowerCase().matches(".*\\.(png|jpg|jpeg|gif|webp)$");
                    addLine((image ? "[Ảnh] " : "[File] ") + name);
                    addFileContent(payload, image);
                }
                case "QUAD" -> {
                    if (parts.length >= 5) {
                        showQuadraticResult(decode(parts[1]), decode(parts[2]), decode(parts[3]), decode(parts[4]));
                    }
                }
                case "QUIZ" -> {
                    if (parts.length >= 4) {
                        showQuizCard(decode(parts[1]), decode(parts[2]), decode(parts[3]));
                    }
                }
                case "QUIZ_RESULT" -> {
                    if (parts.length >= 4) {
                        addSystemLine(decode(parts[1]) + " trả lời câu đố của bạn: " + decode(parts[2])
                                + " -> " + decode(parts[3]));
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
        addLine(nameField.getText().trim() + ": " + text);
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
            addLine(nameField.getText().trim() + ": " + (image ? "[Ảnh] " : "[File] ") + name);
            addFileContent(encodedBytes, image);
            sendRaw(payload);
        } catch (Exception ex) {
            showAlert("Không thể gửi file: " + ex.getMessage());
        }
    }

    private void sendQuadraticProblem() {
        Optional<String> aInput = askInput("Phương trình bậc 2", "Nhập hệ số a:", "1");
        if (aInput.isEmpty()) {
            return;
        }
        Optional<String> bInput = askInput("Phương trình bậc 2", "Nhập hệ số b:", "-3");
        if (bInput.isEmpty()) {
            return;
        }
        Optional<String> cInput = askInput("Phương trình bậc 2", "Nhập hệ số c:", "2");
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

    private void sendQuiz() {
        Optional<String> hintInput = askInput("Tạo câu đố", "Nhập gợi ý cho câu đố:", "Tên thủ đô của Việt Nam?");
        if (hintInput.isEmpty()) {
            return;
        }
        Optional<String> answerInput = askInput("Tạo câu đố", "Nhập đáp án:", "Hà Nội");
        if (answerInput.isEmpty()) {
            return;
        }

        String hint = hintInput.get().trim();
        String answer = answerInput.get().trim();
        if (hint.isEmpty() || answer.isEmpty()) {
            showAlert("Gợi ý và đáp án không được để trống.");
            return;
        }

        addSystemLine("Bạn đã gửi câu đố: " + hint);
        sendRaw("QUIZ|" + encode(nameField.getText().trim()) + "|" + encode(hint) + "|" + encode(answer));
    }

    private void showQuizCard(String sender, String hint, String answer) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(10));
        card.setStyle("-fx-background-color: #eef6ff; -fx-border-color: #6aa9ff; -fx-border-radius: 6; -fx-background-radius: 6;");

        Label title = new Label(sender + " gửi câu đố:");
        title.setStyle("-fx-font-weight: bold;");
        Label hintLabel = new Label("Gợi ý: " + hint);
        hintLabel.setWrapText(true);

        TextField answerField = new TextField();
        answerField.setPromptText("Nhập đáp án của bạn");
        Button submitButton = new Button("Trả lời");
        Label resultLabel = new Label();

        submitButton.setOnAction(e -> {
            String guess = answerField.getText().trim();
            if (guess.isEmpty()) {
                resultLabel.setText("Bạn chưa nhập đáp án.");
                return;
            }
            boolean correct = guess.equalsIgnoreCase(answer.trim());
            String result = correct ? "Đúng rồi" : "Sai rồi";
            resultLabel.setText(result + ". Đáp án: " + answer);
            sendRaw("QUIZ_RESULT|" + encode(nameField.getText().trim()) + "|" + encode(guess) + "|" + encode(result));
            submitButton.setDisable(true);
        });

        HBox actionRow = new HBox(8, answerField, submitButton);
        HBox.setHgrow(answerField, Priority.ALWAYS);
        card.getChildren().addAll(title, hintLabel, actionRow, resultLabel);
        messageBox.getChildren().add(card);
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

    private void addLine(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setStyle("-fx-padding: 2 0 2 0; -fx-text-fill: black;");
        messageBox.getChildren().add(label);
    }

    private void addSystemLine(String text) {
        Label label = new Label("[TCP App] " + text);
        label.setWrapText(true);
        label.setStyle("-fx-padding: 4 0 4 0; -fx-text-fill: #006400; -fx-font-style: italic;");
        messageBox.getChildren().add(label);
    }

    private void addFileContent(String encodedBytes, boolean image) {
        try {
            if (image) {
                byte[] bytes = Base64.getDecoder().decode(encodedBytes);
                ImageView iv = new ImageView(new Image(new ByteArrayInputStream(bytes)));
                iv.setFitWidth(220);
                iv.setPreserveRatio(true);
                messageBox.getChildren().add(iv);
            } else {
                byte[] bytes = Base64.getDecoder().decode(encodedBytes);
                String text = new String(bytes, StandardCharsets.UTF_8);
                TextArea ta = new TextArea(text);
                ta.setEditable(false);
                ta.setWrapText(true);
                ta.setPrefRowCount(4);
                ta.setPrefWidth(320);
                messageBox.getChildren().add(ta);
            }
        } catch (Exception ex) {
            messageBox.getChildren().add(new Label("[Không mở được nội dung]"));
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
