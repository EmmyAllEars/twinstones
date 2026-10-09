package net.punhalla.twinstones;

import java.lang.reflect.Method;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("twinstones")
@PrefixGameTestTemplate(false)
public class ReviewGameTests {
    private static BlockPos place(GameTestHelper h, int x) {
        BlockPos p = h.absolutePos(new BlockPos(x, 2, 2));
        h.getLevel().setBlockAndUpdate(p, Twinstones.TWINSTONE.get().defaultBlockState());
        return p;
    }
    private static TwinstoneBlockEntity stone(ServerLevel l, BlockPos p) {
        return (TwinstoneBlockEntity) l.getBlockEntity(p);
    }
    private static ServerPlayer player(ServerLevel l) {
        return FakePlayerFactory.get(l, new GameProfile(UUID.randomUUID(), "ReviewTest"));
    }
    private static void rename(GameTestHelper h, ServerPlayer p, BlockPos pos, String name) {
        TwinstoneLogic.rename(p, h.getLevel(), pos, stone(h.getLevel(), pos), name);
    }
    private static BlockPos arrival(ServerLevel l, BlockPos p) throws Exception {
        Method m = TwinstoneLogic.class.getDeclaredMethod("findArrival", ServerLevel.class, BlockPos.class);
        m.setAccessible(true);
        return (BlockPos) m.invoke(null, l, p);
    }
    @GameTest(template="review_empty")
    public static void pairing_rename_remove_and_persistence(GameTestHelper h) throws Exception {
        ServerLevel l = h.getLevel(); ServerPlayer p = player(l);
        BlockPos a = place(h, 2), b = place(h, 4), c = place(h, 6);
        rename(h,p,a,"Review Pair"); rename(h,p,b," review PAIR ");
        h.assertTrue(l.getBlockState(a).getValue(TwinstoneBlock.LINKED), "first must link");
        h.assertTrue(stone(l,a).getTagName().equals("Review Pair"), "state flip must preserve BE name");
        rename(h,p,c,"Review Pair");
        h.assertTrue(stone(l,c).getTagName().isEmpty(), "third rename must be refused");
        TwinstoneLinks links = TwinstoneLinks.get(l.getServer());
        CompoundTag data = links.save(new CompoundTag(), l.registryAccess());
        Method load = TwinstoneLinks.class.getDeclaredMethod("load", CompoundTag.class, net.minecraft.core.HolderLookup.Provider.class);
        load.setAccessible(true);
        TwinstoneLinks restored = (TwinstoneLinks) load.invoke(null,data,l.registryAccess());
        h.assertTrue(restored.at("review pair").size()==2, "SavedData round trip");
        rename(h,p,b,"Review Other");
        h.assertTrue(!l.getBlockState(a).getValue(TwinstoneBlock.LINKED), "rename must unlink survivor");
        rename(h,p,c,"Review Pair");
        l.destroyBlock(c,false);
        h.assertTrue(links.at("review pair").size()==1, "break must free name");
        h.assertTrue(!l.getBlockState(a).getValue(TwinstoneBlock.LINKED), "break must unlink survivor");
        h.succeed();
    }
    @GameTest(template="review_empty")
    public static void reproduces_obstructed_fallback(GameTestHelper h) throws Exception {
        ServerLevel l=h.getLevel(); BlockPos a=place(h,2);
        for(Direction d:Direction.Plane.HORIZONTAL) l.setBlockAndUpdate(a.relative(d),Blocks.STONE.defaultBlockState());
        l.setBlockAndUpdate(a.above(),Blocks.STONE.defaultBlockState());
        h.assertTrue(arrival(l,a).equals(a.above()), "unsafe fallback should reproduce inside solid block");
        h.succeed();
    }
    @GameTest(template="review_empty")
    public static void reproduces_magma_arrival(GameTestHelper h) throws Exception {
        ServerLevel l=h.getLevel(); BlockPos a=place(h,2);
        for(Direction d:Direction.Plane.HORIZONTAL) {
            l.setBlockAndUpdate(a.relative(d),Blocks.AIR.defaultBlockState());
            l.setBlockAndUpdate(a.relative(d).above(),Blocks.AIR.defaultBlockState());
            l.setBlockAndUpdate(a.relative(d).below(),Blocks.MAGMA_BLOCK.defaultBlockState());
        }
        h.assertTrue(l.getBlockState(arrival(l,a).below()).is(Blocks.MAGMA_BLOCK), "hazardous supporting block should reproduce");
        h.succeed();
    }
    @GameTest(template="review_empty")
    public static void reproduces_third_imported_stone(GameTestHelper h) {
        ServerLevel l=h.getLevel(); ServerPlayer p=player(l);
        BlockPos a=place(h,2),b=place(h,4),c=place(h,6);
        rename(h,p,a,"Review Clone"); rename(h,p,b,"Review Clone");
        stone(l,c).setTagName("Review Clone"); // same NBT outcome as /clone or a structure copy
        TwinstoneLogic.teleport(p,l,c,stone(l,c));
        h.assertTrue(TwinstoneLinks.get(l.getServer()).at("review clone").size()==3, "teleport bypasses pairing cap");
        h.assertTrue(!l.getBlockState(a).getValue(TwinstoneBlock.LINKED), "third entry extinguishes original pair");
        h.succeed();
    }
    @GameTest(template="review_empty")
    public static void reproduces_absent_dimension_reservation(GameTestHelper h) {
        ServerLevel l=h.getLevel(); ServerPlayer p=player(l); BlockPos a=place(h,2),c=place(h,6);
        rename(h,p,a,"Review Absent");
        TwinstoneLinks links=TwinstoneLinks.get(l.getServer());
        links.add("review absent",GlobalPos.of(ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse("review:removed")),BlockPos.ZERO));
        rename(h,p,c,"Review Absent");
        h.assertTrue(stone(l,c).getTagName().isEmpty(), "missing dimension keeps name reserved");
        h.succeed();
    }
    @GameTest(template="review_empty")
    public static void reproduces_fall_distance_retained(GameTestHelper h) {
        ServerLevel l=h.getLevel(); ServerPlayer p=h.makeMockServerPlayerInLevel(); BlockPos a=place(h,2),b=place(h,4);
        rename(h,p,a,"Review Fall"); rename(h,p,b,"Review Fall");
        p.fallDistance=30.0F;
        TwinstoneLogic.teleport(p,l,a,stone(l,a));
        h.assertTrue(p.fallDistance==30.0F, "teleport does not clear fall damage accumulator");
        h.succeed();
    }
    @GameTest(template="review_empty")
    public static void reproduces_spectator_packet_rename(GameTestHelper h) {
        ServerLevel l=h.getLevel(); BlockPos a=place(h,2);
        ServerPlayer p=new net.neoforged.neoforge.common.util.FakePlayer(l,new GameProfile(UUID.randomUUID(),"ReviewSpectator")) {
            @Override public boolean isSpectator() { return true; }
        };
        p.absMoveTo(a.getX()+0.5,a.getY(),a.getZ()+0.5,0,0);
        var ctx=(net.neoforged.neoforge.network.handling.IPayloadContext)java.lang.reflect.Proxy.newProxyInstance(
            ReviewGameTests.class.getClassLoader(),new Class<?>[]{net.neoforged.neoforge.network.handling.IPayloadContext.class},
            (proxy,method,args)-> { if(method.getName().equals("player")) return p; throw new UnsupportedOperationException(method.getName()); });
        SetTagPayload.handle(new SetTagPayload(a,"Review Spectator"),ctx);
        h.assertTrue(stone(l,a).getTagName().equals("Review Spectator"),"spectator custom payload mutates stone");
        h.succeed();
    }
    @GameTest(template="review_empty", timeoutTicks=200)
    public static void cross_dimension_round_trip(GameTestHelper h) {
        ServerLevel l=h.getLevel(), dest=l.getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        ServerPlayer p=h.makeMockServerPlayerInLevel(); BlockPos a=place(h,2),b=new BlockPos(16,64,16);
        dest.setBlockAndUpdate(b,Twinstones.TWINSTONE.get().defaultBlockState());
        for(Direction d:Direction.Plane.HORIZONTAL) {
            dest.setBlockAndUpdate(b.relative(d).below(),Blocks.STONE.defaultBlockState());
            dest.setBlockAndUpdate(b.relative(d),Blocks.AIR.defaultBlockState());
            dest.setBlockAndUpdate(b.relative(d).above(),Blocks.AIR.defaultBlockState());
        }
        rename(h,p,a,"Review Dimension");
        TwinstoneLogic.rename(p,dest,b,stone(dest,b),"Review Dimension");
        TwinstoneLogic.teleport(p,l,a,stone(l,a));
        h.assertTrue(p.serverLevel()==dest,"player must reach Nether");
        h.assertTrue(p.blockPosition().getY()==64 && p.blockPosition().distManhattan(b)==1,"player must arrive beside destination");
        TwinstoneLogic.teleport(p,dest,b,stone(dest,b));
        h.assertTrue(p.serverLevel()==l,"player must return to overworld");
        h.succeed();
    }
}
