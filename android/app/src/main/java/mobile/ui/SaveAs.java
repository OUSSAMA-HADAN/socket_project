package mobile.ui;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import common.Message;
import mobile.media.Attachments;

import java.io.IOException;

/**
 * "Save as…" for received attachments: Android's own file dialog lets the user pick the folder
 * (Downloads, Documents...), so the app needs no storage permission. Create it in onCreate.
 */
final class SaveAs {

    private final ComponentActivity activity;
    private final ActivityResultLauncher<String> launcher;
    private Message pending;

    SaveAs(ComponentActivity activity) {
        this.activity = activity;
        this.launcher = activity.registerForActivityResult(
                new ActivityResultContracts.CreateDocument("*/*"), this::write);
    }

    void save(Message message) {
        pending = message;
        launcher.launch(message.getFileName());
    }

    private void write(Uri target) {
        Message message = pending;
        pending = null;
        if (target == null || message == null) {
            return; // cancelled
        }
        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> { // disk I/O off the main thread
            String result;
            try {
                Attachments.write(activity.getContentResolver(), target, message.getData());
                result = "Saved " + message.getFileName();
            } catch (IOException e) {
                result = "Could not save: " + e.getMessage();
            }
            String text = result;
            main.post(() -> Toast.makeText(activity, text, Toast.LENGTH_SHORT).show());
        }, "save-file").start();
    }
}
