package com.ruleta.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.ruleta.RuletaMod;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

public class ClientSetup {

    public static final KeyMapping ABRIR = new KeyMapping(
            "key.ruleta.abrir",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            "key.categories.ruleta");

    @Mod.EventBusSubscriber(modid = RuletaMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModBus {
        @SubscribeEvent
        public static void registrarTeclas(RegisterKeyMappingsEvent event) {
            event.register(ABRIR);
        }
    }

    @Mod.EventBusSubscriber(modid = RuletaMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ForgeBus {
        @SubscribeEvent
        public static void tick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            while (ABRIR.consumeClick()) {
                if (mc.player != null && mc.screen == null) {
                    mc.setScreen(new RuletaScreen());
                }
            }
        }
    }
}
