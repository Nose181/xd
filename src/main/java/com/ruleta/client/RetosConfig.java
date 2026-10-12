package com.ruleta.client;

import com.ruleta.Colores;
import com.ruleta.Reto;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Lee y guarda los retos en config/ruleta-retos.txt (formato:  color|reto). */
public class RetosConfig {

    public static final int MINIMO = 2;
    public static final int MAXIMO = 24;

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

    public static List<Reto> cargar() {
        Path p = archivo();
        try {
            if (!Files.exists(p)) guardar(POR_DEFECTO);
            List<Reto> retos = new ArrayList<>();
            for (String linea : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                String s = linea.replace("\uFEFF", "").trim();
                if (s.isEmpty() || s.startsWith("#")) continue;
                String color = "";
                String texto = s;
                int barra = s.indexOf('|');
                if (barra > 0 && Colores.parse(s.substring(0, barra)) >= 0) {
                    color = s.substring(0, barra).trim().toLowerCase();
                    texto = s.substring(barra + 1).trim();
                }
                if (!texto.isEmpty()) retos.add(new Reto(color, texto));
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
        lineas.add("# Un reto por línea con el formato:  color|reto");
        lineas.add("# Colores: " + String.join(", ", Colores.nombres()) + " o un código hex de 6 dígitos (ej: ff8800).");
        lineas.add("# Si no pones color se asigna uno automático. Las líneas que empiezan con # se ignoran.");
        lineas.add("# Mínimo " + MINIMO + " retos, máximo " + MAXIMO + ". Lo más cómodo: /ruleta agregar y /ruleta quitar.");
        for (Reto r : retos) {
            lineas.add((r.color() == null || r.color().isEmpty() ? "" : r.color() + "|") + r.texto());
        }
        Files.write(archivo(), lineas, StandardCharsets.UTF_8);
    }
}
