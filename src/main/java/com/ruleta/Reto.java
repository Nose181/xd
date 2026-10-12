package com.ruleta;

/** Un sector de la ruleta: color (nombre o hex, puede ir vacio) y la condicion/reto. */
public record Reto(String color, String texto) {

    public int rgb(int indice, int total) {
        int c = Colores.parse(color);
        return c >= 0 ? c : Colores.auto(indice, total);
    }
}
