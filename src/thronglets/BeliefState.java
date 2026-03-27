package thronglets;

import java.util.Arrays;

/**
 * Variationelle Posterior Q(hidden) des generativen Modells.
 * Repräsentiert die Überzeugungen des Agenten über den Weltzustand.
 *
 * Vereinfacht: Gaussian mit Mittelwert μ und Varianz σ² (diagonal).
 */
public class BeliefState {

    public static final int DIM = 12; // Hidden-State-Dimension

    public final float[] mu    = new float[DIM]; // Mittlere Erwartung
    public final float[] sigma = new float[DIM]; // Unsicherheit

    // Präferenz-Prior P*(o): was der Agent "erleben will"
    public final float[] preferences = new float[DIM];

    // Vorhersage-Fehler des letzten Ticks
    public float predictionError = 0f;
    public float freeEnergy      = 0f;

    public BeliefState() {
        Arrays.fill(sigma, 1.0f);
        // Präferenzen: hohe Energie, niedrigen Stress, soziale Verbindung
        preferences[0] = 0.8f;  // Energie hoch
        preferences[1] = 0.1f;  // Stress niedrig
        preferences[2] = 0.6f;  // Sozial
        preferences[3] = 0.5f;  // Neugier moderat
        preferences[4] = 0.6f;  // Wärme
    }

    public float[] toVector() {
        float[] v = new float[DIM * 2];
        System.arraycopy(mu,    0, v, 0,   DIM);
        System.arraycopy(sigma, 0, v, DIM, DIM);
        return v;
    }

    public float surprise() { return predictionError; }
    public float uncertainty() {
        float s=0; for (float v:sigma) s+=v; return s/DIM;
    }
}
