package site.zvolcan.fFAUtils.util;

import java.net.http.HttpClient;

/**
 * Releases an {@link HttpClient} without ever blocking the calling thread.
 *
 * <p>
 * {@code HttpClient.close()} waits for every in-flight request to finish, which
 * on a Paper main thread means dropped ticks (measured: several seconds with one
 * stalled request). {@code HttpClient.shutdown()} is the non-blocking release,
 * but it only exists from Java 21 on, while this plugin targets Java 17 bytecode
 * so it can run on 1.18.x servers. It is therefore called reflectively and
 * skipped on older runtimes; dropping the reference is what is left there.</p>
 */
public final class NonBlockingHttpClient {

    private NonBlockingHttpClient() {
    }

    public static void releaseQuietly(HttpClient client) {
        if (client == null) {
            return;
        }
        try {
            HttpClient.class.getMethod("shutdown").invoke(client);
        } catch (NoSuchMethodException ignored) {
            // Java 17 runtime: no non-blocking shutdown exists, and close() must never be used.
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Nothing useful to do if the client refuses to shut down.
        }
    }
}
