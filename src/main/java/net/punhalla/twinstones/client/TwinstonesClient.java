package net.punhalla.twinstones.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.punhalla.twinstones.Twinstones;

@Mod(value = Twinstones.MODID, dist = Dist.CLIENT)
public class TwinstonesClient {
    public TwinstonesClient(IEventBus modBus) {
        modBus.addListener(TwinstonesClient::registerRenderers);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(Twinstones.TWINSTONE_BE.get(), context -> new TwinstoneRenderer());
    }
}
