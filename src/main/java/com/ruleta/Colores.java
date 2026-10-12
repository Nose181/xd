package com.ruleta;

import net.minecraft.util.Mth;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Colores con nombre para los sectores de la ruleta (o codigo hex de 6 digitos, ej: ff8800). */
public class Colores {

    private static final Map<String, Integer> NOMBRES = new LinkedHashMap<>();

    static {
        NOMBRES.put("rojo", 0xE53935);
        NOMBRES.put("naranja", 0xFB8C00);
        NOMBRES.put("amarillo", 0xFDD835);
        NOMBRES.put("verde", 0x43A047);
        NOMBRES.put("lima", 0x9CCC1A);
        NOMBRES.put("turquesa", 0x00A896);
        NOMBRES.put("celeste", 0x29B6F6);
        NOMBRES.put("azul", 0x1E5BD8);
        NOMBRES.put("morado", 0x8E24AA);
        NOMBRES.put("rosado", 0xEC407A);
        NOMBRES.put("cafe", 0x795548);
        NOMBRES.put("gris", 0x9E9E9E);
        NOMBRES.put("blanco", 0xF5F5F5);
        NOMBRES.put("negro", 0x263238);
    }

    public static Set<String> nombres() {
        return NOMBRES.keySet();
    }

    /** Devuelve 0xRRGGBB, o -1 si el texto no es un color valido. */
    public static int parse(String texto) {
        if (texto == null) return -1;
        String s = texto.trim().toLowerCase(Locale.ROOT);
        if (s.startsWith("#")) s = s.substring(1);
        Integer rgb = NOMBRES.get(s);
        if (rgb != null) return rgb;
        if (s.length() == 6) {
            try {
                return Integer.parseInt(s, 16);
            } catch (NumberFormatException ignorado) {
                return -1;
            }
        }
        return -1;
    }

    /** Color automatico para quien no eligio uno. */
    public static int auto(int indice, int total) {
        return Mth.hsvToRgb(indice / (float) Math.max(1, total), 0.7f, 0.9f);
    }
}
