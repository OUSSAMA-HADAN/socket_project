package client.ui;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.shape.SVGPath;

/**
 * Vector icons (24x24 Material paths) so buttons look the same on every system font.
 * Color them from CSS with the {@code .icon} style class.
 * <p>
 * Each constant is an SVG "path": drawing instructions on a 24x24 grid (M = move to,
 * L = line to, c = curve, z = close the shape). They come from Google's Material icons.
 */
public final class Icons {

    public static final String SEND = "M2.01 21L23 12 2.01 3 2 10l15 2-15 2z";
    public static final String MIC = "M12 14c1.66 0 2.99-1.34 2.99-3L15 5c0-1.66-1.34-3-3-3S9 3.34 9 5v6c0 1.66 1.34 3 3 3z"
            + "m5.3-3c0 3-2.54 5.1-5.3 5.1S6.7 14 6.7 11H5c0 3.41 2.72 6.23 6 6.72V21h2v-3.28c3.28-.48 6-3.3 6-6.72h-1.7z";
    public static final String ATTACH = "M16.5 6v11.5c0 2.21-1.79 4-4 4s-4-1.79-4-4V5c0-1.38 1.12-2.5 2.5-2.5s2.5 1.12 2.5 2.5"
            + "v10.5c0 .55-.45 1-1 1s-1-.45-1-1V6H10v9.5c0 1.38 1.12 2.5 2.5 2.5s2.5-1.12 2.5-2.5V5c0-2.21-1.79-4-4-4"
            + "S7 2.79 7 5v12.5c0 3.04 2.46 5.5 5.5 5.5s5.5-2.46 5.5-5.5V6h-1.5z";
    public static final String EMOJI = "M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2z"
            + "M12 20c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8zm3.5-9c.83 0 1.5-.67 1.5-1.5S16.33 8 15.5 8"
            + " 14 8.67 14 9.5s.67 1.5 1.5 1.5zm-7 0c.83 0 1.5-.67 1.5-1.5S9.33 8 8.5 8 7 8.67 7 9.5 7.67 11 8.5 11z"
            + "m3.5 6.5c2.33 0 4.31-1.46 5.11-3.5H6.89c.8 2.04 2.78 3.5 5.11 3.5z";
    public static final String LOGOUT = "M17 7l-1.41 1.41L18.17 11H8v2h10.17l-2.58 2.58L17 17l5-5zM4 5h8V3H4c-1.1 0-2 .9-2 2"
            + "v14c0 1.1.9 2 2 2h8v-2H4V5z";
    public static final String PLAY = "M8 5v14l11-7z";
    public static final String PAUSE = "M6 19h4V5H6v14zm8-14v14h4V5h-4z";
    public static final String FILE = "M6 2c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6H6zm7 7V3.5"
            + "L18.5 9H13z";
    public static final String DOWNLOAD = "M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z";
    public static final String PHOTO = "M21 19V5c0-1.1-.9-2-2-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2z"
            + "M8.5 13.5l2.5 3.01L14.5 12l4.5 6H5l3.5-4.5z";
    public static final String MUSIC = "M12 3v10.55c-.59-.34-1.27-.55-2-.55-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4V7h4V3h-6z";

    private Icons() {
    }

    /** Creates an icon node at the requested size in pixels, e.g. {@code Icons.of(Icons.SEND, 26)}. */
    public static Node of(String path, double size) {
        SVGPath svg = new SVGPath();
        svg.setContent(path);
        svg.getStyleClass().add("icon");
        double scale = size / 24.0;
        svg.setScaleX(scale);
        svg.setScaleY(scale);
        return new Group(svg); // Group so layout uses the scaled size
    }
}
