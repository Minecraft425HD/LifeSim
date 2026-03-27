package thronglets;

/**
 * Das dreischichtige Gehirn von Thronglet V7.
 *
 * Schicht 1 – SNN (Reflex):        schnell, jeder Tick, STDP-Lernen
 * Schicht 2 – FEP (Kognition):     Beliefs + Free Energy
 * Schicht 3 – Meta (Transformer):  Intention, innerer Monolog
 *
 * An neue FEP-API angepasst:
 *  - BeliefState hat nur noch Methoden, keine öffentlichen Felder
 *  - FreeEnergyMinimizer hat keinen Konstruktor mit Parametern mehr
 *  - Schnittstelle: minimize(snnState, sensory) liefert Belief-basierten Action-Vektor
 */
public class TripleLayerBrain {

    public final SpikingBrain        snn;
    public final BeliefState         beliefs;
    public final FreeEnergyMinimizer fep;
    public final MetaCognition       meta;

    // Kombinierter Output (für ThrongletV7 / Renderer)
    private float[] lastSNNOut    = new float[SpikingBrain.OUT];
    private float[] lastIntention = new float[NanoTransformer.INT_DIM];
    private float   lastFE        = 0f;
    private float   lastPE        = 0f;

    public TripleLayerBrain(long seed, java.util.Random rng) {
        snn     = new SpikingBrain(rng);
        beliefs = new BeliefState();
        fep     = new FreeEnergyMinimizer();              // neue API: kein Konstruktor mit Parametern
        meta    = new MetaCognition(seed ^ 0xDEADBEEFL);
    }

    /**
     * @param inputs      Sensor- + Homeostase-Inputs [16]
     * @param homeostasis Aktueller Homeostase-Zustand
     * @param groupSize   Anzahl Mitglieder der aktuellen Gruppe
     * @return            Finaler Action-Vektor [10]
     */
    public float[] forward(float[] inputs, Homeostasis homeostasis, int groupSize) {

        // ═══════════ SCHICHT 1: SNN ═══════════
        float[] snnOut = snn.tick(inputs);
        lastSNNOut     = snnOut.clone();
        float[] snnState = snn.getStateVector(); // Annahme: wie in deinem Original

        // ═══════════ SCHICHT 2: FEP ═══════════
        // Observation = SNN-Zustand + Inputs
        float[] observation = mergeObs(snnState, inputs);

        // Neue API: minimize() macht Belief-Update und liefert „FEP-Aktions“-Vektor
        float[] fepAction = fep.minimize(snnState, observation);
        lastFE = safe(fep.getLastFE());
        lastPE = safe(fep.getLastPE());

        // ═══════════ SCHICHT 3: META ═══════════
        // BeliefState hat jetzt predictionError() als Methode
        float peForMeta = beliefs.predictionError(observation);
        float[] intention = meta.tick(homeostasis, lastFE, peForMeta, groupSize);
        lastIntention     = intention;

        // ═══════════ INTEGRATION ═══════════════
        // Finale Aktion = SNN-Output + FEP-Aktionsvektor + Intention
        float[] action = new float[SpikingBrain.OUT];
        for (int i = 0; i < SpikingBrain.OUT; i++) {
            float snnW = i < snnOut.length   ? snnOut[i]   : 0f;
            float fepW = i < fepAction.length? fepAction[i]: 0f;
            float intW = i < intention.length? intention[i]: 0f;

            float v = 0.5f * snnW + 0.3f * fepW + 0.2f * intW;
            if (Float.isNaN(v) || Float.isInfinite(v)) v = 0f;
            action[i] = v;
        }

        return action;
    }

    // Reward bleibt wie vorher
    public void reinforce(int outputIdx, float reward) {
        snn.reinforce(outputIdx, reward);
    }

    // ── Getter für Renderer ───────────────────────────────────

    public float[]   getLastSNNOut()    { return lastSNNOut;    }
    public float[]   getLastIntention() { return lastIntention; }
    public float     getLastFE()        { return lastFE;        }
    public float     getPredError()     { return lastPE;        }
    public float     getUncertainty()   { return beliefs.uncertainty(); }
    public boolean[] getHiddenSpikes()  { return snn.hiddenSpikes; }
    public boolean[] getOutputSpikes()  { return snn.outputSpikes; }
    public String    getLastThought()   { return meta.getLastThought(); }
    public java.util.List<String> getMonolog() { return meta.getMonolog(); }

    // ── Tiefer Klon (für Evolution) ───────────────────────────

    public TripleLayerBrain copy(long seed, java.util.Random rng) {
        TripleLayerBrain c = new TripleLayerBrain(seed, rng);
        for (int h = 0; h < SpikingBrain.HIDDEN; h++)
            System.arraycopy(snn.w1[h], 0, c.snn.w1[h], 0, SpikingBrain.IN);
        for (int o = 0; o < SpikingBrain.OUT; o++)
            System.arraycopy(snn.w2[o], 0, c.snn.w2[o], 0, SpikingBrain.HIDDEN);
        return c;
    }

    private float[] mergeObs(float[] a, float[] b) {
        int la = a.length, lb = Math.min(b.length, 4);
        float[] m = new float[la + lb];
        System.arraycopy(a, 0, m, 0, la);
        System.arraycopy(b, 0, m, la, lb);
        return m;
    }

    private static float safe(float v) {
        if (Float.isNaN(v) || Float.isInfinite(v)) return 0f;
        return Math.max(-50f, Math.min(50f, v));
    }
}