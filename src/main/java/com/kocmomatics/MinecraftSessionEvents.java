package com.kocmomatics;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(
    modid = Kocmomatics.MOD_ID,
    bus = Mod.EventBusSubscriber.Bus.FORGE,
    value = Dist.CLIENT
)
public class MinecraftSessionEvents {

    @SubscribeEvent
    public static void onPlayerJoin(ClientPlayerNetworkEvent.LoggingIn event) {
        Kocmomatics.LOGGER.info(
            "[KOCMOMATICS] Игрок вошёл в мир Minecraft."
        );
    }

    @SubscribeEvent
    public static void onPlayerLeave(ClientPlayerNetworkEvent.LoggingOut event) {
        Kocmomatics.LOGGER.info(
            "[KOCMOMATICS] Игрок покинул мир Minecraft."
        );
    }
}
