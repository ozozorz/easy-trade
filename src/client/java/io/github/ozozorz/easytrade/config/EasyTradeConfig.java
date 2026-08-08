package io.github.ozozorz.easytrade.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.ozozorz.easytrade.EasyTradeClient;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persistent client settings. Every value is clamped before it can reach the
 * trading state machine so a malformed JSON file cannot create packet spam.
 */
public final class EasyTradeConfig {
	public static final boolean DEFAULT_ENABLED = true;
	public static final int DEFAULT_ACTION_DELAY_MS = 350;
	public static final int DEFAULT_RANDOM_JITTER_MS = 150;
	public static final int DEFAULT_MENU_TIMEOUT_MS = 5000;
	public static final int DEFAULT_SYNC_TIMEOUT_MS = 3000;
	public static final int DEFAULT_ACTIVATION_COOLDOWN_MS = 900;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Path path;
	private boolean enabled = DEFAULT_ENABLED;
	private int actionDelayMs = DEFAULT_ACTION_DELAY_MS;
	private int randomJitterMs = DEFAULT_RANDOM_JITTER_MS;
	private int menuTimeoutMs = DEFAULT_MENU_TIMEOUT_MS;
	private int syncTimeoutMs = DEFAULT_SYNC_TIMEOUT_MS;
	private int activationCooldownMs = DEFAULT_ACTIVATION_COOLDOWN_MS;

	private EasyTradeConfig(Path path) {
		this.path = path;
	}

	/** Loads a config file or creates it with conservative defaults. */
	public static EasyTradeConfig load(Path path) {
		EasyTradeConfig config = new EasyTradeConfig(path);
		config.reload();
		return config;
	}

	/** Reloads all values from disk and falls back to defaults on invalid input. */
	public void reload() {
		setDefaultsInMemory();
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
				enabled = getBoolean(root, "enabled", DEFAULT_ENABLED);
				actionDelayMs = clamp(getInt(root, "actionDelayMs", DEFAULT_ACTION_DELAY_MS), 100, 5000);
				randomJitterMs = clamp(getInt(root, "randomJitterMs", DEFAULT_RANDOM_JITTER_MS), 0, 2000);
				menuTimeoutMs = clamp(getInt(root, "menuTimeoutMs", DEFAULT_MENU_TIMEOUT_MS), 2000, 15000);
				syncTimeoutMs = clamp(getInt(root, "syncTimeoutMs", DEFAULT_SYNC_TIMEOUT_MS), 1000, 10000);
				activationCooldownMs = clamp(
					getInt(root, "activationCooldownMs", DEFAULT_ACTIVATION_COOLDOWN_MS),
					250,
					5000
				);
			} catch (RuntimeException | IOException exception) {
				EasyTradeClient.LOGGER.warn("Could not read {}; using safe defaults", path, exception);
			}
		}

		save();
	}

	/** Saves the current normalized settings as formatted JSON. */
	public void save() {
		JsonObject root = new JsonObject();
		root.addProperty("enabled", enabled);
		root.addProperty("actionDelayMs", actionDelayMs);
		root.addProperty("randomJitterMs", randomJitterMs);
		root.addProperty("menuTimeoutMs", menuTimeoutMs);
		root.addProperty("syncTimeoutMs", syncTimeoutMs);
		root.addProperty("activationCooldownMs", activationCooldownMs);

		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(root, writer);
			}
		} catch (IOException exception) {
			EasyTradeClient.LOGGER.error("Could not save {}", path, exception);
		}
	}

	/** Applies all values from the config screen in one save operation. */
	public void apply(
		boolean enabled,
		int actionDelayMs,
		int randomJitterMs,
		int activationCooldownMs,
		int menuTimeoutMs,
		int syncTimeoutMs
	) {
		this.enabled = enabled;
		this.actionDelayMs = clamp(actionDelayMs, 100, 5000);
		this.randomJitterMs = clamp(randomJitterMs, 0, 2000);
		this.activationCooldownMs = clamp(activationCooldownMs, 250, 5000);
		this.menuTimeoutMs = clamp(menuTimeoutMs, 2000, 15000);
		this.syncTimeoutMs = clamp(syncTimeoutMs, 1000, 10000);
		save();
	}

	public boolean enabled() {
		return enabled;
	}

	public int actionDelayMs() {
		return actionDelayMs;
	}

	public int randomJitterMs() {
		return randomJitterMs;
	}

	public int menuTimeoutMs() {
		return menuTimeoutMs;
	}

	public int syncTimeoutMs() {
		return syncTimeoutMs;
	}

	public int activationCooldownMs() {
		return activationCooldownMs;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
		save();
	}

	public void setActionDelayMs(int actionDelayMs) {
		this.actionDelayMs = clamp(actionDelayMs, 100, 5000);
		save();
	}

	public void setRandomJitterMs(int randomJitterMs) {
		this.randomJitterMs = clamp(randomJitterMs, 0, 2000);
		save();
	}

	/** Restores defaults without writing them until the caller explicitly saves. */
	private void setDefaultsInMemory() {
		enabled = DEFAULT_ENABLED;
		actionDelayMs = DEFAULT_ACTION_DELAY_MS;
		randomJitterMs = DEFAULT_RANDOM_JITTER_MS;
		menuTimeoutMs = DEFAULT_MENU_TIMEOUT_MS;
		syncTimeoutMs = DEFAULT_SYNC_TIMEOUT_MS;
		activationCooldownMs = DEFAULT_ACTIVATION_COOLDOWN_MS;
	}

	private static int getInt(JsonObject object, String name, int fallback) {
		return object.has(name) ? object.get(name).getAsInt() : fallback;
	}

	private static boolean getBoolean(JsonObject object, String name, boolean fallback) {
		return object.has(name) ? object.get(name).getAsBoolean() : fallback;
	}

	private static int clamp(int value, int minimum, int maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}
}
