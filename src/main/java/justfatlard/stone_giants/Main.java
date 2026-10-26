package justfatlard.stone_giants;

import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.stone_giants.worldgen.SleepingGiants;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Giants of living stone, asleep in the mountains as outcrops of rock, that wake when someone
 * starts mining them.
 */
public class Main implements ModInitializer {
	public static final String MOD_ID = "stone-giants-justfatlard";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		StoneGiants.register();
		SleepingGiants.register();
		// The skin, the body, how it moves, the egg's picture and the names.
		PandoricalApi.content().registerModAssets(MOD_ID);

		LOGGER.info("[{}] Stone giants asleep in the mountains", MOD_ID);
	}
}
