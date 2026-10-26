package justfatlard.stone_giants;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;

/**
 * A chunk of rock or ice a giant has torn off itself and thrown.
 *
 * <p>A snowball as far as anyone else knows: every client, vanilla or not, draws a snowball by the
 * item it carries, so this flies as a small block of whatever was thrown and bursts into its bits
 * where it lands, with nothing new to draw. What it does when it hits is this class's alone, and is
 * not saved: one left in flight when its chunk unloads comes back a harmless snowball.
 */
final class Boulder extends Snowball {
	/** How long ice keeps you slow, and how deep the frost it leaves on you goes. */
	private static final int CHILL_TICKS = 100;
	private static final int FROST_TICKS = 160;

	private final float damage;
	private final boolean freezes;

	Boulder(final Level level, final LivingEntity thrower, final ItemStack chunk, final float damage, final boolean freezes) {
		super(level, thrower, chunk);
		this.damage = damage;
		this.freezes = freezes;
	}

	@Override
	protected void onHitEntity(final EntityHitResult hit) {
		Entity target = hit.getEntity();
		if (!(this.level() instanceof ServerLevel level)) return;
		if (!target.hurtServer(level, this.damageSources().thrown(this, this.getOwner()), this.damage)) return;
		if (this.freezes && target instanceof LivingEntity living) {
			living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, CHILL_TICKS, 1), this.getOwner());
			living.setTicksFrozen(Math.max(living.getTicksFrozen(), FROST_TICKS));
		}
	}
}
