package thronglets;

import java.util.*;

/**
 * Thronglet V7 – Das Maximum.
 *
 * Gehirn:       TripleLayerBrain (SNN + FEP + Nano-Transformer)
 *   Reflexe:    SNN (LIF-Neuronen, STDP, Spikes sichtbar im Renderer)
 *   Kognition:  FEP (Beliefs, Freie Energie, Active Inference)
 *   Metakognition: NanoTransformer (Intention + innerer Monolog)
 * Physiologie:  Homeostase (5 Triebe, proto-Emotionen)
 * Nische:       Pheromone (NEAT-Welt aus V6)
 * Evolution:    SNN-Gewichte + NEATGenome für Topologie
 */
public class ThrongletV7 {

    private static int nextId = 0;

    public final int    id;
    public double       x, y;
    public boolean      alive    = true;
    public LifeStage    stage    = LifeStage.EGG;
    public int          groupId  = -1;

    public double       fitness  = 0;
    public double       socialOut = 0;
    public Signal       signalOut = Signal.NONE;

    public final TripleLayerBrain brain;
    public final Homeostasis      homeostasis;
    public final Gene             gene;
    public final Memory           memory;
    public final NEATGenome       genome;

    /** Letzter innerer Monolog für Renderer */
    public String lastThought = "…";
    /** Abkühlzeit bis zur nächsten Reproduktion (in Ticks) */
    public int    reprodCooldown = 0;
    /** Lebens-Meilensteine */
    public int    reproductionCount = 0;
    public int    exploredCount     = 0;
    private final boolean[] exploredGrid = new boolean[400]; // 20×20 Gitter
    private boolean exMile25=false,exMile50=false,exMile75=false,exMile100=false;
    private boolean reMile1=false,reMile5=false,reMile10=false;
    private boolean ageMile500=false,ageMile1000=false,ageMile2000=false;

    private final Random rng;
    private final long   brainSeed;

    // ─── Konstruktoren ────────────────────────────────────────────────────────

    public ThrongletV7(double x, double y, Random rng) {
        id         = nextId++;
        this.x     = x;
        this.y     = y;
        this.rng   = rng;
        brainSeed  = rng.nextLong();
        brain      = new TripleLayerBrain(brainSeed, rng);
        homeostasis= new Homeostasis(rng);
        gene       = new Gene(rng);
        memory     = new Memory();
        genome     = new NEATGenome(rng);
        homeostasis.drives[DriveType.ENERGY.id] = 55 + rng.nextDouble() * 35;
    }

    public ThrongletV7(double x, double y, ThrongletV7 parent, ThrongletV7 partner, Random rng) {
        id         = nextId++;
        this.x     = x;
        this.y     = y;
        this.rng   = rng;
        brainSeed  = rng.nextLong();
        brain      = parent.brain.copy(brainSeed, rng);
        brain.snn.mutate(rng, parent.gene.mutationRate);
        homeostasis= new Homeostasis(rng);
        gene       = Gene.crossover(parent.gene, partner != null ? partner.gene : parent.gene, rng);
        memory     = new Memory();
        genome     = NEATGenome.crossover(parent.genome,
                partner != null ? partner.genome : parent.genome, rng);
        genome.mutate(rng);
        homeostasis.drives[DriveType.ENERGY.id] = 45;
    }

    // ─── Haupt-Tick ───────────────────────────────────────────────────────────

    public ThrongletV7 tick(WorldV6 world, Signal groupSig,
                            double gfx, double gfy,
                            ThrongletV7 nearestMate, int groupSize, int totalPop) {
        if (!alive) return null;
        if (reprodCooldown > 0) reprodCooldown--;

        stage = LifeStage.forAge(memory.age);
        if (memory.age >= LifeStage.ELDER.maxAge) { alive = false; return null; }

        // ① Inputs
        float[] inp = buildInputs(world, groupSig, nearestMate, totalPop);

        // ② Dreischichtiges Gehirn
        float[] out = brain.forward(inp, homeostasis, groupSize);

        // ③ Innerer Monolog
        lastThought = brain.getLastThought();

        // ④ Priorisiertes Verhalten (Wasser > Hunger > Kälte > Paarung > Erkunden)
        double waterD   = world.waterDepthAt(x, y);
        double vitality = homeostasis.drives[DriveType.VITALITY.id];
        // Vitalität: hohe Vitalität = schneller und widerstandsfähiger
        double vitalMod = 0.9 + vitality / 500.0; // 0→0.9×, 50→1.0×, 100→1.1×
        double speed    = gene.moveSpeed * SimConfig.INSTANCE.speedFactor * stage.speedMod
                        * (1.0 - waterD * 0.65) * vitalMod;
        double energy = homeostasis.drives[DriveType.ENERGY.id];
        double warmth = homeostasis.drives[DriveType.WARMTH.id];

        boolean flee   = waterD > 0.25                              // Wasser → sofort fliehen
                      || (out[6] > gene.fearThreshold
                          && world.nearestDangerDist(x, y) < gene.sensorRange);
        boolean hungry = energy < 55;   // früher reagieren – nicht erst im Notfall
        boolean peckish = energy < 75 && !hungry; // sanfter Hunger: leichte Anziehung zu Nahrung
        boolean cold   = warmth < 30;

        // Überlebensinstinkt: niedrige Population senkt Anforderungen für Paarung
        boolean critical = totalPop <= 4;
        double energyForMating = critical ? 45.0 : 65.0;
        boolean mating = !hungry && !cold
                      && stage.canReproduce()
                      && memory.age >= (int) gene.reproductionAge
                      && energy > energyForMating && homeostasis.valence > 0.1;

        double mx = 0, my = 0;

        if (flee) {
            // Gefahr hat höchste Priorität
            double[] esc = escapeDir(world);
            mx = esc[0] * speed * 2.5;
            my = esc[1] * speed * 2.5;

        } else if (hungry) {
            // Direkt zur nächsten Nahrungsquelle navigieren
            double[] fp = world.nearestFoodPos(x, y);
            if (fp != null) {
                double dx = fp[0]-x, dy = fp[1]-y, d = Math.sqrt(dx*dx+dy*dy);
                if (d > 0) { mx = dx/d * speed * 2.2; my = dy/d * speed * 2.2; }
            } else {
                // Kein Futter sichtbar → Pheromonspur folgen
                double[] grad = world.niche.gradient(x, y, PheromoneType.FOOD_TRAIL, gene.sensorRange);
                mx = grad[0] * speed * 1.5; my = grad[1] * speed * 1.5;
            }

        } else if (cold) {
            // Direkt zum nächsten Lagerfeuer navigieren
            double[] fp = world.nearestFirePos(x, y);
            if (fp != null) {
                double dx = fp[0]-x, dy = fp[1]-y, d = Math.sqrt(dx*dx+dy*dy);
                if (d > 0) { mx = dx/d * speed * 1.8; my = dy/d * speed * 1.8; }
            }

        } else if (peckish) {
            // Leicht hungrig: sanfte Anziehung zur nächsten Nahrung (überschreibt Erkundung nicht komplett)
            double[] fp = world.nearestFoodPos(x, y);
            if (fp != null) {
                double dx = fp[0]-x, dy = fp[1]-y, d = Math.sqrt(dx*dx+dy*dy);
                if (d > 0) {
                    double snnMod = (out[0] - 0.5) * Math.PI;
                    double angle  = gene.epiBias * 2 * Math.PI + snnMod;
                    double spd    = speed * (0.5 + SimConfig.INSTANCE.curiosityFactor *
                            (homeostasis.drives[DriveType.CURIOSITY.id] / 400.0));
                    // 60% Nahrungsrichtung, 40% Erkundung → bleibt in der Nähe
                    mx = dx/d * speed * 0.9 + Math.cos(angle) * spd * 0.4;
                    my = dy/d * speed * 0.9 + Math.sin(angle) * spd * 0.4;
                }
            }

        } else if (mating && nearestMate != null) {
            // Zum Partner navigieren
            double dx = nearestMate.x - x, dy = nearestMate.y - y;
            double d  = Math.sqrt(dx*dx + dy*dy);
            if (d > 8 && d < gene.sensorRange * 2.5) {
                mx = dx/d * speed * gene.reproductiveDrive * 1.5;
                my = dy/d * speed * gene.reproductiveDrive * 1.5;
            } else {
                double snnMod = (out[0] - 0.5) * Math.PI;
                double angle  = gene.epiBias * 2 * Math.PI + snnMod;
                double spd    = speed * (0.5 + SimConfig.INSTANCE.curiosityFactor *
                        (homeostasis.drives[DriveType.CURIOSITY.id] / 400.0));
                mx = Math.cos(angle) * spd; my = Math.sin(angle) * spd;
            }

        } else {
            // Normales Erkunden: genetische Basisrichtung + SNN-Modulation ±90°
            double snnMod = (out[0] - 0.5) * Math.PI;
            double angle  = gene.epiBias * 2 * Math.PI + snnMod;
            double spd    = speed * (0.5 + SimConfig.INSTANCE.curiosityFactor *
                    (homeostasis.drives[DriveType.CURIOSITY.id] / 400.0));
            mx = Math.cos(angle) * spd;
            my = Math.sin(angle) * spd;
        }

        // Gruppen-Navigation (immer außer beim Flüchten, aber schwächer im Notstand)
        if (!flee && gfx >= 0 && out[8] > 0.5) {
            double dx = gfx - x, dy = gfy - y, d = Math.sqrt(dx*dx + dy*dy);
            if (d > 1) {
                double gForce = (hungry || peckish || cold) ? gene.socialAffinity * 0.3 : gene.socialAffinity;
                mx += dx/d * speed * gForce;
                my += dy/d * speed * gForce;
            }
        }

        // Anti-Stuck: zufällige Störung wenn festgehangen
        double moved = Math.sqrt(mx*mx + my*my);
        if (memory.isStuck() && !flee) {
            mx += rng.nextGaussian() * speed;
            my += rng.nextGaussian() * speed;
        }

        // ── Wandabstoßung: stark genug um jede genetische Richtung zu überwältigen ──
        double wd = 45.0;
        if (x < wd)
            mx +=  (wd - x) / wd * speed * 3.5;
        if (x > world.width - wd)
            mx -= (x - (world.width - wd)) / wd * speed * 3.5;
        if (y < wd)
            my +=  (wd - y) / wd * speed * 3.5;
        if (y > world.height - wd)
            my -= (y - (world.height - wd)) / wd * speed * 3.5;

        // ── Positions-Update mit NaN-Guard ──────────────────────────────────
        if (stage.canMove()) {
            double nx = world.clampX(x + mx);
            double ny = world.clampY(y + my);
            if (Double.isNaN(nx) || Double.isInfinite(nx)
                    || Double.isNaN(ny) || Double.isInfinite(ny)) {
                nx = world.clampX(world.width  * 0.1 + rng.nextDouble() * world.width  * 0.8);
                ny = world.clampY(world.height * 0.1 + rng.nextDouble() * world.height * 0.8);
            }
            x = nx;
            y = ny;
        }

        // ⑤ Pheromone
        if (stage.canMove()) {
            world.niche.deposit(x, y, PheromoneType.TRAIL, 0.025);
            if (homeostasis.drives[DriveType.SOCIAL.id] > 60)
                world.niche.deposit(x, y, PheromoneType.SOCIAL, 0.04);
            if (mating && nearestMate != null)
                world.niche.deposit(x, y, PheromoneType.SOCIAL, 0.08); // Paarungslocksignal
        }

        // ⑥ Nahrung – immer essen wenn nicht satt und Nahrung in Reichweite (kein SNN-Gate)
        boolean atFood = false;
        if (stage != LifeStage.EGG && energy < 90) {
            double eaten = world.consumeFood(x, y, gene.sensorRange * 0.4);
            if (eaten > 0) {
                homeostasis.drives[DriveType.ENERGY.id] =
                        Math.min(100, homeostasis.drives[DriveType.ENERGY.id] + eaten);
                fitness += eaten * 0.4;
                memory.logFood(world.getTick());
                world.niche.deposit(x, y, PheromoneType.FOOD_TRAIL, 0.15);
                brain.reinforce(2, (float)(eaten / 10.0));
                atFood = true;
            }
        }

        // ⑦ Signal
        if (out[4] > 1.0 - gene.languageAptitude * 0.5 && stage.canMove()) {
            signalOut = Signal.fromFloat(out[5]);
            socialOut = gene.socialAffinity;
        } else {
            signalOut = Signal.NONE;
            socialOut = 0;
        }

        // ⑧ Nest-Marker
        if (out[7] > 0.68 && stage != LifeStage.EGG)
            world.niche.deposit(x, y, PheromoneType.NEST, 0.08);

        // ⑨ Homeostase
        homeostasis.tick(world.currentSeason, groupId >= 0, moved, atFood);

        // Vitalität: passive Boni bei hoher Vitalität
        double vit = homeostasis.drives[DriveType.VITALITY.id];
        if (vit > 70) {
            homeostasis.drives[DriveType.ENERGY.id] = Math.min(100, homeostasis.drives[DriveType.ENERGY.id] + 0.08);
            homeostasis.drives[DriveType.STRESS.id] = Math.max(0,  homeostasis.drives[DriveType.STRESS.id] - 0.08);
        }

        // Karte erkunden – 20×20-Gitter tracken
        int cellX = Math.min(19, (int)(x * 20 / world.width));
        int cellY = Math.min(19, (int)(y * 20 / world.height));
        int cell  = cellY * 20 + cellX;
        if (!exploredGrid[cell]) {
            exploredGrid[cell] = true;
            exploredCount++;
            checkExplorationMilestones();
        }

        // Alters-Meilensteine
        checkAgeMilestones();

        // Wärme von Lagerfeuern anwenden
        double fireWarmth = world.warmthAt(x, y);
        if (fireWarmth > 0) {
            homeostasis.drives[DriveType.WARMTH.id] =
                    Math.min(100, homeostasis.drives[DriveType.WARMTH.id] + fireWarmth);
            brain.reinforce(5, (float)(fireWarmth * 0.5)); // Wärme positiv belohnen
        }

        double dmg = world.dangerDamage(x, y);
        if (dmg > 0) {
            double eff = dmg * (groupId >= 0 ? 0.55 : 1.0);
            homeostasis.drives[DriveType.ENERGY.id] = Math.max(0, homeostasis.drives[DriveType.ENERGY.id] - eff);
            // Kein Stress durch Wasser – nur Energie-Drain
            memory.logDanger(world.getTick());
        }
        if (groupId >= 0) homeostasis.applySocial(true);

        // ⑩ Fitness
        fitness += homeostasis.wellbeing() * 0.12 + 0.05;
        if (brain.getPredError() < 0.15) fitness += 0.03;

        memory.tick(new double[]{
                homeostasis.drives[DriveType.ENERGY.id] / 100.0,
                x / world.width,
                y / world.height
        });

        if (homeostasis.isDead()) { alive = false; return null; }

        // ⑪ Reproduktion – Wohlbefinden nötig; bei kritischer Pop. werden Schwellen gesenkt
        double valenceNeeded = critical ? 0.1 : 0.5;
        double energyNeeded  = critical ? 45.0 : 65.0;
        boolean canRepro = mating
                && homeostasis.drives[DriveType.ENERGY.id] > energyNeeded
                && homeostasis.valence > valenceNeeded
                && reprodCooldown <= 0
                && nearestMate != null && nearestMate.stage.canReproduce();
        if (canRepro) {
            homeostasis.drives[DriveType.ENERGY.id] -= 30;
            reprodCooldown = critical ? 150 : 400;  // Kürzere Pause wenn Art bedroht
            reproductionCount++;
            checkReproductionMilestones();
            fitness += 12;
            memory.logMate(world.getTick());
            brain.reinforce(3, 2.0f);
            ThrongletV7 child = new ThrongletV7(
                    x + rng.nextGaussian() * 8,
                    y + rng.nextGaussian() * 8,
                    this, nearestMate, rng);
            return child;
        }

        return null;
    }

    // ─── Meilenstein-Methoden ────────────────────────────────────────────────

    private void checkExplorationMilestones() {
        if (!exMile25  && exploredCount >= 100) { exMile25=true;  homeostasis.applyVitalityBoost(8);  fitness+=5; }
        if (!exMile50  && exploredCount >= 200) { exMile50=true;  homeostasis.applyVitalityBoost(10); fitness+=8; }
        if (!exMile75  && exploredCount >= 300) { exMile75=true;  homeostasis.applyVitalityBoost(12); fitness+=10; }
        if (!exMile100 && exploredCount >= 400) { exMile100=true; homeostasis.applyVitalityBoost(15); fitness+=15; }
    }

    private void checkReproductionMilestones() {
        if (!reMile1  && reproductionCount >= 1)  { reMile1=true;  homeostasis.applyVitalityBoost(10); fitness+=8; }
        if (!reMile5  && reproductionCount >= 5)  { reMile5=true;  homeostasis.applyVitalityBoost(12); fitness+=12; }
        if (!reMile10 && reproductionCount >= 10) { reMile10=true; homeostasis.applyVitalityBoost(15); fitness+=20; }
    }

    private void checkAgeMilestones() {
        if (!ageMile500  && memory.age >= 500)  { ageMile500=true;  homeostasis.applyVitalityBoost(5);  fitness+=4; }
        if (!ageMile1000 && memory.age >= 1000) { ageMile1000=true; homeostasis.applyVitalityBoost(8);  fitness+=8; }
        if (!ageMile2000 && memory.age >= 2000) { ageMile2000=true; homeostasis.applyVitalityBoost(10); fitness+=12; }
    }

    // ─── Hilfsmethoden ────────────────────────────────────────────────────────

    /** Schützt gegen NaN/Infinity aus FEP und klemmt auf sinnvollen Bereich. */
    private float safeFloat(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return 0f;
        if (v >  1e3) return 1000f;
        if (v < -1e3) return -1000f;
        return (float) v;
    }

    /**
     * 24 Sensor-Inputs – jedes sensorische Objekt hat Distanz + Richtungsvektor (dx,dy → [0,1]).
     * Richtungskodierung: 0 = links/oben, 0.5 = kein X/Y-Anteil, 1 = rechts/unten.
     */
    private float[] buildInputs(WorldV6 world, Signal groupSig, ThrongletV7 nearestAgent, int totalPop) {
        float[] h   = homeostasis.toInputVector();
        float[] inp = new float[SpikingBrain.IN]; // 24

        // [0-6] Homöostase: Energie, Stress, Sozial, Neugier, Wärme, Valenz, Arousal
        System.arraycopy(h, 0, inp, 0, Math.min(h.length, 7));

        // [7-9] Nächste Nahrung: Nähe + Richtung
        double[] fp = world.nearestFoodPos(x, y);
        if (fp != null) {
            double dx = fp[0]-x, dy = fp[1]-y, d = Math.sqrt(dx*dx+dy*dy);
            inp[7] = (float) Math.max(0, 1 - d / gene.sensorRange);
            inp[8] = (float) ((d > 0 ? dx/d : 0) * 0.5 + 0.5);   // [-1,1] → [0,1]
            inp[9] = (float) ((d > 0 ? dy/d : 0) * 0.5 + 0.5);
        }

        // [10-12] Nächstes Lagerfeuer: Nähe + Richtung
        double[] fireP = world.nearestFirePos(x, y);
        if (fireP != null) {
            double dx = fireP[0]-x, dy = fireP[1]-y, d = Math.sqrt(dx*dx+dy*dy);
            inp[10] = (float) Math.max(0, 1 - d / (gene.sensorRange * 3));
            inp[11] = (float) ((d > 0 ? dx/d : 0) * 0.5 + 0.5);
            inp[12] = (float) ((d > 0 ? dy/d : 0) * 0.5 + 0.5);
        }

        // [13-15] Nächster Thronglet: Nähe + Richtung
        if (nearestAgent != null) {
            double dx = nearestAgent.x-x, dy = nearestAgent.y-y, d = Math.sqrt(dx*dx+dy*dy);
            inp[13] = (float) Math.max(0, 1 - d / (gene.sensorRange * 2));
            inp[14] = (float) ((d > 0 ? dx/d : 0) * 0.5 + 0.5);
            inp[15] = (float) ((d > 0 ? dy/d : 0) * 0.5 + 0.5);
        }

        // [16-19] Wand-Abstände: links, rechts, oben, unten (0=weit, 1=direkt an der Wand)
        double ws = 70.0; // Sensorreichweite für Wände
        inp[16] = (float) Math.max(0, 1 - x / ws);
        inp[17] = (float) Math.max(0, 1 - (world.width  - x) / ws);
        inp[18] = (float) Math.max(0, 1 - y / ws);
        inp[19] = (float) Math.max(0, 1 - (world.height - y) / ws);

        // [20] Gefahr-Nähe
        inp[20] = (float) Math.max(0, 1 - world.nearestDangerDist(x, y) / gene.sensorRange);

        // [21] Jahreszeit
        inp[21] = (float) world.currentSeason.toFloat();

        // [22] Alter (0-1)
        inp[22] = (float) Math.min(1, memory.age / 1500.0);

        // [23] Freie Energie (als Meta-Selbstsignal)
        inp[23] = safeFloat(brain.getLastFE());

        // [24] Populationsdruck: 0 = volle Pop, 1 = Aussterben droht
        inp[24] = (float) Math.max(0, 1.0 - (double) totalPop / SimulationV7.MAX_POP);

        // [25] Vitalität (normiert)
        inp[25] = (float)(homeostasis.drives[DriveType.VITALITY.id] / 100.0);

        return inp;
    }

    private double[] escapeDir(World world) {
        double ex = 0, ey = 0;
        // Wasser-Flucht: direkt zur Kartenmitte (stärker je tiefer das Wasser)
        double depth = world.waterDepthAt(x, y);
        if (depth > 0) {
            double toCx = world.width/2 - x, toCy = world.height/2 - y;
            double dist = Math.sqrt(toCx*toCx + toCy*toCy);
            if (dist > 0) { ex += toCx/dist * depth * 3; ey += toCy/dist * depth * 3; }
        }
        // Klassische Gefahren-Flucht (falls noch Danger-Circles vorhanden)
        for (double[] d : world.getDangerList()) {
            double dx   = x - d[0], dy = y - d[1];
            double dist = Math.sqrt(dx*dx + dy*dy);
            if (dist < d[2] * 2 && dist > 0) { ex += dx / dist; ey += dy / dist; }
        }
        double len = Math.sqrt(ex*ex + ey*ey);
        return len > 0 ? new double[]{ex/len, ey/len}
                : new double[]{rng.nextGaussian(), rng.nextGaussian()};
    }

    @Override
    public String toString() {
        return String.format("T#%3d [%s|FE=%.2f|PE=%.2f] E=%.0f Val=%.2f Gedanke:'%s'",
                id, stage.label,
                brain.getLastFE(), brain.getPredError(),
                homeostasis.drives[0], homeostasis.valence, lastThought);
    }
}