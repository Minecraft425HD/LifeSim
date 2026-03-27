package thronglets;

import java.util.*;

public class World {
    public final double width,height;
    public Season currentSeason=Season.SPRING;
    private int tickCount=0,seasonTick=0;
    private static final int SEASON_LEN=500;
    protected final List<double[]> foodList=new ArrayList<>();
    protected final List<double[]> dangerList=new ArrayList<>();
    private final Random rng;

    public World(double w,double h,int food,int danger,Random rng){
        this.rng=rng;
        width=w;height=h;
        // Nahrung nur in der Kartenmitte (Radius 150px vom Zentrum)
        for (int i=0; i<food; i++) {
            double angle  = rng.nextDouble() * 2 * Math.PI;
            double radius = rng.nextDouble() * 150;
            double fx = w/2 + Math.cos(angle) * radius;
            double fy = h/2 + Math.sin(angle) * radius;
            double mx = 12 + rng.nextDouble() * 22;
            foodList.add(new double[]{clampX(fx), clampY(fy), mx*0.8, mx});
        }
        for(int i=0;i<danger;i++)
            dangerList.add(new double[]{rng.nextDouble()*w,rng.nextDouble()*h,18+rng.nextDouble()*22});
    }

    public void addFood(double x,double y){
        double max=14+rng.nextDouble()*18;
        foodList.add(new double[]{clampX(x),clampY(y),max,max});
    }

    public void tick(){
        tickCount++; seasonTick++;
        if(seasonTick>=SEASON_LEN){
            seasonTick=0;
            currentSeason=Season.values()[(currentSeason.ordinal()+1)%4];
        }
        double baseRg=0.06*(currentSeason==Season.SPRING?2.2:currentSeason==Season.WINTER?0.25:1.0);
        double rg=baseRg*SimConfig.INSTANCE.foodRegenFactor;
        for(double[] f:foodList)
            if(f[2]<f[3]) f[2]=Math.min(f[3],f[2]+rg);
    }

    public double consumeFood(double x,double y,double r){
        double tot=0;
        for(double[] f:foodList){
            double d=dist(x,y,f[0],f[1]);
            if(d<r&&f[2]>0){
                double e=Math.min(f[2],5.0*(1-d/r));
                f[2]-=e;tot+=e;
            }
        }
        return tot;
    }

    public double nearestFoodDist(double x,double y){
        double best=width;
        for(double[] f:foodList)
            if(f[2]>0.5)best=Math.min(best,dist(x,y,f[0],f[1]));
        return best;
    }

    public double[] nearestFoodPos(double x,double y){
        double best=Double.MAX_VALUE;double[] bf=null;
        for(double[] f:foodList){
            if(f[2]<0.5)continue;
            double d=dist(x,y,f[0],f[1]);
            if(d<best){best=d;bf=f;}
        }
        return bf!=null?new double[]{bf[0],bf[1]}:null;
    }

    public double nearestDangerDist(double x,double y){
        double best=width;
        for(double[] d:dangerList)
            best=Math.min(best,dist(x,y,d[0],d[1]));
        return best;
    }

    public double dangerDamage(double x,double y){
        double tot=0;
        for(double[] d:dangerList){
            double dd=dist(x,y,d[0],d[1]);
            if(dd<d[2]) tot += 3.0*(1-dd/d[2]);
        }
        return tot * SimConfig.INSTANCE.dangerStrength;
    }

    public double clampX(double x){return Math.max(0,Math.min(width-1,x));}
    public double clampY(double y){return Math.max(0,Math.min(height-1,y));}
    public static double dist(double x1,double y1,double x2,double y2){
        double dx=x1-x2,dy=y1-y2;return Math.sqrt(dx*dx+dy*dy);
    }
    public int getTick(){return tickCount;}
    public List<double[]> getFoodList(){return foodList;}
    public List<double[]> getDangerList(){return dangerList;}
}