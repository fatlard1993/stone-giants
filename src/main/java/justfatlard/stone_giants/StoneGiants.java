package justfatlard.stone_giants;

import java.util.EnumMap;
import java.util.Map;
import justfatlard.pandorical.api.EntityRegistration;
import justfatlard.pandorical.api.ItemRegistration;
import justfatlard.pandorical.api.PandoricalApi;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The giants as the game knows them: an entity type and a spawn egg for each {@link Variety}, how
 * Pandorical draws each, and the moment a pickaxe meets one of their stones.
 *
 * <p>Pandorical clients build each from its own body, at its own proportions, and play its walk,
 * its blows and its rising. Vanilla clients are shown an iron golem in its place, which can read
 * everything a giant syncs. Every type is an iron golem's size, made big by its scale attribute,
 * which every client reads, so what anybody sees and what they can hit agree.
 */
public final class StoneGiants {
	private StoneGiants() {}

	private static final Map<Variety, EntityType<StoneGiant>> TYPES = new EnumMap<>(Variety.class);
	private static final Map<Variety, Item> EGGS = new EnumMap<>(Variety.class);

	static {
		for (Variety variety : Variety.values()) {
			EntityType<StoneGiant> type = FabricEntityType.Builder.createMob(StoneGiant::new,
					MobCategory.MONSTER, mob -> mob.defaultAttributes(() -> StoneGiant.createAttributes(variety)))
				.sized(EntityTypes.IRON_GOLEM.getWidth(), EntityTypes.IRON_GOLEM.getHeight())
				.eyeHeight(EntityTypes.IRON_GOLEM.getDimensions().eyeHeight())
				.clientTrackingRange(EntityTypes.IRON_GOLEM.clientTrackingRange())
				.build(ResourceKey.create(Registries.ENTITY_TYPE, variety.typeIdentifier()));
			TYPES.put(variety, type);
			EGGS.put(variety, new SpawnEggItem(new Item.Properties()
				.setId(ResourceKey.create(Registries.ITEM, eggId(variety)))
				.spawnEgg(type)));
		}
	}

	public static EntityType<StoneGiant> type(final Variety variety) {
		return TYPES.get(variety);
	}

	public static Item egg(final Variety variety) {
		return EGGS.get(variety);
	}

	/** Which giant an entity of this type is; null for anything that is not a giant. */
	public static @Nullable Variety varietyOf(final EntityType<?> type) {
		for (Map.Entry<Variety, EntityType<StoneGiant>> entry : TYPES.entrySet()) {
			if (entry.getValue() == type) return entry.getKey();
		}
		return null;
	}

	private static Identifier eggId(final Variety variety) {
		return Identifier.fromNamespaceAndPath(Main.MOD_ID, variety.typeId() + "_spawn_egg");
	}

	/** Its own body, skin and motion, from {@code generate_textures.py}. */
	public static Identifier skin(final Variety variety) {
		return Identifier.fromNamespaceAndPath(Main.MOD_ID, "textures/entity/" + variety.typeId() + ".png");
	}

	public static void register() {
		for (Variety variety : Variety.values()) {
			Registry.register(BuiltInRegistries.ENTITY_TYPE, variety.typeIdentifier(), TYPES.get(variety));
			Registry.register(BuiltInRegistries.ITEM, eggId(variety), EGGS.get(variety));

			// The body is modelled a block to sixteen units, as its stones are, and so at full size
			// already: drawn down by the scale the attribute puts back on.
			PandoricalApi.content().registerEntity(TYPES.get(variety), new EntityRegistration()
				.model(variety.typeIdentifier())
				.texture(skin(variety))
				.drawScale(1.0F / variety.scale())
				.vanillaFallback(EntityTypes.IRON_GOLEM));
			PandoricalApi.content().registerItem(eggId(variety).toString(), new ItemRegistration());
		}

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS)
			.register(output -> EGGS.values().forEach(output::accept));

		// The first swing at one of its stones wakes it, before the stone can break. Breaking a block
		// with no swing first, as some mods do, wakes it too.
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) ->
			wakeAt(player, level, pos) ? InteractionResult.FAIL : InteractionResult.PASS);
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) ->
			!wakeAt(player, level, pos));
	}

	/** Wakes the giant sleeping in {@code pos}, if there is one; true if there was. */
	private static boolean wakeAt(final Player player, final Level level, final BlockPos pos) {
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer miner) || miner.isSpectator()) return false;
		StoneGiant giant = StoneGiant.sleepingIn(server, pos);
		if (giant == null) return false;
		giant.wake(miner);
		return true;
	}
}
