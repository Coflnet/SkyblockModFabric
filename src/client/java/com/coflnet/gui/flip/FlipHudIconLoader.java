package com.coflnet.gui.flip;

import com.mojang.blaze3d.platform.NativeImage;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

final class FlipHudIconLoader {
    private static final int MAX_IMAGE_BYTES = 1_048_576;
    private static final int MAX_IMAGE_DIMENSION = 256;
    private static final URI ICON_ROOT = URI.create("https://sky.coflnet.com/static/icon/");
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final AtomicBoolean FAILURE_LOGGED = new AtomicBoolean();
    private static final Object REQUEST_LOCK = new Object();
    private static Thread activeRequest;

    private FlipHudIconLoader() {
    }

    static void load(String tag, Consumer<NativeImage> consumer) {
        synchronized (REQUEST_LOCK) {
            cancelLocked();
            if (tag == null || tag.isBlank()) {
                return;
            }
            activeRequest = Thread.ofVirtual().unstarted(() -> runRequest(tag, consumer));
            activeRequest.start();
        }
    }

    static void cancel() {
        synchronized (REQUEST_LOCK) {
            cancelLocked();
        }
    }

    private static void runRequest(String tag, Consumer<NativeImage> consumer) {
        NativeImage image = null;
        try {
            image = download(tag);
            consumer.accept(image);
            image = null;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (IOException | RuntimeException exception) {
            if (FAILURE_LOGGED.compareAndSet(false, true)) {
                System.out.println("Could not load flip HUD icon: " + exception);
            }
        } finally {
            if (image != null) {
                image.close();
            }
            synchronized (REQUEST_LOCK) {
                if (activeRequest == Thread.currentThread()) {
                    activeRequest = null;
                }
            }
        }
    }

    private static void cancelLocked() {
        if (activeRequest != null) {
            activeRequest.interrupt();
            activeRequest = null;
        }
    }

    private static NativeImage download(String tag) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(ICON_ROOT.resolve(tag))
                .timeout(Duration.ofSeconds(5))
                .header("Accept", "image/png,image/gif")
                .GET()
                .build();
        HttpResponse<InputStream> response =
                HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = response.body()) {
            if (response.statusCode() != 200) {
                throw new IOException("icon request returned " + response.statusCode());
            }
            String contentType = response.headers()
                    .firstValue("Content-Type")
                    .orElse("");
            if (!contentType.toLowerCase(java.util.Locale.ROOT).startsWith("image/")) {
                throw new IOException("icon request returned a non image response");
            }
            long contentLength = response.headers()
                    .firstValueAsLong("Content-Length")
                    .orElse(-1L);
            if (contentLength > MAX_IMAGE_BYTES) {
                throw new IOException("icon response exceeded the size limit");
            }
            byte[] bytes = body.readNBytes(MAX_IMAGE_BYTES + 1);
            if (bytes.length > MAX_IMAGE_BYTES) {
                throw new IOException("icon response exceeded the size limit");
            }
            NativeImage image = NativeImage.read(bytes);
            if (image.getWidth() < 1 || image.getHeight() < 1
                    || image.getWidth() > MAX_IMAGE_DIMENSION
                    || image.getHeight() > MAX_IMAGE_DIMENSION) {
                image.close();
                throw new IOException("icon dimensions exceeded the size limit");
            }
            return image;
        }
    }
}
