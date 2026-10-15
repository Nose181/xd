package com.ruleta;

/**
 * Un sector de la ruleta.
 *  color     nombre o hex (vacio = automatico); tambien es el color del cuadro de aviso
 *  segundos  tiempo limite; 0 = sin tiempo
 *  cumplido  true = los jugadores deben escribir /cumplido; false = solo es un aviso
 *  titulo    titulo del aviso (vacio = uno por defecto)
 *  texto     el mensaje que se anuncia (puede tener codigos &c y saltos de linea)
 */
public record Reto(String color, int segundos, boolean cumplido, String titulo, String texto) {

    /** Reto normal: 60 segundos, con /cumplido y titulo por defecto. */
    public Reto(String color, String texto) {
        this(color, 60, true, "", texto);
    }

    public Reto(String color, int segundos, boolean cumplido, String texto) {
        this(color, segundos, cumplido, "", texto);
    }

    public int rgb(int indice, int total) {
        int c = Colores.parse(color);
        return c >= 0 ? c : Colores.auto(indice, total);
    }

    public boolean tieneTiempo() {
        return segundos > 0;
    }

    public String tituloVisible() {
        if (titulo != null && !titulo.isEmpty()) return titulo;
        return cumplido ? "RETO PARA TODOS" : "AVISO";
    }
}
