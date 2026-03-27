package thronglets;

/**
 * Leaky Integrate-and-Fire Neuron (LIF).
 *
 * Biologisches Modell: Membranpotenzial integriert eingehende Ströme
 * und feuert (Spike) wenn Schwellwert überschritten → Reset.
 *
 *   dV/dt = -(V - Vrest)/τ + I   (vereinfacht: V[t] = V[t-1]*decay + I)
 */
public class LIFNeuron {

    // ── Feste Parameter ───────────────────────────────────────
    public static final float V_REST      = -70.0f; // mV (Ruhepotenzial)
    public static final float V_THRESH    = -55.0f; // mV (Schwelle)
    public static final float V_RESET     = -75.0f; // mV (Post-Spike-Reset)
    public static final float DECAY       = 0.92f;  // Leck-Konstante (τ)
    public static final float TRACE_DECAY = 0.85f;  // Eligibility-Trace Decay
    public static final int   REFRAC_TICKS = 2;     // Refraktärzeit (Ticks)
    public static final float TONIC_BIAS  = 2.0f;   // Tonus-Strom: sorgt für Hintergrund-Feuern

    // ── Zustand ───────────────────────────────────────────────
    public float   V          = V_REST;   // Membranpotenzial
    public float   trace      = 0f;       // Pre-Synaptic Eligibility Trace
    public boolean spiked     = false;    // Spike in diesem Tick?
    private int    refracLeft = 0;        // Verbleibende Refraktärzeit

    // ── Tick-Update ───────────────────────────────────────────

    /**
     * Einen Zeitschritt berechnen.
     * @param I Eingehender Strom (gewichtete Summe der eingehenden Spikes)
     */
    public void step(float I) {
        spiked = false;
        if (refracLeft > 0) {
            refracLeft--;
            V = V_RESET;
        } else {
            V = V_REST + (V - V_REST) * DECAY + I + TONIC_BIAS;
            if (V >= V_THRESH) {
                spiked     = true;
                V          = V_RESET;
                refracLeft = REFRAC_TICKS;
            }
        }
        // Eligibility Trace (für STDP) aktualisieren
        trace = trace * TRACE_DECAY + (spiked ? 1.0f : 0.0f);
    }

    /** Rate-kodierter Input → Strom-Äquivalent */
    public static float rateToCurrentn(float rate) {
        return rate * (V_THRESH - V_REST) * 1.2f;
    }

    public void reset() {
        V = V_REST; trace = 0f; spiked = false; refracLeft = 0;
    }
}
