package com.ruleta.client;

import com.ruleta.Colores;
import com.ruleta.Reto;
import com.ruleta.Textos;
import com.ruleta.net.Net;
import com.ruleta.net.PanelAccionPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;
import net.minecraftforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Panel de control de la ruleta (se abre con /ruleta panel): elegir la ruleta por color, escribir el titulo
 * y el mensaje con colores, decidir si hay aviso y lanzar, mostrar solo el aviso o detener.
 */
public class PanelScreen extends Screen {

    private List<Reto> retos;
    private int sel;
    private boolean aviso = true;
    private int segundos;
    private boolean cumplido;
    private String tituloTxt = "";
    private String mensajeTxt = "";
    private boolean forzarCarga = true;
    private int tituloY = 4;

    private EditBox titulo;
    private MultiLineEditBox mensaje;
    private Button btnTiempo;
    private Button btnModo;
    private Button btnAviso;

    public PanelScreen(List<Reto> retos, int seleccionado) {
        super(Component.literal("Panel de Control de Ruletas"));
        this.retos = new ArrayList<>(retos);
        this.sel = Mth.clamp(seleccionado, 0, this.retos.size() - 1);
        cargarEntrada();
    }

    /** El servidor mando la lista actualizada (por ejemplo despues de guardar). */
    public void actualizar(List<Reto> nuevos, int seleccionado) {
        this.retos = new ArrayList<>(nuevos);
        this.sel = Mth.clamp(seleccionado, 0, this.retos.size() - 1);
        cargarEntrada();
        rebuildWidgets();
    }

    private void cargarEntrada() {
        Reto r = retos.get(sel);
        segundos = r.segundos();
        cumplido = r.cumplido();
        tituloTxt = r.titulo() == null ? "" : r.titulo();
        mensajeTxt = r.texto();
        forzarCarga = true;
    }

    // ------------------------------------------------------------------ construccion
    @Override
    protected void init() {
        if (!forzarCarga) {
            if (titulo != null) tituloTxt = titulo.getValue();
            if (mensaje != null) mensajeTxt = mensaje.getValue();
        }
        forzarCarga = false;

        int pw = Math.min(this.width - 20, 360);
        int x0 = (this.width - pw) / 2;
        int y = Math.max(18, (this.height - 270) / 2 + 14);
        tituloY = y - 12;

        // Fila 1: elegir la ruleta (por color)
        Reto r = retos.get(sel);
        addRenderableWidget(Button.builder(etiquetaRuleta(r), b -> {
            sel = (sel + 1) % retos.size();
            cargarEntrada();
            rebuildWidgets();
        }).bounds(x0, y, pw, 18).build());

        // Filas 2 y 3: botones de color y formato para el mensaje
        String[][] fila1 = {
                {"Naranja", "&6", "FFAA00"}, {"Rojo", "&c", "FF5555"}, {"Verde", "&a", "55FF55"},
                {"Cian", "&b", "55FFFF"}, {"Morado", "&5", "AA00AA"}};
        int bw1 = (pw - 4 * 4) / 5;
        for (int i = 0; i < fila1.length; i++) {
            addRenderableWidget(botonColor(x0 + i * (bw1 + 4), y + 22, bw1, fila1[i]));
        }
        String[][] fila2 = {{"Amarillo", "&e", "FFFF55"}, {"Rosa", "&d", "FF55FF"}, {"Azul", "&9", "5555FF"}};
        int bw2 = (pw - 5 * 4) / 6;
        for (int i = 0; i < fila2.length; i++) {
            addRenderableWidget(botonColor(x0 + i * (bw2 + 4), y + 40, bw2, fila2[i]));
        }
        addRenderableWidget(Button.builder(Component.literal("Negrita"), b -> insertar("&l"))
                .bounds(x0 + 3 * (bw2 + 4), y + 40, bw2, 16).build());
        addRenderableWidget(Button.builder(Component.literal("Salto"), b -> insertarSalto())
                .bounds(x0 + 4 * (bw2 + 4), y + 40, bw2, 16).build());
        addRenderableWidget(Button.builder(Component.literal("Reset"), b -> insertar("&r"))
                .bounds(x0 + 5 * (bw2 + 4), y + 40, bw2, 16).build());

        // Titulo y mensaje
        titulo = new EditBox(this.font, x0, y + 60, pw, 18, Component.literal("Título"));
        titulo.setMaxLength(Textos.MAX_TITULO);
        titulo.setHint(Component.literal("Título del aviso"));
        titulo.setValue(tituloTxt);
        addRenderableWidget(titulo);

        int mh = Mth.clamp(this.height - 200, 40, 100);
        mensaje = new MultiLineEditBox(this.font, x0, y + 82, pw, mh,
                Component.literal("Escribe el mensaje... (usa los botones de color)"), Component.literal("Mensaje"));
        mensaje.setCharacterLimit(Textos.MAX_TEXTO);
        mensaje.setValue(mensajeTxt);
        addRenderableWidget(mensaje);

        int yy = y + 82 + mh + 4;
        int mitad = (pw - 4) / 2;

        // Tiempo y modo de este reto
        btnTiempo = addRenderableWidget(Button.builder(etiquetaTiempo(), b -> {
            segundos = siguienteTiempo(segundos);
            btnTiempo.setMessage(etiquetaTiempo());
        }).bounds(x0, yy, mitad, 18).build());
        btnModo = addRenderableWidget(Button.builder(etiquetaModo(), b -> {
            cumplido = !cumplido;
            btnModo.setMessage(etiquetaModo());
        }).bounds(x0 + mitad + 4, yy, mitad, 18).build());

        // Aviso SI/NO y guardar
        btnAviso = addRenderableWidget(Button.builder(etiquetaAviso(), b -> {
            aviso = !aviso;
            btnAviso.setMessage(etiquetaAviso());
        }).bounds(x0, yy + 22, mitad, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Guardar"), b -> enviar(PanelAccionPacket.GUARDAR))
                .bounds(x0 + mitad + 4, yy + 22, mitad, 18).build());

        // Lanzar / Solo aviso / Detener
        int tercio = (pw - 8) / 3;
        addRenderableWidget(Button.builder(
                Component.literal("Lanzar").withStyle(Style.EMPTY.withColor(0x55FF55)),
                b -> enviar(PanelAccionPacket.LANZAR)).bounds(x0, yy + 44, tercio, 18).build());
        addRenderableWidget(Button.builder(
                Component.literal("Solo aviso").withStyle(Style.EMPTY.withColor(0x55FFFF)),
                b -> enviar(PanelAccionPacket.SOLO_AVISO)).bounds(x0 + tercio + 4, yy + 44, tercio, 18).build());
        addRenderableWidget(Button.builder(
                Component.literal("Detener").withStyle(Style.EMPTY.withColor(0xFF5555)),
                b -> enviar(PanelAccionPacket.DETENER)).bounds(x0 + 2 * (tercio + 4), yy + 44, tercio, 18).build());

        addRenderableWidget(Button.builder(Component.literal("Cerrar"), b -> onClose())
                .bounds(x0, yy + 66, pw, 18).build());
    }

    private Button botonColor(int x, int y, int w, String[] datos) {
        int rgb = Integer.parseInt(datos[2], 16);
        String codigo = datos[1];
        return Button.builder(
                Component.literal("★ " + datos[0]).withStyle(Style.EMPTY.withColor(rgb)),
                b -> insertar(codigo)).bounds(x, y, w, 16).build();
    }

    // ------------------------------------------------------------------ etiquetas
    private Component etiquetaRuleta(Reto r) {
        int rgb = r.rgb(sel, retos.size());
        String c = r.color() == null ? "" : r.color();
        String nombre;
        if (c.isEmpty()) {
            nombre = "Auto";
        } else if (Colores.nombres().contains(c)) {
            nombre = Character.toUpperCase(c.charAt(0)) + c.substring(1);
        } else {
            nombre = "#" + c.toUpperCase();
        }
        return Component.literal("Ruleta: " + nombre + "  (" + (sel + 1) + "/" + retos.size() + ")")
                .withStyle(Style.EMPTY.withColor(rgb));
    }

    private Component etiquetaTiempo() {
        return Component.literal("Tiempo: " + (segundos > 0 ? segundos + " s" : "sin"));
    }

    private Component etiquetaModo() {
        return Component.literal("Modo: " + (cumplido ? "/cumplido" : "solo aviso"));
    }

    private Component etiquetaAviso() {
        return aviso
                ? Component.literal("Aviso: SÍ").withStyle(Style.EMPTY.withColor(0x55FF55))
                : Component.literal("Aviso: NO").withStyle(Style.EMPTY.withColor(0xFF5555));
    }

    private static int siguienteTiempo(int actual) {
        if (actual <= 0) return 30;
        int[] opciones = {30, 60, 120, 300};
        for (int o : opciones) {
            if (o > actual) return o;
        }
        return 0; // despues de 300 s vuelve a "sin tiempo"
    }

    // ------------------------------------------------------------------ acciones
    /** Inserta un codigo (como &c) en el mensaje, donde esta el cursor. */
    private void insertar(String codigo) {
        this.setFocused(mensaje);
        for (char c : codigo.toCharArray()) {
            mensaje.charTyped(c, 0);
        }
    }

    private void insertarSalto() {
        this.setFocused(mensaje);
        mensaje.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
    }

    private void enviar(int accion) {
        Net.CHANNEL.send(new PanelAccionPacket(accion, sel, titulo.getValue(), mensaje.getValue(),
                segundos, cumplido, aviso), PacketDistributor.SERVER.noArg());
        if (accion == PanelAccionPacket.LANZAR) onClose();
    }

    // ------------------------------------------------------------------ dibujo
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(this.font, this.title, this.width / 2, tituloY, 0xFFFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
