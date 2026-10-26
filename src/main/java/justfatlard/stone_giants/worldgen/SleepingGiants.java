package justfatlard.stone_giants.worldgen;

import java.util.Arrays;
import justfatlard.stone_giants.Main;
import justfatlard.stone_giants.Variety;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * Where giants sleep: wherever a {@link Variety} is from - the bare and the snowy peaks, the
 * windswept hills, the stony shores, and the meadows under the peaks.
 *
 * <p>The feature type is registered here; the feature and where it is placed are data, under
 * {@code data/stone-giants-justfatlard/worldgen/}, so a datapack can make them rarer or commoner.
 */
public final class SleepingGiants {
	private SleepingGiants() {}

	private static final Identifier ID = Identifier.fromNamespaceAndPath(Main.MOD_ID, "sleeping_giant");

	private static final ResourceKey<PlacedFeature> PLACED = ResourceKey.create(Registries.PLACED_FEATURE, ID);

	public static void register() {
		Registry.register(BuiltInRegistries.FEATURE_TYPE, ID, SleepingGiantFeature.CODEC);

		// Where vanilla sets its mossy boulders down, which is what a sleeping giant is taken for.
		BiomeModifications.addFeature(
			BiomeSelectors.includeByKey(Arrays.stream(Variety.values())
				.flatMap(variety -> variety.biomes().stream()).toList()),
			GenerationStep.Decoration.LOCAL_MODIFICATIONS,
			PLACED);
	}
}
