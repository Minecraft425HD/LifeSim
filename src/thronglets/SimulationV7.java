package thronglets;

import java.util.*;
import java.util.stream.Collectors;

public class SimulationV7 implements Runnable {

    public static final int    WORLD_W  = 500, WORLD_H  = 500;
    public static final int    START_POP= 2,  MIN_POP  = 2, MAX_POP = 100;
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

    public SimulationV7(long seed, RendererV7 r) {
        rng      = new Random(seed);
        SimConfig cfg = SimConfig.INSTANCE;
        world    = new WorldV6(WORLD_W, WORLD_H, cfg.baseFoodCount, cfg.dangerCount, rng);
        neat     = new NEATEvolution(rng);
        renderer = r;
        pop      = new ArrayList<>();
        for (int i=0;i<START_POP;i++)
            pop.add(new ThrongletV7(rng.nextDouble()*WORLD_W, rng.nextDouble()*WORLD_H, rng));
    }

    public void stop() { running=false; }

    @Override
    public void run() {
        System.out.println("═══════════════════════════════════════════════════");
        System.out.println("  THRONGLETS  V7  –  MAXIMUM LIFE");
        System.out.println("  SNN (STDP) + FEP (Friston) + Nano-Transformer");
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

                ThrongletV7 child = t.tick(world, sig, gfx, gfy, nearestMate(t), gsz);

                if (t.signalOut==Signal.FOOD_NEAR&&g!=null) {
                    double[] fp=world.nearestFoodPos(t.x,t.y);
                    if (fp!=null) g.broadcastFood(fp[0],fp[1]);
                }
                if (child!=null&&pop.size()+newborns.size()<MAX_POP) newborns.add(child);
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
            groups.values().forEach(Group::tick);
            for (ThrongletV7 t:pop) t.genome.fitness=t.fitness;

            if (world.getTick()%3==0&&renderer!=null)
                renderer.update(new ArrayList<>(pop), world, groups, generation, neat.speciesCount());

            if (world.getTick()%200==0) logStats();

            if (pop.size()<MIN_POP) {
                generation++;
                List<NEATGenome> genomes=pop.stream().map(t->t.genome).collect(Collectors.toList());
                while (genomes.size()<START_POP) genomes.add(new NEATGenome(rng));
                List<NEATGenome> evolved=neat.evolve(genomes, START_POP);
                pop.clear();
                for (NEATGenome g:evolved)
                    pop.add(new ThrongletV7(rng.nextDouble()*WORLD_W, rng.nextDouble()*WORLD_H, rng));
                System.out.printf("  ↳ Gen %d | Spezies: %d%n", generation, neat.speciesCount());
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

    private ThrongletV7 nearestMate(ThrongletV7 self) {
        ThrongletV7 best=null; double bd=Double.MAX_VALUE;
        for(ThrongletV7 o:pop){
            if(o.id==self.id||!o.alive||!o.stage.canReproduce())continue;
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