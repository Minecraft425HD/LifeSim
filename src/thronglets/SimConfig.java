package thronglets;

public class SimConfig {

    // ── Bewegung ─────────────────────────────
    public volatile double speedFactor      = 0.6;  // globaler Speed-Multiplikator
    public volatile double curiosityFactor  = 0.5;  // Einfluss von Neugier auf Speed

    // ── Homeostase ──────────────────────────
    public volatile double energyDecayFactor = 0.6;  // 1.0 = original, <1 = spart Energie
    public volatile double stressIncrease    = 0.02; // Stress pro Tick
    public volatile double warmthWinterLoss  = 0.15; // Wärmeverlust pro Tick im Winter

    // ── Welt ────────────────────────────────
    public volatile int    baseFoodCount     = 55;   // Start-Food in SimulationV7
    public volatile double foodRegenFactor   = 1.2;  // Multiplikator auf Food-Regeneration
    public volatile int    dangerCount       = 0;    // 0 = Wasser übernimmt Rand-Gefahren
    public volatile double dangerStrength    = 1.0;  // Multiplikator für Danger-Schaden

    // Singleton-Instanz
    public static final SimConfig INSTANCE = new SimConfig();

    private SimConfig() {}
}