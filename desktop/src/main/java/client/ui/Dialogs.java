package client.ui;

import javafx.scene.control.Alert;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * Small pop-up windows used across the app: error messages and "Save as…".
 * Must be called on the JavaFX thread (they show windows).
 */
public final class Dialogs {

    /** Only static helpers here: no instances. */
    private Dialogs() {
    }

    /** Shows an error pop-up and waits until the user closes it. */
    public static void error(Window owner, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        if (owner != null) {
            alert.initOwner(owner); // centered over our window
        }
        alert.setTitle(Theme.APP_NAME);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.getDialogPane().getStylesheets().add(Theme.STYLESHEET); // same look as the app
        alert.showAndWait();
    }

    /** Asks where to save an attachment and writes it there. */
    public static void saveFile(Window owner, String fileName, byte[] data) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save file");
        chooser.setInitialFileName(fileName);
        File target = chooser.showSaveDialog(owner);
        if (target == null) {
            return; // the user cancelled
        }
        try {
            Files.write(target.toPath(), data);
        } catch (IOException e) {
            error(owner, "Could not save the file", e.getMessage());
        }
    }
}
