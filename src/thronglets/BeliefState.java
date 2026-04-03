package thronglets;

public class BeliefState {
    private static final int DIM = 8;
    private final float[] mu    = new float[DIM];
    private final float[] sigma = new float[DIM];

    public BeliefState() { java.util.Arrays.fill(sigma, 0.5f); }

    /** Bereinigt NaN/Inf und klemmt auf [-10, 10]. */
    private static float s(float v) {
        if (Float.isNaN(v) || Float.isInfinite(v)) return 0f;
        return Math.max(-10f, Math.min(10f, v));
    }

    public void update(float[] obs, float[] prior) {
        for (int i = 0; i < DIM && i < obs.length; i++) {
            float o = s(obs[i]);
            float k = sigma[i] / (sigma[i] + 0.1f);
            mu[i]    = s(mu[i] + k * (o - mu[i]));  // mu[] wird niemals NaN
            sigma[i] = Math.max(0.001f, (1 - k) * sigma[i]);
        }
    }

    public float[] getMu()    { return mu; }
    public float[] getSigma() { return sigma; }

    public float predictionError(float[] obs) {
        float e = 0;
        for (int i = 0; i < DIM && i < obs.length; i++) {
            float d = s(obs[i]) - mu[i];
            e += d * d;
        }
        float pe = (float) Math.sqrt(e / DIM);
        return (Float.isNaN(pe) || Float.isInfinite(pe)) ? 0f : pe;
    }

    public float freeEnergy(float[] obs) {
        float pe = predictionError(obs);
        float kl = 0f;
        for (float sv : sigma) kl += Math.log(Math.max(1e-6f, sv));
        float fe = pe - kl * 0.01f;
        if (Float.isNaN(fe) || Float.isInfinite(fe)) return 0f;
        return Math.max(-50f, Math.min(50f, fe));
    }

    public float uncertainty() {
        float sum = 0f;
        for (float sv : sigma) sum += sv;
        return sum / DIM;
    }
}
