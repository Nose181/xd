package com.ruleta.client;

import com.ruleta.Textos;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.List;

/** Cuadro de aviso arriba a la izquierda: titulo de color, mensaje y "[Presiona I] para ocultar aviso". */
public class AvisoOverlay {

    private static String titulo = "";
    private static String mensaje = "";
    private static int rgb = 0xFFAA00;
    private static boolean pideCumplido = false;
    private static boolean visible = false;
    private static long inicio = 0L;
    private static long fin = 0L; // 0 = sin cuenta regresiva

    public static void mostrar(String t, String m, int color, int segundos, boolean pide) {
        titulo = t;
        mensaje = m;
        rgb = color & 0xFFFFFF;
        pideCumplido = pide;
        inicio = Util.getMillis();
        fin = segundos > 0 ? inicio + segundos * 1000L : 0L;
        visible = true;
    }

    public static void limpiar() {
        visible = false;
    }

    public static void ocultar() {
        visible = false;
    }

    public static void render(GuiGraphics g, int w, int h) {
        if (!visible) return;
        long ahora = Util.getMillis();
        int restante = -1;
        if (fin > 0) {
            restante = (int) Math.ceil((fin - ahora) / 1000.0);
            if (restante < 0) {
                visible = false;
                return;
            }
        }
        Font font = Minecraft.getInstance().font;

        int maxTexto = Math.max(120, Math.min(w / 2 - 20, 250));
        List<FormattedCharSequence> lineas = font.split(Component.literal(Textos.colorear(mensaje)), maxTexto);
        if (lineas.size() > 8) lineas = lineas.subList(0, 8);
        MutableComponent cabecera = Component.literal("● " + Textos.colorear(titulo) + ":")
                .withStyle(Style.EMPTY.withColor(rgb).withBold(true));
        String pie = "[Presiona " + ClientSetup.OCULTAR.getTranslatedKeyMessage().getString() + "] para ocultar aviso";
        String extra1 = pideCumplido ? "Escribe /cumplido al cumplirlo" : null;
        String extra2 = restante >= 0 ? "Tiempo: " + restante + " s" : null;

        int ancho = Math.max(font.width(cabecera), font.width(pie));
        for (FormattedCharSequence l : lineas) ancho = Math.max(ancho, font.width(l));
        if (extra1 != null) ancho = Math.max(ancho, font.width(extra1));
        if (extra2 != null) ancho = Math.max(ancho, font.width(extra2));
        ancho += 16;
        int alto = 8 + 12 + lineas.size() * 10 + (extra1 != null ? 10 : 0) + (extra2 != null ? 10 : 0) + 4 + 12;

        // Entra deslizando desde la izquierda
        float k = Mth.clamp((ahora - inicio) / 350f, 0f, 1f);
        float suave = 1f - (float) Math.pow(1f - k, 3);
        int dx = (int) (-(ancho + 12) * (1f - suave));

        int x0 = 6;
        int y0 = 6;
        g.pose().pushPose();
        g.pose().translate(dx, 0, 0);
        g.fill(x0, y0, x0 + ancho, y0 + alto, 0xD00A0F1E);
        g.fill(x0, y0, x0 + ancho, y0 + 2, 0xFF000000 | rgb);

        int y = y0 + 7;
        g.drawString(font, cabecera, x0 + 8, y, 0xFFFFFFFF, true);
        y += 12;
        for (FormattedCharSequence l : lineas) {
            g.drawString(font, l, x0 + 8, y, 0xFFE6E6E6, false);
            y += 10;
        }
        if (extra1 != null) {
            g.drawString(font, extra1, x0 + 8, y, 0xFFC8C8C8, false);
            y += 10;
        }
        if (extra2 != null) {
            g.drawString(font, extra2, x0 + 8, y, 0xFFFF6060, false);
            y += 10;
        }
        y += 4;
        g.drawString(font, pie, x0 + 8, y, 0xFF9A9AA8, false);
        g.pose().popPose();
    }
}
