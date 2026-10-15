package com.ruleta.client;

import com.ruleta.Colores;
import com.ruleta.Reto;
import com.ruleta.Textos;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Lee y guarda los retos en config/ruleta-retos.txt (formato:  color|tiempo|modo|titulo|texto). */
public class RetosConfig {

    public static final int MINIMO = 2;
    public static final int MAXIMO = 24;
    public static final int SEG_MIN = 10;
    public static final int SEG_MAX = 600;

    private static final List<Reto> POR_DEFECTO = List.of(
            new Reto("rojo", "Haz 10 saltos seguidos"),
            new Reto("naranja", "Habla en tercera persona durante 2 minutos"),
            new Reto("amarillo", "No uses espada durante 5 minutos"),
            new Reto("verde", "Regala un ítem a otro jugador"),
            new Reto("turquesa", "Construye una torre de 10 bloques"),
            new Reto("celeste", "Camina sin parar durante 1 minuto"),
            new Reto("azul", "Pica un bloque solo con la mano"),
            new Reto("morado", "Agáchate cada vez que hables"),
            new Reto("rosado", "Cuenta un chiste en el chat"),
            new Reto("cafe", "Quédate tumbado 10 segundos")
    );

    private static Path archivo() {
        return FMLPaths.CONFIGDIR.get().resolve("ruleta-retos.txt");
    }

    /** "sin" -> 0, un numero de 10 a 600 -> ese numero, cualquier otra cosa -> -1. */
    public static int parseTiempo(String texto) {
        String s = texto.trim().toLowerCase(Locale.ROOT);
        if (s.equals("sin")) return 0;
        try {
            int n = Integer.parseInt(s);
            return (n >= SEG_MIN && n <= SEG_MAX) ? n : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** "cumplido" -> true, "aviso" -> false, otra cosa -> null. */
    public static Boolean parseModo(String texto) {
        String s = texto.trim().toLowerCase(Locale.ROOT);
        if (s.equals("cumplido")) return Boolean.TRUE;
        if (s.equals("aviso")) return Boolean.FALSE;
        return null;
    }

    private static boolean colorValido(String c) {
        return c.equals("auto") || Colores.parse(c) >= 0;
    }

    public static List<Reto> cargar() {
        Path p = archivo();
        try {
            if (!Files.exists(p)) guardar(POR_DEFECTO);
            List<Reto> retos = new ArrayList<>();
            for (String linea : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                String s = linea.replace("\uFEFF", "").trim();
                if (s.isEmpty() || s.startsWith("#")) continue;

                // Formato actual: color|tiempo|modo|titulo|texto
                String[] a = s.split("\\|", 5);
                if (a.length == 5) {
                    String c = a[0].trim().toLowerCase(Locale.ROOT);
                    int t = parseTiempo(a[1]);
                    Boolean modo = parseModo(a[2]);
                    String texto = Textos.desdeArchivo(a[4].trim());
                    if (colorValido(c) && t >= 0 && modo != null && !texto.isEmpty()) {
                        retos.add(new Reto(c.equals("auto") ? "" : c, t, modo, Textos.limpiarTitulo(a[3]), texto));
                        continue;
                    }
                }

                // Formato anterior: color|tiempo|modo|texto (sin titulo)
                String[] b = s.split("\\|", 4);
                if (b.length == 4) {
                    String c = b[0].trim().toLowerCase(Locale.ROOT);
                    int t = parseTiempo(b[1]);
                    Boolean modo = parseModo(b[2]);
                    String texto = Textos.desdeArchivo(b[3].trim());
                    if (colorValido(c) && t >= 0 && modo != null && !texto.isEmpty()) {
                        retos.add(new Reto(c.equals("auto") ? "" : c, t, modo, texto));
                        continue;
                    }
                }

                // Formato antiguo: color|reto (60 segundos y con /cumplido), o solo el texto
                String color = "";
                String texto = s;
                int barra = s.indexOf('|');
                if (barra > 0 && Colores.parse(s.substring(0, barra)) >= 0) {
                    color = s.substring(0, barra).trim().toLowerCase(Locale.ROOT);
                    texto = s.substring(barra + 1).trim();
                }
                texto = Textos.desdeArchivo(texto);
                if (!texto.isEmpty()) retos.add(new Reto(color, 60, true, texto));
            }
            if (retos.size() >= MINIMO) {
                return retos.size() > MAXIMO ? new ArrayList<>(retos.subList(0, MAXIMO)) : retos;
            }
        } catch (IOException ignorado) {
            // si falla la lectura, se usan los retos por defecto
        }
        return new ArrayList<>(POR_DEFECTO);
    }

    public static void guardar(List<Reto> retos) throws IOException {
        List<String> lineas = new ArrayList<>();
        lineas.add("# Un reto por línea con el formato:  color|tiempo|modo|titulo|texto");
        lineas.add("#   color:  " + String.join(", ", Colores.nombres()) + ", auto, o un código hex de 6 dígitos (ej: ff8800)");
        lineas.add("#           (es también el color del cuadro de aviso)");
        lineas.add("#   tiempo: 'sin' (sin límite) o segundos, de " + SEG_MIN + " a " + SEG_MAX);
        lineas.add("#   modo:   'cumplido' (hay que escribir /cumplido; quien no lo haga a tiempo muere)");
        lineas.add("#           'aviso' (solo se anuncia el texto, nadie muere)");
        lineas.add("#   titulo: título del aviso (puede quedar vacío)");
        lineas.add("#   texto:  el mensaje. Usa &c, &a, &l, &r... para colores/formato y \\n para saltar de línea.");
        lineas.add("# Las líneas de formatos anteriores siguen funcionando.");
        lineas.add("# Mínimo " + MINIMO + " retos, máximo " + MAXIMO + ". Lo más cómodo: /ruleta panel");
        for (Reto r : retos) {
            String color = (r.color() == null || r.color().isEmpty()) ? "auto" : r.color();
            String tiempo = r.segundos() > 0 ? String.valueOf(r.segundos()) : "sin";
            String modo = r.cumplido() ? "cumplido" : "aviso";
            String titulo = r.titulo() == null ? "" : Textos.limpiarTitulo(r.titulo());
            String texto = Textos.aArchivo(r.texto().replace('|', ' '));
            lineas.add(color + "|" + tiempo + "|" + modo + "|" + titulo + "|" + texto);
        }
        Files.write(archivo(), lineas, StandardCharsets.UTF_8);
    }
}
