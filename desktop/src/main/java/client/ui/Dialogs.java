package client.ui;

import javafx.scene.control.Alert;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public final class Dialogs {

    private Dialogs() {
    }

    public static void error(Window owner, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        if (owner != null) {
            alert.initOwner(owner);
        }
        alert.setTitle(Theme.APP_NAME);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.getDialogPane().getStylesheets().add(Theme.STYLESHEET);
        alert.showAndWait();
    }

    /** Asks where to save an attachment and writes it there. */
    public static void saveFile(Window owner, String fileName, byte[] data) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save file");
        chooser.setInitialFileName(fileName);
        File target = chooser.showSaveDialog(owner);
        if (target == null) {
            return;
        }
        try {
            Files.write(target.toPath(), data);
        } catch (IOException e) {
            error(owner, "Could not save the file", e.getMessage());
        }
    }
}
