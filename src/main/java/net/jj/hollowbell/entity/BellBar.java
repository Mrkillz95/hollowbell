package net.jj.hollowbell.entity;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

/** A boss bar with a fixed id per Hollowbell, so a bar made again after a reload replaces the old one on screen. */
public final class BellBar extends BossEvent {
    private final Set<ServerPlayer> players = new HashSet<>();

    public BellBar(UUID id, Component name, BossBarColor color, BossBarOverlay overlay) {
        super(id, name, color, overlay);
    }

    public static UUID idFor(UUID bell, String which) {
        return UUID.nameUUIDFromBytes(("hollowbell:" + which + ":" + bell).getBytes(StandardCharsets.UTF_8));
    }

    private void send(ClientboundBossEventPacket p) {
        for (ServerPlayer sp : players) sp.connection.send(p);
    }

    @Override
    public void setProgress(float f) {
        if (f != progress) {
            super.setProgress(f);
            send(ClientboundBossEventPacket.createUpdateProgressPacket(this));
        }
    }

    @Override
    public void setName(Component c) {
        if (!c.equals(name)) {
            super.setName(c);
            send(ClientboundBossEventPacket.createUpdateNamePacket(this));
        }
    }

    @Override
    public void setColor(BossBarColor c) {
        if (c != color) {
            super.setColor(c);
            send(ClientboundBossEventPacket.createUpdateStylePacket(this));
        }
    }

    public void addPlayer(ServerPlayer p) {
        if (players.add(p)) p.connection.send(ClientboundBossEventPacket.createAddPacket(this));
    }

    public void removePlayer(ServerPlayer p) {
        if (players.remove(p)) p.connection.send(ClientboundBossEventPacket.createRemovePacket(getId()));
    }

    public void removeAllPlayers() {
        for (ServerPlayer p : new ArrayList<>(players)) removePlayer(p);
    }

    public Collection<ServerPlayer> getPlayers() {
        return Collections.unmodifiableSet(players);
    }
}
