package thronglets;

/**
 * Das dreischichtige Gehirn von Thronglet V7.
 *
 * Schicht 1 – SNN (Reflex):        schnell, jeder Tick, STDP-Lernen
 * Schicht 2 – FEP (Kognition):     belief update, free energy, aktiv-inferenz
 * Schicht 3 – Meta (Transformer):  alle 10 Ticks, Intention, innerer Monolog
 *
 * Informationsfluss:
 *   inputs → SNN.tick() → snnState
 *   [snnState + inputs] → FEP.updateBelief() → beliefs
 *   beliefs → MetaCognition.tick() → intention
 *   snnOutputs + intention → final action weights
 */
public class TripleLayerBrain {

    public final SpikingBrain         snn;
    public final BeliefState          beliefs;
    public final FreeEnergyMinimizer  fep;
    public final MetaCognition        meta;

    // Kombinierter Output (für ThrongletV7 lesbar)
    private float[] lastSNNOut   = new float[SpikingBrain.OUT];
    private float[] lastIntention= new float[NanoTransformer.INT_DIM];
    private float   lastFE       = 0f;

    public TripleLayerBrain(long seed, java.util.Random rng) {
        snn     = new SpikingBrain(rng);
        beliefs = new BeliefState();
        fep     = new FreeEnergyMinimizer(SpikingBrain.OUT + 4, BeliefState.DIM, seed);
        meta    = new MetaCognition(seed ^ 0xDEADBEEFL);
    }

    // ── Haupt-Forward-Pass ────────────────────────────────────

    /**
     * @param inputs       Sensor- + Homeostase-Inputs [16]
     * @param homeostasis  Aktueller Homeostase-Zustand
     * @param groupSize    Anzahl Mitglieder der aktuellen Gruppe
     * @return             Finaler Action-Vektor [10]
     */
    public float[] forward(float[] inputs, Homeostasis homeostasis, int groupSize) {

        // ═══════════ SCHICHT 1: SNN ═══════════
        float[] snnOut = snn.tick(inputs);
        lastSNNOut     = snnOut.clone();

        // SNN-Zustandsvektor (für FEP als Observation)
        float[] snnState  = snn.getStateVector();

        // ═══════════ SCHICHT 2: FEP ═══════════
        // FEP beobachtet SNN-Zustand + Inputs
        float[] observation = mergeObs(snnState, inputs);
        fep.updateBelief(observation, beliefs);
        lastFE = beliefs.freeEnergy;

        // EFE für Action-Selection berechnen
        float[] efe = fep.expectedFreeEnergy(beliefs, SpikingBrain.OUT);

        // ═══════════ SCHICHT 3: META ═══════════
        float[] intention = meta.tick(homeostasis, lastFE, beliefs.predictionError, groupSize);
        lastIntention     = intention;

        // ═══════════ INTEGRATION ═══════════════
        // Finale Aktion = SNN-Output + EFE-Modulation + Intention
        float[] action = new float[SpikingBrain.OUT];
        for (int i=0;i<SpikingBrain.OUT;i++) {
            float snnW   = snnOut[i];
            float fepW   = Math.max(0, 1.0f - Math.max(0,efe[i]));  // EFE → Aktivierungs-Bonus
            float intW   = i<intention.length ? intention[i] : 0f;

            // Gewichtete Kombination: SNN dominiert Reflexe, FEP Planung, Meta Intention
            action[i] = 0.5f*snnW + 0.3f*fepW + 0.2f*intW;
        }

        return action;
    }

    // ── Reward-Signal zurückspeisen (SNN Reinforcement) ──────

    public void reinforce(int outputIdx, float reward) {
        snn.reinforce(outputIdx, reward);
    }

    // ── Getter für Renderer ───────────────────────────────────

    public float[] getLastSNNOut()    { return lastSNNOut;    }
    public float[] getLastIntention() { return lastIntention; }
    public float   getLastFE()        { return lastFE;        }
    public float   getPredError()     { return beliefs.predictionError; }
    public float   getUncertainty()   { return beliefs.uncertainty();   }
    public boolean[] getHiddenSpikes(){ return snn.hiddenSpikes;       }
    public boolean[] getOutputSpikes(){ return snn.outputSpikes;       }
    public String  getLastThought()   { return meta.getLastThought();  }
    public java.util.List<String> getMonolog() { return meta.getMonolog(); }

    // ── Tiefer Klon (für Evolution) ───────────────────────────

    public TripleLayerBrain copy(long seed, java.util.Random rng) {
        TripleLayerBrain c = new TripleLayerBrain(seed, rng);
        // SNN-Gewichte kopieren
        for (int h=0;h<SpikingBrain.HIDDEN;h++) System.arraycopy(snn.w1[h],0,c.snn.w1[h],0,SpikingBrain.IN);
        for (int o=0;o<SpikingBrain.OUT;   o++) System.arraycopy(snn.w2[o],0,c.snn.w2[o],0,SpikingBrain.HIDDEN);
        return c;
    }

    private float[] mergeObs(float[] a, float[] b) {
        int la=a.length, lb=Math.min(b.length,4);
        float[] m=new float[la+lb];
        System.arraycopy(a,0,m,0,la);
        System.arraycopy(b,0,m,la,lb);
        return m;
    }
}
