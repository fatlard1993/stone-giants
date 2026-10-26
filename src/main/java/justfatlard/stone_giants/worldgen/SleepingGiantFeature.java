package justfatlard.stone_giants.worldgen;

import com.mojang.serialization.MapCodec;
import java.util.List;
import justfatlard.stone_giants.Outcrop;
import justfatlard.stone_giants.StoneGiant;
import justfatlard.stone_giants.StoneGiants;
import justfatlard.stone_giants.Variety;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * A giant asleep on the hillside, of the biome's own rock: its outcrop laid on the surface, half into the slope where the
 * slope rises, and the giant itself lying inside it.
 */
public final class SleepingGiantFeature implements Feature {
	public static final MapCodec<SleepingGiantFeature> CODEC = MapCodec.unit(SleepingGiantFeature::new);

	/** How far down it fills a dip under itself; a deeper drop is a cliff edge, and no place to sleep. */
	private static final int DEEPEST_DIP = 3;

	@Override
	public MapCodec<? extends Feature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(final WorldGenLevel level, final ChunkGenerator generator, final RandomSource random, final BlockPos origin) {
		Direction facing = Direction.Plane.HORIZONTAL.getRandomDirection(random);
		Variety variety = Variety.of(level.getBiome(origin));
		if (!Outcrop.fits(variety.shape(), level, origin, facing, SleepingGiantFeature::yields)) return false;

		StoneGiant giant = StoneGiants.type(variety).create(level.getLevel(), EntitySpawnReason.STRUCTURE);
		if (giant == null) return false;
		Outcrop.bed(variety.shape(), level, origin, facing, DEEPEST_DIP);
		BlockState ore = variety.ore(random);
		List<Outcrop.Stone> stones = Outcrop.lay(variety, level, origin, facing, random, ore);
		giant.sleepIn(origin, facing, stones, ore);
		level.addFreshEntity(giant);
		return true;
	}

	/** The hillside gives way to it: its rock, soil and snow, as well as anything a sleeping giant would crush. */
	private static boolean yields(final BlockState state) {
		return state.canBeReplaced() || state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.DIRT)
			|| state.is(BlockTags.SNOW) || state.is(net.minecraft.world.level.block.Blocks.GRAVEL);
	}
}
