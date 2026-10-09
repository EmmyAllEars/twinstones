package net.punhalla.twinstones;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Server-side rules: naming, pairing, and the teleport itself. */
public final class TwinstoneLogic {
    public static final int MAX_NAME_LENGTH = 32;

    private TwinstoneLogic() {}

    public static void rename(ServerPlayer player, ServerLevel level, BlockPos pos, TwinstoneBlockEntity stone, String requested) {
        String name = requested.trim();
        if (name.length() > MAX_NAME_LENGTH) {
            name = name.substring(0, MAX_NAME_LENGTH);
        }
        MinecraftServer server = level.getServer();
        TwinstoneLinks links = TwinstoneLinks.get(server);
        GlobalPos self = GlobalPos.of(level.dimension(), pos);
        String oldKey = TwinstoneLinks.key(stone.getTagName());
        String newKey = TwinstoneLinks.key(name);

        if (newKey.equals(oldKey)) {
            stone.setTagName(name);
            return;
        }
        if (!newKey.isEmpty()) {
            reconcile(server, links, newKey);
            if (links.at(newKey).size() >= TwinstoneLinks.MAX_PER_NAME) {
                player.displayClientMessage(Component.translatable("message.twinstones.already_paired", name), true);
                return;
            }
        }
        if (!oldKey.isEmpty()) {
            links.remove(oldKey, self);
            refreshLinked(server, links, oldKey);
        }
        stone.setTagName(name);
        if (newKey.isEmpty()) {
            setLinked(level, pos, false);
            return;
        }
        links.add(newKey, self);
        refreshLinked(server, links, newKey);
        boolean paired = links.at(newKey).size() == TwinstoneLinks.MAX_PER_NAME;
        player.displayClientMessage(Component.translatable(
                paired ? "message.twinstones.paired" : "message.twinstones.named", name), true);
    }

    public static void teleport(ServerPlayer player, ServerLevel level, BlockPos pos, TwinstoneBlockEntity stone) {
        String key = TwinstoneLinks.key(stone.getTagName());
        if (key.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.twinstones.unnamed"), true);
            return;
        }
        MinecraftServer server = level.getServer();
        TwinstoneLinks links = TwinstoneLinks.get(server);
        GlobalPos self = GlobalPos.of(level.dimension(), pos);
        // A named stone can exist without a list entry (copied by /clone or a structure, or its
        // dimension was absent for a while). It may join only if the name still has room.
        if (!register(server, links, key, self)) {
            player.displayClientMessage(Component.translatable("message.twinstones.already_paired", stone.getTagName()), true);
            return;
        }
        refreshLinked(server, links, key);

        GlobalPos partner = links.at(key).stream().filter(p -> !p.equals(self)).findFirst().orElse(null);
        ServerLevel destination = partner == null ? null : server.getLevel(partner.dimension());
        if (destination == null) {
            player.displayClientMessage(Component.translatable("message.twinstones.no_partner", stone.getTagName()), true);
            return;
        }
        Vec3 arrival = findArrival(destination, partner.pos());
        if (arrival == null) {
            player.displayClientMessage(Component.translatable("message.twinstones.blocked", stone.getTagName()), true);
            return;
        }
        level.playSound(null, pos, SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 0.8F, 1.0F);
        player.teleportTo(destination, arrival.x, arrival.y, arrival.z, player.getYRot(), player.getXRot());
        // teleportTo keeps momentum and the fall accumulator; arriving must not carry a fall over.
        player.resetFallDistance();
        player.setDeltaMovement(Vec3.ZERO);
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
        destination.playSound(null, partner.pos(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 0.8F, 1.0F);
    }

    public static void onStoneRemoved(ServerLevel level, BlockPos pos, TwinstoneBlockEntity stone) {
        String key = TwinstoneLinks.key(stone.getTagName());
        if (key.isEmpty()) {
            return;
        }
        MinecraftServer server = level.getServer();
        TwinstoneLinks links = TwinstoneLinks.get(server);
        links.remove(key, GlobalPos.of(level.dimension(), pos));
        refreshLinked(server, links, key);
    }

    /** The one way a stone joins a name's list. Returns false when the name is full without it. */
    private static boolean register(MinecraftServer server, TwinstoneLinks links, String key, GlobalPos self) {
        reconcile(server, links, key);
        List<GlobalPos> entries = links.at(key);
        if (entries.contains(self)) {
            return true;
        }
        if (entries.size() >= TwinstoneLinks.MAX_PER_NAME) {
            return false;
        }
        links.add(key, self);
        return true;
    }

    /**
     * Brings a name's list back in line with the world: drops entries whose stone is gone or
     * renamed, drops entries in dimensions this server does not have, and trims an oversized
     * list from an older save back to a pair. A stone in a dimension that returns later
     * re-registers the next time it is used.
     */
    private static void reconcile(MinecraftServer server, TwinstoneLinks links, String key) {
        for (GlobalPos entry : links.at(key)) {
            ServerLevel level = server.getLevel(entry.dimension());
            boolean present = level != null
                    && level.getBlockEntity(entry.pos()) instanceof TwinstoneBlockEntity stone
                    && TwinstoneLinks.key(stone.getTagName()).equals(key);
            if (!present) {
                links.remove(key, entry);
            }
        }
        List<GlobalPos> entries = links.at(key);
        for (int i = TwinstoneLinks.MAX_PER_NAME; i < entries.size(); i++) {
            links.remove(key, entries.get(i));
        }
    }

    private static void refreshLinked(MinecraftServer server, TwinstoneLinks links, String key) {
        List<GlobalPos> entries = links.at(key);
        boolean linked = entries.size() == TwinstoneLinks.MAX_PER_NAME;
        for (GlobalPos entry : entries) {
            ServerLevel level = server.getLevel(entry.dimension());
            if (level != null) {
                setLinked(level, entry.pos(), linked);
            }
        }
    }

    private static void setLinked(ServerLevel level, BlockPos pos, boolean linked) {
        BlockState state = level.getBlockState(pos);
        if (state.is(Twinstones.TWINSTONE.get()) && state.getValue(TwinstoneBlock.LINKED) != linked) {
            level.setBlock(pos, state.setValue(TwinstoneBlock.LINKED, linked), Block.UPDATE_ALL);
        }
    }

    /**
     * A safe place to stand next to the stone, else on top of it, else null. Uses the same test
     * vanilla applies when a player leaves a bed or respawns: a floor to stand on, room for the
     * player's whole body, nothing burning or harmful, and inside the world border.
     */
    @Nullable
    static Vec3 findArrival(ServerLevel level, BlockPos stone) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            Vec3 spot = safeSpot(level, stone.relative(direction));
            if (spot != null) {
                return spot;
            }
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            Vec3 spot = safeSpot(level, stone.relative(direction).relative(direction.getClockWise()));
            if (spot != null) {
                return spot;
            }
        }
        return safeSpot(level, stone.above());
    }

    @Nullable
    private static Vec3 safeSpot(ServerLevel level, BlockPos feet) {
        if (!level.isInWorldBounds(feet) || !level.isInWorldBounds(feet.above())) {
            return null;
        }
        return DismountHelper.findSafeDismountLocation(EntityType.PLAYER, level, feet, true);
    }
}
