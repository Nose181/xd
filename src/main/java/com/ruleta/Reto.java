package com.ruleta;

/**
 * Un sector de la ruleta.
 *  color     nombre o hex (vacio = automatico)
 *  segundos  tiempo limite; 0 = sin tiempo
 *  cumplido  true = los jugadores deben escribir /cumplido; false = solo es un aviso
 *  texto     el mensaje que se anuncia cuando sale este sector
 */
public record Reto(String color, int segundos, boolean cumplido, String texto) {

    /** Reto normal: 60 segundos y con /cumplido. */
    public Reto(String color, String texto) {
        this(color, 60, true, texto);
    }

    public int rgb(int indice, int total) {
        int c = Colores.parse(color);
        return c >= 0 ? c : Colores.auto(indice, total);
    }

    public boolean tieneTiempo() {
        return segundos > 0;
    }
}
