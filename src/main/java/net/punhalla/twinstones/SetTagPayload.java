package net.punhalla.twinstones;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: the name typed into a stone's naming screen. */
public record SetTagPayload(BlockPos pos, String name) implements CustomPacketPayload {
    public static final Type<SetTagPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Twinstones.MODID, "set_tag"));
    public static final StreamCodec<FriendlyByteBuf, SetTagPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SetTagPayload::pos,
            ByteBufCodecs.stringUtf8(TwinstoneLogic.MAX_NAME_LENGTH * 2), SetTagPayload::name,
            SetTagPayload::new);

    // The naming screen stays open while the player moves, so allow some slack beyond normal reach.
    private static final double REACH_SLACK = 4.0;

    @Override
    public Type<SetTagPayload> type() {
        return TYPE;
    }

    public static void handle(SetTagPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        BlockPos pos = payload.pos();
        // A packet is not a right-click: apply the limits vanilla puts on a real block interaction.
        if (player.isSpectator() || !level.isLoaded(pos) || !player.canInteractWithBlock(pos, REACH_SLACK)
                || !level.mayInteract(player, pos) || !level.getWorldBorder().isWithinBounds(pos)) {
            return;
        }
        if (level.getBlockEntity(pos) instanceof TwinstoneBlockEntity stone) {
            TwinstoneLogic.rename(player, level, pos, stone, payload.name());
        }
    }
}
