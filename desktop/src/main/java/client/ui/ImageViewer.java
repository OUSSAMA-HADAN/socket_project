package client.ui;

import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Full-size image window, opened when a photo in the chat is clicked.
 */
public final class ImageViewer {

    private ImageViewer() {
    }

    public static void show(Window owner, Image image, String fileName, byte[] data) {
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.setTitle(fileName);

        ImageView view = new ImageView(image);
        view.setPreserveRatio(true);
        view.setSmooth(true);

        StackPane center = new StackPane(view);
        center.getStyleClass().add("viewer-background");
        center.setMinSize(0, 0);
        view.fitWidthProperty().bind(Bindings.min(image.getWidth(), center.widthProperty().subtract(40)));
        view.fitHeightProperty().bind(Bindings.min(image.getHeight(), center.heightProperty().subtract(40)));

        Button save = new Button("Save");
        save.getStyleClass().add("primary-button");
        save.setOnAction(e -> Dialogs.saveFile(stage, fileName, data));
        Button close = new Button("Close");
        close.getStyleClass().add("link-button");
        close.setOnAction(e -> stage.close());
        HBox bar = new HBox(10, close, save);
        bar.getStyleClass().add("viewer-bar");
        bar.setAlignment(Pos.CENTER_RIGHT);

        BorderPane root = new BorderPane(center);
        root.setBottom(bar);

        Scene scene = new Scene(root,
                Math.clamp(image.getWidth() + 80, 400, 1100),
                Math.clamp(image.getHeight() + 140, 300, 800));
        scene.getStylesheets().add(Theme.STYLESHEET);
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                stage.close();
            }
        });
        stage.setScene(scene);
        stage.show();
    }
}
