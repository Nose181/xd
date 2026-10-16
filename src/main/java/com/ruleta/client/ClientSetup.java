package com.ruleta.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.ruleta.RuletaMod;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** Dibuja el cuadro de aviso y la calavera encima del HUD, hace temblar la camara y maneja la tecla para ocultar. */
@Mod.EventBusSubscriber(modid = RuletaMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientSetup {

    /** Tecla para ocultar el cuadro de aviso (se puede cambiar en Controles). */
    public static final KeyMapping OCULTAR = new KeyMapping(
            "key.ruleta.ocultar", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_I, "key.categories.ruleta");

    @Mod.EventBusSubscriber(modid = RuletaMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModBus {
        @SubscribeEvent
        public static void teclas(RegisterKeyMappingsEvent event) {
            event.register(OCULTAR);
        }
    }

    @SubscribeEvent
    public static void alDibujarHud(CustomizeGuiOverlayEvent.Chat event) {
        Window ventana = event.getWindow();
        int w = ventana.getGuiScaledWidth();
        int h = ventana.getGuiScaledHeight();
        RuletaOverlay.render(event.getGuiGraphics(), w, h);
        AvisoOverlay.render(event.getGuiGraphics(), w, h);
        MuerteOverlay.render(event.getGuiGraphics(), w, h);
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

    @SubscribeEvent
    public static void alTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        while (OCULTAR.consumeClick()) {
            AvisoOverlay.ocultar();
            RuletaOverlay.saltar();
        }
    }
}
