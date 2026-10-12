package com.ruleta;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.ruleta.client.RetosConfig;
import com.ruleta.net.GirarPacket;
import com.ruleta.net.Net;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * /ruleta [segundos]  -> la ruleta gira en la pantalla de TODOS y sale un reto.
 * /cumplido           -> cada jugador avisa que cumplio el reto.
 * Quien no escriba /cumplido antes de que se acabe el tiempo muere al instante.
 */
@Mod.EventBusSubscriber(modid = RuletaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class RuletaServer {

    private enum Fase { NINGUNA, GIRANDO, ACTIVO }

    private static final int TICKS_GIRO = 150; // 7,5 s: lo que dura la animacion

    private static Fase fase = Fase.NINGUNA;
    private static int ticks = 0;
    private static int segundosLimite = 60;
    private static String reto = "";
    private static final Set<UUID> participantes = new HashSet<>();
    private static final Set<UUID> cumplidos = new HashSet<>();

    // ------------------------------------------------------------ comandos
    @SubscribeEvent
    public static void comandos(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();

        d.register(Commands.literal("ruleta")
                .executes(ctx -> iniciar(ctx.getSource(), 60))
                .then(Commands.argument("segundos", IntegerArgumentType.integer(10, 600))
                        .executes(ctx -> iniciar(ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "segundos"))))
                .then(Commands.literal("cancelar")
                        .requires(RuletaServer::puedeCancelar)
                        .executes(ctx -> cancelar(ctx.getSource()))));

        d.register(Commands.literal("cumplido")
                .executes(ctx -> cumplido(ctx.getSource())));
    }

    private static boolean puedeCancelar(CommandSourceStack src) {
        if (src.hasPermission(2)) return true;
        return src.getEntity() instanceof ServerPlayer sp
                && src.getServer().isSingleplayerOwner(sp.getGameProfile());
    }

    private static int iniciar(CommandSourceStack src, int segundos) {
        if (fase != Fase.NINGUNA) {
            src.sendFailure(Component.literal("Ya hay una ruleta en marcha."));
            return 0;
        }
        List<String> retos = RetosConfig.cargar();
        int indice = new Random().nextInt(retos.size());
        reto = retos.get(indice);
        segundosLimite = segundos;
        fase = Fase.GIRANDO;
        ticks = TICKS_GIRO;
        participantes.clear();
        cumplidos.clear();

        // El servidor elige el resultado y todos los clientes ven el mismo.
        Net.CHANNEL.send(PacketDistributor.ALL.noArg(), new GirarPacket(retos, indice));
        src.getServer().getPlayerList().broadcastSystemMessage(
                Component.literal("§6[Ruleta] §f¡La ruleta está girando!"), false);
        return 1;
    }

    private static int cancelar(CommandSourceStack src) {
        if (fase == Fase.NINGUNA) {
            src.sendFailure(Component.literal("No hay ninguna ruleta en marcha."));
            return 0;
        }
        reiniciar();
        src.getServer().getPlayerList().broadcastSystemMessage(
                Component.literal("§6[Ruleta] §fRuleta cancelada. Nadie muere."), false);
        return 1;
    }

    private static int cumplido(CommandSourceStack src) {
        if (fase != Fase.ACTIVO) {
            src.sendFailure(Component.literal("No hay ningún reto activo."));
            return 0;
        }
        if (!(src.getEntity() instanceof ServerPlayer jugador)) return 0;
        if (!participantes.contains(jugador.getUUID())) {
            src.sendFailure(Component.literal("No participas en este reto."));
            return 0;
        }
        if (!cumplidos.add(jugador.getUUID())) {
            src.sendFailure(Component.literal("Ya marcaste este reto como cumplido."));
            return 0;
        }
        MinecraftServer server = src.getServer();
        server.getPlayerList().broadcastSystemMessage(Component.literal(
                "§a" + jugador.getName().getString() + " cumplió el reto."), false);

        boolean todos = true;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (participantes.contains(p.getUUID()) && !cumplidos.contains(p.getUUID())) {
                todos = false;
                break;
            }
        }
        if (todos) {
            server.getPlayerList().broadcastSystemMessage(
                    Component.literal("§6[Ruleta] §a¡Todos cumplieron el reto! Nadie muere."), false);
            reiniciar();
        }
        return 1;
    }

    // ------------------------------------------------------------ tiempo
    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || fase == Fase.NINGUNA) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            reiniciar();
            return;
        }

        if (fase == Fase.GIRANDO) {
            if (--ticks <= 0) activar(server);
            return;
        }

        // Fase ACTIVO: cuenta regresiva
        ticks--;
        if (ticks > 0 && ticks % 20 == 0) {
            int seg = ticks / 20;
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (participantes.contains(p.getUUID()) && !cumplidos.contains(p.getUUID())) {
                    p.displayClientMessage(Component.literal(
                            "§e" + reto + " §c(" + seg + " s) §7- escribe /cumplido"), true);
                }
            }
        }
        if (ticks <= 0) castigar(server);
    }

    @SubscribeEvent
    public static void servidorDetenido(ServerStoppedEvent event) {
        reiniciar();
    }

    private static void activar(MinecraftServer server) {
        participantes.clear();
        cumplidos.clear();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.isCreative() || p.isSpectator()) continue;
            participantes.add(p.getUUID());
        }
        if (participantes.isEmpty()) {
            reiniciar();
            return;
        }
        fase = Fase.ACTIVO;
        ticks = segundosLimite * 20;

        server.getPlayerList().broadcastSystemMessage(Component.literal(
                "§6[Ruleta] §fReto para todos: §a" + reto + "§f. Tienen §e" + segundosLimite
                        + " s§f. Al cumplirlo escribe §b/cumplido§f. ¡Quien no lo haga, muere!"), false);

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 80, 20));
            p.connection.send(new ClientboundSetSubtitleTextPacket(
                    Component.literal(reto).withStyle(ChatFormatting.WHITE)));
            p.connection.send(new ClientboundSetTitleTextPacket(
                    Component.literal("¡RETO PARA TODOS!").withStyle(ChatFormatting.GOLD)));
            p.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 1.0f, 1.0f);
        }
    }

    private static void castigar(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!participantes.contains(p.getUUID()) || cumplidos.contains(p.getUUID())) continue;
            if (!p.isAlive()) continue;
            server.getPlayerList().broadcastSystemMessage(Component.literal(
                    "§c" + p.getName().getString() + " no cumplió el reto y murió."), false);
            p.hurt(p.damageSources().genericKill(), Float.MAX_VALUE);
        }
        reiniciar();
    }

    private static void reiniciar() {
        fase = Fase.NINGUNA;
        ticks = 0;
        participantes.clear();
        cumplidos.clear();
    }
}
