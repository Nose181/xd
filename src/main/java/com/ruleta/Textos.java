package com.ruleta;

/** Ayudas para los mensajes: codigos de color con &, saltos de linea y limites. */
public class Textos {

    public static final int MAX_TEXTO = 1000;
    public static final int MAX_TITULO = 48;

    /** Convierte los codigos &c, &l, &r... en codigos de formato de Minecraft. */
    public static String colorear(String s) {
        if (s == null) return "";
        return s.replaceAll("&([0-9a-fA-Fk-oK-OrR])", "\u00A7$1");
    }

    /** Quita los codigos de color (&x y §x). */
    public static String limpiar(String s) {
        if (s == null) return "";
        return s.replaceAll("[&\u00A7][0-9a-fA-Fk-oK-OrR]", "");
    }

    /** Para guardar en el archivo (una sola linea): el salto de linea pasa a ser \n escrito. */
    public static String aArchivo(String s) {
        return s.replace("\r", "").replace("\n", "\\n");
    }

    /** Al leer del archivo o de un comando: \n escrito pasa a ser un salto de linea. */
    public static String desdeArchivo(String s) {
        return s.replace("\\n", "\n");
    }

    public static String limpiarTitulo(String s) {
        if (s == null) return "";
        String t = s.replace("\r", " ").replace("\n", " ").replace('|', ' ').strip();
        return t.length() > MAX_TITULO ? t.substring(0, MAX_TITULO) : t;
    }

    public static String limpiarTexto(String s) {
        if (s == null) return "";
        String t = s.replace("\r", "").replace('|', ' ').strip();
        return t.length() > MAX_TEXTO ? t.substring(0, MAX_TEXTO) : t;
    }
}
