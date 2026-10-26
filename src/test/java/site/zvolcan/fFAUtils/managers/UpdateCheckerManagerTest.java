package site.zvolcan.fFAUtils.managers;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The update check must never block the main thread, must never throw, and must
 * only ever claim "an update exists" when the release really is newer.
 */
class UpdateCheckerManagerTest {

    /** A generous ceiling for "returned without waiting on the network". */
    private static final long NON_BLOCKING_MILLIS = 1_000L;

    @Test
    void aReleaseIsNewerOnlyWhenItsVersionReallyIs() {
        // Same version, with and without the tag prefix.
        assertFalse(UpdateCheckerManager.isNewer("v1.3.0-SNAPSHOT", "1.3.0-SNAPSHOT"));
        assertFalse(UpdateCheckerManager.isNewer("v1.3.0", "v1.3.0"));

        // Higher and lower numbers, numerically and not lexicographically.
        assertTrue(UpdateCheckerManager.isNewer("v1.4.0-SNAPSHOT", "1.3.0-SNAPSHOT"));
        assertTrue(UpdateCheckerManager.isNewer("v1.10.0", "1.9.9"));
        assertFalse(UpdateCheckerManager.isNewer("v1.2.1-SNAPSHOT", "1.3.0-SNAPSHOT"));
        assertFalse(UpdateCheckerManager.isNewer("v0.9.8-BETA", "1.0.0-SNAPSHOT"));

        // A finished release beats a pre-release of the same number.
        assertTrue(UpdateCheckerManager.isNewer("v1.3.0", "1.3.0-SNAPSHOT"));
        assertFalse(UpdateCheckerManager.isNewer("v1.3.0-SNAPSHOT", "1.3.0"));

        // Different pre-release labels of the same number are not an update.
        assertFalse(UpdateCheckerManager.isNewer("v1.3.0-BETA", "1.3.0-SNAPSHOT"));

        // Missing components count as zero, and trailing build metadata is ignored.
        assertTrue(UpdateCheckerManager.isNewer("v1.3.1", "1.3"));
        assertFalse(UpdateCheckerManager.isNewer("v1.3", "1.3.0"));
        assertFalse(UpdateCheckerManager.isNewer("v1.3.0+build.7", "1.3.0"));

        // Anything that is not a version must never nag the server owner.
        assertFalse(UpdateCheckerManager.isNewer("banana", "1.3.0"));
        assertFalse(UpdateCheckerManager.isNewer("v", "1.3.0"));
        assertFalse(UpdateCheckerManager.isNewer(null, "1.3.0"));
        assertFalse(UpdateCheckerManager.isNewer("v1.4.0", null));
        assertFalse(UpdateCheckerManager.isNewer("v1.4.0", "unknown"));
    }

    @Test
    void readsTheVersionAndUrlFromTheReleasePayload() {
        String payload = "{\"tag_name\":\"v1.4.0\",\"name\":\"v1.4.0\","
                + "\"html_url\":\"https://github.com/rodriguezemir/ffautils/releases/tag/v1.4.0\","
                + "\"prerelease\":false}";

        UpdateCheckerManager.Release release = UpdateCheckerManager.parseRelease(payload);

        assertNotNull(release);
        assertEquals("v1.4.0", release.version());
        assertEquals("https://github.com/rodriguezemir/ffautils/releases/tag/v1.4.0", release.url());
    }

    @Test
    void ignoresPayloadsThatCarryNoVersion() {
        assertNull(UpdateCheckerManager.parseRelease("{}"));
        assertNull(UpdateCheckerManager.parseRelease("{\"tag_name\":\"\"}"));
        assertNull(UpdateCheckerManager.parseRelease("{\"message\":\"Not Found\"}"));
        assertNull(UpdateCheckerManager.parseRelease("not json"));
        assertNull(UpdateCheckerManager.parseRelease(null));
    }

    @Test
    void anUnreachableApiIsReportedAsNoUpdateAndNeverThrows() {
        // Nothing listens on this port: the check must fail quietly.
        UpdateCheckerManager manager = newManager("http://127.0.0.1:1/releases/latest", 2);

        CompletableFuture<Boolean> result = assertDoesNotThrow(manager::checkAsync);

        assertFalse(assertDoesNotThrow(() -> result.get(15, TimeUnit.SECONDS)));
        assertTrue(manager.latestVersion().isEmpty());
        manager.shutdown();
    }

    @Test
    void aNonOkResponseIsReportedAsNoUpdate() throws Exception {
        try (ServerSocket server = responding(404, "{\"message\":\"Not Found\"}")) {
            UpdateCheckerManager manager = newManager("http://127.0.0.1:" + server.getLocalPort() + "/releases/latest", 5);

            assertFalse(manager.checkAsync().get(15, TimeUnit.SECONDS));
            assertTrue(manager.latestVersion().isEmpty());
            manager.shutdown();
        }
    }

    @Test
    void aStalledApiNeverBlocksTheCaller() throws Exception {
        CountDownLatch requestOnTheWire = new CountDownLatch(1);
        try (ServerSocket server = stalling(requestOnTheWire)) {
            UpdateCheckerManager manager = newManager(
                    "http://127.0.0.1:" + server.getLocalPort() + "/releases/latest", 5);

            long start = System.nanoTime();
            CompletableFuture<Boolean> result = manager.checkAsync();
            long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;

            assertTrue(elapsedMillis < NON_BLOCKING_MILLIS,
                    "checkAsync blocked the caller for " + elapsedMillis + " ms");
            assertTrue(requestOnTheWire.await(10, TimeUnit.SECONDS), "no request ever reached the server");
            assertFalse(result.isDone(), "a stalled request must not resolve immediately");
            manager.shutdown();
        }
    }

    @Test
    void aNewerReleaseIsFetchedAndKeptForTheJoinNotice() throws Exception {
        String payload = "{\"tag_name\":\"v9.9.9\","
                + "\"html_url\":\"https://github.com/rodriguezemir/ffautils/releases/tag/v9.9.9\"}";
        try (ServerSocket server = responding(200, payload)) {
            UpdateCheckerManager manager = newManager("http://127.0.0.1:" + server.getLocalPort() + "/x", 5);

            assertTrue(manager.checkAsync().get(15, TimeUnit.SECONDS));
            assertEquals("v9.9.9", manager.latestVersion().orElseThrow());
            assertEquals("https://github.com/rodriguezemir/ffautils/releases/tag/v9.9.9",
                    manager.latestUrl().orElseThrow());
            manager.shutdown();
        }
    }

    /** Reads the request line, answers with the given status and body, then closes. */
    private static ServerSocket responding(int status, String body) throws Exception {
        ServerSocket server = new ServerSocket();
        server.bind(new InetSocketAddress("127.0.0.1", 0));
        Thread thread = new Thread(() -> {
            while (!server.isClosed()) {
                try (Socket socket = server.accept()) {
                    socket.getInputStream().read(new byte[2048]);
                    socket.getOutputStream().write(("HTTP/1.1 " + status + " X\r\n"
                            + "Content-Type: application/json\r\n"
                            + "Content-Length: " + body.length() + "\r\n"
                            + "Connection: close\r\n\r\n" + body).getBytes());
                    socket.getOutputStream().flush();
                } catch (Exception ignored) {
                    return;
                }
            }
        });
        thread.setDaemon(true);
        thread.start();
        return server;
    }

    /** Accepts the connection and never answers, so anything blocking would block for the timeout. */
    private static ServerSocket stalling(CountDownLatch requestOnTheWire) throws Exception {
        ServerSocket server = new ServerSocket();
        server.bind(new InetSocketAddress("127.0.0.1", 0));
        Thread thread = new Thread(() -> {
            while (!server.isClosed()) {
                try {
                    Socket socket = server.accept();
                    Thread handler = new Thread(() -> {
                        requestOnTheWire.countDown();
                        try (InputStream in = socket.getInputStream()) {
                            in.read();
                            Thread.sleep(120_000);
                        } catch (Exception ignored) {
                            // Test is over.
                        }
                    });
                    handler.setDaemon(true);
                    handler.start();
                } catch (Exception ignored) {
                    return;
                }
            }
        });
        thread.setDaemon(true);
        thread.start();
        return server;
    }

    private static UpdateCheckerManager newManager(String apiUrl, int timeoutSeconds) {
        return new UpdateCheckerManager(pluginFor("1.3.0-SNAPSHOT"), apiUrl, timeoutSeconds, false);
    }

    /** A plugin stub whose only real content is the version the check compares against. */
    private static JavaPlugin pluginFor(String version) {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("UpdateCheckerManagerTest"));
        when(plugin.getDescription()).thenReturn(new PluginDescriptionFile(
                "FFAUtils", version, "site.zvolcan.fFAUtils.FFAUtils"));
        when(plugin.getConfig()).thenReturn(new YamlConfiguration());
        return plugin;
    }
}
