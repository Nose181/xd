package com.ruleta.client;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/** Animacion de calavera que aparece en pantalla cuando alguien muere (no bloquea el juego). */
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
            "...XXXXXXXXXX...",
            "...X.XX.XX.XX...",
            "....XXXXXXXX....",
            "................"
    };

    private static final long DURACION_MS = 3200L;

    private static List<String> nombres = new ArrayList<>();
    private static boolean castigo = false;
    private static long inicio = -1L;

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
        return true;
    }

    public static void render(GuiGraphics g, int w, int h) {
        if (inicio < 0) return;
        long ms = Util.getMillis() - inicio;
        if (ms >= DURACION_MS) {
            inicio = -1L;
            return;
        }
        float s = ms / 1000f;
        float total = DURACION_MS / 1000f;
        float fade = Mth.clamp(s / 0.15f, 0f, 1f) * Mth.clamp((total - s) / 0.6f, 0f, 1f);
        Font font = Minecraft.getInstance().font;

        // Tinte rojo y viñeta de sangre en los bordes
        g.fill(0, 0, w, h, argb(fade * 0.42f, 0x30, 0x00, 0x00));
        int e = 6;
        for (int i = 0; i < 10; i++) {
            int col = argb(fade * 0.5f * (1f - i / 10f), 0xA0, 0x00, 0x00);
            g.fill(0, i * e, w, (i + 1) * e, col);
            g.fill(0, h - (i + 1) * e, w, h - i * e, col);
            g.fill(i * e, 0, (i + 1) * e, h, col);
            g.fill(w - (i + 1) * e, 0, w - i * e, h, col);
        }

        // Calavera: aparece con rebote, tiembla, late y se desvanece subiendo
        int tam = Math.min(w, h) * 40 / 100;
        int cell = Math.max(3, tam / 16);
        float aparece = easeOutBack(Mth.clamp(s / 0.45f, 0f, 1f));
        float latido = 1f + (s > 0.45f ? 0.035f * (float) Math.sin(s * 18f) : 0f);
        float escala = Math.max(0.01f, aparece) * latido;
        float temblor = (s > 0.45f && s < 2.2f) ? 1f : 0f;
        int dx = (int) (Math.sin(s * 47f) * 3 * temblor);
        int dy = (int) (Math.cos(s * 53f) * 3 * temblor) - (s > 2.4f ? (int) ((s - 2.4f) * 45f) : 0);
        int baseY = (int) (h * 0.42f);

        g.pose().pushPose();
        g.pose().translate(w / 2 + dx, baseY + dy, 0);
        g.pose().scale(escala, escala, 1f);
        int ox = -8 * cell;
        int oy = -8 * cell;

        float brillo = 0.55f + 0.45f * (float) Math.sin(s * 14f);
        for (int row = 0; row < 16; row++) {
            for (int col = 0; col < 16; col++) {
                int x0 = ox + col * cell;
                int y0 = oy + row * cell;
                char c = CALAVERA[row].charAt(col);
                if (c == 'X') {
                    // sombra
                    g.fill(x0 + cell / 2, y0 + cell / 2, x0 + cell / 2 + cell, y0 + cell / 2 + cell,
                            argb(fade * 0.8f, 0x20, 0x00, 0x00));
                } else if (row >= 6 && row <= 8 && col >= 3 && col <= 12) {
                    // ojos que brillan
                    g.fill(x0, y0, x0 + cell, y0 + cell, argb(fade * brillo, 0xFF, 0x20, 0x20));
                }
            }
        }
        for (int row = 0; row < 16; row++) {
            for (int col = 0; col < 16; col++) {
                if (CALAVERA[row].charAt(col) != 'X') continue;
                int x0 = ox + col * cell;
                int y0 = oy + row * cell;
                g.fill(x0, y0, x0 + cell, y0 + cell, argb(fade, 0xED, 0xE6, 0xD3));
            }
        }
        g.pose().popPose();

        // Textos
        float aTexto = fade * Mth.clamp((s - 0.5f) / 0.3f, 0f, 1f);
        boolean varios = nombres.size() > 1;
        String titulo;
        if (castigo) {
            titulo = varios ? "ELIMINADOS" : "ELIMINADO";
        } else {
            titulo = varios ? "HAN MUERTO" : "HA MUERTO";
        }
        texto(g, font, titulo, w / 2 + dx, (int) (h * 0.10f) + dy, 3.5f, argb(fade, 0xFF, 0x30, 0x30));

        int y = baseY + 8 * cell + 14;
        int mostrar = Math.min(3, nombres.size());
        for (int i = 0; i < mostrar; i++) {
            texto(g, font, nombres.get(i), w / 2, y, 2f, argb(aTexto, 0xFF, 0xFF, 0xFF));
            y += 22;
        }
        if (nombres.size() > mostrar) {
            texto(g, font, "y " + (nombres.size() - mostrar) + " más", w / 2, y, 1.5f, argb(aTexto, 0xFF, 0xFF, 0xFF));
            y += 18;
        }
        if (castigo) {
            texto(g, font, "no cumplió el reto", w / 2, y + 2, 1.3f, argb(aTexto, 0xDD, 0xAA, 0xAA));
        }
    }

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
