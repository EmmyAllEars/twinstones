package net.punhalla.twinstones;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class TwinstoneBlockEntity extends BlockEntity {
    private String tagName = "";

    public TwinstoneBlockEntity(BlockPos pos, BlockState state) {
        super(Twinstones.TWINSTONE_BE.get(), pos, state);
    }

    public String getTagName() {
        return tagName;
    }

    public void setTagName(String name) {
        tagName = name;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), UPDATE_ALL);
        }
    }

    private static final int UPDATE_ALL = 3;

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("TagName", tagName);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        tagName = tag.getString("TagName");
    }

    // The name is synced so the client can show it above the stone and prefill the naming screen.
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
