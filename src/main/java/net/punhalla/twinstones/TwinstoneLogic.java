package net.punhalla.twinstones;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

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
            pruneMissing(server, links, newKey);
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
        links.add(key, self);
        pruneMissing(server, links, key);
        refreshLinked(server, links, key);

        GlobalPos partner = links.at(key).stream().filter(p -> !p.equals(self)).findFirst().orElse(null);
        ServerLevel destination = partner == null ? null : server.getLevel(partner.dimension());
        if (destination == null) {
            player.displayClientMessage(Component.translatable("message.twinstones.no_partner", stone.getTagName()), true);
            return;
        }
        BlockPos arrival = findArrival(destination, partner.pos());
        level.playSound(null, pos, SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 0.8F, 1.0F);
        player.teleportTo(destination, arrival.getX() + 0.5, arrival.getY(), arrival.getZ() + 0.5,
                player.getYRot(), player.getXRot());
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

    /** Drops list entries whose stone is gone or renamed (e.g. removed while its mod dimension was absent). */
    private static void pruneMissing(MinecraftServer server, TwinstoneLinks links, String key) {
        for (GlobalPos entry : links.at(key)) {
            ServerLevel level = server.getLevel(entry.dimension());
            // A dimension that is not loaded right now proves nothing; keep the entry.
            if (level == null) {
                continue;
            }
            boolean present = level.getBlockEntity(entry.pos()) instanceof TwinstoneBlockEntity stone
                    && TwinstoneLinks.key(stone.getTagName()).equals(key);
            if (!present) {
                links.remove(key, entry);
            }
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

    /** A standable spot beside the stone, or the top of the stone when it is boxed in. */
    private static BlockPos findArrival(ServerLevel level, BlockPos stone) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos feet = stone.relative(direction);
            if (isOpen(level, feet) && isOpen(level, feet.above())
                    && level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP)) {
                return feet;
            }
        }
        return stone.above();
    }

    private static boolean isOpen(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getFluidState(pos).isEmpty();
    }
}
