package thronglets;

/**
 * Variationelle Freie-Energie-Minimierung (Friston FEP).
 *
 * Freie Energie: F = -log P(obs | belief) + KL(Q || P_prior)
 *             ≈  Vorhersagefehler + Komplexitätskosten
 *
 * Belief-Update: μ += α * (-∂F/∂μ)
 * Action-Selection: a* = argmin_a E_Q[F(obs(a), belief)]
 *
 * Vereinfachtes generatives Modell:
 *   P(obs | μ) = N(W_gen * μ, σ_obs²)
 *   P(μ)       = N(μ_prior, σ_prior²)
 */
public class FreeEnergyMinimizer {

    private static final float LR_BELIEF  = 0.15f;  // Belief-Update-Rate
    private static final float LR_ACTION  = 0.08f;  // Action-Gradient-Rate
    private static final float SIGMA_OBS  = 0.5f;   // Beobachtungsrauschen
    private static final float SIGMA_PRIOR= 1.0f;   // Prior-Unsicherheit

    private final float[][] W_gen; // Generatives Modell: hidden → obs
    private final float[]   mu_prior;

    public FreeEnergyMinimizer(int obsD, int hidD, long seed) {
        java.util.Random rng = new java.util.Random(seed);
        W_gen    = new float[obsD][hidD];
        mu_prior = new float[hidD];
        float scale = (float)Math.sqrt(2.0/hidD);
        for (float[] row : W_gen)
            for (int j=0;j<hidD;j++) row[j]=(float)(rng.nextGaussian()*scale);
    }

    // ── Belief Update (Perception) ────────────────────────────

    /**
     * Aktualisiert μ um freie Energie zu minimieren.
     * @param obs  Aktuelle Beobachtung (SNN-State + Sensor-Inputs)
     * @param bs   Belief-State (wird in-place aktualisiert)
     */
    public void updateBelief(float[] obs, BeliefState bs) {
        int H = bs.mu.length;
        int O = Math.min(obs.length, W_gen.length);

        // Vorhersage: ô = W_gen * μ
        float[] pred = new float[O];
        for (int o=0;o<O;o++)
            for (int h=0;h<H&&h<W_gen[o].length;h++) pred[o]+=W_gen[o][h]*bs.mu[h];

        // Vorhersagefehler: ε = obs - pred
        float[] eps = new float[O];
        float errSum=0;
        for (int o=0;o<O;o++) { eps[o]=obs[o]-pred[o]; errSum+=eps[o]*eps[o]; }
        bs.predictionError = (float)Math.sqrt(errSum/O);

        // Gradient dF/dμ = -W_gen^T*ε/σ_obs² + (μ-μ_prior)/σ_prior²
        float[] grad = new float[H];
        for (int h=0;h<H;h++) {
            float rec = 0;
            for (int o=0;o<O;o++) rec += W_gen[o][h]*eps[o];
            grad[h] = -rec/(SIGMA_OBS*SIGMA_OBS)
                      + (bs.mu[h]-mu_prior[h])/(SIGMA_PRIOR*SIGMA_PRIOR);
        }

        // Belief Update: μ -= lr * grad
        for (int h=0;h<H;h++) bs.mu[h] -= LR_BELIEF * grad[h];

        // Sigma Update (vereinfacht: inverse Fisher-Information)
        for (int h=0;h<H;h++)
            bs.sigma[h] = 1.0f/(1.0f/(SIGMA_PRIOR*SIGMA_PRIOR) + 1.0f/(SIGMA_OBS*SIGMA_OBS));

        // Freie Energie berechnen: F = 0.5*(ε²/σ_obs + (μ-μ_prior)²/σ_prior)
        float F=0;
        for (int o=0;o<O;o++) F+=eps[o]*eps[o]/(SIGMA_OBS*SIGMA_OBS);
        for (int h=0;h<H;h++) F+=(bs.mu[h]-mu_prior[h])*(bs.mu[h]-mu_prior[h])/(SIGMA_PRIOR*SIGMA_PRIOR);
        bs.freeEnergy = F*0.5f/Math.max(1,O);
    }

    // ── Expected Free Energy (für Action-Selection) ───────────

    /**
     * Berechnet Expected Free Energy für jede mögliche Aktion.
     * EFE(a) = E[log Q(s)] - E[log P(o,s | a)]
     *        ≈ Überraschung + Abweichung von Präferenzen
     *
     * @return EFE-Score pro Action-Index (niedrig = besser)
     */
    public float[] expectedFreeEnergy(BeliefState bs, int numActions) {
        float[] efe = new float[numActions];
        for (int a=0;a<numActions;a++) {
            // Simuliere hypothetischen nächsten Zustand bei Aktion a
            float[] hypothBelief = bs.mu.clone();
            hypothBelief[a % bs.mu.length] *= 0.9f; // vereinfachte Transitions-Simulation

            // Abweichung von Präferenzen
            float prefDiv=0;
            for (int h=0;h<Math.min(bs.preferences.length,hypothBelief.length);h++) {
                float d = hypothBelief[h]-bs.preferences[h];
                prefDiv += d*d;
            }

            // Epistemischer Wert (Uncertainty Reduction)
            float epist = bs.uncertainty() * 0.3f;

            efe[a] = prefDiv - epist; // niedrig = gut
        }
        return efe;
    }

    /** Beste Aktion nach EFE (mit Softmax-Sampling) */
    public int selectAction(float[] efe, java.util.Random rng) {
        float[] neg = new float[efe.length];
        float max=Float.NEGATIVE_INFINITY;
        for (float v:efe) max=Math.max(max,v);
        float sum=0;
        for (int i=0;i<efe.length;i++) { neg[i]=(float)Math.exp(-(efe[i]-max)); sum+=neg[i]; }
        float p=rng.nextFloat()*sum;
        for (int i=0;i<neg.length;i++) { p-=neg[i]; if(p<=0) return i; }
        return efe.length-1;
    }
}
