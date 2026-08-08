package io.github.ozozorz.easytrade.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import io.github.ozozorz.easytrade.config.EasyTradeConfigScreen;

/** Optional Mod Menu integration that exposes easy-trade's native config panel. */
public final class EasyTradeModMenu implements ModMenuApi {
	/** Supplies the configuration button shown on easy-trade's Mod Menu entry. */
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return EasyTradeConfigScreen::new;
	}
}
