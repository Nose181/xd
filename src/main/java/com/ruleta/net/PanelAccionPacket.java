package com.ruleta.net;

import com.ruleta.RuletaServer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;

/** Cliente -> servidor: lo que se pulso en el panel de control (el servidor comprueba que sea operador). */
public class PanelAccionPacket {

    public static final int GUARDAR = 0;
    public static final int LANZAR = 1;
    public static final int SOLO_AVISO = 2;
    public static final int DETENER = 3;

    public final int accion;
    public final int indice;
    public final String titulo;
    public final String mensaje;
    public final int segundos;
    public final boolean cumplido;
    public final boolean aviso;

    public PanelAccionPacket(int accion, int indice, String titulo, String mensaje,
                             int segundos, boolean cumplido, boolean aviso) {
        this.accion = accion;
        this.indice = indice;
        this.titulo = titulo;
        this.mensaje = mensaje;
        this.segundos = segundos;
        this.cumplido = cumplido;
        this.aviso = aviso;
    }

    public static void encode(PanelAccionPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.accion);
        buf.writeVarInt(p.indice);
        buf.writeUtf(p.titulo, 120);
        buf.writeUtf(p.mensaje, 1100);
        buf.writeVarInt(p.segundos);
        buf.writeBoolean(p.cumplido);
        buf.writeBoolean(p.aviso);
    }

    public static PanelAccionPacket decode(FriendlyByteBuf buf) {
        int accion = buf.readVarInt();
        int indice = buf.readVarInt();
        String titulo = buf.readUtf(120);
        String mensaje = buf.readUtf(1100);
        int segundos = buf.readVarInt();
        boolean cumplido = buf.readBoolean();
        boolean aviso = buf.readBoolean();
        return new PanelAccionPacket(accion, indice, titulo, mensaje, segundos, cumplido, aviso);
    }

    /** Se ejecuta en el servidor. */
    public static void handle(PanelAccionPacket p, CustomPayloadEvent.Context ctx) {
        ServerPlayer jugador = ctx.getSender();
        if (jugador != null) RuletaServer.panelAccion(jugador, p);
    }
}
