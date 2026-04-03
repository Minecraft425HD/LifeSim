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
            // [x, y, current, max, respawnTimer (0 = aktiv)]
            foodList.add(new double[]{clampX(fx), clampY(fy), mx*0.8, mx, 0});
        }
        // Keine zufälligen Gefahrenzonen – Wasser übernimmt diese Rolle (dangerCount = 0)
    }

    public void addFood(double x,double y){
        double max=14+rng.nextDouble()*18;
        foodList.add(new double[]{clampX(x),clampY(y),max,max,0});
    }

    public void tick(){
        tickCount++; seasonTick++;
        SimConfig cfg=SimConfig.INSTANCE;
        if(cfg.seasonsEnabled && seasonTick>=SEASON_LEN){
            seasonTick=0;
            currentSeason=Season.values()[(currentSeason.ordinal()+1)%4];
        }
        if(!cfg.foodEnabled) return;
        double baseRg=0.12*(currentSeason==Season.SPRING?2.2:currentSeason==Season.WINTER?0.30:1.0);
        double rg=baseRg*cfg.foodRegenFactor;
        for(double[] f:foodList){
            if(f[2]<=0){
                // Aufgefressen: Respawn-Countdown (f[4]>0 = läuft, f[4]==0 = gerade erschöpft)
                if(f[4]==0){
                    f[4]=100; // Countdown starten
                } else {
                    f[4]--;
                    if(f[4]==0){
                        // Neuer Spot nahe der Mitte
                        double a=rng.nextDouble()*2*Math.PI;
                        double rad=rng.nextDouble()*150;
                        f[0]=clampX(width/2+Math.cos(a)*rad);
                        f[1]=clampY(height/2+Math.sin(a)*rad);
                        f[2]=f[3]; // Vollständig aufgefüllt
                    }
                }
            } else if(f[2]<f[3]){
                f[2]=Math.min(f[3],f[2]+rg);
            }
        }
    }

    public double consumeFood(double x,double y,double r){
        if(!SimConfig.INSTANCE.foodEnabled) return 0;
        double tot=0;
        for(double[] f:foodList){
            if(f[2]>0 && dist(x,y,f[0],f[1])<r){
                tot+=f[2];
                f[2]=0;
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
        if(!SimConfig.INSTANCE.foodEnabled) return null;
        double best=Double.MAX_VALUE;double[] bf=null;
        for(double[] f:foodList){
            if(f[2]<0.5)continue;
            double d=dist(x,y,f[0],f[1]);
            if(d<best){best=d;bf=f;}
        }
        return bf!=null?new double[]{bf[0],bf[1]}:null;
    }

    /**
     * Wassertiefe an Position (x,y): 0 = kein Wasser, 1 = tiefes Wasser direkt am Rand.
     * Flachwasser beginnt 70px vom Rand, Tiefwasser ab 35px.
     */
    public double waterDepthAt(double x, double y) {
        double d = Math.min(Math.min(x, width-x), Math.min(y, height-y));
        if (d >= 70) return 0;
        return (70 - d) / 70.0; // linear: 0 bei 70px, 1 bei 0px (Rand)
    }

    /** Distanz zur Wasserzone (0 wenn im Wasser, positiv wenn auf Land) */
    public double nearestDangerDist(double x, double y) {
        double d = Math.min(Math.min(x, width-x), Math.min(y, height-y));
        return Math.max(0, d - 70);
    }

    /** Schaden durch Wasser: Tiefes Wasser schadet mehr */
    public double dangerDamage(double x, double y) {
        double depth = waterDepthAt(x, y);
        return depth > 0 ? depth * 2.0 * SimConfig.INSTANCE.dangerStrength : 0;
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