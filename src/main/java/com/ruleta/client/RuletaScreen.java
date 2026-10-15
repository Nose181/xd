package com.ruleta.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import com.ruleta.Textos;
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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Ruleta misteriosa: los sectores solo muestran colores y "?". Gira 6,5 segundos y se detiene
 * exactamente en el sector que eligio el servidor; recien ahi se revela el reto.
 */
public class RuletaScreen extends Screen {

    private static final float DOS_PI = (float) (Math.PI * 2.0);
    private static final long DURACION_MS = 6500L;
    private static final long CIERRE_MS = 2000L;
    private static final int BOMBILLAS = 28;

    private static class Particula {
        float x, y, vx, vy, vida;
        int color;
    }

    private final int[] colores;
    private final int ganador;
    private final String textoGanador;
    private final float anguloInicial;
    private final float recorrido;
    private final long inicio = Util.getMillis();
    private final Random azar = new Random();
    private final List<Particula> confeti = new ArrayList<>();

    private float angulo;
    private int ultimoIdx;
    private float flick = 0f;
    private long ultimoFrame = Util.getMillis();
    private boolean terminado = false;
    private long finMs = 0L;

    public RuletaScreen(int[] colores, int ganador, String textoGanador) {
        super(Component.literal("Ruleta"));
        this.colores = colores;
        this.ganador = ganador;
        this.textoGanador = textoGanador;

        int n = colores.length;
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
                .bounds(this.width - 70, 8, 62, 20).build());
    }

    private int indiceBajoFlecha() {
        int n = colores.length;
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

    private int centroX() {
        return this.width / 2;
    }

    private int centroY() {
        return this.height / 2 - 14;
    }

    private int radio() {
        return Math.max(40, Math.min(this.height / 2 - 70, 120));
    }

    // ------------------------------------------------------------------ render
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long ahora = Util.getMillis();
        float dt = Math.min((ahora - ultimoFrame) / 1000f, 0.1f);
        ultimoFrame = ahora;
        float t = (ahora - inicio) / 1000f;

        float x = Mth.clamp((ahora - inicio) / (float) DURACION_MS, 0f, 1f);
        float suave = 1f - (float) Math.pow(1f - x, 4);
        angulo = anguloInicial + recorrido * suave;

        flick = Math.max(0f, flick - dt * 5f);
        if (!terminado) {
            int idx = indiceBajoFlecha();
            if (idx != ultimoIdx) {
                ultimoIdx = idx;
                flick = 1f;
                float vel = recorrido * 4f * (float) Math.pow(1f - x, 3) / (DURACION_MS / 1000f);
                float tono = 0.8f + Math.min(vel / 20f, 1f) * 0.8f;
                Minecraft.getInstance().getSoundManager()
                        .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, tono));
            }
            if (x >= 1f) {
                terminado = true;
                finMs = ahora;
                lanzarConfeti();
            }
        }
        actualizarConfeti(dt);

        int cx = centroX();
        int cy = centroY();
        int r = radio();

        // Fondo que se oscurece suavemente
        float fondo = Mth.clamp(t / 0.4f, 0f, 1f);
        g.fill(0, 0, this.width, this.height, ((int) (fondo * 190) << 24) | 0x0A0A18);

        // La rueda aparece creciendo con un pequeño rebote
        float escala = Math.max(0.01f, easeOutBack(Mth.clamp(t / 0.5f, 0f, 1f)));
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().scale(escala, escala, 1f);
        g.pose().translate(-cx, -cy, 0);

        Matrix4f m = g.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        dibujarRueda(m, cx, cy, r, t);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        dibujarSignos(g, cx, cy, r);

        String titulo = "RULETA DE RETOS";
        int colTitulo = 0xFF000000 | Mth.hsvToRgb((t * 0.15f) % 1f, 0.45f, 1f);
        g.pose().pushPose();
        g.pose().translate(cx, cy - r - 40, 0);
        g.pose().scale(2f, 2f, 1f);
        g.drawString(this.font, titulo, -this.font.width(titulo) / 2, 0, colTitulo, true);
        g.pose().popPose();

        g.pose().popPose();

        if (terminado) dibujarResultado(g, cx, cy, r, ahora);

        // Confeti
        for (Particula p : confeti) {
            int alpha = (int) (Mth.clamp(p.vida / 0.8f, 0f, 1f) * 255f);
            g.fill((int) p.x, (int) p.y, (int) p.x + 4, (int) p.y + 3, (alpha << 24) | p.color);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void dibujarRueda(Matrix4f m, int cx, int cy, int r, float t) {
        int n = colores.length;
        float seg = DOS_PI / n;
        long ahora = Util.getMillis();
        float dim = terminado ? 1f - 0.45f * Mth.clamp((ahora - finMs) / 400f, 0f, 1f) : 1f;
        float pulso = terminado ? 1f + 0.25f * (float) Math.sin((ahora - finMs) / 1000f * 9f) : 1f;

        // Sombra y aro dorado
        sector(m, cx + 3, cy + 4, 0, r + 13, 0, DOS_PI, 0x66000000, 0x66000000, 64);
        sector(m, cx, cy, r, r + 10, 0, DOS_PI, 0xFF7A5A12, 0xFFF2D16B, 64);
        sector(m, cx, cy, r + 10, r + 12, 0, DOS_PI, 0xFF3A2A05, 0xFF3A2A05, 64);

        // Sectores con degradado desde el centro
        for (int i = 0; i < n; i++) {
            boolean gana = terminado && i == ganador;
            float f = gana ? pulso : dim;
            int base = colores[i];
            float a0 = angulo + i * seg;
            int pasos = Math.max(2, (int) Math.ceil(seg / 0.1f));
            sector(m, cx, cy, 0, r, a0, a0 + seg, mul(base, 0.5f * f), mul(base, 1.0f * f), pasos);
        }

        // Lineas separadoras
        BufferBuilder b = comenzar(VertexFormat.Mode.QUADS);
        for (int i = 0; i < n; i++) {
            float a = angulo + i * seg;
            float c = Mth.cos(a);
            float s = Mth.sin(a);
            float nx = -s * 1.1f;
            float ny = c * 1.1f;
            int col = 0xAAF5E6B0;
            vert(b, m, cx + nx, cy + ny, col);
            vert(b, m, cx + c * r + nx, cy + s * r + ny, col);
            vert(b, m, cx + c * r - nx, cy + s * r - ny, col);
            vert(b, m, cx - nx, cy - ny, col);
        }
        dibujar(b);

        // Bombillas del aro: corren mientras gira y parpadean al terminar
        for (int k = 0; k < BOMBILLAS; k++) {
            float a = k * DOS_PI / BOMBILLAS;
            float bx = cx + Mth.cos(a) * (r + 5);
            float by = cy + Mth.sin(a) * (r + 5);
            boolean enc = terminado ? (((int) (t * 5f)) + k) % 2 == 0 : (k + (int) (t * 16f)) % 3 == 0;
            if (enc) {
                sector(m, bx, by, 0, 5.5f, 0, DOS_PI, 0x55FFE08A, 0x55FFE08A, 14);
                sector(m, bx, by, 0, 2.6f, 0, DOS_PI, 0xFFFFF6C8, 0xFFFFF6C8, 10);
            } else {
                sector(m, bx, by, 0, 2.4f, 0, DOS_PI, 0xFF6B4E10, 0xFF6B4E10, 10);
            }
        }

        // Centro
        sector(m, cx, cy, 0, 15, 0, DOS_PI, 0xFF2A1F05, 0xFF2A1F05, 30);
        sector(m, cx, cy, 0, 13, 0, DOS_PI, 0xFFD4A82A, 0xFFD4A82A, 30);
        sector(m, cx, cy, 0, 9, 0, DOS_PI, 0xFF3A2A05, 0xFF3A2A05, 24);
        sector(m, cx, cy, 0, 4, 0, DOS_PI, 0xFFFFF0B0, 0xFFFFF0B0, 16);

        // Flecha dorada que se mueve cada vez que pasa un sector
        float px = cx;
        float py = cy - r - 12;
        float ang = flick * 0.45f;
        float cs = Mth.cos(ang);
        float sn = Mth.sin(ang);
        float[][] pts = {{0f, 24f}, {-10f, -4f}, {10f, -4f}};
        triangulo(m, px, py, pts, 1.3f, cs, sn, 0xFF3A2A05);
        triangulo(m, px, py, pts, 1.0f, cs, sn, 0xFFFFD54A);
        sector(m, px, py, 0, 3f, 0, DOS_PI, 0xFFFFFFFF, 0xFFFFFFFF, 12);
    }

    private void dibujarSignos(GuiGraphics g, int cx, int cy, int r) {
        int n = colores.length;
        float seg = DOS_PI / n;
        float tam = Mth.clamp(r / 45f, 0.9f, 2.2f) * (n > 14 ? 0.7f : 1f);
        for (int i = 0; i < n; i++) {
            float medio = angulo + (i + 0.5f) * seg;
            boolean gana = terminado && i == ganador;
            String s = gana ? "!" : "?";
            int rgb = colores[i];
            float lum = (0.299f * ((rgb >> 16) & 255) + 0.587f * ((rgb >> 8) & 255) + 0.114f * (rgb & 255)) / 255f;
            boolean claro = lum > 0.65f;
            g.pose().pushPose();
            g.pose().translate(cx, cy, 0);
            g.pose().mulPose(Axis.ZP.rotation(medio + (float) (Math.PI / 2.0)));
            g.pose().translate(0, -r * 0.66f, 0);
            g.pose().scale(tam, tam, 1f);
            g.drawString(this.font, s, -this.font.width(s) / 2, -this.font.lineHeight / 2,
                    claro ? 0xFF202020 : 0xFFFFFFFF, !claro);
            g.pose().popPose();
        }
    }

    private void dibujarResultado(GuiGraphics g, int cx, int cy, int r, long ahora) {
        float k = Mth.clamp((ahora - finMs) / 350f, 0f, 1f);
        int a = (int) (k * 255f);
        if (a < 8) return;
        int pw = Math.min(this.width - 40, 320);
        List<FormattedCharSequence> lineas = this.font.split(Component.literal(Textos.colorear(textoGanador)), pw - 20);
        if (lineas.size() > 5) lineas = lineas.subList(0, 5);
        int ph = 28 + lineas.size() * 10;
        int x0 = cx - pw / 2;
        int y0 = cy + r + 20 + (int) ((1f - k) * 14f);
        int borde = (a << 24) | (colores[ganador] & 0xFFFFFF);

        g.fill(x0, y0, x0 + pw, y0 + ph, ((a * 3 / 4) << 24) | 0x101018);
        g.fill(x0, y0, x0 + pw, y0 + 2, borde);
        g.fill(x0, y0 + ph - 2, x0 + pw, y0 + ph, borde);
        g.fill(x0, y0, x0 + 2, y0 + ph, borde);
        g.fill(x0 + pw - 2, y0, x0 + pw, y0 + ph, borde);

        g.drawCenteredString(this.font, Component.translatable("gui.ruleta.resultado"), cx, y0 + 7,
                (a << 24) | 0xFFD966);
        int y = y0 + 19;
        for (FormattedCharSequence linea : lineas) {
            g.drawCenteredString(this.font, linea, cx, y, (a << 24) | 0xFFFFFF);
            y += 10;
        }
    }

    // ------------------------------------------------------------------ confeti
    private void lanzarConfeti() {
        int cx = centroX();
        int cy = centroY();
        for (int i = 0; i < 80; i++) {
            Particula p = new Particula();
            p.x = cx;
            p.y = cy;
            float a = azar.nextFloat() * DOS_PI;
            float v = 120f + azar.nextFloat() * 220f;
            p.vx = Mth.cos(a) * v;
            p.vy = Mth.sin(a) * v - 160f;
            p.vida = 1.8f + azar.nextFloat() * 1.2f;
            p.color = colores[azar.nextInt(colores.length)] & 0xFFFFFF;
            confeti.add(p);
        }
    }

    private void actualizarConfeti(float dt) {
        Iterator<Particula> it = confeti.iterator();
        while (it.hasNext()) {
            Particula p = it.next();
            p.vy += 420f * dt;
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.vida -= dt;
            if (p.vida <= 0f || p.y > this.height + 10) it.remove();
        }
    }

    // ------------------------------------------------------------------ dibujo
    private static BufferBuilder comenzar(VertexFormat.Mode modo) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        return Tesselator.getInstance().begin(modo, DefaultVertexFormat.POSITION_COLOR);
    }

    private static void dibujar(BufferBuilder b) {
        BufferUploader.drawWithShader(b.buildOrThrow());
    }

    /** Sector circular (o anillo) con degradado de color entre el borde interior y el exterior. */
    private static void sector(Matrix4f m, float cx, float cy, float rIn, float rOut,
                               float a0, float a1, int cIn, int cOut, int pasos) {
        BufferBuilder b = comenzar(VertexFormat.Mode.TRIANGLE_STRIP);
        for (int k = 0; k <= pasos; k++) {
            float a = a0 + (a1 - a0) * k / pasos;
            float c = Mth.cos(a);
            float s = Mth.sin(a);
            vert(b, m, cx + c * rIn, cy + s * rIn, cIn);
            vert(b, m, cx + c * rOut, cy + s * rOut, cOut);
        }
        dibujar(b);
    }

    private static void triangulo(Matrix4f m, float px, float py, float[][] pts, float esc,
                                  float cs, float sn, int argb) {
        BufferBuilder b = comenzar(VertexFormat.Mode.TRIANGLES);
        for (float[] p : pts) {
            float x = p[0] * esc;
            float y = p[1] * esc;
            vert(b, m, px + x * cs - y * sn, py + x * sn + y * cs, argb);
        }
        dibujar(b);
    }

    private static void vert(BufferBuilder b, Matrix4f m, float x, float y, int argb) {
        b.addVertex(m, x, y, 0f).setColor(argb);
    }

    private static int mul(int rgb, float f) {
        int r = Mth.clamp((int) (((rgb >> 16) & 255) * f), 0, 255);
        int g = Mth.clamp((int) (((rgb >> 8) & 255) * f), 0, 255);
        int b = Mth.clamp((int) ((rgb & 255) * f), 0, 255);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static float easeOutBack(float x) {
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        float y = x - 1f;
        return 1f + c3 * y * y * y + c1 * y * y;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // El fondo oscuro lo dibuja render() con su propia animacion.
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
