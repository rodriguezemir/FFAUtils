package site.zvolcan.fFAUtils.managers;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import site.zvolcan.fFAUtils.util.NonBlockingHttpClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

/**
 * Asks the GitHub API for the latest release so the server owner learns when a
 * newer build exists.
 *
 * <p>
 * The request runs on a dedicated daemon thread with its own {@link HttpClient}
 * and is never allowed to touch the main thread: {@link #checkAsync()} returns
 * as soon as the task is queued. A dead network, a rate limit, a repository
 * without releases or a payload without a version all resolve to "no update"
 * instead of an exception or a stack trace in the server log.</p>
 */
public final class UpdateCheckerManager {

    /** GitHub's "latest release" endpoint; unauthenticated like every read of a public repo. */
    public static final String DEFAULT_API_URL =
            "https://api.github.com/repos/rodriguezemir/ffautils/releases/latest";

    /**
     * GitHub answers 403 to a request without a User-Agent, so the checker has
     * to identify itself.
     */
    private static final String USER_AGENT = "FFAUtils-UpdateChecker";

    private final JavaPlugin plugin;
    private final String apiUrl;
    private final int timeoutSeconds;
    private final boolean notifyPlayers;
    private final ExecutorService executor;
    private final HttpClient httpClient;

    private volatile Release latest;
    private volatile boolean checked;

    public UpdateCheckerManager(@NotNull JavaPlugin plugin) {
        this(plugin,
                plugin.getConfig().getString("update-check.api-url", DEFAULT_API_URL),
                Math.max(1, plugin.getConfig().getInt("update-check.timeout-seconds", 5)),
                plugin.getConfig().getBoolean("update-check.notify-players", true));
    }

    /**
     * @param apiUrl         endpoint returning a GitHub release payload
     * @param timeoutSeconds connect and request timeout
     * @param notifyPlayers  whether players holding {@code ffautils.update-notify}
     *                       should be told on join
     */
    UpdateCheckerManager(@NotNull JavaPlugin plugin, @NotNull String apiUrl, int timeoutSeconds,
            boolean notifyPlayers) {
        this.plugin = plugin;
        this.apiUrl = apiUrl;
        this.timeoutSeconds = timeoutSeconds;
        this.notifyPlayers = notifyPlayers;
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "FFAUtils-Update");
            thread.setDaemon(true);
            return thread;
        });
        // No custom executor on purpose: HttpClient runs its own selector and
        // response callbacks there, and handing it this single-thread pool
        // deadlocks a blocking send() against its own completion handler.
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Fetches the latest release without blocking the caller.
     *
     * @return a future that completes with {@code true} when the fetched release
     *         is newer than the running version; it never completes exceptionally
     */
    public CompletableFuture<Boolean> checkAsync() {
        return CompletableFuture
                .supplyAsync(this::fetchLatest, executor)
                .thenApply(release -> {
                    latest = release;
                    checked = true;
                    Optional<Release> newer = newerRelease();
                    if (newer.isPresent()) {
                        Release update = newer.get();
                        plugin.getLogger().warning("A newer FFAUtils release is available: "
                                + update.version() + " (running " + runningVersion() + ")" + describeUrl(update));
                    } else if (release != null) {
                        plugin.getLogger().info("FFAUtils is up to date (running " + runningVersion()
                                + ", latest release " + release.version() + ").");
                    }
                    return newer.isPresent();
                })
                .exceptionally(error -> {
                    plugin.getLogger().log(Level.FINE, "Update check failed", error);
                    return false;
                });
    }

    /** The release the last completed check fetched, whether or not it is newer. */
    public Optional<String> latestVersion() {
        Release release = latest;
        return release == null ? Optional.empty() : Optional.of(release.version());
    }

    public Optional<String> latestUrl() {
        Release release = latest;
        return release == null ? Optional.empty() : Optional.of(release.url());
    }

    /** The fetched release only when it is newer than the running version. */
    public Optional<Release> newerRelease() {
        Release release = latest;
        if (release == null || !isNewer(release.version(), runningVersion())) {
            return Optional.empty();
        }
        return Optional.of(release);
    }

    public boolean notifyPlayers() {
        return notifyPlayers;
    }

    /** Whether the check has already finished, successfully or not. */
    public boolean isChecked() {
        return checked;
    }

    /** Nothing blocks: in-flight requests are released, never awaited. */
    public void shutdown() {
        NonBlockingHttpClient.releaseQuietly(httpClient);
        executor.shutdownNow();
    }

    private String runningVersion() {
        return plugin.getDescription().getVersion();
    }

    private static String describeUrl(Release release) {
        return release.url() == null ? "" : " -> " + release.url();
    }

    private Release fetchLatest() {
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(apiUrl))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", USER_AGENT)
                    .GET()
                    .build();
        } catch (IllegalArgumentException invalidUrl) {
            plugin.getLogger().log(Level.FINE, "Update check disabled: invalid api-url " + apiUrl, invalidUrl);
            return null;
        }
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                plugin.getLogger().log(Level.FINE,
                        "Update check skipped: HTTP " + response.statusCode() + " from " + apiUrl);
                return null;
            }
            return parseRelease(response.body());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception failure) {
            plugin.getLogger().log(Level.FINE, "Update check failed", failure);
            return null;
        }
    }

    /** Extracts the version and page URL of a release payload, or {@code null} when it has none. */
    static Release parseRelease(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonObject payload = JsonParser.parseString(json).getAsJsonObject();
            if (!payload.has("tag_name") || payload.get("tag_name").isJsonNull()) {
                return null;
            }
            String tag = payload.get("tag_name").getAsString().trim();
            if (tag.isEmpty()) {
                return null;
            }
            String url = payload.has("html_url") && !payload.get("html_url").isJsonNull()
                    ? payload.get("html_url").getAsString()
                    : null;
            return new Release(tag, url);
        } catch (RuntimeException notARelease) {
            return null;
        }
    }

    /** Whether {@code candidate} is a strictly newer version than {@code current}. */
    static boolean isNewer(String candidate, String current) {
        Version newer = Version.parse(candidate);
        Version running = Version.parse(current);
        if (newer == null || running == null) {
            return false;
        }
        return newer.compareTo(running) > 0;
    }

    /** A release as GitHub reports it. */
    public record Release(String version, String url) {
    }

    /**
     * A {@code major.minor.patch} version with an optional pre-release suffix.
     *
     * <p>
     * Release tags and the plugin version are both written as {@code v1.2.3-SNAPSHOT}
     * or {@code 1.2.3}, so the numeric part is compared numerically (1.10 is newer
     * than 1.9) and a pre-release of a number is older than the finished release
     * of that same number. Two different pre-release labels of the same number are
     * treated as equal: that is not enough evidence to wake the server owner.</p>
     */
    private record Version(int[] numbers, boolean preRelease) implements Comparable<Version> {

        static Version parse(String raw) {
            if (raw == null) {
                return null;
            }
            String value = raw.trim();
            int buildMetadata = value.indexOf('+');
            if (buildMetadata >= 0) {
                value = value.substring(0, buildMetadata);
            }
            if (!value.isEmpty() && (value.charAt(0) == 'v' || value.charAt(0) == 'V')) {
                value = value.substring(1);
            }
            if (value.isEmpty()) {
                return null;
            }
            String core = value;
            boolean preRelease = false;
            int suffix = value.indexOf('-');
            if (suffix >= 0) {
                core = value.substring(0, suffix);
                preRelease = !value.substring(suffix + 1).isBlank();
            }
            String[] parts = core.split("\\.");
            int[] numbers = new int[parts.length];
            for (int i = 0; i < parts.length; i++) {
                try {
                    numbers[i] = Integer.parseInt(parts[i]);
                } catch (NumberFormatException notAVersion) {
                    return null;
                }
                if (numbers[i] < 0) {
                    return null;
                }
            }
            return numbers.length == 0 ? null : new Version(numbers, preRelease);
        }

        @Override
        public int compareTo(@NotNull Version other) {
            int length = Math.max(numbers.length, other.numbers.length);
            for (int i = 0; i < length; i++) {
                int mine = i < numbers.length ? numbers[i] : 0;
                int theirs = i < other.numbers.length ? other.numbers[i] : 0;
                if (mine != theirs) {
                    return Integer.compare(mine, theirs);
                }
            }
            if (preRelease == other.preRelease) {
                return 0;
            }
            return preRelease ? -1 : 1;
        }
    }
}
