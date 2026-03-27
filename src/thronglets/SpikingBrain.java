package thronglets;

import java.util.Random;

/**
 * Dreischichtiges Spiking Neural Network mit STDP-Lernen.
 *
 * Architektur:  Input(16) → Hidden(32) → Output(10)
 *
 * Lernen: Spike-Timing-Dependent Plasticity (STDP)
 *   Potenzierung: Δw += A+ * pre.trace  (pre feuerte VOR post)
 *   Depression:   Δw -= A- * post.trace (post feuerte VOR pre)
 *
 * I/O: Kontinuierliche Werte → Rate-Kodierung → Spikes → Rate-Dekodierung
 */
public class SpikingBrain {

    public static final int IN     = 25;  // 7 Homöostase + 3 Nahrung + 3 Feuer + 3 Agent + 4 Wände + Gefahr + Jahreszeit + Alter + FE + Populationsdichte
    public static final int HIDDEN = 32;
    public static final int OUT    = 10;

    private static final float A_PLUS  = 0.012f; // STDP Potenzierungs-Rate
    private static final float A_MINUS = 0.008f; // STDP Depressions-Rate
    private static final float W_MAX   = 3.0f;
    private static final float W_MIN   = -3.0f;

    // ── Neuronen ──────────────────────────────────────────────
    public final LIFNeuron[] inputLayer  = new LIFNeuron[IN];
    public final LIFNeuron[] hiddenLayer = new LIFNeuron[HIDDEN];
    public final LIFNeuron[] outputLayer = new LIFNeuron[OUT];

    // ── Gewichte ──────────────────────────────────────────────
    public final float[][] w1 = new float[HIDDEN][IN];   // Input→Hidden
    public final float[][] w2 = new float[OUT][HIDDEN];  // Hidden→Output

    // ── Kurzzeitgedächtnis (Spike-Raten der letzten Ticks) ───
    private final float[] outputRates = new float[OUT];  // gemittelte Outputraten (init per-agent random)
    private static final float RATE_DECAY = 0.9f;

    // ── Spike-Protokoll (für RendererV7) ─────────────────────
    public final boolean[] hiddenSpikes = new boolean[HIDDEN];
    public final boolean[] outputSpikes = new boolean[OUT];

    public SpikingBrain(Random rng) {
        for (int i=0;i<IN;    i++) inputLayer[i]  = new LIFNeuron();
        for (int i=0;i<HIDDEN;i++) hiddenLayer[i] = new LIFNeuron();
        for (int i=0;i<OUT;   i++) outputLayer[i] = new LIFNeuron();
        // Größere Initialisierung für mehr individuelle Variation zwischen Agenten
        for (int h=0;h<HIDDEN;h++) for (int i=0;i<IN;    i++) w1[h][i]=(float)(rng.nextGaussian()*0.8f);
        for (int o=0;o<OUT;   o++) for (int h=0;h<HIDDEN;h++) w2[o][h]=(float)(rng.nextGaussian()*0.5f);
        // Per-Agent zufällige Startrichtungen (verhindert initiales Gleichlaufen)
        for (int o=0;o<OUT;o++) outputRates[o]=0.25f+rng.nextFloat()*0.5f;
    }

    // ── Forward Pass (ein Tick) ───────────────────────────────

    /**
     * @param inputs Kontinuierliche Werte [0,1] → werden rate-kodiert
     * @return       Output-Rates [0,1] (gemittelt über Zeit)
     */
    public float[] tick(float[] inputs) {
        // ① Input-Neuronen aktualisieren (Rate-Kodierung)
        for (int i=0;i<IN;i++) {
            float I = LIFNeuron.rateToCurrentn(i<inputs.length ? inputs[i] : 0f);
            inputLayer[i].step(I);
        }

        // ② Hidden-Schicht
        for (int h=0;h<HIDDEN;h++) {
            float I=0;
            for (int i=0;i<IN;i++) if (inputLayer[i].spiked) I += w1[h][i];
            hiddenLayer[h].step(I);
            hiddenSpikes[h] = hiddenLayer[h].spiked;
        }

        // ③ Output-Schicht
        for (int o=0;o<OUT;o++) {
            float I=0;
            for (int h=0;h<HIDDEN;h++) if (hiddenLayer[h].spiked) I += w2[o][h];
            outputLayer[o].step(I);
            outputSpikes[o] = outputLayer[o].spiked;
        }

        // ④ STDP-Update
        stdpUpdate();

        // ⑤ Output-Rate aktualisieren (exponentieller gleitender Mittelwert)
        for (int o=0;o<OUT;o++)
            outputRates[o] = outputRates[o]*RATE_DECAY + (outputLayer[o].spiked ? 1-RATE_DECAY : 0);

        return outputRates.clone();
    }

    // ── STDP ──────────────────────────────────────────────────

    private void stdpUpdate() {
        // W1: Input→Hidden STDP
        for (int h=0;h<HIDDEN;h++) {
            if (hiddenLayer[h].spiked) {
                // Post-Spike: Potenzierung für aktive Inputs (pre vor post)
                for (int i=0;i<IN;i++)
                    w1[h][i] = clamp(w1[h][i] + A_PLUS * inputLayer[i].trace);
            }
            for (int i=0;i<IN;i++) {
                if (inputLayer[i].spiked)
                    // Pre-Spike: Depression wenn Post zuletzt aktiv war (post vor pre)
                    w1[h][i] = clamp(w1[h][i] - A_MINUS * hiddenLayer[h].trace);
            }
        }
        // W2: Hidden→Output STDP
        for (int o=0;o<OUT;o++) {
            if (outputLayer[o].spiked) {
                for (int h=0;h<HIDDEN;h++)
                    w2[o][h] = clamp(w2[o][h] + A_PLUS * hiddenLayer[h].trace);
            }
            for (int h=0;h<HIDDEN;h++) {
                if (hiddenLayer[h].spiked)
                    w2[o][h] = clamp(w2[o][h] - A_MINUS * outputLayer[o].trace);
            }
        }
    }

    /** Externe Verstärkung: Hebbsches Lernen mit Reward-Modulation */
    public void reinforce(int outputIdx, float reward) {
        if (outputIdx<0||outputIdx>=OUT) return;
        for (int h=0;h<HIDDEN;h++)
            w2[outputIdx][h] = clamp(w2[outputIdx][h] + reward * hiddenLayer[h].trace * 0.02f);
    }

    /** SNN-Zustand als kompakten Vektor (für FEP als Observation) */
    public float[] getStateVector() {
        float[] v = new float[OUT+4];
        System.arraycopy(outputRates, 0, v, 0, OUT);
        // Mittlere Aktivität Hidden-Layer
        float hAct=0; for (LIFNeuron n:hiddenLayer) hAct+=n.spiked?1:0;
        v[OUT]   = hAct/HIDDEN;
        // Spike-Rate Input
        float iAct=0; for (LIFNeuron n:inputLayer) iAct+=n.spiked?1:0;
        v[OUT+1] = iAct/IN;
        // Mittleres Membranpotenzial (normiert)
        float mV=0; for (LIFNeuron n:hiddenLayer) mV+=n.V;
        v[OUT+2] = (mV/HIDDEN - LIFNeuron.V_REST)/(LIFNeuron.V_THRESH-LIFNeuron.V_REST);
        v[OUT+3] = outputRates[0]; // Bewegungs-Rate
        return v;
    }

    private static float clamp(float w) { return Math.max(W_MIN, Math.min(W_MAX, w)); }

    /** Tiefer Klon für Evolution */
    public SpikingBrain copy(Random rng) {
        SpikingBrain c = new SpikingBrain(rng);
        for (int h=0;h<HIDDEN;h++) System.arraycopy(w1[h],0,c.w1[h],0,IN);
        for (int o=0;o<OUT;   o++) System.arraycopy(w2[o],0,c.w2[o],0,HIDDEN);
        return c;
    }

    public void mutate(Random rng, double rate) {
        for (int h=0;h<HIDDEN;h++) for (int i=0;i<IN;    i++)
            if (rng.nextDouble()<rate) w1[h][i]+=clamp((float)(rng.nextGaussian()*0.15));
        for (int o=0;o<OUT;   o++) for (int h=0;h<HIDDEN;h++)
            if (rng.nextDouble()<rate) w2[o][h]+=clamp((float)(rng.nextGaussian()*0.15));
    }
}
