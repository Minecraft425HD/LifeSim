package thronglets;
import java.util.Random;
public class Homeostasis {
    public final double[] drives=new double[DriveType.values().length];
    public double valence=0,arousal=0.5;
    private final double[] prev=new double[drives.length];
    public Homeostasis(Random rng){
        for(DriveType d:DriveType.values()) drives[d.id]=d.optMin+rng.nextDouble()*(d.optMax-d.optMin);
        System.arraycopy(drives,0,prev,0,drives.length);
    }
    public void tick(Season s,boolean inGroup,double moved,boolean atFood){
        System.arraycopy(drives,0,prev,0,drives.length);
        drives[0]-=DriveType.ENERGY.decayRate*s.energyCost; if(atFood) drives[0]=Math.min(100,drives[0]+15);
        drives[1]+=0.08; if(inGroup) drives[1]-=0.3; drives[1]=clamp(drives[1]);
        drives[2]-=DriveType.SOCIAL.decayRate*(inGroup?-1.5:1);
        drives[3]+=(moved>2?-DriveType.CURIOSITY.decayRate*2:DriveType.CURIOSITY.decayRate);
        drives[4]-=(s==Season.WINTER?0.5:s==Season.AUTUMN?0.2:-0.1);
        for(int i=0;i<drives.length;i++) drives[i]=clamp(drives[i]);
        updateEmotion();
    }
    public void applyDanger(double d){drives[0]-=d;drives[1]=Math.min(100,drives[1]+d*2);}
    public void applySocial(boolean p){drives[2]=Math.min(100,drives[2]+(p?15:-5));drives[1]=Math.max(0,drives[1]-(p?5:2));}
    private void updateEmotion(){double dc=0,ch=0;for(DriveType d:DriveType.values()){dc+=d.discomfort(drives[d.id]);ch+=Math.abs(drives[d.id]-prev[d.id]);}valence=Math.max(-1,Math.min(1,1-2*dc/5));arousal=Math.min(1,ch/10);}
    public boolean isDead(){return drives[0]<=0||drives[1]>=100||drives[4]<=0;}
    public double wellbeing(){double d=0;for(DriveType t:DriveType.values())d+=t.discomfort(drives[t.id]);return 1-d/5;}
    public float[] toInputVector(){float[] v=new float[7];for(int i=0;i<5;i++)v[i]=(float)(drives[i]/100);v[5]=(float)((valence+1)/2);v[6]=(float)arousal;return v;}
    private static double clamp(double v){return Math.max(0,Math.min(100,v));}
}
