package mobile.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import java.util.Locale;

/**
 * Round avatar with the user's initials on a color picked from the name, like in Telegram
 * (same palette and rule as the desktop Avatar, so a user has the same color everywhere).
 * The AI assistant gets a robot on a blue-violet gradient. Optional green "online" dot.
 */
public class AvatarView extends View {

    private static final int[] PALETTE = {
            0xFFE17076, 0xFFFAA774, 0xFFA695E7, 0xFF7BC862, 0xFF6EC9CB, 0xFF65AADD, 0xFFEE7AAE
    };
    private static final int ONLINE = 0xFF0AC630;

    private final Paint circle = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String name = "";
    private boolean ai;
    private boolean online;

    public AvatarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        text.setColor(Color.WHITE);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.DEFAULT_BOLD);
    }

    /** @param showOnline draw the green dot (private chats with someone online) */
    public void set(String name, boolean ai, boolean showOnline) {
        this.name = name == null ? "" : name;
        this.ai = ai;
        this.online = showOnline;
        updateShader();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateShader();
    }

    /** The gradient depends only on the name and the size: built here, not on every draw. */
    private void updateShader() {
        float r = Math.min(getWidth(), getHeight()) / 2f;
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        if (r <= 0) {
            return;
        }
        if (ai) {
            circle.setShader(new LinearGradient(cx - r, cy - r, cx + r, cy + r,
                    0xFF5B8DEF, 0xFFA35BEF, Shader.TileMode.CLAMP));
        } else {
            int base = colorFor(name);
            circle.setShader(new LinearGradient(cx, cy - r, cx, cy + r,
                    lighter(base), base, Shader.TileMode.CLAMP));
        }
    }

    /** Same color as the desktop for the same name (also used for sender names in groups). */
    public static int colorFor(String name) {
        return PALETTE[Math.floorMod(name == null ? 0 : name.hashCode(), PALETTE.length)];
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float size = Math.min(getWidth(), getHeight());
        float r = size / 2f;
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;

        canvas.drawCircle(cx, cy, r, circle);

        String label = ai ? "🤖" : initials(name);
        text.setTextSize(size * (ai ? 0.5f : 0.38f));
        float baseline = cy - (text.descent() + text.ascent()) / 2f;
        canvas.drawText(label, cx, baseline, text);

        if (online) {
            float dotR = size * 0.13f;
            float dx = cx + r * 0.72f;
            float dy = cy + r * 0.72f;
            dot.setColor(Color.WHITE);
            canvas.drawCircle(dx, dy, dotR + size * 0.035f, dot);
            dot.setColor(ONLINE);
            canvas.drawCircle(dx, dy, dotR, dot);
        }
    }

    /** The desktop's deriveColor(0, 0.85, 1.12): a bit less saturated, a bit brighter. */
    private static int lighter(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[1] *= 0.85f;
        hsv[2] = Math.min(1f, hsv[2] * 1.12f);
        return Color.HSVToColor(hsv);
    }

    private static String initials(String name) {
        String[] parts = name.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(2, parts.length); i++) {
            if (!parts[i].isEmpty()) {
                sb.appendCodePoint(parts[i].codePointAt(0));
            }
        }
        return sb.length() == 0 ? "?" : sb.toString().toUpperCase(Locale.ROOT);
    }
}
