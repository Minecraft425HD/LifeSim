package thronglets;

public class FreeEnergyMinimizer {
    private final BeliefState belief = new BeliefState();
    private float lastFE = 0f, lastPE = 0f;
    private final float[] prior = new float[8];

    public float[] minimize(float[] snnState, float[] sensory) {
        // Alle Inputs VOR dem Einschreiben bereinigen
        float[] obs = new float[8];
        for (int i = 0; i < 8; i++) {
            float raw = (i < sensory.length) ? sensory[i]
                      : (i < snnState.length ? snnState[i] : 0f);
            obs[i] = (Float.isNaN(raw) || Float.isInfinite(raw)) ? 0f
                   : Math.max(-10f, Math.min(10f, raw));
        }

        belief.update(obs, prior);

        lastPE = belief.predictionError(obs);
        lastFE = belief.freeEnergy(obs);

        // Doppeltes Sicherheitsnetz
        if (Float.isNaN(lastFE) || Float.isInfinite(lastFE)) lastFE = 0f;
        if (Float.isNaN(lastPE) || Float.isInfinite(lastPE)) lastPE = 0f;
        lastFE = Math.max(-50f, Math.min(50f, lastFE));
        lastPE = Math.max(  0f, Math.min(50f, lastPE));

        float[] mu  = belief.getMu();
        float[] act = new float[10];
        for (int i = 0; i < 10; i++) {
            act[i] = (i < mu.length) ? mu[i] : 0f;
            if (Float.isNaN(act[i]) || Float.isInfinite(act[i])) act[i] = 0f;
        }
        return act;
    }

    public float getLastFE()      { return lastFE; }
    public float getLastPE()      { return lastPE; }
    public float getUncertainty() { return belief.uncertainty(); }
}
