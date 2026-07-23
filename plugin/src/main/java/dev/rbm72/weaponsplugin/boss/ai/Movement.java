package dev.rbm72.weaponsplugin.boss.ai;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

/** Velocity-driven boss movement — lunges, leaps, and target knock-ups. Never touches blocks. */
public final class Movement {

    private Movement() {
    }

    /** Horizontal direction from {@code self} to {@code toward}, or an arbitrary fallback if they share an X/Z (can't normalize a zero-length vector). */
    private static Vector horizontalDirection(LivingEntity self, Location toward) {
        Vector delta = toward.toVector().subtract(self.getLocation().toVector()).setY(0);
        return delta.lengthSquared() > 1.0E-6 ? delta.normalize() : new Vector(1, 0, 0);
    }

    /** Horizontal lunge toward {@code toward} at {@code speed} blocks/tick. */
    public static void dash(LivingEntity self, Location toward, double speed) {
        self.setVelocity(horizontalDirection(self, toward).multiply(speed));
    }

    /** Arcing jump toward {@code toward}: {@code up} vertical, {@code forward} horizontal. */
    public static void leap(LivingEntity self, Location toward, double up, double forward) {
        Vector direction = horizontalDirection(self, toward).multiply(forward);
        self.setVelocity(new Vector(direction.getX(), up, direction.getZ()));
    }

    /** Knock a target straight up (Storm/Void airborne kits). */
    public static void launchTarget(LivingEntity target, double up) {
        target.setVelocity(target.getVelocity().add(new Vector(0, up, 0)));
    }
}
