package client.ui;

import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.Locale;

/**
 * Round colored avatar with the user's initials, colored from the name like in Telegram.
 */
public class Avatar extends StackPane {

    private static final Color[] PALETTE = {
            Color.web("#e17076"), Color.web("#faa774"), Color.web("#a695e7"), Color.web("#7bc862"),
            Color.web("#6ec9cb"), Color.web("#65aadd"), Color.web("#ee7aae")
    };

    /** A circle (with a light-to-normal gradient) and the white initials on top of it. */
    public Avatar(String name, double size) {
        Color base = colorFor(name);
        Circle circle = new Circle(size / 2);
        circle.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, base.deriveColor(0, 0.85, 1.12, 1)), new Stop(1, base)));

        Label initials = new Label(initialsOf(name));
        initials.setTextFill(Color.WHITE);
        initials.setFont(Font.font(Font.getDefault().getFamily(), FontWeight.BOLD, size * 0.38));

        getChildren().addAll(circle, initials);
        setMinSize(size, size);
        setPrefSize(size, size);
        setMaxSize(size, size);
    }

    /** Avatar of the AI assistant: a robot on a blue-violet gradient. */
    public static StackPane bot(double size) {
        Circle circle = new Circle(size / 2);
        circle.setFill(new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#5b8def")), new Stop(1, Color.web("#a35bef"))));
        StackPane avatar = new StackPane(circle, EmojiText.icon("🤖", size * 0.6));
        avatar.setMinSize(size, size);
        avatar.setPrefSize(size, size);
        avatar.setMaxSize(size, size);
        return avatar;
    }

    /**
     * Same name -> same color, always: hashCode() turns the name into a number, and floorMod
     * turns that number into an index 0..6 in the palette (floorMod is never negative).
     */
    public static Color colorFor(String name) {
        return PALETTE[Math.floorMod(name == null ? 0 : name.hashCode(), PALETTE.length)];
    }

    /** "Alice Martin" -> "AM", "bob" -> "B" (first letter of the first two words). */
    private static String initialsOf(String name) {
        String[] parts = name == null ? new String[0] : name.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(2, parts.length); i++) {
            if (!parts[i].isEmpty()) {
                sb.appendCodePoint(parts[i].codePointAt(0));
            }
        }
        return sb.isEmpty() ? "?" : sb.toString().toUpperCase(Locale.ROOT);
    }
}
