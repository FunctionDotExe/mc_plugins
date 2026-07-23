package dev.rbm72.weaponsplugin.boss.ai;

import dev.rbm72.weaponsplugin.boss.BossAttack;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Weighted-random attack choice: never immediately repeats the last attack
 * (unless the pool only has one), never picks something still on cooldown.
 * If everything is on cooldown, returns {@code null} so the caller waits
 * rather than spamming or forcing a repeat.
 */
public final class AttackSelector {

    private AttackSelector() {
    }

    public static BossAttack select(List<BossAttack> pool, BossAttack lastUsed,
                                     Map<BossAttack, Long> lastUsedAtMs, double cooldownScale) {
        long now = System.currentTimeMillis();

        List<BossAttack> candidates = pool.stream()
                .filter(attack -> pool.size() == 1 || attack != lastUsed)
                .filter(attack -> isReady(attack, lastUsedAtMs, cooldownScale, now))
                .toList();

        if (candidates.isEmpty()) {
            // Nothing fresh and un-repeated — relax the no-repeat rule before giving up entirely.
            candidates = pool.stream()
                    .filter(attack -> isReady(attack, lastUsedAtMs, cooldownScale, now))
                    .toList();
        }

        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    private static boolean isReady(BossAttack attack, Map<BossAttack, Long> lastUsedAtMs, double cooldownScale, long now) {
        Long lastUsed = lastUsedAtMs.get(attack);
        if (lastUsed == null) {
            return true;
        }
        long cooldownMs = Math.round(attack.cooldownSeconds() * cooldownScale * 1000);
        return now - lastUsed >= cooldownMs;
    }
}
