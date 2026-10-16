package com.ruleta.client;

import com.ruleta.Reto;
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
        RuletaOverlay.iniciar(colores, i, texto);
    }

    public static void mostrarMuerte(List<String> nombres, boolean castigo) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (MuerteOverlay.iniciar(nombres, castigo)) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WITHER_DEATH, 0.9f, 0.6f));
        }
    }

    public static void recibirAviso(String titulo, String mensaje, int rgb, int segundos,
                                    boolean pideCumplido, boolean limpiar) {
        if (limpiar) {
            AvisoOverlay.limpiar();
        } else {
            AvisoOverlay.mostrar(titulo, mensaje, rgb, segundos, pideCumplido);
        }
    }

    public static void abrirPanel(List<Reto> retos, int seleccionado) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || retos.isEmpty()) return;
        if (mc.screen instanceof PanelScreen panel) {
            panel.actualizar(retos, seleccionado);
        } else {
            mc.setScreen(new PanelScreen(retos, seleccionado));
        }
    }
}
