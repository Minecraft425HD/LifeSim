package thronglets;
import java.util.Random;
public class Gene {
    public double moveSpeed,sensorRange,fearThreshold,reproductionAge,reproductiveDrive;
    public double mutationRate,mutationStrength,socialAffinity,languageAptitude;
    public double metabolismRate,epiBias,curiosity;
    public Gene(Random rng){
        moveSpeed=0.8+rng.nextDouble()*2.2; sensorRange=15+rng.nextDouble()*30;
        fearThreshold=0.3+rng.nextDouble()*0.6; reproductionAge=80+rng.nextDouble()*200;
        reproductiveDrive=0.3+rng.nextDouble()*0.65; mutationRate=0.01+rng.nextDouble()*0.18;
        mutationStrength=0.05+rng.nextDouble()*0.45; socialAffinity=0.1+rng.nextDouble()*0.9;
        languageAptitude=0.1+rng.nextDouble()*0.9; metabolismRate=0.08+rng.nextDouble()*0.35;
        epiBias=rng.nextDouble(); curiosity=0.1+rng.nextDouble()*0.9;
    }
    private Gene(){}
    public static Gene crossover(Gene a,Gene b,Random rng){
        Gene c=new Gene(); c.moveSpeed=cross(a.moveSpeed,b.moveSpeed,rng,a.mutationStrength);
        c.sensorRange=cross(a.sensorRange,b.sensorRange,rng,a.mutationStrength*10);
        c.fearThreshold=cross(a.fearThreshold,b.fearThreshold,rng,a.mutationStrength*0.3);
        c.reproductionAge=cross(a.reproductionAge,b.reproductionAge,rng,a.mutationStrength*30);
        c.reproductiveDrive=cross(a.reproductiveDrive,b.reproductiveDrive,rng,a.mutationStrength*0.2);
        c.mutationRate=cross(a.mutationRate,b.mutationRate,rng,0.005);
        c.mutationStrength=cross(a.mutationStrength,b.mutationStrength,rng,0.02);
        c.socialAffinity=cross(a.socialAffinity,b.socialAffinity,rng,a.mutationStrength*0.3);
        c.languageAptitude=cross(a.languageAptitude,b.languageAptitude,rng,a.mutationStrength*0.3);
        c.metabolismRate=cross(a.metabolismRate,b.metabolismRate,rng,a.mutationStrength*0.1);
        c.epiBias=cross(a.epiBias,b.epiBias,rng,a.mutationStrength*0.3);
        c.curiosity=cross(a.curiosity,b.curiosity,rng,a.mutationStrength*0.3); return c;
    }
    private static double cross(double a,double b,Random rng,double noise){
        double v=(rng.nextBoolean()?a:b)+rng.nextGaussian()*noise;return Math.max(0.01,v);
    }
    public String dominantTrait(){
        if(curiosity>0.75)return "Neugierig"; if(socialAffinity>0.75)return "Sozial";
        if(fearThreshold<0.4)return "Mutig"; if(moveSpeed>2.5)return "Schnell";
        if(languageAptitude>0.75)return "Kommunik."; return "Ausgewogen";
    }
}
