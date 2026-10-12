package com.ruleta.net;

import com.ruleta.RuletaMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

/** Canal de red: el cliente avisa al servidor del reto y el servidor lo anuncia a todos. */
public class Net {

    private static final String VERSION = "1";

    // Las dos condiciones "v -> true" permiten entrar a servidores que no tengan el mod.
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(RuletaMod.MODID, "main"),
            () -> VERSION,
            v -> true,
            v -> true);

    public static void registrar() {
        CHANNEL.registerMessage(0, AnunciarRetoPacket.class,
                AnunciarRetoPacket::encode,
                AnunciarRetoPacket::decode,
                AnunciarRetoPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }
}
