package com.fuelcast.ingestion;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;

/** Downloads a MIMIT CSV as a UTF-8 string. */
@Component
public class MimitDownloader {

    private final HttpClient client;
    private final Duration timeout;

    public MimitDownloader(IngestionProperties props) {
        this.timeout = Duration.ofSeconds(props.getDownloadTimeoutSeconds());
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public String download(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(timeout)
                .header("User-Agent", "fuel-cast/0.1 (+https://github.com/AndreaCiani/fuel-cast)")
                .GET()
                .build();
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IOException("Download failed for %s: HTTP %d".formatted(url, response.statusCode()));
        }
        return new String(response.body(), StandardCharsets.UTF_8);
    }

    /** Streams a (potentially large, binary) download straight to disk. */
    public void downloadToFile(String url, Path target) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(timeout)
                .header("User-Agent", "fuel-cast/0.1 (+https://github.com/AndreaCiani/fuel-cast)")
                .GET()
                .build();
        HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(target));
        if (response.statusCode() != 200) {
            throw new IOException("Download failed for %s: HTTP %d".formatted(url, response.statusCode()));
        }
    }
}
