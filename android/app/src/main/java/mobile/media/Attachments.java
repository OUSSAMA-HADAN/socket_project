package mobile.media;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import client.util.FileKinds;
import common.Config;
import common.Message;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Reading picked files, preparing photos, and handing received files to other apps.
 * Everything here may block (disk, decoding): call it off the main thread.
 */
public final class Attachments {

    /** A file chosen by the user, ready to be sent. */
    public static final class Picked {
        public final String name;
        public final byte[] data;

        Picked(String name, byte[] data) {
            this.name = name;
            this.data = data;
        }
    }

    public static class TooLargeException extends IOException {
        TooLargeException(String name) {
            super(name + " is bigger than " + Config.MAX_ATTACHMENT_BYTES / (1024 * 1024) + " MB.");
        }
    }

    private Attachments() {
    }

    /** Any file from the system picker: its real name and bytes (25 MB max, like the desktop). */
    public static Picked read(ContentResolver resolver, Uri uri) throws IOException {
        String name = displayName(resolver, uri);
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) {
                throw new IOException("Cannot open " + name);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
                if (out.size() > Config.MAX_ATTACHMENT_BYTES) {
                    throw new TooLargeException(name);
                }
            }
            return new Picked(name, out.toByteArray());
        }
    }

    /**
     * A photo from the gallery. Phones often store HEIC or WebP, which the desktop app can't
     * display: those are converted to JPEG so every client can show the picture.
     * Returns null if the file isn't a readable image at all.
     */
    public static Picked readPhoto(ContentResolver resolver, Uri uri) throws IOException {
        Picked file = read(resolver, uri);
        if (FileKinds.IMAGE_EXTENSIONS.contains(FileKinds.extension(file.name)) && isImage(file.data)) {
            return file; // png, jpg, gif, bmp: everyone can show it as is
        }
        Bitmap bitmap = BitmapFactory.decodeByteArray(file.data, 0, file.data.length);
        if (bitmap == null) {
            return null;
        }
        ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, jpeg);
        bitmap.recycle();
        String base = file.name.contains(".") ? file.name.substring(0, file.name.lastIndexOf('.')) : file.name;
        return new Picked(base + ".jpg", jpeg.toByteArray());
    }

    public static boolean isImage(byte[] data) {
        int[] size = imageSize(data);
        return size[0] > 0 && size[1] > 0;
    }

    /** Width and height without decoding the pixels (cheap). */
    public static int[] imageSize(byte[] data) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, options);
        return new int[]{options.outWidth, options.outHeight};
    }

    /** Decodes a picture no bigger than needed for the given size (saves a lot of memory). */
    public static Bitmap decode(byte[] data, int maxWidth, int maxHeight) {
        int[] size = imageSize(data);
        int sample = 1;
        while (size[0] / (sample * 2) >= maxWidth || size[1] / (sample * 2) >= maxHeight) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        return BitmapFactory.decodeByteArray(data, 0, data.length, options);
    }

    /** Length of a WAV file (our voice notes) from its header, or -1 if it isn't a plain WAV. */
    public static long wavDurationMillis(byte[] data) {
        if (data == null || data.length < 44 || data[0] != 'R' || data[1] != 'I' || data[8] != 'W') {
            return -1;
        }
        int byteRate = (data[28] & 0xFF) | (data[29] & 0xFF) << 8 | (data[30] & 0xFF) << 16 | (data[31] & 0xFF) << 24;
        return byteRate <= 0 ? -1 : (data.length - 44) * 1000L / byteRate;
    }

    /**
     * The message's bytes as a real file in our cache (cache/shared/&lt;id&gt;/&lt;name&gt;),
     * written once, so MediaPlayer or another app can open it.
     */
    public static File cacheFile(Context context, Message message) throws IOException {
        File dir = new File(context.getCacheDir(), "shared/" + message.getId());
        File file = new File(dir, safeName(message.getFileName()));
        if (!file.isFile() || file.length() != message.getSize()) {
            if (!dir.isDirectory() && !dir.mkdirs()) {
                throw new IOException("Cannot write to the app cache.");
            }
            try (OutputStream out = new FileOutputStream(file)) {
                out.write(message.getData());
            }
        }
        return file;
    }

    public static void write(ContentResolver resolver, Uri target, byte[] data) throws IOException {
        try (OutputStream out = resolver.openOutputStream(target)) {
            if (out == null) {
                throw new IOException("Cannot write the file.");
            }
            out.write(data);
        }
    }

    public static String mimeType(String fileName) {
        String type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(FileKinds.extension(fileName));
        return type == null ? "application/octet-stream" : type;
    }

    private static String displayName(ContentResolver resolver, Uri uri) {
        try (Cursor cursor = resolver.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) {
                return cursor.getString(0);
            }
        } catch (RuntimeException ignored) {
            // some providers don't support the query
        }
        String last = uri.getLastPathSegment();
        return last == null ? "file" : last;
    }

    /** File names come from other users: never let one escape the folder. */
    private static String safeName(String name) {
        String clean = name == null ? "file" : name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return clean.isEmpty() || clean.startsWith(".") ? "file" + clean : clean;
    }
}
