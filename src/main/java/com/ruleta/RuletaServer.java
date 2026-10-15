package com.ruleta;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.ruleta.client.RetosConfig;
import com.ruleta.net.AvisoPacket;
import com.ruleta.net.GirarPacket;
import com.ruleta.net.MuertePacket;
import com.ruleta.net.Net;
import com.ruleta.net.PanelAccionPacket;
import com.ruleta.net.PanelPacket;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
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
 * /ruleta [segundos]  -> la ruleta gira en la pantalla de TODOS y sale un reto.
 * /cumplido           -> cada jugador avisa que cumplio el reto (si el reto lo pide).
 * /ruleta panel       -> abre el panel de control (operadores).
 * /ruleta agregar <color> <tiempo|sin> <cumplido|aviso> <texto>, /ruleta quitar <n>,
 * /ruleta lista, /ruleta probar <n>, /ruleta cancelar  -> administrar (operadores).
 *
 * Solo mueren los que no escriban /cumplido a tiempo cuando el reto tiene tiempo y pide /cumplido.
 * Cada vez que muere un jugador (por la ruleta o por cualquier causa) sale la animacion de la calavera.
 */
@Mod.EventBusSubscriber(modid = RuletaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class RuletaServer {

    private enum Fase { NINGUNA, GIRANDO, ACTIVO, EJECUCION }

    private static final int TICKS_GIRO = 180;      // 9 s: animacion de la ruleta + resultado
    private static final int TICKS_EJECUCION = 55;  // ~2,7 s: animacion de la calavera antes de morir

    private static Fase fase = Fase.NINGUNA;
    private static int ticks = 0;
    private static int cuenta = 0;
    private static int segundosLimite = 0;          // 0 = sin tiempo
    private static Reto retoActual = null;
    private static int colorAviso = 0xFFAA00;
    private static boolean avisoActivo = true;      // si al terminar de girar se muestra el cuadro de aviso
    private static boolean avisoMostrado = false;   // si hay un cuadro de aviso en pantalla de los jugadores
    private static boolean ejecutando = false;
    private static final Set<UUID> participantes = new HashSet<>();
    private static final Set<UUID> cumplidos = new HashSet<>();
    private static final Set<UUID> condenados = new HashSet<>();

    // ------------------------------------------------------------ comandos
    @SubscribeEvent
    public static void comandos(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();

        List<String> colores = new ArrayList<>(Colores.nombres());
        colores.add("auto");

        d.register(Commands.literal("ruleta")
                .executes(ctx -> iniciar(ctx.getSource(), 0, -1))
                .then(Commands.argument("segundos", IntegerArgumentType.integer(RetosConfig.SEG_MIN, RetosConfig.SEG_MAX))
                        .executes(ctx -> iniciar(ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "segundos"), -1)))
                .then(Commands.literal("panel")
                        .requires(RuletaServer::puedeAdministrar)
                        .executes(ctx -> panel(ctx.getSource())))
                .then(Commands.literal("cancelar")
                        .requires(RuletaServer::puedeAdministrar)
                        .executes(ctx -> cancelar(ctx.getSource())))
                .then(Commands.literal("lista")
                        .requires(RuletaServer::puedeAdministrar)
                        .executes(ctx -> lista(ctx.getSource())))
                .then(Commands.literal("probar")
                        .requires(RuletaServer::puedeAdministrar)
                        .then(Commands.argument("numero", IntegerArgumentType.integer(1, RetosConfig.MAXIMO))
                                .executes(ctx -> probar(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "numero")))))
                .then(Commands.literal("quitar")
                        .requires(RuletaServer::puedeAdministrar)
                        .then(Commands.argument("numero", IntegerArgumentType.integer(1, RetosConfig.MAXIMO))
                                .executes(ctx -> quitar(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "numero")))))
                .then(Commands.literal("agregar")
                        .requires(RuletaServer::puedeAdministrar)
                        .then(Commands.argument("color", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(colores, b))
                                .then(Commands.argument("tiempo", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                                List.of("sin", "30", "60", "120"), b))
                                        .then(Commands.argument("modo", StringArgumentType.word())
                                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                                        List.of("cumplido", "aviso"), b))
                                                .then(Commands.argument("texto", StringArgumentType.greedyString())
                                                        .executes(ctx -> agregar(ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "color"),
                                                                StringArgumentType.getString(ctx, "tiempo"),
                                                                StringArgumentType.getString(ctx, "modo"),
                                                                StringArgumentType.getString(ctx, "texto")))))))));

        d.register(Commands.literal("cumplido")
                .executes(ctx -> cumplido(ctx.getSource())));
    }

    private static boolean puedeAdministrar(CommandSourceStack src) {
        if (src.hasPermission(2)) return true;
        return src.getEntity() instanceof ServerPlayer sp
                && src.getServer().isSingleplayerOwner(sp.getGameProfile());
    }

    private static int panel(CommandSourceStack src) {
        if (!(src.getEntity() instanceof ServerPlayer jugador)) {
            src.sendFailure(Component.literal("Solo un jugador puede abrir el panel."));
            return 0;
        }
        enviarPanel(jugador, 0);
        return 1;
    }

    private static void enviarPanel(ServerPlayer jugador, int seleccionado) {
        Net.CHANNEL.send(new PanelPacket(RetosConfig.cargar(), seleccionado), PacketDistributor.PLAYER.with(jugador));
    }

    // ------------------------------------------------------------ acciones del panel
    /** Lo que llega desde el panel de control (siempre se comprueba que sea operador). */
    public static void panelAccion(ServerPlayer jugador, PanelAccionPacket a) {
        if (!puedeAdministrar(jugador.createCommandSourceStack())) {
            jugador.sendSystemMessage(Component.literal("§cNo tienes permiso para usar el panel de la ruleta."));
            return;
        }
        MinecraftServer server = jugador.getServer();
        if (server == null) return;

        if (a.accion == PanelAccionPacket.DETENER) {
            detener(server);
            return;
        }

        List<Reto> retos = new ArrayList<>(RetosConfig.cargar());
        if (a.indice < 0 || a.indice >= retos.size()) return;
        Reto base = retos.get(a.indice);

        String titulo = Textos.limpiarTitulo(a.titulo);
        String texto = Textos.limpiarTexto(a.mensaje);
        int seg = a.segundos;
        if (seg != 0 && (seg < RetosConfig.SEG_MIN || seg > RetosConfig.SEG_MAX)) seg = base.segundos();
        if (texto.isEmpty()) {
            jugador.sendSystemMessage(Component.literal("§cEscribe un mensaje primero."));
            return;
        }
        Reto editado = new Reto(base.color(), seg, a.cumplido, titulo, texto);
        int rgb = base.rgb(a.indice, retos.size());

        switch (a.accion) {
            case PanelAccionPacket.GUARDAR -> {
                if (fase != Fase.NINGUNA) {
                    jugador.sendSystemMessage(Component.literal("§cEspera a que termine la ruleta en marcha."));
                    return;
                }
                retos.set(a.indice, editado);
                try {
                    RetosConfig.guardar(retos);
                } catch (IOException e) {
                    jugador.sendSystemMessage(Component.literal("§cNo se pudo guardar el archivo de retos."));
                    return;
                }
                jugador.sendSystemMessage(Component.literal("§aGuardado."));
                enviarPanel(jugador, a.indice);
            }
            case PanelAccionPacket.LANZAR -> {
                String error = lanzar(server, 0, a.indice, editado, a.aviso);
                if (error != null) jugador.sendSystemMessage(Component.literal("§c" + error));
            }
            case PanelAccionPacket.SOLO_AVISO -> {
                String t = titulo.isEmpty() ? "AVISO" : titulo;
                mostrarAviso(server, t, texto, rgb, 0, false);
                sonido(server);
            }
            default -> { }
        }
    }

    // ------------------------------------------------------------ iniciar / detener
    /** override = segundos pedidos con /ruleta <segundos> (0 = ninguno); forzado = indice a probar (-1 = al azar). */
    private static int iniciar(CommandSourceStack src, int override, int forzado) {
        String error = lanzar(src.getServer(), override, forzado, null, true);
        if (error != null) {
            src.sendFailure(Component.literal(error));
            return 0;
        }
        return 1;
    }

    /** Devuelve null si todo salio bien, o el texto del error. */
    private static String lanzar(MinecraftServer server, int override, int forzado, Reto personalizado, boolean aviso) {
        if (fase != Fase.NINGUNA) return "Ya hay una ruleta en marcha.";

        List<Reto> retos = RetosConfig.cargar();
        int n = retos.size();
        int[] colores = new int[n];
        for (int i = 0; i < n; i++) colores[i] = retos.get(i).rgb(i, n);
        int indice = forzado >= 0 ? Math.min(forzado, n - 1) : new Random().nextInt(n);
        retoActual = personalizado != null ? personalizado : retos.get(indice);
        colorAviso = colores[indice];
        avisoActivo = aviso;

        // Un reto "sin tiempo" ignora el tiempo pedido; uno con tiempo puede cambiarlo con /ruleta <segundos>.
        segundosLimite = retoActual.tieneTiempo() ? (override > 0 ? override : retoActual.segundos()) : 0;

        fase = Fase.GIRANDO;
        ticks = TICKS_GIRO;
        cuenta = 0;
        participantes.clear();
        cumplidos.clear();
        condenados.clear();

        // El servidor elige el resultado, todos ven el mismo y solo se envia el reto ganador.
        Net.CHANNEL.send(new GirarPacket(colores, indice, retoActual.texto()), PacketDistributor.ALL.noArg());
        server.getPlayerList().broadcastSystemMessage(
                Component.literal("§6[Ruleta] §f¡La ruleta está girando!"), false);
        return null;
    }

    private static int probar(CommandSourceStack src, int numero) {
        int n = RetosConfig.cargar().size();
        if (numero > n) {
            src.sendFailure(Component.literal("No existe el reto #" + numero + " (hay " + n + ")."));
            return 0;
        }
        return iniciar(src, 0, numero - 1);
    }

    private static int cancelar(CommandSourceStack src) {
        if (fase == Fase.NINGUNA && !avisoMostrado) {
            src.sendFailure(Component.literal("No hay nada en marcha."));
            return 0;
        }
        detener(src.getServer());
        return 1;
    }

    /** Para todo: la ruleta en marcha, el castigo pendiente y el cuadro de aviso. Nadie muere. */
    private static void detener(MinecraftServer server) {
        boolean habia = fase != Fase.NINGUNA || avisoMostrado;
        reiniciar(false);
        avisoMostrado = false;
        Net.CHANNEL.send(AvisoPacket.quitar(), PacketDistributor.ALL.noArg());
        if (habia) {
            server.getPlayerList().broadcastSystemMessage(
                    Component.literal("§6[Ruleta] §fDetenida. Nadie muere."), false);
        }
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
        if (retoActual == null || !retoActual.cumplido()) {
            src.sendFailure(Component.literal("Este aviso no necesita /cumplido."));
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

        if (!hayPendientes(server)) {
            server.getPlayerList().broadcastSystemMessage(
                    Component.literal("§6[Ruleta] §a¡Todos cumplieron el reto! Nadie muere."), false);
            reiniciar(true);
        }
        return 1;
    }

    private static boolean hayPendientes(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (participantes.contains(p.getUUID()) && !cumplidos.contains(p.getUUID())) return true;
        }
        return false;
    }

    // ------------------------------------------------------------ editar retos con comandos
    private static String resumen(String texto) {
        String t = texto.replace("\n", " ");
        if (t.length() > 60) t = t.substring(0, 57) + "...";
        return Textos.colorear(t) + "§r";
    }

    private static String descripcion(Reto r) {
        String tiempo = r.tieneTiempo() ? r.segundos() + " s" : "sin tiempo";
        String modo = r.cumplido() ? "/cumplido" : "aviso";
        String titulo = (r.titulo() == null || r.titulo().isEmpty()) ? "" : "§l" + Textos.colorear(r.titulo()) + "§r§f: ";
        return "§7[" + tiempo + ", " + modo + "] §f" + titulo + resumen(r.texto());
    }

    private static int lista(CommandSourceStack src) {
        List<Reto> l = RetosConfig.cargar();
        src.sendSuccess(() -> Component.literal("§6[Ruleta] §fRetos (" + l.size() + "):"), false);
        for (int i = 0; i < l.size(); i++) {
            final int num = i + 1;
            final Reto r = l.get(i);
            final int rgb = r.rgb(i, l.size());
            src.sendSuccess(() -> Component.literal("§e" + num + ". ")
                    .append(Component.literal("■ ").withStyle(Style.EMPTY.withColor(rgb)))
                    .append(Component.literal(descripcion(r))), false);
        }
        return 1;
    }

    private static int agregar(CommandSourceStack src, String color, String tiempo, String modo, String texto) {
        if (fase != Fase.NINGUNA) {
            src.sendFailure(Component.literal("Espera a que termine la ruleta en marcha."));
            return 0;
        }
        final String c = color.toLowerCase(Locale.ROOT);
        final boolean auto = c.equals("auto");
        if (!auto && Colores.parse(c) < 0) {
            src.sendFailure(Component.literal("Color no válido. Usa: " + String.join(", ", Colores.nombres())
                    + ", auto, o un código hex de 6 dígitos (ej: ff8800)."));
            return 0;
        }
        int seg = RetosConfig.parseTiempo(tiempo);
        if (seg < 0) {
            src.sendFailure(Component.literal("Tiempo no válido. Usa 'sin' o un número de segundos entre "
                    + RetosConfig.SEG_MIN + " y " + RetosConfig.SEG_MAX + "."));
            return 0;
        }
        Boolean exige = RetosConfig.parseModo(modo);
        if (exige == null) {
            src.sendFailure(Component.literal("Modo no válido. Usa 'cumplido' (hay que escribir /cumplido) o 'aviso' (solo se anuncia)."));
            return 0;
        }
        String t = Textos.limpiarTexto(Textos.desdeArchivo(texto));
        if (t.isEmpty()) {
            src.sendFailure(Component.literal("Escribe el texto del reto."));
            return 0;
        }

        List<Reto> l = new ArrayList<>(RetosConfig.cargar());
        if (l.size() >= RetosConfig.MAXIMO) {
            src.sendFailure(Component.literal("Máximo " + RetosConfig.MAXIMO + " retos. Quita alguno con /ruleta quitar."));
            return 0;
        }
        final Reto nuevo = new Reto(auto ? "" : c, seg, exige, t);
        l.add(nuevo);
        try {
            RetosConfig.guardar(l);
        } catch (IOException e) {
            src.sendFailure(Component.literal("No se pudo guardar el archivo de retos."));
            return 0;
        }
        final int n = l.size();
        final int rgb = nuevo.rgb(n - 1, n);
        src.sendSuccess(() -> Component.literal("§aAgregado #" + n + ": ")
                .append(Component.literal("■ ").withStyle(Style.EMPTY.withColor(rgb)))
                .append(Component.literal(descripcion(nuevo))), false);
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
        src.sendSuccess(() -> Component.literal("§cQuitado: §f" + resumen(quitado.texto())), false);
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
        if (server == null || retoActual == null) {
            reiniciar(true);
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

        // Fase ACTIVO
        String titulo = Textos.colorear(retoActual.tituloVisible());
        if (segundosLimite > 0) {
            ticks--;
            if (ticks > 0 && ticks % 20 == 0) {
                int seg = ticks / 20;
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    if (retoActual.cumplido()) {
                        if (participantes.contains(p.getUUID()) && !cumplidos.contains(p.getUUID())) {
                            p.displayClientMessage(Component.literal("§e" + titulo
                                    + " §c(" + seg + " s) §7- escribe /cumplido"), true);
                        }
                    } else {
                        p.displayClientMessage(Component.literal("§e" + titulo + " §c(" + seg + " s)"), true);
                    }
                }
            }
            if (ticks <= 0) {
                if (retoActual.cumplido()) {
                    castigar(server);
                } else {
                    server.getPlayerList().broadcastSystemMessage(
                            Component.literal("§6[Ruleta] §f¡Se acabó el tiempo!"), false);
                    reiniciar(true);
                }
            }
        } else {
            // Sin tiempo (solo puede ser un reto con /cumplido): recordatorio cada 5 s
            cuenta++;
            if (cuenta % 100 == 0) {
                if (!hayPendientes(server)) {
                    reiniciar(true);
                    return;
                }
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    if (participantes.contains(p.getUUID()) && !cumplidos.contains(p.getUUID())) {
                        p.displayClientMessage(Component.literal("§e" + titulo
                                + " §7- escribe /cumplido cuando lo cumplas"), true);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void servidorDetenido(ServerStoppedEvent event) {
        avisoMostrado = false;
        reiniciar(false);
    }

    // ------------------------------------------------------------ aviso (cuadro arriba a la izquierda + chat)
    private static String instruccion(boolean exige, int seg) {
        if (exige && seg > 0) return "§7Tienen " + seg + " s. Al cumplirlo escribe /cumplido. ¡Quien no lo haga, muere!";
        if (exige) return "§7Sin límite de tiempo. Al cumplirlo escribe /cumplido.";
        if (seg > 0) return "§7Tienen " + seg + " s.";
        return "";
    }

    private static void mostrarAviso(MinecraftServer server, String titulo, String texto,
                                     int rgb, int segundos, boolean pideCumplido) {
        // Cuadro de aviso arriba a la izquierda (se oculta con la tecla I)
        Net.CHANNEL.send(new AvisoPacket(titulo, texto, rgb, segundos, pideCumplido, false),
                PacketDistributor.ALL.noArg());
        avisoMostrado = true;

        // Y el mismo aviso en el chat
        MutableComponent cabecera = Component.literal("● " + Textos.colorear(titulo) + ":")
                .withStyle(Style.EMPTY.withColor(rgb).withBold(true));
        server.getPlayerList().broadcastSystemMessage(cabecera, false);
        server.getPlayerList().broadcastSystemMessage(Component.literal(Textos.colorear(texto) + "§r"), false);
        String extra = instruccion(pideCumplido, segundos);
        if (!extra.isEmpty()) {
            server.getPlayerList().broadcastSystemMessage(Component.literal(extra), false);
        }
    }

    private static void sonido(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 1.0f, 1.0f);
        }
    }

    private static void activar(MinecraftServer server) {
        final boolean exige = retoActual.cumplido();
        final boolean conTiempo = segundosLimite > 0;

        participantes.clear();
        cumplidos.clear();
        if (exige) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (p.isCreative() || p.isSpectator()) continue;
                participantes.add(p.getUUID());
            }
            if (participantes.isEmpty()) {
                reiniciar(true);
                return;
            }
        }

        if (avisoActivo) {
            mostrarAviso(server, retoActual.tituloVisible(), retoActual.texto(), colorAviso,
                    conTiempo ? segundosLimite : 0, exige);
        }
        sonido(server);

        if (!exige && !conTiempo) {
            // Aviso simple: no hay nada que esperar (el cuadro de aviso se queda hasta que lo ocultes).
            reiniciar(false);
            return;
        }
        fase = Fase.ACTIVO;
        ticks = conTiempo ? segundosLimite * 20 : 0;
        cuenta = 0;
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
            reiniciar(true);
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
        reiniciar(true);
    }

    /** limpiarAviso = true quita tambien el cuadro de aviso de la pantalla de los jugadores. */
    private static void reiniciar(boolean limpiarAviso) {
        fase = Fase.NINGUNA;
        ticks = 0;
        cuenta = 0;
        segundosLimite = 0;
        retoActual = null;
        participantes.clear();
        cumplidos.clear();
        condenados.clear();
        if (limpiarAviso && avisoMostrado) {
            avisoMostrado = false;
            if (ServerLifecycleHooks.getCurrentServer() != null) {
                Net.CHANNEL.send(AvisoPacket.quitar(), PacketDistributor.ALL.noArg());
            }
        }
    }
}
