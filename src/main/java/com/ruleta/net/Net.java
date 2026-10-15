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

        CHANNEL.messageBuilder(AvisoPacket.class, 2)
                .direction(PacketFlow.CLIENTBOUND)
                .encoder(AvisoPacket::encode)
                .decoder(AvisoPacket::decode)
                .consumerMainThread(AvisoPacket::handle)
                .add();

        CHANNEL.messageBuilder(PanelPacket.class, 3)
                .direction(PacketFlow.CLIENTBOUND)
                .encoder(PanelPacket::encode)
                .decoder(PanelPacket::decode)
                .consumerMainThread(PanelPacket::handle)
                .add();

        CHANNEL.messageBuilder(PanelAccionPacket.class, 4)
                .direction(PacketFlow.SERVERBOUND)
                .encoder(PanelAccionPacket::encode)
                .decoder(PanelAccionPacket::decode)
                .consumerMainThread(PanelAccionPacket::handle)
                .add();
    }
}
