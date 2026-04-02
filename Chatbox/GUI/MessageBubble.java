package Chatbox.GUI;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

public class MessageBubble extends HBox {

    public MessageBubble(String text, boolean isSender) {
        setPadding(new Insets(4));
        setAlignment(isSender ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        Label label = new Label(text);
        label.setWrapText(true);
        label.setMaxWidth(320);
        label.setPadding(new Insets(10, 14, 10, 14));

        if (isSender) {
            label.setStyle(
                "-fx-background-color: #1877f2;" +
                "-fx-text-fill: white;" +
                "-fx-background-radius: 18;"
            );
        } else {
            label.setStyle(
                "-fx-background-color: #e4e6eb;" +
                "-fx-text-fill: black;" +
                "-fx-background-radius: 18;"
            );
        }

        getChildren().add(label);
    }
}