package net.punhalla.twinstones;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Regression tests. The setup helpers and the cases come from Codex's 2026-10-09 review
 * harness (docs/reviews/), with its bug reproductions turned into assertions of the fix.
 */
@GameTestHolder("twinstones")
@PrefixGameTestTemplate(false)
public class TwinstoneGameTests {
    private static BlockPos place(GameTestHelper h, int x) {
        BlockPos pos = h.absolutePos(new BlockPos(x, 2, 2));
        h.getLevel().setBlockAndUpdate(pos, Twinstones.TWINSTONE.get().defaultBlockState());
        return pos;
    }

    private static TwinstoneBlockEntity stone(ServerLevel level, BlockPos pos) {
        return (TwinstoneBlockEntity) level.getBlockEntity(pos);
    }

    private static ServerPlayer fakePlayer(ServerLevel level) {
        return FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "TwinstoneTest"));
    }

    private static void rename(GameTestHelper h, ServerPlayer player, BlockPos pos, String name) {
        TwinstoneLogic.rename(player, h.getLevel(), pos, stone(h.getLevel(), pos), name);
    }

    private static boolean linked(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getValue(TwinstoneBlock.LINKED);
    }

    private static void sendRename(ServerPlayer player, BlockPos pos, String name) {
        IPayloadContext context = (IPayloadContext) Proxy.newProxyInstance(
                TwinstoneGameTests.class.getClassLoader(), new Class<?>[] {IPayloadContext.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("player")) {
                        return player;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        SetTagPayload.handle(new SetTagPayload(pos, name), context);
    }

    @GameTest(template = "empty")
    public static void pairing_rename_remove_and_persistence(GameTestHelper h) throws Exception {
        ServerLevel level = h.getLevel();
        ServerPlayer player = fakePlayer(level);
        BlockPos a = place(h, 2), b = place(h, 4), c = place(h, 6);
        rename(h, player, a, "Test Pair");
        rename(h, player, b, " test PAIR ");
        h.assertTrue(linked(level, a) && linked(level, b), "matching names must link both stones");
        h.assertTrue(stone(level, a).getTagName().equals("Test Pair"), "linking must keep the stone's name");
        rename(h, player, c, "Test Pair");
        h.assertTrue(stone(level, c).getTagName().isEmpty(), "a third stone must be refused");

        TwinstoneLinks links = TwinstoneLinks.get(level.getServer());
        CompoundTag data = links.save(new CompoundTag(), level.registryAccess());
        Method load = TwinstoneLinks.class.getDeclaredMethod("load", CompoundTag.class, HolderLookup.Provider.class);
        load.setAccessible(true);
        TwinstoneLinks restored = (TwinstoneLinks) load.invoke(null, data, level.registryAccess());
        h.assertTrue(restored.at("test pair").size() == 2, "the list must survive save and load");

        rename(h, player, b, "Test Other");
        h.assertTrue(!linked(level, a), "renaming one stone must unlink the other");
        rename(h, player, c, "Test Pair");
        h.assertTrue(linked(level, a) && linked(level, c), "a freed name must pair again");
        level.destroyBlock(c, false);
        h.assertTrue(links.at("test pair").size() == 1, "breaking a stone must free its name");
        h.assertTrue(!linked(level, a), "breaking one stone must unlink the other");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void boxed_in_stone_refuses_travel(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        BlockPos a = place(h, 2), b = place(h, 5);
        rename(h, player, a, "Test Boxed");
        rename(h, player, b, "Test Boxed");
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 || dz != 0) {
                    level.setBlockAndUpdate(b.offset(dx, 0, dz), Blocks.STONE.defaultBlockState());
                    level.setBlockAndUpdate(b.offset(dx, 1, dz), Blocks.STONE.defaultBlockState());
                }
            }
        }
        level.setBlockAndUpdate(b.above(), Blocks.STONE.defaultBlockState());
        h.assertTrue(TwinstoneLogic.findArrival(level, b) == null, "a boxed-in stone must have no arrival spot");
        Vec3 before = player.position();
        TwinstoneLogic.teleport(player, level, a, stone(level, a));
        h.assertTrue(player.position().equals(before), "travel to a boxed-in stone must be refused");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void hazardous_floor_is_not_chosen(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos a = place(h, 3);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 || dz != 0) {
                    level.setBlockAndUpdate(a.offset(dx, 0, dz), Blocks.AIR.defaultBlockState());
                    level.setBlockAndUpdate(a.offset(dx, 1, dz), Blocks.AIR.defaultBlockState());
                    level.setBlockAndUpdate(a.offset(dx, -1, dz), Blocks.MAGMA_BLOCK.defaultBlockState());
                }
            }
        }
        Vec3 arrival = TwinstoneLogic.findArrival(level, a);
        h.assertTrue(arrival != null, "the top of the stone is still a safe spot");
        h.assertTrue(BlockPos.containing(arrival).equals(a.above()), "arrival must be on the stone, not on magma");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void copied_third_stone_cannot_join_a_pair(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        BlockPos a = place(h, 2), b = place(h, 4), c = place(h, 6);
        rename(h, player, a, "Test Clone");
        rename(h, player, b, "Test Clone");
        stone(level, c).setTagName("Test Clone"); // what /clone or a structure copy leaves behind
        Vec3 before = player.position();
        TwinstoneLogic.teleport(player, level, c, stone(level, c));
        h.assertTrue(TwinstoneLinks.get(level.getServer()).at("test clone").size() == 2, "the pair must stay a pair");
        h.assertTrue(linked(level, a) && linked(level, b), "the original pair must stay linked");
        h.assertTrue(player.position().equals(before), "the extra stone must not teleport");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void missing_dimension_does_not_reserve_a_name(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer player = fakePlayer(level);
        BlockPos a = place(h, 2), c = place(h, 6);
        rename(h, player, a, "Test Absent");
        TwinstoneLinks links = TwinstoneLinks.get(level.getServer());
        links.add("test absent", GlobalPos.of(
                ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("twinstones_test:removed")), BlockPos.ZERO));
        rename(h, player, c, "Test Absent");
        h.assertTrue(stone(level, c).getTagName().equals("Test Absent"), "a replacement stone must be accepted");
        h.assertTrue(linked(level, a) && linked(level, c), "the replacement must pair with the survivor");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void travel_resets_fall_distance(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        BlockPos a = place(h, 2), b = place(h, 5);
        rename(h, player, a, "Test Fall");
        rename(h, player, b, "Test Fall");
        player.fallDistance = 30.0F;
        TwinstoneLogic.teleport(player, level, a, stone(level, a));
        h.assertTrue(player.blockPosition().distManhattan(b) <= 2, "the player must arrive at the twin");
        h.assertTrue(player.fallDistance == 0.0F, "travel must clear the fall accumulator");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void naming_packet_respects_interaction_rules(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos a = place(h, 2);
        ServerPlayer spectator = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "TwinstoneSpectator")) {
            @Override
            public boolean isSpectator() {
                return true;
            }
        };
        spectator.absMoveTo(a.getX() + 0.5, a.getY(), a.getZ() + 0.5, 0, 0);
        sendRename(spectator, a, "Test Spectator");
        h.assertTrue(stone(level, a).getTagName().isEmpty(), "a spectator's naming packet must be ignored");

        ServerPlayer far = fakePlayer(level);
        far.absMoveTo(a.getX() + 40.5, a.getY(), a.getZ() + 0.5, 0, 0);
        sendRename(far, a, "Test Far");
        h.assertTrue(stone(level, a).getTagName().isEmpty(), "an out-of-reach naming packet must be ignored");

        ServerPlayer near = fakePlayer(level);
        near.absMoveTo(a.getX() + 1.5, a.getY(), a.getZ() + 0.5, 0, 0);
        sendRename(near, a, "Test Near");
        h.assertTrue(stone(level, a).getTagName().equals("Test Near"), "a player in reach must be able to name");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void cross_dimension_round_trip(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerLevel nether = level.getServer().getLevel(Level.NETHER);
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        BlockPos a = place(h, 2), b = new BlockPos(16, 64, 16);
        nether.setBlockAndUpdate(b, Twinstones.TWINSTONE.get().defaultBlockState());
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            nether.setBlockAndUpdate(b.relative(direction).below(), Blocks.STONE.defaultBlockState());
            nether.setBlockAndUpdate(b.relative(direction), Blocks.AIR.defaultBlockState());
            nether.setBlockAndUpdate(b.relative(direction).above(), Blocks.AIR.defaultBlockState());
        }
        rename(h, player, a, "Test Dimension");
        TwinstoneLogic.rename(player, nether, b, stone(nether, b), "Test Dimension");
        TwinstoneLogic.teleport(player, level, a, stone(level, a));
        h.assertTrue(player.serverLevel() == nether, "the player must reach the Nether");
        h.assertTrue(player.blockPosition().getY() == 64 && player.blockPosition().distManhattan(b) == 1,
                "the player must arrive beside the Nether stone");
        TwinstoneLogic.teleport(player, nether, b, stone(nether, b));
        h.assertTrue(player.serverLevel() == level, "the player must return to the overworld");
        h.succeed();
    }
}
