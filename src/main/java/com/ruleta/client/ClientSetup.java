package com.ruleta.client;

import com.ruleta.RuletaMod;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

public class ClientSetup {

    // El comando solo "pide" abrir la ruleta; se abre en el siguiente tick,
    // porque al ejecutar un comando el chat se cierra solo y taparia la ruleta.
    private static boolean abrirPendiente = false;

    @Mod.EventBusSubscriber(modid = RuletaMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ForgeBus {

        @SubscribeEvent
        public static void comandos(RegisterClientCommandsEvent event) {
            event.getDispatcher().register(
                    Commands.literal("ruleta").executes(ctx -> {
                        abrirPendiente = true;
                        return 1;
                    }));
        }

        @SubscribeEvent
        public static void tick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END || !abrirPendiente) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) {
                abrirPendiente = false;
                return;
            }
            if (mc.screen == null) {
                abrirPendiente = false;
                mc.setScreen(new RuletaScreen(true));
            }
        }
    }
}
