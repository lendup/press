package press.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.util.zip.GZIPOutputStream;

public class Gzip {

    public static ByteArrayOutputStream gzip(final InputStream inputStream)
        throws IOException
    {
        final ByteArrayOutputStream stringOutputStream = new ByteArrayOutputStream();
        final OutputStream gzipOutputStream = new GZIPOutputStream(stringOutputStream);

        final byte[] buf = new byte[5000];
        int len;
        while ((len = inputStream.read(buf)) > 0) {
            gzipOutputStream.write(buf, 0, len);
        }

        inputStream.close();
        gzipOutputStream.close();

        return stringOutputStream;

    }

    public static ByteArrayOutputStream gzip(final String input)
            throws IOException {
        return gzip(new ByteArrayInputStream(input.getBytes()));
    }
}
