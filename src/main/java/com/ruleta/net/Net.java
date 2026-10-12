package com.ruleta.net;

import com.ruleta.RuletaMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public class Net {

    private static final String VERSION = "1";

    // Las dos condiciones "v -> true" permiten entrar a servidores/clientes sin el mod.
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(RuletaMod.MODID, "main"),
            () -> VERSION,
            v -> true,
            v -> true);

    public static void registrar() {
        CHANNEL.registerMessage(0, GirarPacket.class,
                GirarPacket::encode, GirarPacket::decode, GirarPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1, MuertePacket.class,
                MuertePacket::encode, MuertePacket::decode, MuertePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
}
