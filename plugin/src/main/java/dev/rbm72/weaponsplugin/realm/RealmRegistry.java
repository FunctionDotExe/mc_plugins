package dev.rbm72.weaponsplugin.realm;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class RealmRegistry {

    private final Map<String, Realm> realms = new LinkedHashMap<>();

    public void register(Realm realm) {
        realms.put(realm.id(), realm);
    }

    public Optional<Realm> get(String id) {
        return Optional.ofNullable(realms.get(id.toLowerCase(Locale.ROOT)));
    }

    public Optional<Realm> byBossId(String bossId) {
        return realms.values().stream().filter(realm -> realm.bossId().equals(bossId)).findFirst();
    }

    public Collection<Realm> all() {
        return realms.values();
    }
}
