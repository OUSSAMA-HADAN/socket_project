package mobile.ui;

import android.content.Context;
import android.view.View;

import androidx.activity.ComponentActivity;
import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Small helpers shared by the screens.
 */
final class Ui {

    private Ui() {
    }

    /**
     * Draws the app behind the status and navigation bars (Telegram look: the blue header runs
     * up under the clock) and keeps content out of the way: the header gets the status bar height
     * as top padding, the screen gets the navigation bar or the keyboard height at the bottom.
     */
    static void edgeToEdge(ComponentActivity activity, View root, View header) {
        EdgeToEdge.enable(activity);
        WindowCompat.getInsetsController(activity.getWindow(), root).setAppearanceLightStatusBars(false);
        int headerTop = header.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets keyboard = insets.getInsets(WindowInsetsCompat.Type.ime());
            header.setPadding(header.getPaddingLeft(), headerTop + bars.top, header.getPaddingRight(),
                    header.getPaddingBottom());
            view.setPadding(bars.left, 0, bars.right, Math.max(bars.bottom, keyboard.bottom));
            return WindowInsetsCompat.CONSUMED;
        });
    }

    static int dp(Context context, float dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }
}
