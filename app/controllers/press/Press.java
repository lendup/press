package controllers.press;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import play.Logger;
import play.exceptions.UnexpectedException;
import play.mvc.Controller;
import play.mvc.Http.Header;
import press.CachingStrategy;
import press.PluginConfig;
import press.ScriptCompressedFileManager;
import press.ScriptCompressor;
import press.ScriptRequestHandler;
import press.StyleCompressedFileManager;
import press.StyleCompressor;
import press.StyleRequestHandler;
import press.io.CompressedFile;
import press.io.FileIO;
import press.io.Gzip;

public class Press extends Controller {
    public static final DateTimeFormatter httpDateTimeFormatter = DateTimeFormat
            .forPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'");

    public static void getCompressedJS(String key) {
        key = FileIO.unescape(key);
        CompressedFile compressedFile = new ScriptCompressedFileManager().getCompressedFile(key);
        renderCompressedFile(compressedFile, "JavaScript");
    }

    public static void getCompressedCSS(String key) {
        key = FileIO.unescape(key);
        CompressedFile compressedFile = new StyleCompressedFileManager().getCompressedFile(key);
        renderCompressedFile(compressedFile, "CSS");
    }

    private static void renderCompressedFile(CompressedFile compressedFile, String type) {
        flash.keep();

        if (compressedFile == null) {
            renderBadResponse(type);
        }

        InputStream inputStream = compressedFile.inputStream();

        // This seems to be buggy, so instead of passing the file length we
        // reset the input stream and allow play to manually copy the bytes from
        // the input stream to the response
        // renderBinary(inputStream, compressedFile.name(),
        // compressedFile.length());

        try {
            if (inputStream.markSupported()) {
                inputStream.reset();
            }
        } catch (IOException e) {
            throw new UnexpectedException(e);
        }

        // If the caching strategy is always, the timestamp is not part of the
        // key. If we let the browser cache, then the browser will keep holding
        // old copies, even after changing the files at the server and
        // restarting the server, since the key will stay the same.
        // If the caching strategy is never, we also don't want to cache at the
        // browser, for obvious reasons.
        // If the caching strategy is Change, then the modified timestamp is a
        // part of the key, so if the file changes, the key in the html file
        // will be modified, and the browser will request a new version. Each
        // version can therefore be cached indefinitely.
        if (PluginConfig.cache.equals(CachingStrategy.Change)) {
            // Cache for a year
            response.setHeader("Cache-Control", "max-age=" + 31536000);
            response.setHeader("Expires", httpDateTimeFormatter.print(new DateTime().plusYears(1)));
            if(!PluginConfig.p3pHeader.isEmpty()) {
                response.setHeader("P3P", PluginConfig.p3pHeader);
            }
        }

        /*
         * google wants this: "Instructs proxy servers to cache two versions
         * of the resource: one compressed, and one uncompressed. This helps
         * avoid issues with public proxies that do not detect the presence
         * of a Content-Encoding header properly."
         */
        response.headers.put("Vary", new Header("Vary", "Accept-Encoding"));

        // attempt gzip if the client requests it.
        //
        Header encodings = request.headers.get("accept-encoding");

        if (PluginConfig.gzipEnabled &&
                (encodings != null) &&
                (encodings.value().indexOf("gzip") != -1)) {
            try {
                ByteArrayOutputStream gzip = Gzip.gzip(inputStream);
                response.setHeader("Content-Encoding", "gzip");
                response.setHeader("Content-Length", gzip.size() + "");
                renderBinary(new ByteArrayInputStream(gzip.toByteArray()), compressedFile.name());
            } catch (IOException e) {
                Logger.error(e, "Unable to compress output: %s", e.getMessage());
            }
        }

        renderBinary(inputStream, compressedFile.name());
    }

    public static void clearJSCache() {
        if (!PluginConfig.cacheClearEnabled) {
            forbidden();
        }

        int count = ScriptRequestHandler.clearCache();
        renderText("Cleared " + count + " JS files from cache");
    }

    public static void clearCSSCache() {
        if (!PluginConfig.cacheClearEnabled) {
            forbidden();
        }

        int count = StyleRequestHandler.clearCache();
        renderText("Cleared " + count + " CSS files from cache");
    }

    private static void renderBadResponse(String fileType) {
        String response = "/*\n";
        response += "The compressed " + fileType + " file could not be generated.\n";
        response += "This can occur in two situations:\n";
        response += "1. The time between when the page was rendered by the ";
        response += "server and when the browser requested the compressed ";
        response += "file was greater than the timeout. (The timeout is ";
        response += "currently configured to be ";
        response += PluginConfig.compressionKeyStorageTime + ")\n";
        response += "2. There was an exception thrown while rendering the ";
        response += "page.\n";
        response += "*/";
        renderBinaryResponse(response);
    }

    private static void renderBinaryResponse(String response) {
        try {
            renderBinary(new ByteArrayInputStream(response.getBytes("UTF-8")));
        } catch (UnsupportedEncodingException e) {
            throw new UnexpectedException(e);
        }
    }
}
