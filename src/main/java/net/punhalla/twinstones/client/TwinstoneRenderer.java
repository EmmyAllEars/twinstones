package net.punhalla.twinstones.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.punhalla.twinstones.TwinstoneBlockEntity;

/** Floats the stone's name above it while the player is looking at it. */
public class TwinstoneRenderer implements BlockEntityRenderer<TwinstoneBlockEntity> {
    @Override
    public void render(TwinstoneBlockEntity stone, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
            int packedLight, int packedOverlay) {
        String name = stone.getTagName();
        if (name.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK
                || !hit.getBlockPos().equals(stone.getBlockPos())) {
            return;
        }
        Component text = Component.literal(name);
        Font font = minecraft.font;
        poseStack.pushPose();
        poseStack.translate(0.5, 1.35, 0.5);
        poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(0.025F, -0.025F, 0.025F);
        Matrix4f matrix = poseStack.last().pose();
        int background = (int) (minecraft.options.getBackgroundOpacity(0.25F) * 255.0F) << 24;
        font.drawInBatch(text, -font.width(text) / 2.0F, 0, 0xFFFFFFFF, false, matrix, buffers,
                Font.DisplayMode.NORMAL, background, LightTexture.FULL_BRIGHT);
        poseStack.popPose();
    }
}
