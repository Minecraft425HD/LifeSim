package thronglets;

/**
 * Nano-Transformer: 2-Layer, 2-Head Attention, Vocab=32, Dim=16.
 *
 * Operiert auf internen Zustandstokens (KEIN natürliche Sprache).
 * Generiert: (1) Intentions-Vektor [8], (2) nächstes Token (innerer Monolog).
 *
 * Architektur:
 *   Token-Embedding [32×16] + Positional Encoding
 *   → Layer 1: Multi-Head Attention (2 Heads, dim=8) + FFN [16→32→16]
 *   → Layer 2: Multi-Head Attention (2 Heads, dim=8) + FFN [16→32→16]
 *   → Head: Linear [16→32] → Logits (nächstes Token)
 *         + Linear [16→8]  → Intention Vector
 */
public class NanoTransformer {

    public static final int VOCAB   = 32;
    public static final int CTX     = 8;   // Kontext-Länge
    public static final int DIM     = 16;  // Embedding Dim
    public static final int HEADS   = 2;
    public static final int HEAD_D  = DIM / HEADS;   // 8
    public static final int FF_DIM  = 32;             // FFN Hidden Dim
    public static final int INT_DIM = 8;              // Intentions-Output

    // ── Gewichtsmatrizen ──────────────────────────────────────
    private final float[][] embeds;          // [VOCAB, DIM]
    private final float[][] posEnc;          // [CTX, DIM]

    // Layer 1
    private final float[][][] Wq1, Wk1, Wv1;  // [HEADS][HEAD_D][DIM]
    private final float[][]   Wo1;             // [DIM][DIM]
    private final float[][]   Wff1a, Wff1b;   // [FF_DIM][DIM], [DIM][FF_DIM]

    // Layer 2
    private final float[][][] Wq2, Wk2, Wv2;
    private final float[][]   Wo2;
    private final float[][]   Wff2a, Wff2b;

    // Head
    private final float[][] W_lm;   // [VOCAB][DIM] → Token Logits
    private final float[][] W_int;  // [INT_DIM][DIM] → Intention

    public NanoTransformer(long seed) {
        java.util.Random rng = new java.util.Random(seed);
        embeds = rand(rng, VOCAB, DIM, 0.1f);
        posEnc = buildPositional();

        Wq1=randHeads(rng); Wk1=randHeads(rng); Wv1=randHeads(rng);
        Wo1=rand(rng,DIM,DIM,0.1f);
        Wff1a=rand(rng,FF_DIM,DIM,0.1f); Wff1b=rand(rng,DIM,FF_DIM,0.1f);

        Wq2=randHeads(rng); Wk2=randHeads(rng); Wv2=randHeads(rng);
        Wo2=rand(rng,DIM,DIM,0.1f);
        Wff2a=rand(rng,FF_DIM,DIM,0.1f); Wff2b=rand(rng,DIM,FF_DIM,0.1f);

        W_lm =rand(rng,VOCAB,DIM,0.1f);
        W_int=rand(rng,INT_DIM,DIM,0.1f);
    }

    // ── Forward Pass ──────────────────────────────────────────

    /**
     * @param tokens  int[CTX] – Kontext-Token-IDs
     * @return        TransformerOutput (Intention + next Token + Logits)
     */
    public TransformerOutput forward(int[] tokens) {
        // Embedding + Positional Encoding
        float[][] x = new float[CTX][DIM];
        for (int t=0;t<CTX;t++) {
            int tok=Math.max(0,Math.min(VOCAB-1,tokens[t]));
            for (int d=0;d<DIM;d++) x[t][d]=embeds[tok][d]+posEnc[t][d];
        }

        // Layer 1
        x = transformerBlock(x, Wq1,Wk1,Wv1,Wo1,Wff1a,Wff1b);
        // Layer 2
        x = transformerBlock(x, Wq2,Wk2,Wv2,Wo2,Wff2a,Wff2b);

        // Letztes Token als Output-Repräsentation
        float[] last = x[CTX-1];

        // Logits (nächstes Token)
        float[] logits = matVec(W_lm, last);
        int nextToken = argmax(softmax(logits));

        // Intentions-Vektor
        float[] intent = new float[INT_DIM];
        float[] raw    = matVec(W_int, last);
        for (int i=0;i<INT_DIM;i++) intent[i]=sigmoid(raw[i]);

        return new TransformerOutput(intent, nextToken, logits, last.clone());
    }

    // ── Transformer Block ─────────────────────────────────────

    private float[][] transformerBlock(float[][] x,
            float[][][] Wq, float[][][] Wk, float[][][] Wv, float[][] Wo,
            float[][] Wffa, float[][] Wffb) {

        // Multi-Head Self-Attention
        float[][] attn = multiHeadAttention(x, Wq, Wk, Wv, Wo);
        // Residual + LayerNorm
        float[][] x2 = layerNorm(add(x, attn));

        // Feed-Forward
        float[][] ff = feedForward(x2, Wffa, Wffb);
        // Residual + LayerNorm
        return layerNorm(add(x2, ff));
    }

    private float[][] multiHeadAttention(float[][] x,
            float[][][] Wq, float[][][] Wk, float[][][] Wv, float[][] Wo) {
        float[][] result = new float[CTX][DIM];
        float scale = (float)Math.sqrt(HEAD_D);

        for (int h=0;h<HEADS;h++) {
            // Q, K, V projizieren
            float[][] Q=project(x,Wq[h]), K=project(x,Wk[h]), V=project(x,Wv[h]);
            // Causal Attention
            float[][] out = new float[CTX][HEAD_D];
            for (int t=0;t<CTX;t++) {
                float[] scores = new float[t+1];
                for (int s=0;s<=t;s++) {
                    float dot=0; for (int d=0;d<HEAD_D;d++) dot+=Q[t][d]*K[s][d];
                    scores[s]=dot/scale;
                }
                float[] weights=softmaxN(scores);
                for (int s=0;s<=t;s++)
                    for (int d=0;d<HEAD_D;d++) out[t][d]+=weights[s]*V[s][d];
            }
            // Head-Output in result konkatenieren
            for (int t=0;t<CTX;t++)
                for (int d=0;d<HEAD_D;d++) result[t][h*HEAD_D+d]+=out[t][d];
        }
        // Output-Projektion
        return project(result, Wo);
    }

    private float[][] feedForward(float[][] x, float[][] Wa, float[][] Wb) {
        float[][] mid = new float[CTX][FF_DIM];
        float[][] out = new float[CTX][DIM];
        for (int t=0;t<CTX;t++) {
            mid[t]=matVec(Wa,x[t]); for(int d=0;d<FF_DIM;d++) mid[t][d]=gelu(mid[t][d]);
            out[t]=matVec(Wb,mid[t]);
        }
        return out;
    }

    // ── Hilfsfunktionen ───────────────────────────────────────

    private float[][] project(float[][] x, float[][] W) {
        int outD=W.length, inD=W[0].length;
        float[][] r=new float[CTX][outD];
        for (int t=0;t<CTX;t++) {
            for (int o=0;o<outD;o++) {
                float s=0; for(int d=0;d<Math.min(inD,x[t].length);d++) s+=W[o][d]*x[t][d];
                r[t][o]=s;
            }
        }
        return r;
    }

    private float[] matVec(float[][] W, float[] x) {
        float[] r=new float[W.length];
        for (int o=0;o<W.length;o++) for (int d=0;d<Math.min(W[o].length,x.length);d++) r[o]+=W[o][d]*x[d];
        return r;
    }

    private float[][] add(float[][] a, float[][] b) {
        float[][] r=new float[CTX][DIM];
        for (int t=0;t<CTX;t++) for (int d=0;d<DIM;d++) r[t][d]=a[t][d]+b[t][d];
        return r;
    }

    private float[][] layerNorm(float[][] x) {
        float[][] r=new float[CTX][DIM];
        for (int t=0;t<CTX;t++) {
            float mean=0,var=0;
            for (float v:x[t]) mean+=v; mean/=DIM;
            for (float v:x[t]) var+=(v-mean)*(v-mean); var=var/DIM;
            float std=(float)Math.sqrt(var+1e-6);
            for (int d=0;d<DIM;d++) r[t][d]=(x[t][d]-mean)/std;
        }
        return r;
    }

    private float[][] buildPositional() {
        float[][] p=new float[CTX][DIM];
        for (int t=0;t<CTX;t++) for (int d=0;d<DIM;d++)
            p[t][d]=(float)(d%2==0?Math.sin(t/Math.pow(10000,d/(double)DIM)):Math.cos(t/Math.pow(10000,(d-1)/(double)DIM)));
        return p;
    }

    private float[] softmax(float[] x) {
        float m=Float.NEGATIVE_INFINITY; for(float v:x) m=Math.max(m,v);
        float[] e=new float[x.length]; float s=0;
        for(int i=0;i<x.length;i++){e[i]=(float)Math.exp(x[i]-m);s+=e[i];}
        for(int i=0;i<x.length;i++) e[i]/=s; return e;
    }

    private float[] softmaxN(float[] x) { return softmax(x); }

    private int argmax(float[] x) { int b=0; for(int i=1;i<x.length;i++) if(x[i]>x[b]) b=i; return b; }
    private static float gelu(float x){return x*0.5f*(1f+(float)Math.tanh(0.7978845f*(x+0.044715f*x*x*x)));}
    private static float sigmoid(float x){return 1f/(1f+(float)Math.exp(-x));}

    private float[][] rand(java.util.Random rng, int r, int c, float scale) {
        float[][] m=new float[r][c];
        for (float[] row:m) for(int j=0;j<c;j++) row[j]=(float)(rng.nextGaussian()*scale);
        return m;
    }
    private float[][][] randHeads(java.util.Random rng) {
        float[][][] m=new float[HEADS][HEAD_D][DIM];
        for (float[][] h:m) for (float[] row:h) for(int j=0;j<DIM;j++) row[j]=(float)(rng.nextGaussian()*0.1f);
        return m;
    }

    public static class TransformerOutput {
        public final float[] intention;
        public final int     nextToken;
        public final float[] logits;
        public final float[] hiddenState;
        TransformerOutput(float[] i, int t, float[] l, float[] h){intention=i;nextToken=t;logits=l;hiddenState=h;}
    }
}
