package com.ruleta.client;

import com.mojang.blaze3d.platform.Window;
import com.ruleta.RuletaMod;
import net.minecraft.Util;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Dibuja la animacion de la calavera encima del HUD y hace temblar la camara. */
@Mod.EventBusSubscriber(modid = RuletaMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientSetup {

    @SubscribeEvent
    public static void alDibujarHud(CustomizeGuiOverlayEvent.Chat event) {
        Window ventana = event.getWindow();
        MuerteOverlay.render(event.getGuiGraphics(), ventana.getGuiScaledWidth(), ventana.getGuiScaledHeight());
    }

    @SubscribeEvent
    public static void alCalcularCamara(ViewportEvent.ComputeCameraAngles event) {
        float i = MuerteOverlay.intensidadTemblor();
        if (i <= 0f) return;
        float t = Util.getMillis() / 1000f;
        event.setRoll(event.getRoll() + (float) Math.sin(t * 58f) * 3.2f * i);
        event.setPitch(event.getPitch() + (float) Math.cos(t * 49f) * 1.4f * i);
        event.setYaw(event.getYaw() + (float) Math.sin(t * 43f) * 1.4f * i);
    }
}
