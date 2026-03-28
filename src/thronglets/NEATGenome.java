package thronglets;
import java.util.*;import java.util.stream.*;
public class NEATGenome {
    public static final int IN=16,OUT=10;
    // SNN-Gewichte – werden durch NEAT-Evolution übertragen
    public final float[][] snnW1=new float[SpikingBrain.HIDDEN][SpikingBrain.IN];
    public final float[][] snnW2=new float[SpikingBrain.OUT][SpikingBrain.HIDDEN];
    public final List<NEATGene> genes=new ArrayList<>();
    public final TreeSet<Integer> nodeIds=new TreeSet<>();
    public double fitness=0; public int speciesId=-1,age=0;
    public NEATGenome(Random rng){
        for(int i=0;i<IN;i++) nodeIds.add(i); for(int o=0;o<OUT;o++) nodeIds.add(IN+o);
        for(int i=0;i<IN;i++){int out=IN+rng.nextInt(OUT);genes.add(new NEATGene(i,out,(float)(rng.nextGaussian()*0.5),NEATInnovation.getConnection(i,out)));}
        // SNN-Gewichte zufällig initialisieren
        for(int h=0;h<SpikingBrain.HIDDEN;h++) for(int i=0;i<SpikingBrain.IN;i++) snnW1[h][i]=(float)(rng.nextGaussian()*0.8);
        for(int o=0;o<SpikingBrain.OUT;o++) for(int h=0;h<SpikingBrain.HIDDEN;h++) snnW2[o][h]=(float)(rng.nextGaussian()*0.5);
    }
    private NEATGenome(){}
    public NEATGenome copy(){
        NEATGenome c=new NEATGenome();genes.forEach(g->c.genes.add(g.copy()));c.nodeIds.addAll(nodeIds);c.speciesId=speciesId;
        for(int h=0;h<SpikingBrain.HIDDEN;h++) System.arraycopy(snnW1[h],0,c.snnW1[h],0,SpikingBrain.IN);
        for(int o=0;o<SpikingBrain.OUT;o++) System.arraycopy(snnW2[o],0,c.snnW2[o],0,SpikingBrain.HIDDEN);
        return c;
    }
    public void mutate(Random rng){double r=rng.nextDouble();if(r<0.80)mutW(rng);else if(r<0.93)addConn(rng);else addNode(rng);mutSNN(rng);}
    private void mutW(Random r){for(NEATGene g:genes){if(r.nextDouble()<0.9)g.weight+=(float)(r.nextGaussian()*0.18);else g.weight=(float)(r.nextGaussian()*0.5);g.weight=Math.max(-4,Math.min(4,g.weight));}}
    private void mutSNN(Random rng){
        for(int h=0;h<SpikingBrain.HIDDEN;h++) for(int i=0;i<SpikingBrain.IN;i++){
            if(rng.nextDouble()<0.85) snnW1[h][i]+=rng.nextGaussian()*0.12;
            else snnW1[h][i]=(float)(rng.nextGaussian()*0.8);
            snnW1[h][i]=Math.max(-3,Math.min(3,snnW1[h][i]));
        }
        for(int o=0;o<SpikingBrain.OUT;o++) for(int h=0;h<SpikingBrain.HIDDEN;h++){
            if(rng.nextDouble()<0.85) snnW2[o][h]+=rng.nextGaussian()*0.08;
            else snnW2[o][h]=(float)(rng.nextGaussian()*0.5);
            snnW2[o][h]=Math.max(-3,Math.min(3,snnW2[o][h]));
        }
    }
    private void addConn(Random rng){List<Integer> ids=new ArrayList<>(nodeIds);for(int t=0;t<25;t++){int f=ids.get(rng.nextInt(ids.size())),to=ids.get(rng.nextInt(ids.size()));if(f==to||to<IN||f>=IN&&f<IN+OUT)continue;if(genes.stream().noneMatch(g->g.fromNode==f&&g.toNode==to&&g.enabled)){genes.add(new NEATGene(f,to,(float)(rng.nextGaussian()*0.5),NEATInnovation.getConnection(f,to)));return;}}}
    private void addNode(Random rng){List<NEATGene> act=genes.stream().filter(g->g.enabled).collect(Collectors.toList());if(act.isEmpty()){addConn(rng);return;}NEATGene sp=act.get(rng.nextInt(act.size()));sp.enabled=false;int nid=NEATInnovation.newNode(sp.innovation);nodeIds.add(nid);int i1=NEATInnovation.getConnection(sp.fromNode,nid),i2=NEATInnovation.getConnection(nid,sp.toNode);genes.add(new NEATGene(sp.fromNode,nid,1f,i1));genes.add(new NEATGene(nid,sp.toNode,sp.weight,i2));}
    public static NEATGenome crossover(NEATGenome fit,NEATGenome other,Random rng){
        NEATGenome c=new NEATGenome();c.nodeIds.addAll(fit.nodeIds);c.nodeIds.addAll(other.nodeIds);
        Map<Integer,NEATGene> om=new HashMap<>();for(NEATGene g:other.genes)om.put(g.innovation,g);
        for(NEATGene g:fit.genes){NEATGene o=om.get(g.innovation);NEATGene ch=(o!=null&&rng.nextBoolean())?o.copy():g.copy();if(o!=null&&(!g.enabled||!o.enabled))ch.enabled=rng.nextDouble()>0.75;c.genes.add(ch);}
        // SNN-Gewichte: für jedes Gewicht zufällig von einem Elternteil
        for(int h=0;h<SpikingBrain.HIDDEN;h++) for(int i=0;i<SpikingBrain.IN;i++)
            c.snnW1[h][i]=rng.nextBoolean()?fit.snnW1[h][i]:other.snnW1[h][i];
        for(int o=0;o<SpikingBrain.OUT;o++) for(int h=0;h<SpikingBrain.HIDDEN;h++)
            c.snnW2[o][h]=rng.nextBoolean()?fit.snnW2[o][h]:other.snnW2[o][h];
        return c;
    }
    public double compatibility(NEATGenome o){int mA=genes.stream().mapToInt(g->g.innovation).max().orElse(0),mB=o.genes.stream().mapToInt(g->g.innovation).max().orElse(0);Map<Integer,Float> wA=new HashMap<>(),wB=new HashMap<>();for(NEATGene g:genes)if(g.enabled)wA.put(g.innovation,g.weight);for(NEATGene g:o.genes)if(g.enabled)wB.put(g.innovation,g.weight);Set<Integer> all=new HashSet<>();all.addAll(wA.keySet());all.addAll(wB.keySet());int ex=0,dj=0,mt=0;double wd=0;for(int inn:all){boolean a=wA.containsKey(inn),b=wB.containsKey(inn);if(a&&b){mt++;wd+=Math.abs(wA.get(inn)-wB.get(inn));}else if(a){if(inn>mB)ex++;else dj++;}else{if(inn>mA)ex++;else dj++;}}int N=Math.max(1,Math.max(wA.size(),wB.size()));if(N<20)N=1;return 1.0*ex/N+1.0*dj/N+0.4*(mt>0?wd/mt:0);}
    public int hiddenNodes(){return(int)nodeIds.stream().filter(n->n>=IN+OUT).count();}
    public int geneCount(){return genes.size();}
}
