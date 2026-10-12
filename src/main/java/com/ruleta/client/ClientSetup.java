package com.ruleta.client;

import com.ruleta.RuletaMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Registra la capa que dibuja la animacion de la calavera encima de todo. */
@Mod.EventBusSubscriber(modid = RuletaMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ClientSetup {

    @SubscribeEvent
    public static void capas(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("calavera", MuerteOverlay::render);
    }
}
