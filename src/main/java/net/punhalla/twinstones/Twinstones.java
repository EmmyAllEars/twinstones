package net.punhalla.twinstones;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(Twinstones.MODID)
public class Twinstones {
    public static final String MODID = "twinstones";

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);

    public static final DeferredBlock<TwinstoneBlock> TWINSTONE = BLOCKS.register("twinstone",
            () -> new TwinstoneBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(2.0F, 1200.0F)
                    .lightLevel(state -> state.getValue(TwinstoneBlock.LINKED) ? 10 : 0)
                    .pushReaction(PushReaction.BLOCK)
                    .noOcclusion()));
    public static final DeferredItem<BlockItem> TWINSTONE_ITEM = ITEMS.registerSimpleBlockItem("twinstone", TWINSTONE);
    public static final Supplier<BlockEntityType<TwinstoneBlockEntity>> TWINSTONE_BE = BLOCK_ENTITIES.register("twinstone",
            () -> BlockEntityType.Builder.of(TwinstoneBlockEntity::new, TWINSTONE.get()).build(null));

    public Twinstones(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener(Twinstones::addCreative);
        modBus.addListener(Twinstones::registerPayloads);
        NeoForge.EVENT_BUS.addListener(Twinstones::onRightClickBlock);
    }

    private static void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(TWINSTONE_ITEM);
        }
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(SetTagPayload.TYPE, SetTagPayload.STREAM_CODEC, SetTagPayload::handle);
    }

    // Vanilla skips block interaction while sneaking with an item in hand; the stone's
    // sneak + right-click (rename) has to work whatever the player is holding.
    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity().isSecondaryUseActive()
                && event.getLevel().getBlockState(event.getPos()).is(TWINSTONE.get())) {
            event.setUseBlock(TriState.TRUE);
            event.setUseItem(TriState.FALSE);
        }
    }
}
