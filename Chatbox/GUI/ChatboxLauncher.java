package Chatbox.GUI;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class ChatboxLauncher {
    public static void main(String[] args) {
        Application.launch(ChatboxLauncherFx.class, args);
    }

    public static class ChatboxLauncherFx extends Application {
        @Override
        public void start(Stage primaryStage) {
            Label titleLabel = new Label("Chatbox - Chọn chế độ chạy");
            titleLabel.setStyle("-fx-font-size: 28px; -fx-font-weight: bold;");

            Label subtitleLabel = new Label("Chọn một trong hai giao thức để mở chương trình chính.");
            subtitleLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #666666;");

            Button udpButton = createMenuButton("Mở UDP Main Menu");
            udpButton.setOnAction(event -> openWindow(new UdpMainMenu(), "UDP Main Menu"));

            Button tcpButton = createMenuButton("Mở TCP Main Menu");
            tcpButton.setOnAction(event -> openWindow(new TcpMainMenu(), "TCP Main Menu"));

            VBox root = new VBox(24, titleLabel, subtitleLabel, udpButton, tcpButton);
            root.setAlignment(Pos.CENTER);
            root.setPadding(new Insets(40));
            root.setStyle("-fx-background-color: linear-gradient(to bottom, #f8fbff, #eef3ff);");

            Scene scene = new Scene(root, 560, 340);
            primaryStage.setTitle("Chatbox Launcher");
            primaryStage.setScene(scene);
            primaryStage.setResizable(false);
            primaryStage.show();
        }

        private Button createMenuButton(String text) {
            Button button = new Button(text);
            button.setPrefWidth(300);
            button.setPrefHeight(44);
            button.setStyle("-fx-font-size: 16px; -fx-background-radius: 8; -fx-border-radius: 8;");
            return button;
        }

        private void openWindow(Application app, String fallbackTitle) {
            try {
                Stage stage = new Stage();
                app.start(stage);
                if (stage.getTitle() == null || stage.getTitle().isBlank()) {
                    stage.setTitle(fallbackTitle);
                }
            } catch (Exception e) {
                throw new RuntimeException("Không thể mở cửa sổ: " + fallbackTitle, e);
            }
        }
    }
}
