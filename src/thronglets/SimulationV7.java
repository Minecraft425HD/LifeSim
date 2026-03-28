package thronglets;

import java.util.*;
import java.util.stream.Collectors;

public class SimulationV7 implements Runnable {

    public static final int    WORLD_W  = 500, WORLD_H  = 500;
    public static final int    START_POP= 6,  MIN_POP  = 3, MAX_POP = 100;
    public static final long   TICK_MS  = 28;
    public static final double GRP_R    = 38.0;

    private final WorldV6           world;
    private       List<ThrongletV7> pop;
    private final Map<Integer,Group>groups = new HashMap<>();
    private final NEATEvolution     neat;
    private final Random            rng;
    private       int               generation = 0;
    private final RendererV7        renderer;
    private volatile boolean        running = true;
    private int                     extinctionCount = 0;
    private final List<Double>      genFitnessHistory = new ArrayList<>();

    public SimulationV7(long seed, RendererV7 r) {
        rng      = new Random(seed);
        SimConfig cfg = SimConfig.INSTANCE;
        world    = new WorldV6(WORLD_W, WORLD_H, cfg.baseFoodCount, cfg.dangerCount, rng);
        neat     = new NEATEvolution(rng);
        renderer = r;
        pop      = new ArrayList<>();
        for (int i=0;i<START_POP;i++) {
            double a = rng.nextDouble()*2*Math.PI;
            double rad = rng.nextDouble()*80;
            pop.add(new ThrongletV7(WORLD_W/2.0+Math.cos(a)*rad, WORLD_H/2.0+Math.sin(a)*rad, rng));
        }
    }

    public void stop() { running=false; }

    @Override
    public void run() {
        System.out.println("═══════════════════════════════════════════════════");
        System.out.println("  THRONGLETS  V9  –  MAXIMUM LIFE");
        System.out.println("  SNN (STDP) + FEP (Friston) + Nano-Transformer");
        System.out.println("  Episodisches Gedächtnis + Brain-Bars + Kontrollen");
        System.out.println("═══════════════════════════════════════════════════\n");

        while (running) {
            long t0 = System.currentTimeMillis();

            // Spieler-platzierte Nahrung einfügen
            if (renderer != null) {
                double[] fp;
                while ((fp=renderer.getFoodQueue().poll())!=null)
                    world.addFood(fp[0],fp[1]);
            }

            world.tick();
            updateGroups();

            List<ThrongletV7> newborns = new ArrayList<>();

            for (ThrongletV7 t : pop) {
                if (!t.alive) continue;
                Group  g   = t.groupId>=0?groups.get(t.groupId):null;
                Signal sig = g!=null?g.sharedSignal:Signal.NONE;
                double gfx = g!=null?g.sharedFoodX:-1;
                double gfy = g!=null?g.sharedFoodY:-1;
                int    gsz = g!=null?g.size():0;

                ThrongletV7 child = t.tick(world, sig, gfx, gfy, nearestAgent(t), gsz, pop.size());

                if (t.signalOut==Signal.FOOD_NEAR&&g!=null) {
                    double[] fp=world.nearestFoodPos(t.x,t.y);
                    if (fp!=null) g.broadcastFood(fp[0],fp[1]);
                }
                // Gruppen-Broadcast: jedes Signal wird an die ganze Gruppe gesendet
                if (t.signalOut!=Signal.NONE&&g!=null) g.sharedSignal=t.signalOut;
                if (child!=null&&pop.size()+newborns.size()<MAX_POP) {
                    child.memory.logEvent(Memory.EV_BIRTH, world.getTick());
                    newborns.add(child);
                }
            }

            pop.addAll(newborns);

            // Todesereignisse für Renderer erfassen
            if (renderer != null) {
                for (ThrongletV7 t : pop) if (!t.alive) {
                    String cause =
                            t.homeostasis.drives[0]<=0.1? "Hunger" :
                                    t.homeostasis.drives[1]>=99.9? "Stress" :
                                            t.homeostasis.drives[4]<=0.1? "Kälte" :
                                                    "Alter";
                    renderer.addDeath(t.x, t.y, world.getTick(), cause);
                }
            }

            pop.removeIf(t->!t.alive);

            // Aussterbe-Erkennung: Strafe + Warnung
            if (pop.isEmpty() && world.getTick() > 10) {
                extinctionCount++;
                System.out.printf("%n  ╔══════════════════════════════════════════╗%n");
                System.out.printf("  ║  AUSSTERBEN #%d bei Tick %5d !          ║%n", extinctionCount, world.getTick());
                System.out.printf("  ║  Die Art hat versagt. Fitness halbiert.  ║%n");
                System.out.printf("  ╚══════════════════════════════════════════╝%n%n");
                if (renderer != null) renderer.notifyExtinction(extinctionCount);
            }

            groups.values().forEach(Group::tick);

            // ── Nest-Bonus: 3+ Agenten im Zentrum (Radius 120px) = Gemeinschaftsbonus ──
            {
                final double nestR=120.0, cx=WORLD_W/2.0, cy=WORLD_H/2.0;
                int nc=0;
                for (ThrongletV7 t:pop) if(t.alive&&World.dist(t.x,t.y,cx,cy)<nestR) nc++;
                final boolean nestActive=nc>=3;
                for (ThrongletV7 t:pop) {
                    if(!t.alive||World.dist(t.x,t.y,cx,cy)>=nestR) continue;
                    // Immer: weniger Stress, mehr Wärme im Zentrum
                    t.homeostasis.drives[DriveType.STRESS.id]=Math.max(0,t.homeostasis.drives[DriveType.STRESS.id]-2.5);
                    t.homeostasis.drives[DriveType.WARMTH.id]=Math.min(100,t.homeostasis.drives[DriveType.WARMTH.id]+0.8);
                    // Nur wenn Rudel aktiv: Fitness-Bonus + halber Reprod.-Cooldown
                    if(nestActive){ if(t.reprodCooldown>200) t.reprodCooldown=200; t.fitness+=0.15; }
                }
                if(renderer!=null) renderer.setNestCount(nc);
            }

            for (ThrongletV7 t:pop) {
                t.syncWeightsToGenome();   // STDP-gelernte Gewichte ins Genom übertragen
                t.genome.fitness=t.fitness;
            }

            if (world.getTick()%3==0&&renderer!=null)
                renderer.update(new ArrayList<>(pop), world, groups, generation, neat.speciesCount(), genFitnessHistory);

            if (world.getTick()%200==0) logStats();

            if (pop.size()<MIN_POP) {
                // Beste Fitness dieser Generation festhalten
                double bestFit = pop.stream().mapToDouble(t->t.fitness).max().orElse(0);
                genFitnessHistory.add(bestFit);
                generation++;
                List<NEATGenome> genomes=pop.stream().map(t->t.genome).collect(Collectors.toList());
                // Aussterbe-Strafe: Fitness der überlebenden Genome stark reduzieren
                if (pop.isEmpty()) genomes.forEach(g -> g.fitness *= 0.2);
                while (genomes.size()<START_POP) genomes.add(new NEATGenome(rng));
                List<NEATGenome> evolved=neat.evolve(genomes, START_POP);
                pop.clear();
                for (NEATGenome g:evolved) {
                    double a = rng.nextDouble()*2*Math.PI;
                    double rad = rng.nextDouble()*80;
                    // Evolviertes Genom wird genutzt – Gehirn startet mit geerbten Gewichten
                    pop.add(new ThrongletV7(WORLD_W/2.0+Math.cos(a)*rad, WORLD_H/2.0+Math.sin(a)*rad, g, rng));
                }
                System.out.printf("  ↳ Gen %d | Spezies: %d | Aussterbungen: %d%n",
                        generation, neat.speciesCount(), extinctionCount);
            }

            long el=System.currentTimeMillis()-t0;
            if (el<TICK_MS) {
                try { Thread.sleep(TICK_MS-el); } catch(InterruptedException e){ break; }
            }
        }
    }

    private void updateGroups() {
        groups.values().removeIf(g->g.size()<2);
        for (ThrongletV7 t:pop)
            if(t.groupId>=0&&!groups.containsKey(t.groupId)) t.groupId=-1;

        for (ThrongletV7 t:pop) {
            if(!t.alive||t.stage==LifeStage.EGG||t.socialOut<=0) continue;
            for (ThrongletV7 o:pop) {
                if(o.id==t.id||!o.alive) continue;
                if(World.dist(t.x,t.y,o.x,o.y)>GRP_R) continue;
                if(t.groupId>=0&&groups.containsKey(t.groupId)){
                    groups.get(t.groupId).addMember(o.id);o.groupId=t.groupId;
                } else if(o.groupId>=0&&groups.containsKey(o.groupId)){
                    groups.get(o.groupId).addMember(t.id);t.groupId=o.groupId;
                } else {
                    Group ng=new Group();ng.addMember(t.id);ng.addMember(o.id);
                    t.groupId=ng.id;o.groupId=ng.id;groups.put(ng.id,ng);
                }
            }
        }
        Map<Integer,ThrongletV7> byId=new HashMap<>();
        for(ThrongletV7 t:pop) byId.put(t.id,t);
        for(Group g:groups.values()){
            int alpha=g.memberIds.stream().filter(byId::containsKey)
                    .max(Comparator.comparingDouble(id->byId.get(id).fitness)).orElse(-1);
            g.alphaId=alpha;
        }
    }

    /** Nächster beliebiger lebender Thronglet (für Navigation + Reproduktion) */
    private ThrongletV7 nearestAgent(ThrongletV7 self) {
        ThrongletV7 best=null; double bd=Double.MAX_VALUE;
        for(ThrongletV7 o:pop){
            if(o.id==self.id||!o.alive) continue;
            double d=World.dist(self.x,self.y,o.x,o.y);
            if(d<bd){bd=d;best=o;}
        }
        return best;
    }

    private void logStats() {
        double avgF=pop.stream().mapToDouble(t->t.fitness).average().orElse(0);
        double avgFE=pop.stream().mapToDouble(t->t.brain.getLastFE()).average().orElse(0);
        double avgPE=pop.stream().mapToDouble(t->t.brain.getPredError()).average().orElse(0);
        double avgV=pop.stream().mapToDouble(t->t.homeostasis.valence).average().orElse(0);
        Map<String,Long> thoughts=new HashMap<>();
        for(ThrongletV7 t:pop) thoughts.merge(t.lastThought,1L,Long::sum);
        String topThought=thoughts.entrySet().stream().max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse("?");
        System.out.printf(
                "Tick%5d|Pop:%3d|Fit:%5.1f|FE:%.2f|PE:%.2f|Val:%.2f|Sp:%d|Gen:%d|Top:'%s'|%s%n",
                world.getTick(),pop.size(),avgF,avgFE,avgPE,avgV,
                neat.speciesCount(),generation,topThought,world.currentSeason.label);
    }
}