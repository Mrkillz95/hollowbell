package net.jj.hollowbell.entity;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A boss bar with a fixed id for each Hollowbell. The game's own bars get a new random id every time, so if he was
 * unloaded and loaded again (while you were inside him, say) you ended up with his old bar stuck on screen next
 * to the new one. With the same id the new bar simply takes the old one's place.
 */
public final class BellBar extends BossEvent {
    private final Set<ServerPlayer> players = new HashSet<>();

    public BellBar(UUID id, Component name, BossBarColor color, BossBarOverlay overlay) { super(id, name, color, overlay); }

    public static UUID idFor(UUID bell, String which) {
        return UUID.nameUUIDFromBytes(("hollowbell:" + which + ":" + bell).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private void send(ClientboundBossEventPacket p) { for (ServerPlayer sp : players) sp.connection.send(p); }

    @Override public void setProgress(float f) { if (f != progress) { super.setProgress(f); send(ClientboundBossEventPacket.createUpdateProgressPacket(this)); } }
    @Override public void setName(Component c) { if (!c.equals(name)) { super.setName(c); send(ClientboundBossEventPacket.createUpdateNamePacket(this)); } }
    @Override public void setColor(BossBarColor c) { if (c != color) { super.setColor(c); send(ClientboundBossEventPacket.createUpdateStylePacket(this)); } }

    public void addPlayer(ServerPlayer p) { if (players.add(p)) p.connection.send(ClientboundBossEventPacket.createAddPacket(this)); }
    public void removePlayer(ServerPlayer p) { if (players.remove(p)) p.connection.send(ClientboundBossEventPacket.createRemovePacket(getId())); }
    public void removeAllPlayers() { for (ServerPlayer p : new ArrayList<>(players)) removePlayer(p); }
    public Collection<ServerPlayer> getPlayers() { return Collections.unmodifiableSet(players); }
}
