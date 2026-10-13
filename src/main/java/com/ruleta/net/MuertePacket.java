package com.ruleta.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;

import java.util.ArrayList;
import java.util.List;

/** Servidor -> clientes: "muestra la animacion de la calavera para estos jugadores". */
public class MuertePacket {

    private final List<String> nombres;
    private final boolean castigo;

    public MuertePacket(List<String> nombres, boolean castigo) {
        this.nombres = nombres;
        this.castigo = castigo;
    }

    public static void encode(MuertePacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.nombres.size());
        for (String s : p.nombres) buf.writeUtf(s, 64);
        buf.writeBoolean(p.castigo);
    }

    public static MuertePacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<String> nombres = new ArrayList<>();
        for (int i = 0; i < n; i++) nombres.add(buf.readUtf(64));
        return new MuertePacket(nombres, buf.readBoolean());
    }

    /** Solo se ejecuta en el cliente. */
    public static void handle(MuertePacket p, CustomPayloadEvent.Context ctx) {
        com.ruleta.client.ClientHooks.mostrarMuerte(p.nombres, p.castigo);
    }
}
