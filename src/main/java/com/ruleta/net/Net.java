package com.ruleta.net;

import com.ruleta.RuletaMod;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.SimpleChannel;

public class Net {

    // optional(): se puede entrar a servidores (o recibir jugadores) que no tengan el mod.
    public static final SimpleChannel CHANNEL = ChannelBuilder
            .named(ResourceLocation.fromNamespaceAndPath(RuletaMod.MODID, "main"))
            .networkProtocolVersion(1)
            .optional()
            .simpleChannel();

    public static void registrar() {
        CHANNEL.messageBuilder(GirarPacket.class, 0)
                .direction(PacketFlow.CLIENTBOUND)
                .encoder(GirarPacket::encode)
                .decoder(GirarPacket::decode)
                .consumerMainThread(GirarPacket::handle)
                .add();

        CHANNEL.messageBuilder(MuertePacket.class, 1)
                .direction(PacketFlow.CLIENTBOUND)
                .encoder(MuertePacket::encode)
                .decoder(MuertePacket::decode)
                .consumerMainThread(MuertePacket::handle)
                .add();
    }
}
