package net.punhalla.twinstones;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.punhalla.twinstones.client.ClientHooks;

public class TwinstoneBlock extends Block implements EntityBlock {
    public static final BooleanProperty LINKED = BooleanProperty.create("linked");
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(2, 0, 2, 14, 3, 14),
            Block.box(4, 3, 5, 12, 16, 11));

    public TwinstoneBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LINKED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LINKED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TwinstoneBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof TwinstoneBlockEntity stone)) {
            return InteractionResult.PASS;
        }
        if (player.isSecondaryUseActive()) {
            if (level.isClientSide) {
                ClientHooks.openNamingScreen(pos, stone.getTagName());
            }
        } else if (player instanceof ServerPlayer serverPlayer) {
            TwinstoneLogic.teleport(serverPlayer, (ServerLevel) level, pos, stone);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        // LINKED flips are a state change on the same block, not a removal.
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof TwinstoneBlockEntity stone) {
            TwinstoneLogic.onStoneRemoved(serverLevel, pos, stone);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(LINKED) && random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.REVERSE_PORTAL,
                    pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.6,
                    pos.getY() + 0.4 + random.nextDouble() * 0.7,
                    pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.6,
                    0, 0.02, 0);
        }
    }
}
