package thronglets;
import java.util.*;
public class Memory {
    public int age=0;
    private final Deque<double[]> history=new ArrayDeque<>(12);
    private int lastFoodTick=-999,lastMateTick=-999,lastDangerTick=-999;

    // ── Episodisches Gedächtnis: 16-Ereignis-Ringpuffer ─────────────────
    public static final int EV_FOOD=0,EV_DANGER=1,EV_MATE=2,EV_GROUP=3,EV_BIRTH=4;
    private static final String[] EV_LABELS={"Nahrung","Gefahr","Paarung","Gruppe","Geburt"};
    private final int[] evType  = new int[16];
    private final int[] evTick  = new int[16];
    private int evHead=0, evSize=0;

    public void logEvent(int type, int tick) {
        evType[evHead]=type; evTick[evHead]=tick;
        evHead=(evHead+1)%16; if(evSize<16) evSize++;
    }
    /** Gibt die letzten n Ereignisse als "Typ@Alter"-Strings zurück (neueste zuerst). */
    public List<String> recentEvents(int n){
        List<String> out=new ArrayList<>();
        int count=Math.min(n,evSize);
        for(int i=0;i<count;i++){
            int idx=(evHead-1-i+16)%16;
            int delta=age-evTick[idx];
            out.add(EV_LABELS[evType[idx]]+(delta<999?"-"+delta:""));
        }
        return out;
    }

    public void tick(double[] obs){
        age++;
        if(history.size()>=10) history.pollFirst();
        history.addLast(obs.clone());
    }
    public boolean isStuck(){
        if(history.size()<6) return false;
        double[][] arr=history.toArray(new double[0][]); double var=0;
        for(int i=1;i<arr.length;i++) if(arr[i].length>2) var+=Math.abs(arr[i][1]-arr[i-1][1])+Math.abs(arr[i][2]-arr[i-1][2]);
        return var/history.size()<0.004;
    }
    public double[] selfSignal(){return new double[]{lastFoodTick>0?Math.exp(-(age-lastFoodTick)/200.0):0,lastDangerTick>0?Math.exp(-(age-lastDangerTick)/100.0):0};}
    public void logFood(int tick)  { lastFoodTick=tick;   logEvent(EV_FOOD,  tick); }
    public void logMate(int tick)  { lastMateTick=tick;   logEvent(EV_MATE,  tick); }
    public void logDanger(int tick){ lastDangerTick=tick; logEvent(EV_DANGER,tick); }
}
