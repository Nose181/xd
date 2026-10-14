package com.ruleta.client;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Animacion de calavera cuando alguien muere (no bloquea el juego): destello, camara que tiembla,
 * calavera con mandibula que se abre y cierra, ojos que arden, grietas, chispas, ascuas y gotas de sangre.
 */
public class MuerteOverlay {

    private static final String[] CALAVERA = {
            "................",
            ".....XXXXXX.....",
            "...XXXXXXXXXX...",
            "..XXXXXXXXXXXX..",
            "..XXXXXXXXXXXX..",
            ".XXXXXXXXXXXXXX.",
            ".XXX..XXXX..XXX.",
            ".XX....XX....XX.",
            ".XXX..XXXX..XXX.",
            ".XXXXXXXXXXXXXX.",
            "..XXXXX..XXXXX..",
            "...XXXXXXXXXX...",
            "...X.XX.XX.XX...",
            "...XXXXXXXXXX...",
            "....XXXXXXXX....",
            "................"
    };
    /** Desde esta fila hacia abajo es la mandibula (se mueve). */
    private static final int MANDIBULA = 12;
    /** Celdas (columna, fila) de las grietas del craneo. */
    private static final int[][] GRIETAS = {{7, 2}, {7, 3}, {8, 4}, {8, 5}, {9, 5}};

    private static final long DURACION_MS = 3400L;

    private static class Particula {
        float x, y, vx, vy, vida, vidaMax, tam;
        int color;
        int tipo; // 0 = chispa/hueso, 1 = ascua, 2 = gota de sangre
    }

    private static final Random AZAR = new Random();
    private static final List<Particula> particulas = new ArrayList<>();

    private static List<String> nombres = new ArrayList<>();
    private static boolean castigo = false;
    private static long inicio = -1L;
    private static long ultimoMs = 0L;
    private static boolean explosion = false;
    private static float resto = 0f;

    /** Devuelve true si empezo una animacion nueva (false si solo se sumaron nombres a la que ya corria). */
    public static boolean iniciar(List<String> lista, boolean esCastigo) {
        long ahora = Util.getMillis();
        boolean activa = inicio >= 0 && ahora - inicio < 1500L;
        if (activa) {
            for (String n : lista) {
                if (!nombres.contains(n)) nombres.add(n);
            }
            castigo = castigo || esCastigo;
            return false;
        }
        nombres = new ArrayList<>(lista);
        castigo = esCastigo;
        inicio = ahora;
        ultimoMs = ahora;
        explosion = false;
        resto = 0f;
        particulas.clear();
        return true;
    }

    /** 0..1: fuerza del temblor de camara (fuerte al inicio, se apaga en ~1,8 s). */
    public static float intensidadTemblor() {
        if (inicio < 0) return 0f;
        float s = (Util.getMillis() - inicio) / 1000f;
        if (s < 0f || s > 1.8f) return 0f;
        float rampa = Mth.clamp(s / 0.06f, 0f, 1f);
        float decae = 1f - Mth.clamp((s - 0.1f) / 1.7f, 0f, 1f);
        return rampa * decae * decae;
    }

    public static void render(GuiGraphics g, int w, int h) {
        if (inicio < 0) return;
        long ahora = Util.getMillis();
        long ms = ahora - inicio;
        if (ms >= DURACION_MS) {
            inicio = -1L;
            particulas.clear();
            return;
        }
        float dt = Math.min((ahora - ultimoMs) / 1000f, 0.1f);
        ultimoMs = ahora;
        float s = ms / 1000f;
        float total = DURACION_MS / 1000f;
        float fade = Mth.clamp(s / 0.12f, 0f, 1f) * Mth.clamp((total - s) / 0.6f, 0f, 1f);
        float sacudida = intensidadTemblor();
        Font font = Minecraft.getInstance().font;

        int tam = Math.min(w, h) * 40 / 100;
        int cell = Math.max(3, tam / 16);
        int baseY = (int) (h * 0.42f);

        if (!explosion) {
            explosion = true;
            lanzarExplosion(w / 2f, baseY, cell);
        }
        actualizarParticulas(dt, w, h, s);

        // ---- Fondo: tinte rojo, destello blanco, viñeta de sangre y lineas de escaneo
        g.fill(0, 0, w, h, argb(fade * 0.42f, 0x30, 0x00, 0x00));
        if (s < 0.18f) {
            g.fill(0, 0, w, h, argb((1f - s / 0.18f) * 0.65f, 0xFF, 0xFF, 0xFF));
        }
        int e = 6;
        for (int i = 0; i < 10; i++) {
            int col = argb(fade * 0.5f * (1f - i / 10f), 0xA0, 0x00, 0x00);
            g.fill(0, i * e, w, (i + 1) * e, col);
            g.fill(0, h - (i + 1) * e, w, h - i * e, col);
            g.fill(i * e, 0, (i + 1) * e, h, col);
            g.fill(w - (i + 1) * e, 0, w - i * e, h, col);
        }
        int lineaTenue = argb(fade * 0.10f, 0x00, 0x00, 0x00);
        for (int y = 0; y < h; y += 4) {
            g.fill(0, y, w, y + 1, lineaTenue);
        }
        int bandaY = (int) ((s * 420f) % h);
        g.fill(0, bandaY, w, bandaY + 8, argb(fade * 0.07f, 0xFF, 0x30, 0x30));

        // ---- Todo lo demas se sacude junto con la camara
        int shx = (int) (Math.sin(s * 90f) * 6f * sacudida);
        int shy = (int) (Math.cos(s * 77f) * 6f * sacudida);
        g.pose().pushPose();
        g.pose().translate(shx, shy, 0);

        // Calavera: aparece con rebote, tiembla, late y se desvanece subiendo
        float aparece = easeOutBack(Mth.clamp(s / 0.45f, 0f, 1f));
        float latido = 1f + (s > 0.45f ? 0.035f * (float) Math.sin(s * 18f) : 0f);
        float escala = Math.max(0.01f, aparece) * latido;
        float temblor = (s > 0.45f && s < 2.2f) ? 1f : 0f;
        int dx = (int) (Math.sin(s * 47f) * 3 * temblor);
        int dy = (int) (Math.cos(s * 53f) * 3 * temblor) - (s > 2.6f ? (int) ((s - 2.6f) * 45f) : 0);

        // Mandibula: se abre grande al gritar y despues "castañetea"
        int abre = 0;
        if (s > 0.35f && s < 2.8f) {
            float rampa = Mth.clamp((s - 0.35f) / 0.25f, 0f, 1f) * Mth.clamp((2.8f - s) / 0.3f, 0f, 1f);
            float amplitud = 3f + (s < 1.0f ? 1.6f : 0f);
            abre = (int) (rampa * Math.abs((float) Math.sin(s * 11f)) * cell * amplitud);
        }

        g.pose().pushPose();
        g.pose().translate(w / 2 + dx, baseY + dy, 0);
        g.pose().scale(escala, escala, 1f);
        int ox = -8 * cell;
        int oy = -8 * cell;

        // Interior de la boca (oscuro con un poco de rojo)
        if (abre > 0) {
            int bx0 = ox + 3 * cell;
            int bx1 = ox + 13 * cell;
            int by0 = oy + MANDIBULA * cell;
            g.fill(bx0, by0, bx1, by0 + abre, argb(fade * 0.95f, 0x0A, 0x00, 0x00));
            g.fill(bx0 + cell, by0 + abre / 3, bx1 - cell, by0 + abre, argb(fade * 0.35f, 0x80, 0x00, 0x00));
        }

        // Ojos que arden (mas fuerte cuando grita)
        float brillo = 0.55f + 0.45f * (float) Math.sin(s * 14f);
        if (s > 0.35f && s < 1.0f) brillo = Math.min(1f, brillo + 0.4f);
        for (int row = 6; row <= 8; row++) {
            for (int col = 3; col <= 12; col++) {
                if (CALAVERA[row].charAt(col) != '.') continue;
                int x0 = ox + col * cell;
                int y0 = oy + row * cell;
                g.fill(x0, y0, x0 + cell, y0 + cell, argb(fade * brillo, 0xFF, 0x20, 0x20));
                g.fill(x0 + cell / 4, y0 + cell / 4, x0 + cell - cell / 4, y0 + cell - cell / 4,
                        argb(fade * brillo, 0xFF, 0xC0, 0x40));
            }
        }

        // Sombra
        pintarCalavera(g, cell, ox + cell / 2, oy + cell / 2, abre, argb(fade * 0.8f, 0x20, 0x00, 0x00), 0);
        // Aberracion cromatica al inicio (rojo y cian desplazados)
        if (s < 0.7f) {
            int d = Math.max(1, (int) (cell * (0.7f - s)));
            pintarCalavera(g, cell, ox - d, oy, abre, argb(fade * 0.45f, 0xFF, 0x20, 0x20), 0);
            pintarCalavera(g, cell, ox + d, oy, abre, argb(fade * 0.45f, 0x20, 0xE0, 0xFF), 0);
        }
        // Hueso (craneo y mandibula un poco mas oscura)
        pintarCalavera(g, cell, ox, oy, abre, argb(fade, 0xED, 0xE6, 0xD3), argb(fade, 0xD8, 0xCF, 0xB8));
        // Grietas
        for (int[] c : GRIETAS) {
            int x0 = ox + c[0] * cell;
            int y0 = oy + c[1] * cell;
            g.fill(x0, y0, x0 + cell, y0 + cell, argb(fade * 0.75f, 0x5A, 0x45, 0x30));
        }
        g.pose().popPose();

        // Particulas (chispas, ascuas, gotas)
        for (Particula p : particulas) {
            float k = Mth.clamp(p.vida / (p.vidaMax * 0.4f), 0f, 1f);
            int r = (p.color >> 16) & 255;
            int gr = (p.color >> 8) & 255;
            int b = p.color & 255;
            int col = argb(fade * k, r, gr, b);
            int px = (int) p.x;
            int py = (int) p.y;
            int t = Math.max(1, (int) p.tam);
            if (p.tipo == 2) {
                g.fill(px, py, px + 2, py + t * 4, col);
            } else {
                g.fill(px, py, px + t, py + t, col);
            }
        }

        // Textos
        float aTexto = fade * Mth.clamp((s - 0.5f) / 0.3f, 0f, 1f);
        boolean varios = nombres.size() > 1;
        String titulo;
        if (castigo) {
            titulo = varios ? "ELIMINADOS" : "ELIMINADO";
        } else {
            titulo = varios ? "HAN MUERTO" : "HA MUERTO";
        }
        int ondaY = (int) (Math.sin(s * 9f) * 3f);
        texto(g, font, titulo, w / 2 + dx, (int) (h * 0.10f) + dy + ondaY, 3.5f, argb(fade, 0xFF, 0x30, 0x30));

        int y = baseY + 8 * cell + 14;
        int mostrar = Math.min(3, nombres.size());
        for (int i = 0; i < mostrar; i++) {
            int jit = (int) (Math.sin(s * 30f + i * 2f) * 2f * sacudida);
            texto(g, font, nombres.get(i), w / 2 + jit, y, 2f, argb(aTexto, 0xFF, 0xFF, 0xFF));
            y += 22;
        }
        if (nombres.size() > mostrar) {
            texto(g, font, "y " + (nombres.size() - mostrar) + " más", w / 2, y, 1.5f, argb(aTexto, 0xFF, 0xFF, 0xFF));
            y += 18;
        }
        if (castigo) {
            texto(g, font, "no cumplió el reto", w / 2, y + 2, 1.3f, argb(aTexto, 0xDD, 0xAA, 0xAA));
        }

        g.pose().popPose();
    }

    /** Dibuja la calavera con la mandibula desplazada hacia abajo. colorMandibula 0 = usar el mismo color. */
    private static void pintarCalavera(GuiGraphics g, int cell, int ox, int oy, int abre, int color, int colorMandibula) {
        for (int row = 0; row < 16; row++) {
            int cc = (row >= MANDIBULA && colorMandibula != 0) ? colorMandibula : color;
            int desp = row >= MANDIBULA ? abre : 0;
            for (int col = 0; col < 16; col++) {
                if (CALAVERA[row].charAt(col) != 'X') continue;
                int x0 = ox + col * cell;
                int y0 = oy + row * cell + desp;
                g.fill(x0, y0, x0 + cell, y0 + cell, cc);
            }
        }
    }

    // ------------------------------------------------------------ particulas
    private static void lanzarExplosion(float cx, float cy, int cell) {
        for (int i = 0; i < 70; i++) {
            Particula p = new Particula();
            float a = AZAR.nextFloat() * Mth.TWO_PI;
            float v = 90f + AZAR.nextFloat() * 300f;
            p.x = cx;
            p.y = cy;
            p.vx = Mth.cos(a) * v;
            p.vy = Mth.sin(a) * v - 80f;
            p.vidaMax = 0.9f + AZAR.nextFloat() * 0.9f;
            p.vida = p.vidaMax;
            p.tam = 2f + AZAR.nextFloat() * (cell * 0.5f);
            p.tipo = 0;
            p.color = AZAR.nextBoolean() ? 0xEDE6D3 : (AZAR.nextBoolean() ? 0xFF3030 : 0xFF9030);
            particulas.add(p);
        }
    }

    private static void actualizarParticulas(float dt, int w, int h, float s) {
        // Ascuas que suben desde abajo y gotas de sangre que bajan desde arriba
        if (s < 2.9f && particulas.size() < 380) {
            resto += dt * 55f;
            while (resto >= 1f) {
                resto -= 1f;
                Particula p = new Particula();
                if (AZAR.nextInt(4) == 0) {
                    p.tipo = 2;
                    p.x = AZAR.nextFloat() * w;
                    p.y = -4f;
                    p.vx = 0f;
                    p.vy = 50f + AZAR.nextFloat() * 90f;
                    p.tam = 1f;
                    p.color = 0xB00010;
                    p.vidaMax = 1.6f + AZAR.nextFloat();
                } else {
                    p.tipo = 1;
                    p.x = AZAR.nextFloat() * w;
                    p.y = h + 2f;
                    p.vx = (AZAR.nextFloat() - 0.5f) * 30f;
                    p.vy = -(50f + AZAR.nextFloat() * 120f);
                    p.tam = 1f + AZAR.nextFloat() * 2f;
                    p.color = AZAR.nextBoolean() ? 0xFF5020 : 0xFFA030;
                    p.vidaMax = 1.4f + AZAR.nextFloat() * 1.2f;
                }
                p.vida = p.vidaMax;
                particulas.add(p);
            }
        }
        Iterator<Particula> it = particulas.iterator();
        while (it.hasNext()) {
            Particula p = it.next();
            if (p.tipo == 0) {
                p.vy += 380f * dt;
            } else if (p.tipo == 1) {
                p.vx += (float) Math.sin(p.y * 0.05f) * 40f * dt;
            } else {
                p.vy += 120f * dt;
            }
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.vida -= dt;
            if (p.vida <= 0f || p.y > h + 40 || p.y < -40) it.remove();
        }
    }

    // ------------------------------------------------------------ utilidades
    private static void texto(GuiGraphics g, Font font, String s, int x, int y, float esc, int argb) {
        if ((argb >>> 24) < 8) return;
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(esc, esc, 1f);
        g.drawString(font, s, -font.width(s) / 2, 0, argb, true);
        g.pose().popPose();
    }

    private static int argb(float a, int r, int gr, int b) {
        int al = (int) (Mth.clamp(a, 0f, 1f) * 255f);
        return (al << 24) | (r << 16) | (gr << 8) | b;
    }

    private static float easeOutBack(float x) {
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        float y = x - 1f;
        return 1f + c3 * y * y * y + c1 * y * y;
    }
}
