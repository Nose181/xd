package com.ruleta;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.ruleta.client.RetosConfig;
import com.ruleta.net.GirarPacket;
import com.ruleta.net.MuertePacket;
import com.ruleta.net.Net;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * /ruleta [segundos]            -> gira la ruleta en la pantalla de TODOS y sale un reto.
 * /cumplido                     -> cada jugador avisa que cumplio el reto.
 * /ruleta agregar <color> <reto>, /ruleta quitar <n>, /ruleta lista  -> editar los retos (operadores).
 * Quien no escriba /cumplido antes de que se acabe el tiempo muere al instante.
 * Cada vez que muere un jugador (por la ruleta o por cualquier causa) sale la animacion de la calavera.
 */
@Mod.EventBusSubscriber(modid = RuletaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class RuletaServer {

    private enum Fase { NINGUNA, GIRANDO, ACTIVO, EJECUCION }

    private static final int TICKS_GIRO = 180;      // 9 s: animacion de la ruleta + resultado
    private static final int TICKS_EJECUCION = 55;  // ~2,7 s: animacion de la calavera antes de morir

    private static Fase fase = Fase.NINGUNA;
    private static int ticks = 0;
    private static int segundosLimite = 60;
    private static String reto = "";
    private static boolean ejecutando = false;
    private static final Set<UUID> participantes = new HashSet<>();
    private static final Set<UUID> cumplidos = new HashSet<>();
    private static final Set<UUID> condenados = new HashSet<>();

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
                        .requires(RuletaServer::puedeAdministrar)
                        .executes(ctx -> cancelar(ctx.getSource())))
                .then(Commands.literal("lista")
                        .requires(RuletaServer::puedeAdministrar)
                        .executes(ctx -> lista(ctx.getSource())))
                .then(Commands.literal("quitar")
                        .requires(RuletaServer::puedeAdministrar)
                        .then(Commands.argument("numero", IntegerArgumentType.integer(1, RetosConfig.MAXIMO))
                                .executes(ctx -> quitar(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "numero")))))
                .then(Commands.literal("agregar")
                        .requires(RuletaServer::puedeAdministrar)
                        .then(Commands.argument("color", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(Colores.nombres(), b))
                                .then(Commands.argument("condicion", StringArgumentType.greedyString())
                                        .executes(ctx -> agregar(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "color"),
                                                StringArgumentType.getString(ctx, "condicion")))))));

        d.register(Commands.literal("cumplido")
                .executes(ctx -> cumplido(ctx.getSource())));
    }

    private static boolean puedeAdministrar(CommandSourceStack src) {
        if (src.hasPermission(2)) return true;
        return src.getEntity() instanceof ServerPlayer sp
                && src.getServer().isSingleplayerOwner(sp.getGameProfile());
    }

    private static int iniciar(CommandSourceStack src, int segundos) {
        if (fase != Fase.NINGUNA) {
            src.sendFailure(Component.literal("Ya hay una ruleta en marcha."));
            return 0;
        }
        List<Reto> retos = RetosConfig.cargar();
        int n = retos.size();
        int[] colores = new int[n];
        for (int i = 0; i < n; i++) colores[i] = retos.get(i).rgb(i, n);
        int indice = new Random().nextInt(n);
        reto = retos.get(indice).texto();

        segundosLimite = segundos;
        fase = Fase.GIRANDO;
        ticks = TICKS_GIRO;
        participantes.clear();
        cumplidos.clear();
        condenados.clear();

        // El servidor elige el resultado, todos ven el mismo y solo se envia el reto ganador.
        Net.CHANNEL.send(new GirarPacket(colores, indice, reto), PacketDistributor.ALL.noArg());
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
        if (fase == Fase.EJECUCION) {
            src.sendFailure(Component.literal("Ya es tarde..."));
            return 0;
        }
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

    // ------------------------------------------------------------ editar retos
    private static int lista(CommandSourceStack src) {
        List<Reto> l = RetosConfig.cargar();
        src.sendSuccess(() -> Component.literal("§6[Ruleta] §fRetos (" + l.size() + "):"), false);
        for (int i = 0; i < l.size(); i++) {
            final int num = i + 1;
            final Reto r = l.get(i);
            final int rgb = r.rgb(i, l.size());
            src.sendSuccess(() -> Component.literal("§e" + num + ". ")
                    .append(Component.literal("■ ").withStyle(Style.EMPTY.withColor(rgb)))
                    .append(Component.literal("§f" + r.texto())), false);
        }
        return 1;
    }

    private static int agregar(CommandSourceStack src, String color, String condicion) {
        if (fase != Fase.NINGUNA) {
            src.sendFailure(Component.literal("Espera a que termine la ruleta en marcha."));
            return 0;
        }
        final String c = color.toLowerCase(Locale.ROOT);
        final int rgb = Colores.parse(c);
        if (rgb < 0) {
            src.sendFailure(Component.literal("Color no válido. Usa: " + String.join(", ", Colores.nombres())
                    + " o un código hex de 6 dígitos (ej: ff8800)."));
            return 0;
        }
        String t = condicion.replace('|', ' ').trim();
        if (t.length() > 200) t = t.substring(0, 200);
        if (t.isEmpty()) {
            src.sendFailure(Component.literal("Escribe la condición del reto."));
            return 0;
        }
        final String texto = t;

        List<Reto> l = new ArrayList<>(RetosConfig.cargar());
        if (l.size() >= RetosConfig.MAXIMO) {
            src.sendFailure(Component.literal("Máximo " + RetosConfig.MAXIMO + " retos. Quita alguno con /ruleta quitar."));
            return 0;
        }
        l.add(new Reto(c, texto));
        try {
            RetosConfig.guardar(l);
        } catch (IOException e) {
            src.sendFailure(Component.literal("No se pudo guardar el archivo de retos."));
            return 0;
        }
        final int n = l.size();
        src.sendSuccess(() -> Component.literal("§aAgregado #" + n + ": ")
                .append(Component.literal("■ ").withStyle(Style.EMPTY.withColor(rgb)))
                .append(Component.literal("§f" + texto)), false);
        return 1;
    }

    private static int quitar(CommandSourceStack src, int numero) {
        if (fase != Fase.NINGUNA) {
            src.sendFailure(Component.literal("Espera a que termine la ruleta en marcha."));
            return 0;
        }
        List<Reto> l = new ArrayList<>(RetosConfig.cargar());
        if (numero > l.size()) {
            src.sendFailure(Component.literal("No existe el reto #" + numero + " (hay " + l.size() + ")."));
            return 0;
        }
        if (l.size() <= RetosConfig.MINIMO) {
            src.sendFailure(Component.literal("Debe haber al menos " + RetosConfig.MINIMO + " retos."));
            return 0;
        }
        final Reto quitado = l.remove(numero - 1);
        try {
            RetosConfig.guardar(l);
        } catch (IOException e) {
            src.sendFailure(Component.literal("No se pudo guardar el archivo de retos."));
            return 0;
        }
        src.sendSuccess(() -> Component.literal("§cQuitado: §f" + quitado.texto()), false);
        return 1;
    }

    // ------------------------------------------------------------ animacion en cada muerte
    @SubscribeEvent
    public static void alMorir(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer jugador)) return;
        if (ejecutando) return; // la ruleta ya mostro la animacion antes de matar
        List<String> nombres = new ArrayList<>();
        nombres.add(jugador.getName().getString());
        Net.CHANNEL.send(new MuertePacket(nombres, false), PacketDistributor.ALL.noArg());
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
        if (fase == Fase.EJECUCION) {
            if (--ticks <= 0) ejecutar(server);
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

    /** Se acabo el tiempo: se muestra la calavera a todos y, unos segundos despues, mueren los que no cumplieron. */
    private static void castigar(MinecraftServer server) {
        condenados.clear();
        List<String> nombres = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!participantes.contains(p.getUUID()) || cumplidos.contains(p.getUUID())) continue;
            if (!p.isAlive()) continue;
            condenados.add(p.getUUID());
            nombres.add(p.getName().getString());
        }
        if (condenados.isEmpty()) {
            reiniciar();
            return;
        }
        fase = Fase.EJECUCION;
        ticks = TICKS_EJECUCION;
        server.getPlayerList().broadcastSystemMessage(
                Component.literal("§c§l[Ruleta] ¡Se acabó el tiempo!"), false);
        Net.CHANNEL.send(new MuertePacket(nombres, true), PacketDistributor.ALL.noArg());
    }

    private static void ejecutar(MinecraftServer server) {
        ejecutando = true;
        try {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (!condenados.contains(p.getUUID()) || !p.isAlive()) continue;
                server.getPlayerList().broadcastSystemMessage(Component.literal(
                        "§c" + p.getName().getString() + " no cumplió el reto y murió."), false);
                p.hurt(p.damageSources().genericKill(), Float.MAX_VALUE);
            }
        } finally {
            ejecutando = false;
        }
        reiniciar();
    }

    private static void reiniciar() {
        fase = Fase.NINGUNA;
        ticks = 0;
        participantes.clear();
        cumplidos.clear();
        condenados.clear();
    }
}
