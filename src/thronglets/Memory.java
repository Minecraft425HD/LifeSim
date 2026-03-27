package thronglets;
import java.util.*;
public class Memory {
    public int age=0;
    private final Deque<double[]> history=new ArrayDeque<>(12);
    private int lastFoodTick=-999,lastMateTick=-999,lastDangerTick=-999;
    public void tick(double[] obs){
        age++;
        if(history.size()>=10) history.pollFirst();
        history.addLast(obs.clone());
    }
    public boolean isStuck(){
        if(history.size()<6) return false;
        double[][] arr=history.toArray(new double[0][]); double var=0;
        for(int i=1;i<arr.length;i++) var+=Math.abs(arr[i][0]-arr[i-1][0]);
        return var/history.size()<0.003;
    }
    public double[] selfSignal(){return new double[]{lastFoodTick>0?Math.exp(-(age-lastFoodTick)/200.0):0,lastDangerTick>0?Math.exp(-(age-lastDangerTick)/100.0):0};}
    public void logFood(int tick){lastFoodTick=tick;}
    public void logMate(int tick){lastMateTick=tick;}
    public void logDanger(int tick){lastDangerTick=tick;}
}
