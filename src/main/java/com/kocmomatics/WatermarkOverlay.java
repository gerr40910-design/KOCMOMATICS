package com.kocmomatics;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(
    modid = Kocmomatics.MOD_ID,
    bus = Mod.EventBusSubscriber.Bus.FORGE,
    value = Dist.CLIENT
)
public class WatermarkOverlay {

    @SubscribeEvent
    public static void renderWatermark(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null) {
            return;
        }

        event.getGuiGraphics().drawString(
            mc.font,
            Component.literal("KOCMOMATICS"),
            8,
            8,
            0xA020F0,
            false
        );
    }
}
