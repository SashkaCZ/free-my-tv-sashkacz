package io.freemytv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import java.io.*;
import java.util.*;

/** Private, bounded photo copies: no storage permission or persistent provider access needed. */
final class PhotoAlbum {
    private PhotoAlbum() {}

    static List<Recs.Pick> picks(Context c) {
        List<Recs.Pick> out = new ArrayList<>();
        File[] files = new File(c.getFilesDir(), "photos").listFiles();
        if (files == null) return out;
        Arrays.sort(files);
        for (File f : files) if (f.getName().endsWith(".jpg")) {
            Recs.Pick p = new Recs.Pick();
            p.kind = "local"; p.id = out.size(); p.title = ""; p.backdrop = f.getAbsolutePath();
            out.add(p);
        }
        return out;
    }

    static synchronized boolean importPhotos(Context c, List<Uri> uris) {
        if (uris.isEmpty()) return false;
        File stage = new File(c.getFilesDir(), "photos-new");
        clear(stage); stage.mkdirs();
        try {
            int n = 0;
            for (Uri uri : uris) {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                try (InputStream in = c.getContentResolver().openInputStream(uri)) { BitmapFactory.decodeStream(in, null, o); }
                if (o.outWidth <= 0 || o.outHeight <= 0) throw new IOException("Invalid image");
                o.inSampleSize = 1;
                while (o.outWidth / o.inSampleSize > 3840 || o.outHeight / o.inSampleSize > 2160) o.inSampleSize *= 2;
                o.inJustDecodeBounds = false;
                Bitmap bitmap;
                try (InputStream in = c.getContentResolver().openInputStream(uri)) { bitmap = BitmapFactory.decodeStream(in, null, o); }
                if (bitmap == null) throw new IOException("Invalid image");
                try (OutputStream out = new FileOutputStream(new File(stage, String.format(Locale.ROOT, "%02d.jpg", n++)))) {
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)) throw new IOException("Cannot save image");
                } finally { bitmap.recycle(); }
            }
            File album = new File(c.getFilesDir(), "photos"), old = new File(c.getFilesDir(), "photos-old");
            clear(old);
            if (album.exists() && !album.renameTo(old)) throw new IOException("Cannot replace album");
            if (!stage.renameTo(album)) { old.renameTo(album); throw new IOException("Cannot save album"); }
            clear(old);
            return true;
        } catch (Exception | OutOfMemoryError e) { clear(stage); return false; }
    }

    private static void clear(File dir) {
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) f.delete();
        dir.delete();
    }
}
