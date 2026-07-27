package dev.rbm72.weaponsplugin.boss.meter;

/**
 * One yes/no question a meter asks about a player every pulse — "are you near her", "are you standing
 * on ice", "are you in the campfire's light", "did you keep moving".
 * <p>
 * A single-method interface so the common cases are lambdas and the recurring ones are constants in
 * {@link MeterConditions}. Called on the main thread once per attached meter per player per pulse, so
 * it must be cheap and must never schedule, damage, or modify anything: a condition that has side
 * effects would fire a different number of times depending on how many meters happen to be attached.
 */
@FunctionalInterface
public interface MeterCondition {

    boolean test(MeterContext ctx);

    default MeterCondition and(MeterCondition other) {
        return ctx -> test(ctx) && other.test(ctx);
    }

    default MeterCondition or(MeterCondition other) {
        return ctx -> test(ctx) || other.test(ctx);
    }

    default MeterCondition negate() {
        return ctx -> !test(ctx);
    }
}
