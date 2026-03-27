package thronglets;
import java.util.*;import java.util.stream.*;
public class NEATEvolution {
    private static final double THRESH=3.5; private static final int TOUR=3,STAG=15;
    private int nextSid=0;
    private final Map<Integer,List<NEATGenome>> sp=new LinkedHashMap<>();
    private final Map<Integer,Double> best=new HashMap<>();
    private final Map<Integer,Integer> ages=new HashMap<>();
    private final Random rng;
    public NEATEvolution(Random rng){this.rng=rng;}
    public List<NEATGenome> evolve(List<NEATGenome> pop,int target){
        NEATInnovation.newGeneration(); assign(pop);
        for(Map.Entry<Integer,List<NEATGenome>> e:sp.entrySet()){int sz=e.getValue().size();for(NEATGenome g:e.getValue())g.fitness=Math.max(0,g.fitness)/sz;}
        Set<Integer> ext=new HashSet<>();
        for(Map.Entry<Integer,List<NEATGenome>> e:sp.entrySet()){int sid=e.getKey();double bf=e.getValue().stream().mapToDouble(g->g.fitness).max().orElse(0);if(!best.containsKey(sid)||bf>best.get(sid)){best.put(sid,bf);ages.put(sid,0);}else{ages.merge(sid,1,Integer::sum);if(ages.get(sid)>STAG&&sp.size()>2)ext.add(sid);}}
        ext.forEach(s->{sp.remove(s);best.remove(s);ages.remove(s);});
        double tf=sp.values().stream().flatMap(List::stream).mapToDouble(g->g.fitness).sum();if(tf<=0)tf=1;
        List<NEATGenome> next=new ArrayList<>();
        for(Map.Entry<Integer,List<NEATGenome>> e:sp.entrySet()){List<NEATGenome> s=e.getValue();s.sort((a,b)->Double.compare(b.fitness,a.fitness));if(s.size()>2){NEATGenome el=s.get(0).copy();el.age++;el.speciesId=e.getKey();next.add(el);}double sf=s.stream().mapToDouble(g->g.fitness).sum();int off=Math.max(0,(int)Math.round(sf/tf*target));int cut=Math.max(1,s.size()/2);for(int i=0;i<off&&next.size()<target*2;i++){NEATGenome ch;if(s.size()>1&&rng.nextDouble()<0.75){NEATGenome p1=tour(s.subList(0,cut)),p2=tour(s.subList(0,cut));ch=p1.fitness>=p2.fitness?NEATGenome.crossover(p1,p2,rng):NEATGenome.crossover(p2,p1,rng);}else ch=tour(s).copy();ch.mutate(rng);ch.speciesId=-1;next.add(ch);}}
        while(next.size()<target){NEATGenome g=new NEATGenome(rng);g.mutate(rng);next.add(g);}
        if(next.size()>target)next.subList(target,next.size()).clear(); return next;
    }
    private void assign(List<NEATGenome> pop){Map<Integer,NEATGenome> reps=new HashMap<>();for(Map.Entry<Integer,List<NEATGenome>> e:sp.entrySet())if(!e.getValue().isEmpty())reps.put(e.getKey(),e.getValue().get(rng.nextInt(e.getValue().size())));sp.values().forEach(List::clear);for(NEATGenome g:pop){boolean asgn=false;for(Map.Entry<Integer,NEATGenome> r:reps.entrySet())if(g.compatibility(r.getValue())<THRESH){sp.computeIfAbsent(r.getKey(),k->new ArrayList<>()).add(g);g.speciesId=r.getKey();asgn=true;break;}if(!asgn){int sid=nextSid++;sp.put(sid,new ArrayList<>());sp.get(sid).add(g);reps.put(sid,g);g.speciesId=sid;ages.put(sid,0);}}sp.entrySet().removeIf(e->e.getValue().isEmpty());}
    private NEATGenome tour(List<NEATGenome> pool){NEATGenome b=null;for(int i=0;i<TOUR;i++){NEATGenome t=pool.get(rng.nextInt(pool.size()));if(b==null||t.fitness>b.fitness)b=t;}return b;}
    public int speciesCount(){return sp.size();}
    public Map<Integer,List<NEATGenome>> getSpecies(){return sp;}
}
