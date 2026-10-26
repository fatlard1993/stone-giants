package justfatlard.stone_giants;

import java.util.ArrayList;
import java.util.List;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.stone_giants.Outcrop.Stone;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A giant of living stone that sleeps as an outcrop of rock, and wakes when somebody starts mining it.
 *
 * <p>Asleep it is its {@link Outcrop}: real blocks in the world, which it remembers, and itself
 * hidden inside them, lying down so it is too small to hit or to stand in anybody's way. Awake it
 * stands up out of them, comes for whoever woke it, and when it has had nobody to fight for a
 * while and nobody is watching, lies down again wherever it is and is stone once more, healed.
 *
 * <p>One class for every {@link Variety}, each its own entity type: which one it is comes from its
 * type, and with it its size, its rock, its outcrop and how it fights - with its fists, by stamping,
 * by shoving, or by throwing rock or ice from further off.
 *
 * <p>It runs on {@link AbstractGolem}, the iron golem's parent, and adds no synced data, which is
 * what lets a vanilla client be shown an iron golem in its place. A Pandorical client draws it
 * from its own body.
 */
public class StoneGiant extends AbstractGolem implements Enemy {
	/** How long it takes to stand up out of the rock, in ticks; it neither moves nor strikes until it has. */
	public static final int RISING_TICKS = 40;
	/** How long it goes without anybody to fight before it lies down again. */
	public static final int SETTLE_AFTER = 1200;
	/** How long to wait before trying again where there was no room to lie down. */
	private static final int SETTLE_RETRY = 200;
	/** It never turns back to stone with a player this near: nobody sees the rock grow. */
	public static final double UNSEEN_RADIUS = 16.0;
	/** How often a sleeping one makes sure its stones are all still there. */
	private static final int STONE_CHECK_INTERVAL = 20;

	/** A sleeping giant breathes about once in this many ticks, while a player is near enough to notice. */
	private static final int BREATH_CHANCE = 300;
	private static final double BREATH_HEARD = 10.0;

	/** At most this many of its keepsake stone when it dies. */
	private static final int KEEPSAKES = 4;

	/** From this far, and no nearer, a thrower throws; nearer, it walks up and hits you. */
	public static final double THROW_FROM = 6.0;
	public static final double THROW_TO = 24.0;
	private static final int THROW_COOLDOWN = 60;
	/** Ticks between the arm going back and the rock leaving it, so there is time to see it coming. */
	public static final int THROW_WINDUP = 12;
	/** What a thrown chunk does, against a blow from the fist. */
	private static final float THROWN_SHARE = 0.6F;
	private static final float THROW_SPEED = 1.5F;

	/** How near it must be to stamp, and how far the ground shakes. */
	public static final double STOMP_FROM = 5.0;
	public static final double STOMP_REACH = 4.5;
	private static final int STOMP_COOLDOWN = 80;
	public static final int STOMP_WINDUP = 14;
	private static final float STOMP_SHARE = 0.7F;

	/** How far a shore giant's nip sends you, against an ordinary blow. */
	private static final double SHOVE = 1.6;

	private final List<Stone> body = new ArrayList<>();
	private boolean asleep;
	private @Nullable BlockState ore;
	private int rising;
	private int idle;
	/** Until its next throw or stamp. */
	private int cooldown;
	/** Counting down to the throw or stamp it has started, and the animation that goes with it. */
	private int windup;

	public StoneGiant(final EntityType<? extends StoneGiant> type, final Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes(final Variety variety) {
		return Mob.createMobAttributes()
			.add(Attributes.MAX_HEALTH, variety.health())
			.add(Attributes.ARMOR, 8.0)
			.add(Attributes.MOVEMENT_SPEED, variety.speed())
			.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
			.add(Attributes.ATTACK_DAMAGE, variety.damage())
			.add(Attributes.ATTACK_KNOCKBACK, 1.5)
			.add(Attributes.FOLLOW_RANGE, THROW_TO + 4.0)
			.add(Attributes.STEP_HEIGHT, 1.5)
			.add(Attributes.FALL_DAMAGE_MULTIPLIER, 0.0)
			.add(Attributes.SCALE, variety.scale());
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.1, true));
		this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.6));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		// On peaceful a woken giant only stands and looks at you, and then goes back to sleep.
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
			(target, level) -> level.getDifficulty() != Difficulty.PEACEFUL));
	}

	/** The spawn egg sets one down asleep, which is the only way anyone meets one; a command brings it awake. */
	@Override
	public @Nullable SpawnGroupData finalizeSpawn(final ServerLevelAccessor level, final DifficultyInstance difficulty,
			final EntitySpawnReason spawnReason, final @Nullable SpawnGroupData groupData) {
		if (spawnReason == EntitySpawnReason.SPAWN_ITEM_USE) this.asleep = true;
		return super.finalizeSpawn(level, difficulty, spawnReason, groupData);
	}

	public Variety variety() {
		Variety variety = StoneGiants.varietyOf(this.getType());
		return variety != null ? variety : Variety.MOSSY;
	}

	/** Whether it is partway into a throw or a stamp. */
	public boolean isWindingUp() {
		return this.windup > 0;
	}

	public boolean isAsleep() {
		return this.asleep;
	}

	public boolean isRising() {
		return this.rising > 0;
	}

	public List<Stone> body() {
		return List.copyOf(this.body);
	}

	public BlockState ore() {
		if (this.ore == null) this.ore = this.variety().ore(this.random);
		return this.ore;
	}

	/** Whether {@code pos} is one of this sleeping giant's stones, and still the stone it laid there. */
	public boolean holds(final BlockPos pos) {
		if (!this.asleep) return false;
		for (Stone stone : this.body) {
			if (stone.pos().equals(pos)) return this.level().getBlockState(pos).is(stone.state().getBlock());
		}
		return false;
	}

	/** The sleeping giant {@code pos} is a stone of, if any. */
	public static @Nullable StoneGiant sleepingIn(final ServerLevel level, final BlockPos pos) {
		List<StoneGiant> giants = level.getEntitiesOfClass(StoneGiant.class, new AABB(pos).inflate(Outcrop.REACH),
			giant -> giant.holds(pos));
		return giants.isEmpty() ? null : giants.getFirst();
	}

	/**
	 * Lies down here as an outcrop, facing the way it faces now. False where there is no room, in
	 * which case nothing has changed.
	 */
	public boolean settle(final ServerLevel level) {
		BlockPos feet = this.blockPosition();
		Direction facing = Direction.fromYRot(this.getYRot());
		if (!Outcrop.fits(this.variety().shape(), level, feet, facing)) return false;
		this.body.clear();
		this.body.addAll(Outcrop.lay(this.variety(), level, feet, facing, this.random, this.ore()));
		this.snapTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, facing.toYRot(), 0.0F);
		this.setYHeadRot(facing.toYRot());
		this.setYBodyRot(facing.toYRot());
		this.setHealth(this.getMaxHealth());
		this.setTarget(null);
		this.asleep = true;
		this.idle = 0;
		this.lieStill();
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.STONE_PLACE, SoundSource.HOSTILE, 1.5F, 0.5F);
		return true;
	}

	/** Takes up the stones it was laid in, as {@link Outcrop#lay} would have them. For worldgen, where the stones are already down. */
	public void sleepIn(final BlockPos feet, final Direction facing, final List<Stone> stones, final BlockState ore) {
		this.body.clear();
		this.body.addAll(stones);
		this.ore = ore;
		this.snapTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, facing.toYRot(), 0.0F);
		this.setYHeadRot(facing.toYRot());
		this.setYBodyRot(facing.toYRot());
		this.asleep = true;
		this.lieStill();
	}

	/**
	 * Stands up out of its stones and, unless it was woken by nobody in particular, comes for whoever
	 * woke it. Its stones crumble as it rises; whatever was built against it stays.
	 */
	public void wake(final @Nullable ServerPlayer by) {
		if (!this.asleep || !(this.level() instanceof ServerLevel level)) return;
		this.asleep = false;
		for (Stone stone : this.body) {
			if (level.getBlockState(stone.pos()).is(stone.state().getBlock())) level.destroyBlock(stone.pos(), false);
		}
		this.body.clear();
		this.standUp();
		this.rising = RISING_TICKS;
		this.idle = 0;
		this.windup = 0;
		PandoricalApi.animations().play(this, this.variety().animation("rise"), false);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.0F, 0.45F);
		if (by != null) {
			Awards.wokeAGiant(by);
			if (!by.isCreative() && level.getDifficulty() != Difficulty.PEACEFUL) this.setTarget(by);
		}
	}

	// Hidden in its stones, lying down: small enough to sit inside the block at its feet, so nothing
	// can hit it, push it or stand in it, and it never moves or makes a sound of its own.
	private void lieStill() {
		this.setPose(Pose.SLEEPING);
		this.setInvisible(true);
		this.setNoAi(true);
		this.setNoGravity(true);
		this.setSilent(true);
		this.setDeltaMovement(Vec3.ZERO);
		this.refreshDimensions();
	}

	private void standUp() {
		this.setPose(Pose.STANDING);
		this.setInvisible(false);
		this.setNoAi(false);
		this.setNoGravity(false);
		this.setSilent(false);
		this.refreshDimensions();
	}

	@Override
	public void tick() {
		super.tick();
		if (!this.asleep || !(this.level() instanceof ServerLevel level)) return;
		// Set down asleep by the spawn egg or a command: it lies down where it is, or, with no room, stays up.
		if (this.body.isEmpty()) {
			if (!this.settle(level)) {
				this.asleep = false;
				this.standUp();
			}
			return;
		}
		if (this.tickCount % STONE_CHECK_INTERVAL == 0 && this.disturbed()) {
			this.wake(null);
			return;
		}
		if (this.random.nextInt(BREATH_CHANCE) == 0
				&& level.getNearestPlayer(this, BREATH_HEARD) != null) this.breathe(level);
	}

	/** Any of its stones gone or changed: blown up, pulled by a piston, taken by an enderman. */
	private boolean disturbed() {
		for (Stone stone : this.body) {
			if (!this.level().getBlockState(stone.pos()).is(stone.state().getBlock())) return true;
		}
		return false;
	}

	// The tell. A trickle of grit off its back and a low grind, now and then, for anybody close
	// enough to wonder why a rock would do that.
	private void breathe(final ServerLevel level) {
		Stone top = this.body.stream().max((a, b) -> Integer.compare(a.pos().getY(), b.pos().getY())).orElseThrow();
		Vec3 at = Vec3.atCenterOf(top.pos());
		level.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, this.variety().dust().defaultBlockState()),
			at.x, at.y - 0.4, at.z, 6, 0.9, 0.1, 0.9, 0.0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GRINDSTONE_USE, SoundSource.HOSTILE, 0.35F, 0.4F);
	}

	@Override
	protected void customServerAiStep(final ServerLevel level) {
		super.customServerAiStep(level);
		if (this.rising > 0) {
			this.getNavigation().stop();
			if (--this.rising == 0) PandoricalApi.animations().stop(this);
		}
		boolean standingUp = this.rising > 0;
		this.goalSelector.setControlFlag(Goal.Flag.MOVE, !standingUp);
		this.goalSelector.setControlFlag(Goal.Flag.JUMP, !standingUp);
		if (standingUp) return;

		this.fight(level);

		this.idle = this.getTarget() == null && this.onGround() ? this.idle + 1 : 0;
		if (this.idle >= SETTLE_AFTER && level.getNearestPlayer(this, UNSEEN_RADIUS) == null && !this.settle(level)) {
			this.idle = SETTLE_AFTER - SETTLE_RETRY;
		}
	}

	/**
	 * Its own way of fighting, on top of walking up and hitting: a thrower starts a throw at whatever
	 * it is after from far enough off, a stamper a stamp at whatever is close, and each lets go when
	 * its wind-up runs out, if what it was after is still there to be hit.
	 */
	private void fight(final ServerLevel level) {
		Variety.Attack attack = this.variety().attack();
		if (this.cooldown > 0) this.cooldown--;
		LivingEntity target = this.getTarget();
		if (this.windup > 0) {
			this.getNavigation().stop();
			if (target != null) this.getLookControl().setLookAt(target, 30.0F, 30.0F);
			if (--this.windup > 0) return;
			PandoricalApi.animations().stop(this);
			if (target == null || !target.isAlive()) return;
			if (attack.thrown() != null) this.release(level, target, attack.thrown());
			else if (attack == Variety.Attack.STOMPS) this.stomp(level);
			return;
		}
		if (target == null || this.cooldown > 0 || level.getDifficulty() == Difficulty.PEACEFUL) return;
		double distance = this.distanceTo(target);
		if (attack.thrown() != null && distance >= THROW_FROM && distance <= THROW_TO && this.hasLineOfSight(target)) {
			this.windup = THROW_WINDUP;
			this.cooldown = THROW_COOLDOWN + this.random.nextInt(THROW_COOLDOWN / 2);
			PandoricalApi.animations().play(this, this.variety().animation("throw"), false);
		} else if (attack == Variety.Attack.STOMPS && distance <= STOMP_FROM) {
			this.windup = STOMP_WINDUP;
			this.cooldown = STOMP_COOLDOWN;
			PandoricalApi.animations().play(this, this.variety().animation("stomp"), false);
			this.playSound(SoundEvents.RAVAGER_ROAR, 1.5F, 0.6F);
		}
	}

	/** A chunk of itself, overarm, at where the target is now. */
	private void release(final ServerLevel level, final LivingEntity target, final Block chunk) {
		Boulder rock = new Boulder(level, this, new ItemStack(chunk),
			(float) this.getAttributeValue(Attributes.ATTACK_DAMAGE) * THROWN_SHARE, chunk == Blocks.PACKED_ICE);
		rock.setPos(this.getX(), this.getY() + this.getBbHeight() * 0.85, this.getZ());
		double dx = target.getX() - rock.getX();
		double dz = target.getZ() - rock.getZ();
		double dy = target.getY(0.5) - rock.getY();
		// Lobbed: aimed above the target by enough to make up the fall over the distance.
		rock.shoot(dx, dy + Math.sqrt(dx * dx + dz * dz) * 0.15, dz, THROW_SPEED, 3.0F);
		level.addFreshEntity(rock);
		this.playSound(SoundEvents.WITCH_THROW, 1.5F, 0.4F);
	}

	/** The ground shakes: everything near but other giants is hurt and thrown up and away. */
	private void stomp(final ServerLevel level) {
		float damage = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE) * STOMP_SHARE;
		for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(STOMP_REACH),
				each -> each != this && !(each instanceof StoneGiant) && each.onGround()
					&& each.distanceToSqr(this) <= (STOMP_REACH + this.getBbWidth() / 2) * (STOMP_REACH + this.getBbWidth() / 2))) {
			if (near instanceof Player player && (player.isCreative() || player.isSpectator())) continue;
			if (!near.hurtServer(level, this.damageSources().mobAttack(this), damage)) continue;
			Vec3 away = near.position().subtract(this.position()).multiply(1, 0, 1).normalize().scale(0.7);
			near.setDeltaMovement(near.getDeltaMovement().add(away.x, 0.65, away.z));
			near.needsSync = true;
		}
		BlockState ground = level.getBlockState(this.blockPosition().below());
		if (!ground.isAir()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), this.getX(), this.getY() + 0.1, this.getZ(),
				80, STOMP_REACH * 0.6, 0.1, STOMP_REACH * 0.6, 0.2);
		}
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.2F, 0.5F);
	}

	/**
	 * A heavy fist: what it hits goes up as well as back. A shore giant's nip is lighter on the way
	 * up and a good deal further on the way back.
	 */
	@Override
	public boolean doHurtTarget(final ServerLevel level, final Entity target) {
		boolean hit = super.doHurtTarget(level, target);
		if (hit) {
			if (this.variety().attack() == Variety.Attack.SHOVES) {
				Vec3 away = target.position().subtract(this.position()).multiply(1, 0, 1).normalize().scale(SHOVE);
				target.setDeltaMovement(target.getDeltaMovement().add(away.x, 0.3, away.z));
			} else {
				target.setDeltaMovement(target.getDeltaMovement().add(0.0, 0.45, 0.0));
			}
			target.needsSync = true;
			this.playSound(SoundEvents.DEEPSLATE_BREAK, 1.2F, 0.5F);
		}
		return hit;
	}

	/** Sleeping, it is not there to hit, shoot or bump into: only its stones are. */
	@Override
	public boolean isPickable() {
		return !this.asleep && super.isPickable();
	}

	/** Nor anything to hunt: an iron golem needs no sight of an enemy, and would stand pounding the rock. */
	@Override
	public boolean canBeSeenAsEnemy() {
		return !this.asleep && super.canBeSeenAsEnemy();
	}

	@Override
	public boolean isAttackable() {
		return !this.asleep && super.isAttackable();
	}

	@Override
	public boolean isInvulnerableTo(final ServerLevel level, final DamageSource source) {
		return this.asleep && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || super.isInvulnerableTo(level, source);
	}

	@Override
	public boolean isPushable() {
		return !this.asleep && super.isPushable();
	}

	@Override
	public boolean canBeCollidedWith(final @Nullable Entity entity) {
		return !this.asleep && super.canBeCollidedWith(entity);
	}

	@Override
	public boolean canBreatheUnderwater() {
		return true;
	}

	/** It gives back the ore it wore, and a few of the stone it was most made of. */
	@Override
	protected void dropCustomDeathLoot(final ServerLevel level, final DamageSource source, final boolean killedByPlayer) {
		super.dropCustomDeathLoot(level, source, killedByPlayer);
		this.spawnAtLocation(level, new ItemStack(this.ore().getBlock()));
		this.spawnAtLocation(level, new ItemStack(this.variety().keepsake(), 1 + this.random.nextInt(KEEPSAKES)));
	}

	@Override
	public void die(final DamageSource source) {
		super.die(source);
		if (this.level() instanceof ServerLevel level) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, this.variety().keepsake().defaultBlockState()),
				this.getX(), this.getY() + this.getBbHeight() * 0.5, this.getZ(), 60,
				this.getBbWidth() * 0.4, this.getBbHeight() * 0.3, this.getBbWidth() * 0.4, 0.1);
		}
	}

	@Override
	protected SoundEvent getHurtSound(final DamageSource source) {
		return SoundEvents.STONE_HIT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.STONE_BREAK;
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return null;
	}

	@Override
	protected void playStepSound(final BlockPos pos, final BlockState state) {
		this.playSound(SoundEvents.STONE_STEP, 1.4F, 0.5F);
	}

	@Override
	public float getVoicePitch() {
		return super.getVoicePitch() * 0.6F;
	}

	@Override
	protected void addAdditionalSaveData(final ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("Asleep", this.asleep);
		output.store("Body", Stone.CODEC.listOf(), this.body);
		output.storeNullable("Ore", BlockState.CODEC, this.ore);
	}

	@Override
	protected void readAdditionalSaveData(final ValueInput input) {
		super.readAdditionalSaveData(input);
		this.body.clear();
		input.read("Body", Stone.CODEC.listOf()).ifPresent(this.body::addAll);
		this.ore = input.read("Ore", BlockState.CODEC).orElse(null);
		this.asleep = input.getBooleanOr("Asleep", false);
		// Pose and invisibility are not saved with an entity, so a sleeping one is laid down again.
		if (this.asleep) this.lieStill();
	}

	@Override
	protected Component getTypeName() {
		return Component.translatableWithFallback(this.getType().getDescriptionId(), this.variety().displayName());
	}
}
