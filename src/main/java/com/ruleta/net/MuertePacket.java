package com.ruleta.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Servidor -> clientes: "muestra la animacion de la calavera para estos jugadores". */
public class MuertePacket {

    private final List<String> nombres;

    public MuertePacket(List<String> nombres) {
        this.nombres = nombres;
    }

    public static void encode(MuertePacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.nombres.size());
        for (String s : p.nombres) buf.writeUtf(s, 64);
    }

    public static MuertePacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<String> nombres = new ArrayList<>();
        for (int i = 0; i < n; i++) nombres.add(buf.readUtf(64));
        return new MuertePacket(nombres);
    }

    public static void handle(MuertePacket p, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.ruleta.client.ClientHooks.mostrarMuerte(p.nombres)));
        c.setPacketHandled(true);
    }
}
