package com.ruleta.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
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

import java.util.List;
import java.util.Random;

/** Ruleta animada: gira durante 7 segundos y se detiene exactamente en el sector que eligio el servidor. */
public class RuletaScreen extends Screen {

    private static final float DOS_PI = (float) (Math.PI * 2.0);
    private static final long DURACION_MS = 7000L;
    private static final long CIERRE_MS = 2500L;

    private final List<String> retos;
    private final int ganador;
    private final float anguloInicial;
    private final float recorrido;
    private final long inicio = Util.getMillis();

    private float angulo;
    private int ultimoIdx;
    private boolean terminado = false;
    private long finMs = 0L;

    public RuletaScreen(List<String> retos, int ganador) {
        super(Component.literal("Ruleta"));
        this.retos = retos;
        this.ganador = ganador;

        Random azar = new Random();
        int n = retos.size();
        float seg = DOS_PI / n;
        this.anguloInicial = azar.nextFloat() * DOS_PI;

        // Angulo final para que la flecha (arriba) quede dentro del sector ganador.
        float jitter = (azar.nextFloat() - 0.5f) * 0.7f;
        float relObjetivo = (ganador + 0.5f + jitter) * seg;
        float angFinal = (float) (-Math.PI / 2.0 - relObjetivo);
        float dif = (((angFinal - anguloInicial) % DOS_PI) + DOS_PI) % DOS_PI;
        this.recorrido = dif + DOS_PI * (4 + azar.nextInt(2));

        this.angulo = anguloInicial;
        this.ultimoIdx = indiceBajoFlecha();
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("gui.ruleta.cerrar"), b -> onClose())
                .bounds(this.width / 2 - 45, this.height - 28, 90, 20).build());
    }

    /** Indice del sector que esta justo bajo la flecha. */
    private int indiceBajoFlecha() {
        int n = retos.size();
        float seg = DOS_PI / n;
        float rel = (float) ((((-Math.PI / 2.0 - angulo) % DOS_PI) + DOS_PI) % DOS_PI);
        return Math.min(n - 1, (int) (rel / seg));
    }

    @Override
    public void tick() {
        if (terminado && Util.getMillis() - finMs > CIERRE_MS) {
            this.onClose();
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);

        long ahora = Util.getMillis();
        float x = Mth.clamp((ahora - inicio) / (float) DURACION_MS, 0f, 1f);
        float suave = 1f - (float) Math.pow(1f - x, 4);
        angulo = anguloInicial + recorrido * suave;

        if (!terminado) {
            int idx = indiceBajoFlecha();
            if (idx != ultimoIdx) {
                ultimoIdx = idx;
                float vel = recorrido * 4f * (float) Math.pow(1f - x, 3) / (DURACION_MS / 1000f);
                float tono = 0.8f + Math.min(vel / 20f, 1f) * 0.8f;
                Minecraft.getInstance().getSoundManager()
                        .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, tono));
            }
            if (x >= 1f) {
                terminado = true;
                finMs = ahora;
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
            boolean esGanador = terminado && i == ganador;
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
            boolean esGanador = terminado && i == ganador;
            g.pose().pushPose();
            g.pose().translate(cx, cy, 0);
            g.pose().mulPose(Axis.ZP.rotation(medio));
            g.pose().translate(r * 0.22f, -this.font.lineHeight / 2f, 0);
            String txt = this.font.plainSubstrByWidth(retos.get(i), (int) (r * 0.72f));
            g.drawString(this.font, txt, 0, 0, esGanador ? 0x000000 : 0xFFFFFF, !esGanador);
            g.pose().popPose();
        }

        // --- Resultado ---
        if (terminado) {
            int y = cy + r + 14;
            g.drawCenteredString(this.font, Component.translatable("gui.ruleta.resultado"), cx, y, 0xFFD966);
            List<FormattedCharSequence> lineas = this.font.split(
                    Component.literal(retos.get(ganador)), Math.min(this.width - 40, 300));
            y += 12;
            for (FormattedCharSequence linea : lineas) {
                g.drawCenteredString(this.font, linea, cx, y, 0xFFFFFF);
                y += 10;
            }
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
    public boolean isPauseScreen() {
        return false;
    }
}
