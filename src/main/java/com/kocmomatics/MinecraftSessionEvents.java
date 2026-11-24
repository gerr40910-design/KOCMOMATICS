package com.kocmomatics;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

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

    private static final HttpClient CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2))
        .build();

    private static void sendEvent(String event) {
        try {
            String json = "{\"event\":\"" + event + "\"}";

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:8765/minecraft-event"))
                .timeout(Duration.ofSeconds(3))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

            CLIENT.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .thenAccept(response ->
                    Kocmomatics.LOGGER.info(
                        "[KOCMOMATICS] Agent ответил: HTTP {}",
                        response.statusCode()
                    )
                )
                .exceptionally(error -> {
                    Kocmomatics.LOGGER.warn(
                        "[KOCMOMATICS] Agent недоступен."
                    );
                    return null;
                });

        } catch (Exception error) {
            Kocmomatics.LOGGER.warn(
                "[KOCMOMATICS] Не удалось отправить событие."
            );
        }
    }

    @SubscribeEvent
    public static void onPlayerJoin(
        ClientPlayerNetworkEvent.LoggingIn event
    ) {
        Kocmomatics.LOGGER.info(
            "[KOCMOMATICS] Игрок вошёл в мир Minecraft."
        );
        sendEvent("join");
    }

    @SubscribeEvent
    public static void onPlayerLeave(
        ClientPlayerNetworkEvent.LoggingOut event
    ) {
        Kocmomatics.LOGGER.info(
            "[KOCMOMATICS] Игрок покинул мир Minecraft."
        );
        sendEvent("leave");
    }
}
