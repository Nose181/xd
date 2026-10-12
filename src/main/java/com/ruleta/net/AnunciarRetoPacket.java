package com.ruleta.net;

import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** Cliente -> servidor: "me salio este reto". El servidor lo muestra a todos los jugadores. */
public class AnunciarRetoPacket {

    private static final Map<UUID, Long> ULTIMO_USO = new HashMap<>();

    private final String reto;

    public AnunciarRetoPacket(String reto) {
        this.reto = reto;
    }

    public static void encode(AnunciarRetoPacket p, FriendlyByteBuf buf) {
        buf.writeUtf(p.reto, 256);
    }

    public static AnunciarRetoPacket decode(FriendlyByteBuf buf) {
        return new AnunciarRetoPacket(buf.readUtf(256));
    }

    public static void handle(AnunciarRetoPacket p, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> {
            ServerPlayer emisor = c.getSender();
            if (emisor == null) return;
            MinecraftServer server = emisor.getServer();
            if (server == null) return;

            // Evita spam: maximo un anuncio cada 3 segundos por jugador.
            long ahora = System.currentTimeMillis();
            Long antes = ULTIMO_USO.get(emisor.getUUID());
            if (antes != null && ahora - antes < 3000) return;
            ULTIMO_USO.put(emisor.getUUID(), ahora);

            String texto = p.reto.replace('\n', ' ').trim();
            if (texto.length() > 200) texto = texto.substring(0, 200);
            if (texto.isEmpty()) return;

            Component chat = Component.literal("§6[Ruleta] §e" + emisor.getName().getString()
                    + " §fgiró la ruleta: §a" + texto);
            server.getPlayerList().broadcastSystemMessage(chat, false);

            for (ServerPlayer jugador : server.getPlayerList().getPlayers()) {
                jugador.connection.send(new ClientboundSetTitlesAnimationPacket(10, 80, 20));
                jugador.connection.send(new ClientboundSetSubtitleTextPacket(
                        Component.literal(texto).withStyle(ChatFormatting.WHITE)));
                jugador.connection.send(new ClientboundSetTitleTextPacket(
                        Component.literal("¡RETO PARA TODOS!").withStyle(ChatFormatting.GOLD)));
                jugador.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 1.0f, 1.0f);
            }
        });
        c.setPacketHandled(true);
    }
}
