package justfatlard.stone_giants.gametest;

import java.util.List;
import java.util.UUID;
import justfatlard.pandorical.entitykind.EntityKinds;
import justfatlard.pandorical.gametest.Smoke;
import justfatlard.stone_giants.Main;
import justfatlard.stone_giants.Outcrop;
import justfatlard.stone_giants.StoneGiant;
import justfatlard.stone_giants.StoneGiants;
import justfatlard.stone_giants.Variety;
import net.minecraft.world.entity.ai.attributes.Attributes;
import justfatlard.stone_giants.worldgen.SleepingGiantFeature;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * What the readme promises, on a walled stone floor in the sky: the spawn egg sets a giant down
 * asleep as an outcrop of real blocks with an ore on its front, hidden too small to hit inside them;
 * the first swing of a pickaxe at any of its stones wakes it, the rock crumbles, and it stands up
 * full size and comes for the miner, held still while it rises; a sleeping giant whose stones are
 * disturbed wakes on its own; left alone and unwatched it lies down again, healed; killed, it drops
 * the ore it wore and a few of its own stone; each variety lies in its own shape and stone, at its
 * own size and strength, wearing its own skin, and is the one for the biomes it is from; worldgen lays
 * one asleep, and is set to in every one of those biomes; each fights its own way, throwing rock,
 * throwing ice that freezes, stamping, shoving or lifting; and it is drawn from
 * its own body by a Pandorical client and sent to a vanilla one as an iron golem.
 */
public final class StoneGiantBehaviour implements FabricClientGameTest {

	private static final int FLOOR = 40;
	private static final int WALL = 4;

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, Main.MOD_ID, session -> {
			BlockPos[] floor = new BlockPos[1];
			UUID[] giantId = new UUID[1];
			UUID[] restlessId = new UUID[1];
			UUID[] lineup = new UUID[Variety.values().length];

			// Set down asleep by the egg: it lies down as rock on its next tick.
			session.onServer(server -> {
				var source = server.createCommandSourceStack().withSuppressedOutput();
				server.getCommands().performPrefixedCommand(source, "gamerule spawn_mobs false");
				server.getCommands().performPrefixedCommand(source, "difficulty normal");
				ServerPlayer player = session.player();
				ServerLevel level = (ServerLevel) player.level();
				player.setGameMode(GameType.SURVIVAL);
				floor[0] = player.blockPosition().above(40);
				lay(level, floor[0]);
				player.teleportTo(floor[0].getX() + 3.5, floor[0].getY() + 1, floor[0].getZ() + 3.5);

				StoneGiant giant = StoneGiants.type(Variety.MOSSY).spawn(level, feet(floor[0], 10, 10), EntitySpawnReason.SPAWN_ITEM_USE);
				check(giant != null, "the spawn egg made no giant");
				giantId[0] = giant.getUUID();

				StoneGiant restless = StoneGiants.type(Variety.MOSSY).spawn(level, feet(floor[0], 22, 22), EntitySpawnReason.SPAWN_ITEM_USE);
				restlessId[0] = restless.getUUID();

				// Every variety along the far side, each laid down in its own shape.
				for (Variety variety : Variety.values()) {
					StoneGiant sample = StoneGiants.type(variety).create(level, EntitySpawnReason.COMMAND);
					BlockPos at = feet(floor[0], 4 + variety.ordinal() * 8, FLOOR - 4);
					sample.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0F, 0F);
					level.addFreshEntity(sample);
					check(sample.settle(level), "a " + variety.id() + " giant found no room to lie down");
					lineup[variety.ordinal()] = sample.getUUID();
				}
			});
			session.waitTicks(5);

			// Each variety its own type, its own name, its own size, its own strength and its own stone.
			session.onServer(server -> {
				ServerLevel level = (ServerLevel) session.player().level();
				for (Variety variety : Variety.values()) {
					StoneGiant giant = (StoneGiant) level.getEntity(lineup[variety.ordinal()]);
					String name = "a " + variety.id() + " giant";
					check(giant.isAsleep() && giant.variety() == variety, name + " is not asleep as itself");
					check(giant.body().size() == variety.shape().stones(),
						name + " lies in " + giant.body().size() + " stones, not " + variety.shape().stones());
					check(Math.abs(giant.getAttributeValue(Attributes.SCALE) - variety.scale()) < 0.001,
						name + " is scale " + giant.getAttributeValue(Attributes.SCALE));
					check(giant.getMaxHealth() == (float) variety.health() && giant.getHealth() == giant.getMaxHealth(),
						name + " has " + giant.getHealth() + " of " + giant.getMaxHealth() + " health");
					check(giant.getName().getString().equals(variety.displayName()), name + " is called " + giant.getName().getString());
					check(giant.body().stream()
						.anyMatch(stone -> stone.state() == giant.ore()), name + " wears no ore");
					giant.discard();
					for (Outcrop.Stone stone : giant.body()) level.setBlockAndUpdate(stone.pos(), Blocks.AIR.defaultBlockState());
				}

				// The giant of each biome it sleeps in, and a mossy one elsewhere.
				var biomes = level.registryAccess().lookupOrThrow(Registries.BIOME);
				for (Variety variety : Variety.values()) {
					for (var key : variety.biomes()) {
						check(Variety.of(biomes.getOrThrow(key)) == variety, key.identifier() + " is not where " + variety.id() + " giants sleep");
					}
				}
				check(Variety.of(biomes.getOrThrow(Biomes.PLAINS)) == Variety.MOSSY, "the plains have no mossy giants");
			});

			session.onServer(server -> {
				ServerPlayer player = session.player();
				ServerLevel level = (ServerLevel) player.level();
				StoneGiant giant = (StoneGiant) level.getEntity(giantId[0]);
				check(giant.isAsleep(), "a giant from the spawn egg is awake");
				List<Outcrop.Stone> body = giant.body();
				check(giant.variety() == Variety.MOSSY, "a giant from the mossy egg is " + giant.variety().id());
				check(body.size() == Variety.MOSSY.shape().stones(), "a sleeping giant is " + body.size() + " stones");
				int ores = 0;
				for (Outcrop.Stone stone : body) {
					check(level.getBlockState(stone.pos()) == stone.state(), "a sleeping giant's stone at " + stone.pos() + " is not in the world");
					if (stone.state() == giant.ore()) ores++;
				}
				check(ores == 1, "a sleeping giant wears " + ores + " ores");
				check(giant.isInvisible(), "a sleeping giant can be seen");
				check(giant.getBbWidth() < 0.5F && giant.getBbHeight() < 0.5F,
					"a sleeping giant is " + giant.getBbWidth() + " by " + giant.getBbHeight() + ", too big to hide in its stones");
				check(!giant.isPickable(), "a sleeping giant can be hit");
				check(!giant.canBeSeenAsEnemy(), "a sleeping giant can be hunted");
				check(StoneGiant.sleepingIn(level, body.getFirst().pos()) == giant, "a sleeping giant's stone does not know it");
				check(StoneGiant.sleepingIn(level, floor[0].offset(2, 1, 2)) == null, "open air belongs to a giant");

				// The first swing of a pickaxe at its ore.
				BlockPos ore = body.stream().filter(stone -> stone.state() == giant.ore()).findFirst().orElseThrow().pos();
				Vec3 stand = Vec3.atBottomCenterOf(ore).add(Vec3.atLowerCornerOf(Direction.fromYRot(giant.getYRot()).getUnitVec3i()).scale(2));
				player.teleportTo(stand.x, floor[0].getY() + 1, stand.z);
				player.gameMode.handleBlockBreakAction(ore, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
					Direction.UP, level.getMaxY(), 0);

				check(!giant.isAsleep(), "a swing at a sleeping giant's ore did not wake it");
				for (Outcrop.Stone stone : body) {
					check(level.getBlockState(stone.pos()).isAir(), "a woken giant left its stone at " + stone.pos());
				}
				check(giant.body().isEmpty(), "a woken giant still keeps stones");
				check(!giant.isInvisible() && giant.isPickable(), "a woken giant cannot be seen or hit");
				check(Math.abs(giant.getBbHeight() - EntityTypes.IRON_GOLEM.getHeight() * giant.variety().scale()) < 0.01F,
					"a woken giant stands " + giant.getBbHeight() + " tall");
				check(giant.getTarget() == player, "a woken giant is after " + giant.getTarget());
				check(giant.isRising(), "a woken giant is already up");
				check(awarded(player, "woke_a_giant"), "waking a giant earned nothing");
			});

			// Held still while it rises, so it cannot reach anybody before it is up.
			session.waitTicks(StoneGiant.RISING_TICKS / 2);
			session.onServer(server -> {
				ServerLevel level = (ServerLevel) session.player().level();
				StoneGiant giant = (StoneGiant) level.getEntity(giantId[0]);
				check(giant.isRising(), "a giant was up after " + StoneGiant.RISING_TICKS / 2 + " ticks");
				check(session.player().getHealth() == session.player().getMaxHealth(), "a rising giant struck its miner");
			});
			session.waitTicks(StoneGiant.RISING_TICKS / 2 + 5);
			session.onServer(server -> {
				ServerLevel level = (ServerLevel) session.player().level();
				StoneGiant giant = (StoneGiant) level.getEntity(giantId[0]);
				check(!giant.isRising(), "a giant was still rising after " + (StoneGiant.RISING_TICKS + 5) + " ticks");

				// Its stones disturbed by anything at all, it wakes by itself.
				StoneGiant restless = (StoneGiant) level.getEntity(restlessId[0]);
				check(restless.isAsleep(), "the second giant was not asleep");
				level.setBlockAndUpdate(restless.body().getLast().pos(), Blocks.AIR.defaultBlockState());

				// Left alone and unwatched, hurt, with nobody to fight.
				ServerPlayer player = session.player();
				player.setGameMode(GameType.CREATIVE);
				player.teleportTo(floor[0].getX() + FLOOR / 2.0, floor[0].getY() + 1, floor[0].getZ() + FLOOR + 40);
				giant.setTarget(null);
				giant.setHealth(30F);
			});
			session.waitTicks(25);
			session.onServer(server -> {
				ServerLevel level = (ServerLevel) session.player().level();
				StoneGiant restless = (StoneGiant) level.getEntity(restlessId[0]);
				check(!restless.isAsleep(), "a giant whose stone was taken slept on");
			});

			session.waitTicks(StoneGiant.SETTLE_AFTER + 60);
			session.onServer(server -> {
				ServerLevel level = (ServerLevel) session.player().level();
				StoneGiant giant = (StoneGiant) level.getEntity(giantId[0]);
				check(giant.isAsleep(), "a giant left a minute alone did not lie down again");
				check(giant.body().size() == giant.variety().shape().stones(), "a giant that lay down again is " + giant.body().size() + " stones");
				for (Outcrop.Stone stone : giant.body()) {
					check(level.getBlockState(stone.pos()) == stone.state(), "a giant lay down without its stone at " + stone.pos());
				}
				check(giant.getHealth() == giant.getMaxHealth(), "a giant lay down at " + giant.getHealth() + " health");
			});

			// Killed, it gives back its ore.
			session.onServer(server -> {
				ServerLevel level = (ServerLevel) session.player().level();
				StoneGiant giant = StoneGiants.type(Variety.MOSSY).spawn(level, feet(floor[0], 6, 24), EntitySpawnReason.COMMAND);
				check(!giant.isAsleep(), "a giant from a command is asleep");
				var ore = giant.ore().getBlock().asItem();
				giant.kill(level);
				List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, giant.getBoundingBox().inflate(4));
				check(drops.stream().anyMatch(drop -> drop.getItem().is(ore)), "a dead giant did not drop its " + ore);
				check(drops.stream().anyMatch(drop -> drop.getItem().is(Blocks.COBBLESTONE.asItem())), "a dead giant did not drop cobblestone");
				check(drops.stream().anyMatch(drop -> drop.getItem().is(giant.variety().keepsake().asItem())),
					"a dead giant did not drop its " + giant.variety().keepsake());
				drops.forEach(Entity::discard);
			});

			// Worldgen lays one asleep, and is set to in the stony biomes.
			session.onServer(server -> {
				ServerLevel level = (ServerLevel) session.player().level();
				BlockPos at = feet(floor[0], 22, 8);
				boolean placed = new SleepingGiantFeature().place(level, level.getChunkSource().getGenerator(), level.getRandom(), at);
				check(placed, "the sleeping giant feature placed nothing on open floor");
				List<StoneGiant> laid = level.getEntitiesOfClass(StoneGiant.class, new AABB(at).inflate(Outcrop.REACH),
					each -> each.isAsleep() && !each.getUUID().equals(giantId[0]) && !each.getUUID().equals(restlessId[0]));
				check(laid.size() == 1 && laid.getFirst().body().size() == laid.getFirst().variety().shape().stones()
					&& laid.getFirst().variety() == Variety.of(level.getBiome(at)), "worldgen laid " + laid.size() + " sleeping giants");

				PlacedFeature feature = level.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE)
					.getValueOrThrow(ResourceKey.create(Registries.PLACED_FEATURE, Identifier.fromNamespaceAndPath(Main.MOD_ID, "sleeping_giant")));
				var biomes = level.registryAccess().lookupOrThrow(Registries.BIOME);
				check(biomes.getValueOrThrow(Biomes.STONY_PEAKS).getGenerationSettings().hasFeature(feature), "no giants sleep in the stony peaks");
				check(!biomes.getValueOrThrow(Biomes.PLAINS).getGenerationSettings().hasFeature(feature), "giants sleep in the plains");
				for (Variety variety : Variety.values()) for (var key : variety.biomes()) {
					check(biomes.getValueOrThrow(key).getGenerationSettings().hasFeature(feature), "no giants sleep in " + key.identifier());
				}
			});

			// Each fights its own way. A thrower throws from a distance, rock or ice that freezes; a
			// stamper shakes the ground under what is near; a shore giant's nip sends you a long way;
			// a mossy giant's fist sends you up. The targets are villagers held still where they stand.
			UUID[] targets = new UUID[3];
			session.onServer(server -> {
				ServerLevel level = (ServerLevel) session.player().level();
				int lane = 0;
				for (Variety variety : List.of(Variety.CRAGGY, Variety.FROST, Variety.PEAK)) {
					BlockPos at = feet(floor[0], 4, 26 + lane * 4);
					StoneGiant giant = StoneGiants.type(variety).spawn(level, at, EntitySpawnReason.COMMAND);
					double gap = variety.attack().thrown() != null ? 12 : 3;
					Villager villager = EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
					villager.snapTo(at.getX() + 0.5 + gap, at.getY(), at.getZ() + 0.5, 90F, 0F);
					villager.setNoAi(true);
					level.addFreshEntity(villager);
					giant.setTarget(villager);
					targets[lane++] = villager.getUUID();
				}
			});
			session.waitTicks(StoneGiant.THROW_WINDUP + 40);
			session.onServer(server -> {
				ServerLevel level = (ServerLevel) session.player().level();
				Villager struck = (Villager) level.getEntity(targets[0]);
				check(struck == null || struck.getHealth() < struck.getMaxHealth(), "a crag giant hit nothing from twelve blocks off");
				Villager frozen = (Villager) level.getEntity(targets[1]);
				check(frozen == null || frozen.hasEffect(MobEffects.SLOWNESS) && frozen.getTicksFrozen() > 0,
					"a frost giant's ice did not freeze what it hit");
				Villager stamped = (Villager) level.getEntity(targets[2]);
				check(stamped == null || stamped.getHealth() < stamped.getMaxHealth(), "a peak giant stamped and hurt nothing beside it");
				level.getEntitiesOfClass(StoneGiant.class, new AABB(feet(floor[0], 4, 26)).inflate(16), each -> !each.isAsleep()
					&& each.variety() != Variety.MOSSY).forEach(Entity::discard);
				for (UUID id : targets) {
					Entity left = level.getEntity(id);
					if (left != null) left.discard();
				}

				// Struck by hand: a shore giant's nip goes a long way back, a mossy giant's fist up.
				Vec3 shoved = struck(level, Variety.SHORE, feet(floor[0], 30, 26));
				Vec3 lifted = struck(level, Variety.MOSSY, feet(floor[0], 30, 32));
				check(shoved.horizontalDistance() > 1.2, "a shore giant's nip sent a villager back " + shoved.horizontalDistance());
				check(lifted.y > 0.3 && lifted.horizontalDistance() < shoved.horizontalDistance(),
					"a mossy giant's fist sent a villager " + lifted);
			});

			// A vanilla client is shown an iron golem in its place; a Pandorical client is shown the giant.
			session.onServer(server -> {
				ServerPlayer player = session.player();
				StoneGiant giant = (StoneGiant) ((ServerLevel) player.level()).getEntity(giantId[0]);
				ClientboundAddEntityPacket add = new ClientboundAddEntityPacket(giant.getId(), giant.getUUID(),
					giant.getX(), giant.getY(), giant.getZ(), 0F, 0F, giant.getType(), 0, Vec3.ZERO, 0);
				EntityKinds.understood(player.getUUID(), false);
				try {
					check(EntityKinds.mayShow(giant, player), "a vanilla client was refused a giant");
					check(EntityKinds.forPlayer(add, player).getType() == EntityTypes.IRON_GOLEM,
						"a vanilla client was sent " + EntityKinds.forPlayer(add, player).getType());
				} finally {
					EntityKinds.understood(player.getUUID(), true);
				}
				check(EntityKinds.forPlayer(add, player).getType() == StoneGiants.type(Variety.MOSSY),
					"a Pandorical client was sent " + EntityKinds.forPlayer(add, player).getType());

				// Awake again for the client to draw it.
				giant.wake(null);
				player.teleportTo(giant.getX() + 8, giant.getY(), giant.getZ());
			});

			// Drawn from its own body, under its own type.
			session.waitTicks(20);
			String drawn = session.context().computeOnClient(client -> {
				for (Entity each : client.level.entitiesForRendering()) {
					if (!each.getUUID().equals(giantId[0])) continue;
					var renderer = client.getEntityRenderDispatcher().getRenderer(each);
					return (renderer == null ? "nothing" : renderer.getClass().getSimpleName()) + " for "
						+ BuiltInRegistries.ENTITY_TYPE.getKey(each.getType());
				}
				return "no giant on the client";
			});
			check(drawn.equals("BodyRenderer for stone-giants-justfatlard:mossy_giant"), "the client drew " + drawn);
		});
	}

	/** How a villager beside a giant of this variety is sent flying by one blow. */
	private static Vec3 struck(ServerLevel level, Variety variety, BlockPos at) {
		StoneGiant giant = StoneGiants.type(variety).spawn(level, at, EntitySpawnReason.COMMAND);
		Villager villager = EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
		villager.snapTo(at.getX() + 2.5, at.getY(), at.getZ() + 0.5, 90F, 0F);
		level.addFreshEntity(villager);
		villager.setDeltaMovement(Vec3.ZERO);
		check(giant.doHurtTarget(level, villager), "a " + variety.id() + " giant's blow missed");
		Vec3 sent = villager.getDeltaMovement();
		giant.discard();
		villager.discard();
		return sent;
	}

	/** Where a giant's feet go, standing on the floor at this corner of it. */
	private static BlockPos feet(BlockPos floor, int x, int z) {
		return floor.offset(x, 1, z);
	}

	private static boolean awarded(ServerPlayer player, String path) {
		AdvancementHolder holder = player.level().getServer().getAdvancements().get(Identifier.fromNamespaceAndPath(Main.MOD_ID, path));
		return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
	}

	/** A floor with a wall round it the giants cannot step over, and headroom over it. */
	private static void lay(ServerLevel level, BlockPos corner) {
		for (int x = -1; x <= FLOOR; x++) for (int z = -1; z <= FLOOR; z++) {
			boolean edge = x < 0 || z < 0 || x == FLOOR || z == FLOOR;
			level.setBlockAndUpdate(corner.offset(x, 0, z), Blocks.STONE.defaultBlockState());
			for (int y = 1; y <= 8; y++) {
				level.setBlockAndUpdate(corner.offset(x, y, z), edge && y <= WALL
					? Blocks.BARRIER.defaultBlockState() : Blocks.AIR.defaultBlockState());
			}
		}
	}

	private static void check(boolean condition, String failure) {
		if (!condition) throw new AssertionError(failure);
	}
}
