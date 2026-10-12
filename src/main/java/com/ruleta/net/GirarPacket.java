package com.ruleta.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Servidor -> clientes: "abre la ruleta con esta lista y que caiga en este sector". */
public class GirarPacket {

    private final List<String> retos;
    private final int indice;

    public GirarPacket(List<String> retos, int indice) {
        this.retos = retos;
        this.indice = indice;
    }

    public static void encode(GirarPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.retos.size());
        for (String s : p.retos) buf.writeUtf(s, 256);
        buf.writeVarInt(p.indice);
    }

    public static GirarPacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<String> retos = new ArrayList<>();
        for (int i = 0; i < n; i++) retos.add(buf.readUtf(256));
        return new GirarPacket(retos, buf.readVarInt());
    }

    public static void handle(GirarPacket p, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.ruleta.client.ClientHooks.abrirRuleta(p.retos, p.indice)));
        c.setPacketHandled(true);
    }
}
