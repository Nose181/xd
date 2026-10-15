package com.ruleta.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;

/** Servidor -> clientes: muestra (o quita) el cuadro de aviso de arriba a la izquierda. */
public class AvisoPacket {

    private final String titulo;
    private final String mensaje;
    private final int rgb;
    private final int segundos;
    private final boolean pideCumplido;
    private final boolean limpiar;

    public AvisoPacket(String titulo, String mensaje, int rgb, int segundos, boolean pideCumplido, boolean limpiar) {
        this.titulo = titulo;
        this.mensaje = mensaje;
        this.rgb = rgb;
        this.segundos = segundos;
        this.pideCumplido = pideCumplido;
        this.limpiar = limpiar;
    }

    public static AvisoPacket quitar() {
        return new AvisoPacket("", "", 0, 0, false, true);
    }

    public static void encode(AvisoPacket p, FriendlyByteBuf buf) {
        buf.writeUtf(p.titulo, 120);
        buf.writeUtf(p.mensaje, 1100);
        buf.writeInt(p.rgb);
        buf.writeVarInt(p.segundos);
        buf.writeBoolean(p.pideCumplido);
        buf.writeBoolean(p.limpiar);
    }

    public static AvisoPacket decode(FriendlyByteBuf buf) {
        String titulo = buf.readUtf(120);
        String mensaje = buf.readUtf(1100);
        int rgb = buf.readInt();
        int segundos = buf.readVarInt();
        boolean pide = buf.readBoolean();
        boolean limpiar = buf.readBoolean();
        return new AvisoPacket(titulo, mensaje, rgb, segundos, pide, limpiar);
    }

    /** Solo se ejecuta en el cliente. */
    public static void handle(AvisoPacket p, CustomPayloadEvent.Context ctx) {
        com.ruleta.client.ClientHooks.recibirAviso(p.titulo, p.mensaje, p.rgb, p.segundos, p.pideCumplido, p.limpiar);
    }
}
