package justfatlard.stone_giants;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Which giant this is, which is a matter of where it fell asleep: each kind of high country has its
 * own, made of its own rock, built its own way, and fighting its own way.
 *
 * <p>Each is an entity type of its own, with its own body, skin and animations from
 * {@code generate_textures.py}, and its own outcrop to sleep as, laid in its own rock with its own
 * ores on its front. The body is modelled at its full size; the scale attribute is what makes its
 * hitbox, and is taken back off the drawing.
 */
public enum Variety {
	/** The meadows and the wooded hills: old, hunched and grown over, a brawler. */
	MOSSY("mossy", "mossy_giant", "Mossy Giant", Outcrop.Shape.HUMP, 1.9F, 0.22, Attack.FISTS,
		new Mix().with(Blocks.STONE, 45).with(Blocks.COBBLESTONE, 25).with(Blocks.ANDESITE, 15).with(Blocks.MOSSY_COBBLESTONE, 15),
		new Mix().with(Blocks.MOSSY_COBBLESTONE, 45).with(Blocks.MOSS_BLOCK, 25).with(Blocks.STONE, 15).with(Blocks.COBBLESTONE, 15),
		new Mix().with(Blocks.COAL_ORE, 55).with(Blocks.IRON_ORE, 30).with(Blocks.COPPER_ORE, 10).with(Blocks.GOLD_ORE, 5),
		Blocks.MOSSY_COBBLESTONE, Blocks.GRAVEL,
		Set.of(Biomes.MEADOW, Biomes.WINDSWEPT_FOREST)),

	/** The windswept hills: lanky, broken and bad tempered, it throws the hillside at you. */
	CRAGGY("craggy", "crag_giant", "Crag Giant", Outcrop.Shape.CRAG, 2.0F, 0.24, Attack.THROWS_ROCK,
		new Mix().with(Blocks.ANDESITE, 35).with(Blocks.STONE, 30).with(Blocks.COBBLESTONE, 25).with(Blocks.MOSSY_COBBLESTONE, 10),
		new Mix().with(Blocks.COBBLESTONE, 45).with(Blocks.ANDESITE, 35).with(Blocks.MOSSY_COBBLESTONE, 20),
		new Mix().with(Blocks.COAL_ORE, 40).with(Blocks.IRON_ORE, 35).with(Blocks.COPPER_ORE, 15).with(Blocks.EMERALD_ORE, 10),
		Blocks.ANDESITE, Blocks.GRAVEL,
		Set.of(Biomes.WINDSWEPT_HILLS, Biomes.WINDSWEPT_GRAVELLY_HILLS)),

	/** The bare peaks: the biggest, a summit on legs, and the ground shakes when it stamps. */
	PEAK("peak", "peak_giant", "Peak Giant", Outcrop.Shape.TOR, 2.6F, 0.19, Attack.STOMPS,
		new Mix().with(Blocks.STONE, 45).with(Blocks.CALCITE, 20).with(Blocks.ANDESITE, 15).with(Blocks.TUFF, 10).with(Blocks.COBBLESTONE, 10),
		new Mix().with(Blocks.STONE, 50).with(Blocks.CALCITE, 35).with(Blocks.COBBLESTONE, 15),
		new Mix().with(Blocks.IRON_ORE, 40).with(Blocks.COAL_ORE, 25).with(Blocks.GOLD_ORE, 15).with(Blocks.EMERALD_ORE, 10).with(Blocks.COPPER_ORE, 10),
		Blocks.CALCITE, Blocks.CALCITE,
		Set.of(Biomes.STONY_PEAKS)),

	/** The stony shores: squat and shelled like a turtle, quick, and it shoves you into the sea. */
	SHORE("shore", "shore_giant", "Shore Giant", Outcrop.Shape.SHELF, 1.2F, 0.28, Attack.SHOVES,
		new Mix().with(Blocks.STONE, 45).with(Blocks.ANDESITE, 25).with(Blocks.COBBLESTONE, 20).with(Blocks.SMOOTH_STONE, 10),
		new Mix().with(Blocks.STONE, 50).with(Blocks.SMOOTH_STONE, 30).with(Blocks.MOSSY_COBBLESTONE, 20),
		new Mix().with(Blocks.COAL_ORE, 45).with(Blocks.COPPER_ORE, 40).with(Blocks.IRON_ORE, 15),
		Blocks.SMOOTH_STONE, Blocks.SAND,
		Set.of(Biomes.STONY_SHORE)),

	/** The snow on the high peaks: bearded with icicles, and it throws ice that freezes where it hits. */
	FROST("frost", "frost_giant", "Frost Giant", Outcrop.Shape.DRIFT, 2.4F, 0.2, Attack.THROWS_ICE,
		new Mix().with(Blocks.STONE, 40).with(Blocks.PACKED_ICE, 25).with(Blocks.ANDESITE, 15).with(Blocks.COBBLESTONE, 10).with(Blocks.SNOW_BLOCK, 10),
		new Mix().with(Blocks.SNOW_BLOCK, 70).with(Blocks.PACKED_ICE, 15).with(Blocks.STONE, 15),
		new Mix().with(Blocks.IRON_ORE, 45).with(Blocks.COAL_ORE, 40).with(Blocks.EMERALD_ORE, 15),
		Blocks.PACKED_ICE, Blocks.SNOW_BLOCK,
		Set.of(Biomes.FROZEN_PEAKS, Biomes.JAGGED_PEAKS, Biomes.SNOWY_SLOPES, Biomes.GROVE));

	/** How it fights, besides walking up and hitting you, which they all do. */
	public enum Attack {
		/** Both fists down on you: up as well as back. */
		FISTS(null),
		/** Tears a chunk off itself and throws it, from a distance. */
		THROWS_ROCK(Blocks.COBBLESTONE),
		/** The same with ice, which freezes and slows what it hits. */
		THROWS_ICE(Blocks.PACKED_ICE),
		/** Stamps the ground: everything near is hurt and thrown up. */
		STOMPS(null),
		/** Quick nips that shove you a long way back. */
		SHOVES(null);

		private final @Nullable Block thrown;

		Attack(final @Nullable Block thrown) {
			this.thrown = thrown;
		}

		/** What it throws, or null for one that does not throw. */
		public @Nullable Block thrown() { return this.thrown; }
	}

	// Health and blow at the size a mossy giant's body is modelled at, and in step with size.
	private static final double HEALTH_AT_MOSSY = 120.0;
	private static final double DAMAGE_AT_MOSSY = 14.0;
	private static final float MOSSY_SCALE = 1.9F;

	private final String id;
	private final String typeId;
	private final String name;
	private final Outcrop.Shape shape;
	private final float scale;
	private final double speed;
	private final Attack attack;
	private final Mix body;
	private final Mix tops;
	private final Mix ores;
	private final Block keepsake;
	private final Block dust;
	private final Set<ResourceKey<Biome>> biomes;

	Variety(final String id, final String typeId, final String name, final Outcrop.Shape shape, final float scale,
			final double speed, final Attack attack, final Mix body, final Mix tops, final Mix ores, final Block keepsake,
			final Block dust, final Set<ResourceKey<Biome>> biomes) {
		this.id = id;
		this.typeId = typeId;
		this.name = name;
		this.shape = shape;
		this.scale = scale;
		this.speed = speed;
		this.attack = attack;
		this.body = body;
		this.tops = tops;
		this.ores = ores;
		this.keepsake = keepsake;
		this.dust = dust;
		this.biomes = biomes;
	}

	public String id() { return this.id; }
	/** Its entity type's path, which also names its model, its skin and its animations. */
	public String typeId() { return this.typeId; }
	public String displayName() { return this.name; }
	public Outcrop.Shape shape() { return this.shape; }
	public float scale() { return this.scale; }
	public double speed() { return this.speed; }
	public Attack attack() { return this.attack; }
	public Set<ResourceKey<Biome>> biomes() { return this.biomes; }
	/** What it drops besides its ore and its cobblestone: a few of the stone it is most made of. */
	public Block keepsake() { return this.keepsake; }
	/** What trickles off its back while it sleeps. */
	public Block dust() { return this.dust; }

	public double health() { return HEALTH_AT_MOSSY * this.scale / MOSSY_SCALE; }
	public double damage() { return DAMAGE_AT_MOSSY * this.scale / MOSSY_SCALE; }

	public Identifier typeIdentifier() {
		return Identifier.fromNamespaceAndPath(Main.MOD_ID, this.typeId);
	}

	/** One of its animations: {@code walk}, {@code idle}, {@code blow}, {@code rise}, {@code throw}, {@code stomp}. */
	public String animation(final String name) {
		return Main.MOD_ID + ":" + this.typeId + "_" + name;
	}

	/** A stone of its body, or of its top, where the weather reaches. */
	public BlockState stone(final RandomSource random, final boolean top) {
		return (top ? this.tops : this.body).pick(random).defaultBlockState();
	}

	public BlockState ore(final RandomSource random) {
		return this.ores.pick(random).defaultBlockState();
	}

	/** The giant that would have fallen asleep in this biome; a mossy one anywhere else. */
	public static Variety of(final Holder<Biome> biome) {
		for (Variety variety : values()) {
			for (ResourceKey<Biome> key : variety.biomes) {
				if (biome.is(key)) return variety;
			}
		}
		return MOSSY;
	}

	/** Blocks by weight. */
	private static final class Mix {
		private final List<Block> blocks = new ArrayList<>();
		private final List<Integer> weights = new ArrayList<>();
		private int total;

		Mix with(final Block block, final int weight) {
			this.blocks.add(block);
			this.weights.add(weight);
			this.total += weight;
			return this;
		}

		Block pick(final RandomSource random) {
			int roll = random.nextInt(this.total);
			for (int i = 0; i < this.blocks.size(); i++) {
				roll -= this.weights.get(i);
				if (roll < 0) return this.blocks.get(i);
			}
			return this.blocks.getLast();
		}
	}
}
