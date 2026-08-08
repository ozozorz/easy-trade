package io.github.ozozorz.easytrade.trade;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import io.github.ozozorz.easytrade.EasyTradeClient;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Stores one offer fingerprint per villager. Minecraft codecs retain data
 * components, so visually similar trades such as enchanted books stay distinct.
 */
public final class BookmarkStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Path path;
	private final Map<String, JsonObject> entries = new HashMap<>();

	/** Creates the local store and loads any existing bookmarks. */
	public BookmarkStore(Path path) {
		this.path = path;
		reload();
	}

	/** Reloads raw bookmark JSON; decoding waits until world registries exist. */
	public void reload() {
		entries.clear();
		if (!Files.exists(path)) {
			return;
		}

		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			JsonObject bookmarkObject = root.has("bookmarks") ? root.getAsJsonObject("bookmarks") : new JsonObject();
			for (Map.Entry<String, JsonElement> entry : bookmarkObject.entrySet()) {
				if (entry.getValue().isJsonObject()) {
					entries.put(entry.getKey(), entry.getValue().getAsJsonObject());
				}
			}
		} catch (RuntimeException | IOException exception) {
			EasyTradeClient.LOGGER.warn("Could not read {}", path, exception);
		}
	}

	/** Encodes and persists the selected offer for a scoped villager key. */
	public void put(String key, int preferredIndex, MerchantOffer offer, HolderLookup.Provider registries) {
		DynamicOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registries);
		JsonObject entry = new JsonObject();
		entry.addProperty("preferredIndex", preferredIndex);
		entry.add("costA", ItemCost.CODEC.encodeStart(ops, offer.getItemCostA()).getOrThrow());
		offer.getItemCostB().ifPresent(cost -> entry.add("costB", ItemCost.CODEC.encodeStart(ops, cost).getOrThrow()));
		entry.add("result", ItemStack.CODEC.encodeStart(ops, offer.getResult()).getOrThrow());
		entries.put(key, entry);
		save();
	}

	/** Decodes a bookmark against the current world's registries. */
	public Optional<Bookmark> get(String key, HolderLookup.Provider registries) {
		JsonObject entry = entries.get(key);
		if (entry == null) {
			return Optional.empty();
		}

		try {
			DynamicOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registries);
			ItemCost costA = ItemCost.CODEC.parse(ops, entry.get("costA")).getOrThrow();
			Optional<ItemCost> costB = entry.has("costB")
				? Optional.of(ItemCost.CODEC.parse(ops, entry.get("costB")).getOrThrow())
				: Optional.empty();
			ItemStack result = ItemStack.CODEC.parse(ops, entry.get("result")).getOrThrow();
			return Optional.of(new Bookmark(entry.get("preferredIndex").getAsInt(), costA, costB, result));
		} catch (RuntimeException exception) {
			EasyTradeClient.LOGGER.warn("Could not decode bookmark {}", key, exception);
			return Optional.empty();
		}
	}

	/** Writes all bookmark entries as formatted JSON. */
	private void save() {
		JsonObject root = new JsonObject();
		root.addProperty("version", 1);
		JsonObject bookmarkObject = new JsonObject();
		entries.forEach(bookmarkObject::add);
		root.add("bookmarks", bookmarkObject);

		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(root, writer);
			}
		} catch (IOException exception) {
			EasyTradeClient.LOGGER.error("Could not save {}", path, exception);
		}
	}

	/** Immutable decoded fingerprint used to match refreshed server offers. */
	public record Bookmark(int preferredIndex, ItemCost costA, Optional<ItemCost> costB, ItemStack result) {
		/** Returns true when the triggering hand holds either required input type. */
		public boolean matchesHeld(ItemStack held) {
			return costA.test(held) || costB.map(cost -> cost.test(held)).orElse(false);
		}

		/** Matches stable offer identity while ignoring price demand and remaining uses. */
		public boolean matches(MerchantOffer offer) {
			return sameCost(costA, offer.getItemCostA())
				&& sameOptionalCost(costB, offer.getItemCostB())
				&& ItemStack.matches(result, offer.getResult());
		}

		private static boolean sameOptionalCost(Optional<ItemCost> first, Optional<ItemCost> second) {
			return first.isEmpty() && second.isEmpty()
				|| first.isPresent() && second.isPresent() && sameCost(first.get(), second.get());
		}

		private static boolean sameCost(ItemCost first, ItemCost second) {
			return first.count() == second.count()
				&& ItemStack.isSameItemSameComponents(first.itemStack(), second.itemStack());
		}
	}
}
