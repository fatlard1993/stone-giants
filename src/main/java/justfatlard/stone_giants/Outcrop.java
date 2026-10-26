package justfatlard.stone_giants;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A sleeping giant as the world sees it: a hunched outcrop of whatever rock it is made of, and on
 * its front one ore, showing, which is what gets it mined.
 *
 * <p>The blocks are real, so a vanilla client sees the disguise as well as anybody, and the giant
 * keeps a list of them so it knows its own stones from whatever was built against it later.
 */
public final class Outcrop {
	private Outcrop() {}

	/** One block of a sleeping giant, as it was laid. */
	public record Stone(BlockPos pos, BlockState state) {
		public static final Codec<Stone> CODEC = RecordCodecBuilder.create(stone -> stone.group(
			BlockPos.CODEC.fieldOf("pos").forGetter(Stone::pos),
			BlockState.CODEC.fieldOf("state").forGetter(Stone::state)
		).apply(stone, Stone::new));
	}

	/**
	 * The shape a giant lies in. From the ground up; in each layer, rows from its back to its front,
	 * and across from its right to its left. The ore is low on its front, where a passing miner sees
	 * it first.
	 */
	public enum Shape {
		/** Hunched forward, shoulders wider than its hips, its head sunk between them. */
		HUMP(new String[][] {
			{".###.", "#####", ".###."},
			{".###.", "#####", ".#*#."},
			{"..#..", ".###.", "..#.."},
		}),
		/** Broken and tall, a shard standing up off one shoulder. */
		CRAG(new String[][] {
			{".###.", "#####", ".###."},
			{".#.#.", "#####", ".#*#."},
			{"...#.", ".###.", ".#..."},
			{"...#.", "..#..", "....."},
			{"...#.", ".....", "....."},
		}),
		/** A stepped tor, broad at the foot and climbing to a summit. */
		TOR(new String[][] {
			{"..###..", ".#####.", "#######", ".#####.", "..###.."},
			{"..###..", ".#####.", "#######", ".#####.", "..#*#.."},
			{"...#...", "..###..", ".#####.", "..###..", "......."},
			{".......", "...#...", "..###..", "...#...", "......."},
			{".......", ".......", "...#...", ".......", "......."},
		}),
		/** Low and flat, worn down by the sea, its shell barely clear of the shingle. */
		SHELF(new String[][] {
			{"#####", "#####", "#####"},
			{"..#..", ".###.", ".#*#."},
		}),
		/** A great round drift, wider than it is deep, the snow banked up over it. */
		DRIFT(new String[][] {
			{".#####.", "#######", "#######", ".#####."},
			{".#####.", "#######", "#######", ".##*##."},
			{"..###..", ".#####.", ".#####.", "..###.."},
			{"...#...", "..###..", "..###..", "...#..."},
		});

		private final String[][] layers;

		Shape(final String[][] layers) {
			this.layers = layers;
		}

		/** How many stones it is laid in. */
		public int stones() {
			int count = 0;
			for (String[] rows : this.layers) for (String row : rows) for (char mark : row.toCharArray()) if (mark != '.') count++;
			return count;
		}
	}

	/** Furthest any of its stones lies from its feet, in blocks, at the largest: how far to look for the giant a stone belongs to. */
	public static final int REACH = 4;

	/** Where each stone goes for a giant whose feet are at {@code feet} and who faces {@code facing}. */
	private record Cell(BlockPos pos, boolean top, boolean ore) {}

	private static List<Cell> cells(final Shape shape, final BlockPos feet, final Direction facing) {
		Direction right = facing.getClockWise();
		String[][] layers = shape.layers;
		List<Cell> cells = new ArrayList<>();
		for (int y = 0; y < layers.length; y++) {
			String[] rows = layers[y];
			for (int row = 0; row < rows.length; row++) {
				// Rows run back to front; the front half is the larger where the count is even, so it
				// faces forward of its feet rather than behind them.
				int forward = row - (rows.length - 1) / 2;
				for (int column = 0; column < rows[row].length(); column++) {
					char mark = rows[row].charAt(column);
					if (mark == '.') continue;
					int across = column - rows[row].length() / 2;
					BlockPos pos = feet.above(y).relative(facing, forward).relative(right, across);
					cells.add(new Cell(pos, !solidAbove(layers, y, row, column), mark == '*'));
				}
			}
		}
		return cells;
	}

	private static boolean solidAbove(final String[][] layers, final int y, final int row, final int column) {
		return y + 1 < layers.length && layers[y + 1][row].charAt(column) != '.';
	}

	/** Whether a giant could lie down here: open ground to lie on, and nothing in the way but grass and snow. */
	public static boolean fits(final Shape shape, final LevelReader level, final BlockPos feet, final Direction facing) {
		return fits(shape, level, feet, facing, BlockState::canBeReplaced);
	}

	/** Whether a giant could lie down here, where it may take the place of whatever {@code yields}. */
	public static boolean fits(final Shape shape, final LevelReader level, final BlockPos feet, final Direction facing,
			final Predicate<BlockState> yields) {
		BlockPos ground = feet.below();
		if (!level.getBlockState(ground).isFaceSturdy(level, ground, Direction.UP)) return false;
		for (Cell cell : cells(shape, feet, facing)) {
			BlockState there = level.getBlockState(cell.pos());
			if (!yields.test(there) || !there.getFluidState().isEmpty()) return false;
		}
		return true;
	}

	/**
	 * Fills the ground in under it, where it lies across a dip, so it rests on the hillside rather
	 * than hanging off it. The fill is ground, not giant: it stays when the giant gets up.
	 */
	public static void bed(final Shape shape, final LevelAccessor level, final BlockPos feet, final Direction facing, final int deepest) {
		for (Cell cell : cells(shape, feet, facing)) {
			if (cell.pos().getY() != feet.getY()) continue;
			BlockPos under = cell.pos().below();
			for (int depth = 0; depth < deepest && level.getBlockState(under).canBeReplaced(); depth++, under = under.below()) {
				level.setBlock(under, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}
	}

	/** Lays a giant's stones, in its own rock, with {@code ore} on its front, and says which they were. */
	public static List<Stone> lay(final Variety variety, final LevelAccessor level, final BlockPos feet, final Direction facing,
			final RandomSource random, final BlockState ore) {
		List<Stone> laid = new ArrayList<>();
		for (Cell cell : cells(variety.shape(), feet, facing)) {
			BlockState state = cell.ore() ? ore : variety.stone(random, cell.top());
			level.setBlock(cell.pos(), state, Block.UPDATE_ALL);
			laid.add(new Stone(cell.pos(), state));
		}
		return laid;
	}
}
