package com.ruleta.client;

import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Lee los retos desde config/ruleta-retos.txt (uno por linea). */
public class RetosConfig {

    private static final List<String> POR_DEFECTO = List.of(
            "Haz 10 saltos seguidos",
            "Habla en tercera persona durante 2 minutos",
            "No uses espada durante 5 minutos",
            "Regala un ítem a otro jugador",
            "Construye una torre de 10 bloques",
            "Camina sin parar durante 1 minuto",
            "Pica un bloque solo con la mano",
            "Agáchate cada vez que hables",
            "Cuenta un chiste en el chat",
            "Duerme... ¡o finge hacerlo! (túmbate 10 segundos)"
    );

    public static List<String> cargar() {
        Path archivo = FMLPaths.CONFIGDIR.get().resolve("ruleta-retos.txt");
        try {
            if (!Files.exists(archivo)) {
                List<String> inicial = new ArrayList<>();
                inicial.add("# Un reto por linea. Las lineas que empiezan con # se ignoran.");
                inicial.add("# Minimo 2 retos, maximo 24. Guarda el archivo y vuelve a abrir la ruleta.");
                inicial.addAll(POR_DEFECTO);
                Files.write(archivo, inicial, StandardCharsets.UTF_8);
            }
            List<String> retos = new ArrayList<>();
            for (String linea : Files.readAllLines(archivo, StandardCharsets.UTF_8)) {
                String s = linea.replace("\uFEFF", "").trim();
                if (!s.isEmpty() && !s.startsWith("#")) retos.add(s);
            }
            if (retos.size() >= 2) {
                return retos.size() > 24 ? new ArrayList<>(retos.subList(0, 24)) : retos;
            }
        } catch (IOException ignorado) {
            // si falla la lectura, se usan los retos por defecto
        }
        return new ArrayList<>(POR_DEFECTO);
    }
}
