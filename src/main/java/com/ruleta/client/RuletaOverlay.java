package com.ruleta.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Ruleta misteriosa dibujada ENCIMA del juego (no es una pantalla): el jugador puede seguir moviendose,
 * mirando y jugando mientras gira. Los sectores solo muestran colores y "?". Entra girando con un destello,
 * gira 6,5 segundos y se detiene exactamente en el sector que eligio el servidor; ahi el sector salta hacia
 * afuera con rayos de luz, ondas y confeti del color elegido, y despues la rueda se encoge y desaparece.
 * El reto lo muestra el aviso. La tecla de ocultar (I) la salta.
 */
public class RuletaOverlay {

    private static final float DOS_PI = (float) (Math.PI * 2.0);
    private static final long DURACION_MS = 6500L;
    private static final long CIERRE_MS = 1800L;   // cuanto se queda la rueda despues de elegir el color
    private static final long SALIDA_MS = 450L;    // animacion de salida
    private static final float ENTRADA_S = 0.7f;   // animacion de entrada
    private static final int BOMBILLAS = 28;

    private static class Particula {
        float x, y, vx, vy, vida;
        int color;
    }

    private static RuletaOverlay actual = null;

    /** Empieza una ruleta nueva (reemplaza la que hubiera en pantalla). */
    public static void iniciar(int[] colores, int ganador, String texto) {
        actual = new RuletaOverlay(colores, ganador, texto);
    }

    /** Salta lo que quede de la ruleta (con su animacion de salida). */
    public static void saltar() {
        if (actual != null) actual.salir();
    }

    public static void render(GuiGraphics g, int w, int h) {
        if (actual == null) return;
        if (!actual.dibujar(g, w, h)) actual = null;
    }

    private final int[] colores;
    private final int ganador;
    private final String textoGanador;
    private final float anguloInicial;
    private final float recorrido;
    private final long inicio = Util.getMillis();
    private final Random azar = new Random();
    private final List<Particula> confeti = new ArrayList<>();

    private int ancho = 0;
    private int alto = 0;
    private float angulo;
    private int ultimoIdx;
    private float flick = 0f;
    private long ultimoFrame = Util.getMillis();
    private boolean terminado = false;
    private long finMs = 0L;
    private boolean saliendo = false;
    private long inicioSalida = 0L;

    private RuletaOverlay(int[] colores, int ganador, String textoGanador) {
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

    private void salir() {
        if (!saliendo) {
            saliendo = true;
            inicioSalida = Util.getMillis();
        }
    }

    private int indiceBajoFlecha() {
        int n = colores.length;
        float seg = DOS_PI / n;
        float rel = (float) ((((-Math.PI / 2.0 - angulo) % DOS_PI) + DOS_PI) % DOS_PI);
        return Math.min(n - 1, (int) (rel / seg));
    }

    private int centroX() {
        return ancho / 2;
    }

    private int centroY() {
        return alto / 2;
    }

    private int radio() {
        return Math.max(36, Math.min(alto / 2 - 40, 110));
    }

    // ------------------------------------------------------------------ dibujo
    /** Devuelve false cuando la ruleta ya termino y hay que quitarla. */
    private boolean dibujar(GuiGraphics g, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        this.ancho = w;
        this.alto = h;

        long ahora = Util.getMillis();
        if (saliendo && ahora - inicioSalida >= SALIDA_MS) return false;
        if (!saliendo && terminado && ahora - finMs > CIERRE_MS) salir();

        float dt = Math.min((ahora - ultimoFrame) / 1000f, 0.1f);
        ultimoFrame = ahora;
        float t = (ahora - inicio) / 1000f;
        float ts = saliendo ? Mth.clamp((ahora - inicioSalida) / (float) SALIDA_MS, 0f, 1f) : 0f;

        // La rueda entra girando hacia atras y se acomoda; al salir da media vuelta mas
        float x = Mth.clamp((ahora - inicio) / (float) DURACION_MS, 0f, 1f);
        float suave = 1f - (float) Math.pow(1f - x, 4);
        float entradaK = Mth.clamp(t / ENTRADA_S, 0f, 1f);
        float giroEntrada = (1f - easeOutCubic(entradaK)) * (float) (-Math.PI * 1.2);
        angulo = anguloInicial + recorrido * suave + giroEntrada + ts * ts * (float) Math.PI * 1.1f;

        flick = Math.max(0f, flick - dt * 5f);
        if (!terminado) {
            int idx = indiceBajoFlecha();
            if (idx != ultimoIdx) {
                ultimoIdx = idx;
                flick = 1f;
                float vel = recorrido * 4f * (float) Math.pow(1f - x, 3) / (DURACION_MS / 1000f);
                float tono = 0.8f + Math.min(vel / 20f, 1f) * 0.8f;
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, tono));
            }
            if (x >= 1f) {
                terminado = true;
                finMs = ahora;
                flick = 1.6f;
                lanzarConfeti();
            }
        }
        float tr = terminado ? (ahora - finMs) / 1000f : -1f;
        actualizarConfeti(dt);

        int cx = centroX();
        int cy = centroY();
        int r = radio();
        int rgbGan = colores[ganador] & 0xFFFFFF;

        // Destello suave del color elegido
        if (terminado && tr < 0.4f) {
            g.fill(0, 0, ancho, alto, argb((1f - tr / 0.4f) * 0.22f, rgbGan));
        }

        // Entra con rebote, "late" al elegir el color y se encoge al salir
        float escala = easeOutBack(Mth.clamp(t / 0.6f, 0f, 1f));
        if (terminado && tr < 0.4f) escala *= 1f + 0.07f * (float) Math.sin(tr / 0.4f * Math.PI);
        if (saliendo) escala *= 1f - easeInBack(ts);
        escala = Math.max(0.01f, escala);

        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().scale(escala, escala, 1f);
        g.pose().translate(-cx, -cy, 0);

        Matrix4f m = g.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        dibujarRueda(m, cx, cy, r, t, tr, ts);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        dibujarSignos(g, mc.font, cx, cy, r, tr);
        g.pose().popPose();

        // Confeti
        for (Particula p : confeti) {
            int alpha = (int) (Mth.clamp(p.vida / 0.8f, 0f, 1f) * 255f * (1f - ts));
            if (alpha < 4) continue;
            g.fill((int) p.x, (int) p.y, (int) p.x + 4, (int) p.y + 3, (alpha << 24) | p.color);
        }
        return true;
    }

    private void dibujarRueda(Matrix4f m, int cx, int cy, int r, float t, float tr, float ts) {
        int n = colores.length;
        float seg = DOS_PI / n;
        boolean rev = terminado && tr >= 0f;
        int rgbGan = colores[ganador] & 0xFFFFFF;

        // Rayos de luz hacia el sector elegido (detras de la rueda)
        if (rev) {
            float fuerza = Mth.clamp(tr / 0.12f, 0f, 1f) * Mth.clamp(1f - tr / 1.7f, 0f, 1f);
            if (fuerza > 0.01f) {
                float medio = angulo + (ganador + 0.5f) * seg;
                float largo = r * 2.6f;
                sector(m, cx, cy, 0, largo, medio - seg * 0.9f, medio + seg * 0.9f,
                        ((int) (fuerza * 150) << 24) | rgbGan, rgbGan, 14);
                sector(m, cx, cy, 0, largo, medio - seg * 0.35f, medio + seg * 0.35f,
                        ((int) (fuerza * 120) << 24) | 0xFFFFFF, 0xFFFFFF, 14);
            }
        }

        float dim = rev ? 1f - 0.45f * Mth.clamp(tr / 0.4f, 0f, 1f) : 1f;

        // Sombra y aro dorado
        sector(m, cx + 3, cy + 4, 0, r + 13, 0, DOS_PI, 0x66000000, 0x66000000, 64);
        sector(m, cx, cy, r, r + 10, 0, DOS_PI, 0xFF7A5A12, 0xFFF2D16B, 64);
        sector(m, cx, cy, r + 10, r + 12, 0, DOS_PI, 0xFF3A2A05, 0xFF3A2A05, 64);

        // Sectores con degradado desde el centro
        int pasos = Math.max(2, (int) Math.ceil(seg / 0.1f));
        for (int i = 0; i < n; i++) {
            if (rev && i == ganador) continue;
            int base = colores[i];
            float a0 = angulo + i * seg;
            sector(m, cx, cy, 0, r, a0, a0 + seg, mul(base, 0.5f * dim), mul(base, 1.0f * dim), pasos);
        }

        // El sector elegido salta hacia afuera y brilla
        if (rev) {
            float pop = 10f * easeOutBack(Mth.clamp(tr / 0.3f, 0f, 1f));
            float pulso = 1f + 0.22f * (float) Math.sin(tr * 9f);
            float a0 = angulo + ganador * seg;
            int base = colores[ganador];
            sector(m, cx, cy, 0, r + pop, a0, a0 + seg, mul(base, 0.5f * pulso), mul(base, 1.0f * pulso), pasos);
            sector(m, cx, cy, r + pop - 2, r + pop + 1, a0, a0 + seg, 0xFFFFFFFF, 0xFFFFFFFF, pasos);
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

        // Bombillas del aro: se encienden una a una al entrar, corren mientras gira y parpadean con el color elegido
        int visibles = (int) (BOMBILLAS * Mth.clamp(t / 0.8f, 0f, 1f));
        int luz = rev ? mul(colores[ganador], 1.25f) : 0xFFFFF6C8;
        int brillo = 0x55000000 | (luz & 0xFFFFFF);
        for (int k = 0; k < visibles; k++) {
            float a = (float) (-Math.PI / 2.0) + k * DOS_PI / BOMBILLAS;
            float bx = cx + Mth.cos(a) * (r + 5);
            float by = cy + Mth.sin(a) * (r + 5);
            boolean enc;
            if (saliendo) {
                enc = false;
            } else if (rev) {
                enc = (((int) (tr * 6f)) + k) % 2 == 0;
            } else {
                enc = (k + (int) (t * 16f)) % 3 == 0;
            }
            if (enc) {
                sector(m, bx, by, 0, 5.5f, 0, DOS_PI, brillo, brillo, 14);
                sector(m, bx, by, 0, 2.6f, 0, DOS_PI, luz, luz, 10);
            } else {
                sector(m, bx, by, 0, 2.4f, 0, DOS_PI, 0xFF6B4E10, 0xFF6B4E10, 10);
            }
        }

        // Centro
        sector(m, cx, cy, 0, 15, 0, DOS_PI, 0xFF2A1F05, 0xFF2A1F05, 30);
        sector(m, cx, cy, 0, 13, 0, DOS_PI, 0xFFD4A82A, 0xFFD4A82A, 30);
        sector(m, cx, cy, 0, 9, 0, DOS_PI, 0xFF3A2A05, 0xFF3A2A05, 24);
        sector(m, cx, cy, 0, 4, 0, DOS_PI, 0xFFFFF0B0, 0xFFFFF0B0, 16);

        // Flecha dorada: cae al entrar, se mueve con cada sector y sube al salir
        float caida = 1f - easeOutBack(Mth.clamp((t - 0.2f) / 0.5f, 0f, 1f));
        float px = cx;
        float py = cy - r - 12 - caida * 40f - ts * 40f;
        float ang = flick * 0.45f;
        float cs = Mth.cos(ang);
        float sn = Mth.sin(ang);
        float[][] pts = {{0f, 24f}, {-10f, -4f}, {10f, -4f}};
        triangulo(m, px, py, pts, 1.3f, cs, sn, 0xFF3A2A05);
        triangulo(m, px, py, pts, 1.0f, cs, sn, 0xFFFFD54A);
        sector(m, px, py, 0, 3f, 0, DOS_PI, 0xFFFFFFFF, 0xFFFFFFFF, 12);

        // Onda dorada al entrar
        if (t < 0.7f) {
            float k = t / 0.7f;
            float rad = r * (0.4f + 1.3f * easeOutCubic(k));
            int alfa = (int) ((1f - k) * 150);
            anillo(m, cx, cy, rad, 3f + 6f * (1f - k), (alfa << 24) | 0xFFE08A);
        }
        // Ondas del color elegido
        if (rev) {
            for (int j = 0; j < 2; j++) {
                float kk = Mth.clamp((tr - j * 0.18f) / 0.9f, 0f, 1f);
                if (kk > 0f && kk < 1f) {
                    float rad = r * (0.2f + 1.9f * easeOutCubic(kk));
                    int alfa = (int) ((1f - kk) * 180);
                    anillo(m, cx, cy, rad, 6f, (alfa << 24) | rgbGan);
                }
            }
        }
    }

    private void dibujarSignos(GuiGraphics g, Font font, int cx, int cy, int r, float tr) {
        int n = colores.length;
        float seg = DOS_PI / n;
        boolean rev = terminado && tr >= 0f;
        float pop = rev ? 10f * easeOutBack(Mth.clamp(tr / 0.3f, 0f, 1f)) : 0f;
        float base = Mth.clamp(r / 45f, 0.9f, 2.2f) * (n > 14 ? 0.7f : 1f);
        for (int i = 0; i < n; i++) {
            float medio = angulo + (i + 0.5f) * seg;
            boolean gana = rev && i == ganador;
            String s = gana ? "!" : "?";
            int rgb = colores[i];
            float lum = (0.299f * ((rgb >> 16) & 255) + 0.587f * ((rgb >> 8) & 255) + 0.114f * (rgb & 255)) / 255f;
            boolean claro = lum > 0.65f;
            float tam = gana ? base * (1.4f + 0.25f * (float) Math.sin(tr * 8f)) : base;
            float dist = r * 0.66f + (gana ? pop * 0.4f : 0f);
            g.pose().pushPose();
            g.pose().translate(cx, cy, 0);
            g.pose().mulPose(Axis.ZP.rotation(medio + (float) (Math.PI / 2.0)));
            g.pose().translate(0, -dist, 0);
            g.pose().scale(tam, tam, 1f);
            g.drawString(font, s, -font.width(s) / 2, -font.lineHeight / 2,
                    claro ? 0xFF202020 : 0xFFFFFFFF, !claro);
            g.pose().popPose();
        }
    }

    // ------------------------------------------------------------------ confeti
    private void lanzarConfeti() {
        int cx = centroX();
        int cy = centroY();
        int ganadorRgb = colores[ganador] & 0xFFFFFF;
        for (int i = 0; i < 90; i++) {
            Particula p = new Particula();
            p.x = cx;
            p.y = cy;
            float a = azar.nextFloat() * DOS_PI;
            float v = 120f + azar.nextFloat() * 240f;
            p.vx = Mth.cos(a) * v;
            p.vy = Mth.sin(a) * v - 160f;
            p.vida = 1.8f + azar.nextFloat() * 1.2f;
            if (azar.nextInt(10) < 6) {
                p.color = mul(ganadorRgb, 0.8f + azar.nextFloat() * 0.5f) & 0xFFFFFF;
            } else {
                p.color = colores[azar.nextInt(colores.length)] & 0xFFFFFF;
            }
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
            if (p.vida <= 0f || p.y > alto + 10) it.remove();
        }
    }

    // ------------------------------------------------------------------ utilidades de dibujo
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

    private static void anillo(Matrix4f m, float cx, float cy, float rad, float ancho, int argb) {
        int pasos = Math.min(120, Math.max(32, (int) (rad / 2f)));
        sector(m, cx, cy, rad, rad + ancho, 0, DOS_PI, argb, argb, pasos);
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

    private static int argb(float a, int rgb) {
        int al = (int) (Mth.clamp(a, 0f, 1f) * 255f);
        return (al << 24) | (rgb & 0xFFFFFF);
    }

    private static float easeOutBack(float x) {
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        float y = x - 1f;
        return 1f + c3 * y * y * y + c1 * y * y;
    }

    private static float easeInBack(float x) {
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        return c3 * x * x * x - c1 * x * x;
    }

    private static float easeOutCubic(float x) {
        float y = 1f - x;
        return 1f - y * y * y;
    }
}
