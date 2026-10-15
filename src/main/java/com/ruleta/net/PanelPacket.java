package com.ruleta.net;

import com.ruleta.Reto;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;

import java.util.ArrayList;
import java.util.List;

/** Servidor -> un jugador administrador: abre (o actualiza) el panel de control con la lista de retos. */
public class PanelPacket {

    private final List<Reto> retos;
    private final int seleccionado;

    public PanelPacket(List<Reto> retos, int seleccionado) {
        this.retos = retos;
        this.seleccionado = seleccionado;
    }

    public static void encode(PanelPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.retos.size());
        for (Reto r : p.retos) {
            buf.writeUtf(r.color() == null ? "" : r.color(), 16);
            buf.writeVarInt(r.segundos());
            buf.writeBoolean(r.cumplido());
            buf.writeUtf(r.titulo() == null ? "" : r.titulo(), 120);
            buf.writeUtf(r.texto(), 1100);
        }
        buf.writeVarInt(p.seleccionado);
    }

    public static PanelPacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<Reto> retos = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String color = buf.readUtf(16);
            int segundos = buf.readVarInt();
            boolean cumplido = buf.readBoolean();
            String titulo = buf.readUtf(120);
            String texto = buf.readUtf(1100);
            retos.add(new Reto(color, segundos, cumplido, titulo, texto));
        }
        return new PanelPacket(retos, buf.readVarInt());
    }

    /** Solo se ejecuta en el cliente. */
    public static void handle(PanelPacket p, CustomPayloadEvent.Context ctx) {
        com.ruleta.client.ClientHooks.abrirPanel(p.retos, p.seleccionado);
    }
}
