package com.ruleta.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import com.ruleta.net.AnunciarRetoPacket;
import com.ruleta.net.Net;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Random;

public class RuletaScreen extends Screen {

    private static final float DOS_PI = (float) (Math.PI * 2.0);

    private final List<String> retos = RetosConfig.cargar();
    private final Random azar = new Random();

    private float angulo = azar.nextFloat() * DOS_PI;
    private float velocidad = 0f;
    private boolean girando = false;
    private int ganador = -1;
    private int ultimoIdx = -1;
    private String resultado = null;
    private long ultimo = Util.getMillis();
    private Button botonGirar;
    private final boolean girarAlAbrir;
    private boolean iniciado = false;

    public RuletaScreen() {
        this(false);
    }

    public RuletaScreen(boolean girarAlAbrir) {
        super(Component.literal("Ruleta"));
        this.girarAlAbrir = girarAlAbrir;
    }

    @Override
    protected void init() {
        int ancho = 90;
        botonGirar = addRenderableWidget(Button.builder(Component.translatable("gui.ruleta.girar"), b -> girar())
                .bounds(this.width / 2 - ancho - 4, this.height - 28, ancho, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.ruleta.cerrar"), b -> onClose())
                .bounds(this.width / 2 + 4, this.height - 28, ancho, 20).build());
        botonGirar.active = !girando;
        if (girarAlAbrir && !iniciado) {
            iniciado = true;
            girar();
        }
    }

    private void girar() {
        if (girando) return;
        velocidad = 14f + azar.nextFloat() * 8f;
        girando = true;
        ganador = -1;
        resultado = null;
        if (botonGirar != null) botonGirar.active = false;
        ultimoIdx = indiceBajoFlecha();
    }

    /** Indice del sector que esta justo bajo la flecha. */
    private int indiceBajoFlecha() {
        int n = retos.size();
        float seg = DOS_PI / n;
        float rel = (float) ((((-Math.PI / 2.0 - angulo) % DOS_PI) + DOS_PI) % DOS_PI);
        return Math.min(n - 1, (int) (rel / seg));
    }

    private void terminar() {
        ganador = indiceBajoFlecha();
        resultado = retos.get(ganador);
        if (botonGirar != null) botonGirar.active = true;

        Minecraft mc = Minecraft.getInstance();
        boolean enviado = false;
        // Si el servidor tiene el mod, el reto se anuncia a todos los jugadores.
        if (mc.getConnection() != null && Net.CHANNEL.isRemotePresent(mc.getConnection().getConnection())) {
            Net.CHANNEL.sendToServer(new AnunciarRetoPacket(resultado));
            enviado = true;
        }
        // Si no, se muestra solo para ti.
        if (!enviado) {
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal("§6[Ruleta] §f" + resultado), false);
            }
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f));
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);

        long ahora = Util.getMillis();
        float dt = Math.min((ahora - ultimo) / 1000f, 0.1f);
        ultimo = ahora;
        if (girando) {
            angulo += velocidad * dt;
            velocidad *= (float) Math.exp(-0.8 * dt);
            int idx = indiceBajoFlecha();
            if (idx != ultimoIdx) {
                ultimoIdx = idx;
                float tono = 0.8f + Math.min(velocidad / 20f, 1f) * 0.8f;
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, tono));
            }
            if (velocidad < 0.04f) {
                velocidad = 0f;
                girando = false;
                terminar();
            }
        }

        int cx = this.width / 2;
        int cy = this.height / 2 - 14;
        int r = Math.max(40, Math.min(this.height / 2 - 70, 120));
        int n = retos.size();
        float seg = DOS_PI / n;

        // --- Geometria de la rueda ---
        Matrix4f m = g.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        sector(m, cx, cy, r + 4, 0f, DOS_PI, 0x202020);
        for (int i = 0; i < n; i++) {
            boolean esGanador = !girando && i == ganador;
            int rgb = esGanador
                    ? Mth.hsvToRgb(i / (float) n, 0.45f, 1.0f)
                    : Mth.hsvToRgb(i / (float) n, 0.65f, 0.65f);
            float a0 = angulo + i * seg;
            sector(m, cx, cy, r, a0, a0 + seg, rgb);
        }
        sector(m, cx, cy, 10, 0f, DOS_PI, 0x202020);
        sector(m, cx, cy, 6, 0f, DOS_PI, 0xEEEEEE);

        // Flecha (arriba)
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        b.vertex(m, cx - 9, cy - r - 14, 0).color(255, 255, 255, 255).endVertex();
        b.vertex(m, cx + 9, cy - r - 14, 0).color(255, 255, 255, 255).endVertex();
        b.vertex(m, cx, cy - r + 6, 0).color(255, 255, 255, 255).endVertex();
        Tesselator.getInstance().end();

        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        // --- Textos de cada sector ---
        for (int i = 0; i < n; i++) {
            float medio = angulo + (i + 0.5f) * seg;
            boolean esGanador = !girando && i == ganador;
            g.pose().pushPose();
            g.pose().translate(cx, cy, 0);
            g.pose().mulPose(Axis.ZP.rotation(medio));
            g.pose().translate(r * 0.22f, -this.font.lineHeight / 2f, 0);
            String txt = this.font.plainSubstrByWidth(retos.get(i), (int) (r * 0.72f));
            g.drawString(this.font, txt, 0, 0, esGanador ? 0x000000 : 0xFFFFFF, !esGanador);
            g.pose().popPose();
        }

        // --- Resultado / pista ---
        int y = cy + r + 14;
        if (resultado != null && !girando) {
            g.drawCenteredString(this.font, Component.translatable("gui.ruleta.resultado"), cx, y, 0xFFD966);
            List<FormattedCharSequence> lineas = this.font.split(Component.literal(resultado), Math.min(this.width - 40, 300));
            y += 12;
            for (FormattedCharSequence linea : lineas) {
                g.drawCenteredString(this.font, linea, cx, y, 0xFFFFFF);
                y += 10;
            }
        } else if (!girando) {
            g.drawCenteredString(this.font, Component.translatable("gui.ruleta.pista"), cx, y, 0xAAAAAA);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    /** Dibuja un sector circular relleno (abanico de triangulos). */
    private static void sector(Matrix4f m, float cx, float cy, float radio, float a0, float a1, int rgb) {
        int rr = (rgb >> 16) & 255, gg = (rgb >> 8) & 255, bb = rgb & 255;
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        b.vertex(m, cx, cy, 0).color(rr, gg, bb, 255).endVertex();
        int pasos = Math.max(2, (int) Math.ceil((a1 - a0) / 0.12f));
        for (int k = 0; k <= pasos; k++) {
            float a = a0 + (a1 - a0) * k / pasos;
            b.vertex(m, cx + Mth.cos(a) * radio, cy + Mth.sin(a) * radio, 0).color(rr, gg, bb, 255).endVertex();
        }
        Tesselator.getInstance().end();
    }

    @Override
    public boolean keyPressed(int key, int scancode, int mods) {
        if (key == GLFW.GLFW_KEY_SPACE && !girando) {
            girar();
            return true;
        }
        return super.keyPressed(key, scancode, mods);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
