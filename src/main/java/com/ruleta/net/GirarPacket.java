package com.ruleta.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;

/**
 * Servidor -> clientes: "gira la ruleta con estos colores y que caiga en este sector".
 * Solo se manda el texto del reto ganador, asi nadie puede ver los demas.
 */
public class GirarPacket {

    private final int[] colores;
    private final int indice;
    private final String texto;

    public GirarPacket(int[] colores, int indice, String texto) {
        this.colores = colores;
        this.indice = indice;
        this.texto = texto;
    }

    public static void encode(GirarPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.colores.length);
        for (int c : p.colores) buf.writeInt(c);
        buf.writeVarInt(p.indice);
        buf.writeUtf(p.texto, 256);
    }

    public static GirarPacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        int[] colores = new int[n];
        for (int i = 0; i < n; i++) colores[i] = buf.readInt();
        int indice = buf.readVarInt();
        String texto = buf.readUtf(256);
        return new GirarPacket(colores, indice, texto);
    }

    /** Solo se ejecuta en el cliente (el paquete va del servidor hacia los clientes). */
    public static void handle(GirarPacket p, CustomPayloadEvent.Context ctx) {
        com.ruleta.client.ClientHooks.abrirRuleta(p.colores, p.indice, p.texto);
    }
}
