package net.punhalla.twinstones.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/** Client-only entry points, kept out of common classes so a dedicated server never loads screen code. */
public final class ClientHooks {
    private ClientHooks() {}

    public static void openNamingScreen(BlockPos pos, String currentName) {
        Minecraft.getInstance().setScreen(new TwinstoneScreen(pos, currentName));
    }
}
