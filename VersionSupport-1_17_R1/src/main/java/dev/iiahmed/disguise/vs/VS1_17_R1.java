package dev.iiahmed.disguise.vs;

import dev.iiahmed.disguise.DisguiseProvider;
import dev.iiahmed.disguise.DisguiseUtil;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_17_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

@SuppressWarnings("all")
public final class VS1_17_R1 extends DisguiseProvider {

    private final Field id;

    {
        try {
            id = ClientboundAddEntityPacket.class.getDeclaredField("c");
            id.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void refreshAsPlayer(@NotNull final Player player) {
        if (!player.isOnline()) {
            return;
        }
        final Location location = player.getLocation();
        final long seed = player.getWorld().getSeed();
        final ServerPlayer ep = ((CraftPlayer) player).getHandle();
        ep.connection.send(new ClientboundPlayerInfoPacket(
                ClientboundPlayerInfoPacket.Action.REMOVE_PLAYER,
                ep));
        ep.connection.send(new ClientboundRespawnPacket(ep.getLevel().dimensionType(),
                ep.getLevel().dimension(),
                seed, ep.gameMode.getGameModeForPlayer(),
                ep.gameMode.getGameModeForPlayer(), false, false, true));
        player.teleport(location);
        ep.connection.send(new ClientboundPlayerInfoPacket(
                ClientboundPlayerInfoPacket.Action.ADD_PLAYER,
                ep));
        player.updateInventory();
        for (final Player serverPlayer : Bukkit.getOnlinePlayers()) {
            if (serverPlayer == player) continue;
            serverPlayer.hidePlayer(plugin, player);
            serverPlayer.showPlayer(plugin, player);
        }
    }

    @Override
    public void refreshAsEntity(@NotNull final Player refreshed, final boolean remove, final Player... targets) {
        if (!isDisguised(refreshed) || targets.length == 0 || !getInfo(refreshed).hasEntity()) {
            return;
        }
        final ServerPlayer rfep = ((CraftPlayer) refreshed).getHandle();
        final org.bukkit.entity.EntityType type = getInfo(refreshed).getEntityType();
        final ClientboundAddEntityPacket spawn;
        try {
            final Constructor<?> constructor = DisguiseUtil.getEntity(type);
            final Entity entity;
            if (constructor.getParameterCount() == 1) {
                entity = (Entity) DisguiseUtil.getEntity(type).newInstance(rfep.getLevel());
            } else {
                entity = (Entity) DisguiseUtil.getEntity(type)
                        .newInstance(net.minecraft.world.entity.EntityType.class.getDeclaredField(type.name()).get(null), rfep.getLevel());
            }
            spawn = new ClientboundAddEntityPacket(entity);
            id.set(spawn, refreshed.getEntityId());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        final ClientboundRemoveEntitiesPacket destroy = new ClientboundRemoveEntitiesPacket(refreshed.getEntityId());
        final ClientboundTeleportEntityPacket tp = new ClientboundTeleportEntityPacket(rfep);
        final ClientboundUpdateAttributesPacket attributes = new ClientboundUpdateAttributesPacket(refreshed.getEntityId(), rfep.getAttributes().getDirtyAttributes());
        for (final Player player : targets) {
            if (player == refreshed) continue;
            final ServerPlayer ep = ((CraftPlayer) player).getHandle();
            if (remove) {
                ep.connection.send(destroy);
            }
            ep.connection.send(spawn);
            ep.connection.send(tp);
            ep.connection.send(attributes);
        }
    }

}