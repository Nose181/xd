package com.ruleta.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import java.util.List;

/** Solo se carga en el cliente: reacciona a lo que manda el servidor. */
public class ClientHooks {

    public static void abrirRuleta(int[] colores, int indice, String texto) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || colores.length < 2) return;
        int i = Math.max(0, Math.min(indice, colores.length - 1));
        mc.setScreen(new RuletaScreen(colores, i, texto));
    }

    public static void mostrarMuerte(List<String> nombres) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        MuerteOverlay.iniciar(nombres);
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WITHER_DEATH, 0.9f, 0.6f));
    }
}
