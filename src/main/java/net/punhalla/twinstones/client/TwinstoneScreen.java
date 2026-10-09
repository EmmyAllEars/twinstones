package net.punhalla.twinstones.client;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import net.punhalla.twinstones.SetTagPayload;
import net.punhalla.twinstones.TwinstoneLogic;

public class TwinstoneScreen extends Screen {
    private final BlockPos pos;
    private final String initialName;
    private EditBox nameBox;

    public TwinstoneScreen(BlockPos pos, String initialName) {
        super(Component.translatable("screen.twinstones.name"));
        this.pos = pos;
        this.initialName = initialName;
    }

    @Override
    protected void init() {
        nameBox = new EditBox(font, width / 2 - 100, height / 2 - 10, 200, 20, title);
        nameBox.setMaxLength(TwinstoneLogic.MAX_NAME_LENGTH);
        nameBox.setValue(initialName);
        addRenderableWidget(nameBox);
        setInitialFocus(nameBox);
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> commit())
                .bounds(width / 2 - 100, height / 2 + 20, 200, 20).build());
    }

    private void commit() {
        PacketDistributor.sendToServer(new SetTagPayload(pos, nameBox.getValue()));
        onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            commit();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 30, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
