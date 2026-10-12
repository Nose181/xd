package com.ruleta.client;

import net.minecraft.client.Minecraft;

import java.util.List;

/** Solo se carga en el cliente: abre la pantalla de la ruleta cuando el servidor lo pide. */
public class ClientHooks {

    public static void abrirRuleta(List<String> retos, int indice) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || retos.isEmpty()) return;
        int i = Math.max(0, Math.min(indice, retos.size() - 1));
        mc.setScreen(new RuletaScreen(retos, i));
    }
}
