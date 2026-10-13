package com.ruleta.client;

import com.mojang.blaze3d.platform.Window;
import com.ruleta.RuletaMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Dibuja la animacion de la calavera encima del HUD. */
@Mod.EventBusSubscriber(modid = RuletaMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientSetup {

    @SubscribeEvent
    public static void alDibujarHud(CustomizeGuiOverlayEvent.Chat event) {
        Window ventana = event.getWindow();
        MuerteOverlay.render(event.getGuiGraphics(), ventana.getGuiScaledWidth(), ventana.getGuiScaledHeight());
    }
}
