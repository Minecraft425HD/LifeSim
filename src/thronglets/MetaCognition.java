package thronglets;

import java.util.*;

/**
 * Meta-Kognition: Verwaltet den inneren Monolog des Thronglet.
 *
 * Jeder Token entspricht einem internen Konzept.
 * Der Nano-Transformer sagt den nächsten Token vorher →
 * das ist das "Denken" des Agenten.
 *
 * Alle 10 Ticks: Transformer-Forward-Pass → neue Intention
 * Kontinuierlich: Token-History aktualisieren
 */
public class MetaCognition {

    // ── Token-Vokabular (innere Konzepte) ─────────────────────
    public static final String[] VOCAB = {
        "Hunger",    "Satt",       "Gefahr!",   "Sicher",
        "Allein",    "InGruppe",   "Neugierig", "Erschöpft",
        "Glücklich", "Besorgt",    "NahrungNah","Erkunde",
        "Ruhe",      "Fliehe!",    "Fortpflanz","Sozial",
        "Kalt",      "Warm",       "Überrasch", "Vertraut",
        "Stark",     "Schwach",    "Lerne",     "Erinnere",
        "Plane",     "Reagiere",   "Beobachte", "Warte",
        "Angreife",  "Verbünde",   "Sterbe",    "Geburt"
    };

    private final NanoTransformer transformer;
    private final int[]   tokenHistory = new int[NanoTransformer.CTX];
    private       float[] intention    = new float[NanoTransformer.INT_DIM];
    private       int     lastToken    = 0;
    private       int     ticksSinceUpdate = 0;
    private static final int UPDATE_INTERVAL = 10; // Ticks zwischen Transformer-Läufen

    // Innerer Monolog (letzte 5 Gedanken)
    private final Deque<String> monolog = new ArrayDeque<>(5);

    public MetaCognition(long seed) {
        transformer = new NanoTransformer(seed);
        Arrays.fill(tokenHistory, 0);
    }

    // ── Tick-Update ───────────────────────────────────────────

    /**
     * Tick der Meta-Kognition. Alle UPDATE_INTERVAL Ticks: Transformer-Forward-Pass.
     * @param homeostasis   Homeostase-Zustand
     * @param freeEnergy    Aktuelle freie Energie (aus FEP)
     * @param predError     Vorhersagefehler (aus FEP)
     * @param groupSize     Größe der aktuellen Gruppe
     */
    public float[] tick(Homeostasis homeostasis, float freeEnergy, float predError, int groupSize) {
        // ① Aktuellen Zustand als Token enkodieren
        int newToken = encodeState(homeostasis, freeEnergy, predError, groupSize);

        // Token-History verschieben
        System.arraycopy(tokenHistory, 1, tokenHistory, 0, NanoTransformer.CTX-1);
        tokenHistory[NanoTransformer.CTX-1] = newToken;

        ticksSinceUpdate++;

        // ② Alle UPDATE_INTERVAL Ticks: Transformer-Forward-Pass
        if (ticksSinceUpdate >= UPDATE_INTERVAL) {
            ticksSinceUpdate = 0;
            NanoTransformer.TransformerOutput out = transformer.forward(tokenHistory);
            intention = out.intention;
            lastToken = out.nextToken;

            // Inneren Monolog aktualisieren
            String thought = VOCAB[Math.min(lastToken, VOCAB.length-1)];
            if (monolog.size()>=5) monolog.pollFirst();
            monolog.addLast(thought);
        }
        return intention.clone();
    }

    // ── Zustand → Token-Enkodierung ───────────────────────────

    private int encodeState(Homeostasis h, float fe, float pe, int grpSz) {
        double energy   = h.drives[DriveType.ENERGY.id];
        double stress   = h.drives[DriveType.STRESS.id];
        double curiosity= h.drives[DriveType.CURIOSITY.id];
        double social   = h.drives[DriveType.SOCIAL.id];

        // Prioritätsbasierte Token-Wahl
        if (energy < 25)                  return 0;  // Hunger
        if (stress > 70)                  return 2;  // Gefahr!
        if (grpSz == 0 && social < 30)    return 4;  // Allein
        if (grpSz > 0)                    return 5;  // InGruppe
        if (energy > 75 && h.valence>0.4) return 1;  // Satt
        if (curiosity > 65)               return 6;  // Neugierig
        if (energy < 40)                  return 7;  // Erschöpft
        if (h.valence > 0.6)              return 8;  // Glücklich
        if (pe > 0.4)                     return 18; // Überrasch
        if (fe < 0.1)                     return 19; // Vertraut
        if (h.arousal > 0.7)              return 25; // Reagiere
        return 26; // Beobachte (Default)
    }

    // ── Getter ────────────────────────────────────────────────

    public float[] getIntention()  { return intention.clone(); }
    public String  getLastThought(){ return monolog.isEmpty() ? "…" : monolog.peekLast(); }
    public List<String> getMonolog(){ return new ArrayList<>(monolog); }
    public int     getCurrentToken(){ return lastToken; }
    public String  tokenName(int t) { return t<VOCAB.length?VOCAB[t]:"?"; }
}
