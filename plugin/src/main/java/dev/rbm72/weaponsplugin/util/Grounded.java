package dev.rbm72.weaponsplugin.util;

import org.bukkit.entity.Player;

/**
 * The one place {@code Player#isOnGround()} is called.
 * <p>
 * Paper deprecates that method because the value is reported by the client and a modified client can
 * lie about it. There is no server-side replacement in the API, and every alternative (sampling the
 * block under the feet, watching {@code getVelocity().getY()}) answers a subtly different question and
 * would change the feel of every ability built on it — the double jumps, the wall grabs, the slam
 * landings. Those all want "does the player believe they are standing on something", which is exactly
 * what the deprecated call returns.
 * <p>
 * So the call stays, and the deprecation is acknowledged once, here, instead of twisting fifteen
 * movement abilities around a warning. Nothing in this plugin grants an advantage a client could
 * meaningfully cheat by spoofing the flag: the worst case is an unearned mid-air jump.
 */
public final class Grounded {

    private Grounded() {
    }

    /** True when the player's client reports them standing on a surface. */
    @SuppressWarnings("deprecation")
    public static boolean onGround(Player player) {
        return player.isOnGround();
    }
}
