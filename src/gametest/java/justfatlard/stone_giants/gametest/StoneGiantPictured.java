package justfatlard.stone_giants.gametest;

import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.gametest.Pictures;
import justfatlard.stone_giants.StoneGiant;
import justfatlard.stone_giants.StoneGiants;
import justfatlard.stone_giants.Variety;
import net.minecraft.world.phys.AABB;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.Vec3;

/**
 * A giant asleep as an outcrop, the same giant standing, and one rising out of the rock, into
 * {@code build/run/clientGameTest/screenshots/} (and {@code screenshots/} with {@code -Ppictures}):
 * the way to see a change to the body, the skin, the flinch or the rise; then every variety in a row, up and then
 * asleep. Taken as {@code stone-giant-asleep}, {@code stone-giant-front}, {@code stone-giant-quarter},
 * {@code stone-giant-hurt}, {@code stone-giant-rising}, {@code stone-giant-varieties} and {@code stone-giant-outcrops}.
 */
public final class StoneGiantPictured implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			TestServerConnection connection = world.getConnection();
			connection.waitForChunksRender();
			server.waitFor(s -> PandoricalApi.isContentReady(connection.getServerPlayer()));
			Pictures.stage(context, server, Pictures.MORNING);

			int[] riser = new int[1];
			int[] front = new int[1];
			Vec3 centre = server.computeOnServer(s -> {
				ServerLevel level = s.overworld();
				BlockPos ground = Pictures.ground(level, connection.getServerPlayer().getBlockX(),
					connection.getServerPlayer().getBlockZ());
				Pictures.clearAround(level, ground, 12, 10);
				Vec3 at = Vec3.atBottomCenterOf(ground);
				asleep(level, ground.west(5));
				front[0] = stood(level, Variety.MOSSY, at.add(2, 0, 0)).getId();
				riser[0] = asleep(level, ground.east(14)).getId();
				return at;
			});
			context.waitTicks(20);

			Pictures.look(server, centre.add(-5, 2.4, -7), centre.add(-5, 0.8, 0));
			Pictures.settle(context, connection);
			System.out.println("[stone-giants test] picture " + Pictures.shoot(context, connection, "stone-giant-asleep").toAbsolutePath());

			Pictures.look(server, centre.add(2, 2.8, -10), centre.add(2, 2.5, 0));
			Pictures.settle(context, connection);
			System.out.println("[stone-giants test] picture " + Pictures.shoot(context, connection, "stone-giant-front").toAbsolutePath());

			Pictures.look(server, centre.add(-5, 4.5, -6.5), centre.add(2, 2.2, 0));
			Pictures.settle(context, connection);
			System.out.println("[stone-giants test] picture " + Pictures.shoot(context, connection, "stone-giant-quarter").toAbsolutePath());

			// Hit, and caught mid-flinch, which is over in half a second.
			server.runOnServer(s -> {
				ServerLevel level = s.overworld();
				level.getEntity(front[0]).hurtServer(level, level.damageSources().generic(), 1F);
			});
			context.waitTicks(2);
			Path hurt = context.takeScreenshot(TestScreenshotOptions.of("stone-giant-hurt")
				.withSize(Pictures.WIDTH, Pictures.HEIGHT).disableCounterPrefix());
			System.out.println("[stone-giants test] picture " + hurt.toAbsolutePath());

			// Woken, then caught early in its rise, still on its haunches.
			Pictures.look(server, centre.add(10, 3, -8), centre.add(14, 1.8, 0));
			Pictures.settle(context, connection);
			server.runOnServer(s -> ((StoneGiant) s.overworld().getEntity(riser[0])).wake(null));
			// Shot straight away rather than through Pictures.shoot, which waits on the world to settle
			// first: the rise is timed by the clock, and is over by the time a settle is.
			context.waitTicks(3);
			Path rising = context.takeScreenshot(TestScreenshotOptions.of("stone-giant-rising")
				.withSize(Pictures.WIDTH, Pictures.HEIGHT).disableCounterPrefix());
			System.out.println("[stone-giants test] picture " + rising.toAbsolutePath());

			// Every variety side by side, up, and then asleep as the rock each is made of.
			Vec3 row = server.computeOnServer(s -> {
				ServerLevel level = s.overworld();
				BlockPos ground = Pictures.ground(level, connection.getServerPlayer().getBlockX(),
					connection.getServerPlayer().getBlockZ() + 40);
				Pictures.clearAround(level, ground, 24, 12);
				for (Variety variety : Variety.values()) {
					stood(level, variety, Vec3.atBottomCenterOf(ground).add(lineup(variety), 0, 0));
				}
				return Vec3.atBottomCenterOf(ground);
			});
			context.waitTicks(20);
			Pictures.look(server, row.add(0, 5, -20), row.add(0, 2.5, 0));
			Pictures.settle(context, connection);
			System.out.println("[stone-giants test] picture " + Pictures.shoot(context, connection, "stone-giant-varieties").toAbsolutePath());

			server.runOnServer(s -> {
				for (StoneGiant giant : s.overworld().getEntitiesOfClass(StoneGiant.class, new AABB(row, row).inflate(24))) {
					if (!giant.settle(s.overworld())) throw new AssertionError("no room to lay a " + giant.variety().id() + " giant down");
				}
			});
			context.waitTicks(10);
			Pictures.settle(context, connection);
			System.out.println("[stone-giants test] picture " + Pictures.shoot(context, connection, "stone-giant-outcrops").toAbsolutePath());
		}
	}

	/** Across from the left of the picture, in the order they are declared, spaced alike, with room for the largest outcrop. */
	private static double lineup(Variety variety) {
		return (variety.ordinal() - (Variety.values().length - 1) / 2.0) * -9.0;
	}

	/** Lain down as rock, facing -z, toward the camera. */
	private static StoneGiant asleep(ServerLevel level, BlockPos feet) {
		StoneGiant giant = StoneGiants.type(Variety.MOSSY).create(level, EntitySpawnReason.COMMAND);
		giant.snapTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, 180F, 0F);
		level.addFreshEntity(giant);
		if (!giant.settle(level)) throw new AssertionError("no room to lay a giant down at " + feet);
		return giant;
	}

	/** Up, still, facing -z, toward the camera. */
	private static StoneGiant stood(ServerLevel level, Variety variety, Vec3 at) {
		StoneGiant giant = StoneGiants.type(variety).create(level, EntitySpawnReason.COMMAND);
		giant.snapTo(at.x, at.y, at.z, 180F, 0F);
		giant.setYHeadRot(180F);
		giant.setYBodyRot(180F);
		giant.setNoAi(true);
		level.addFreshEntity(giant);
		return giant;
	}
}
