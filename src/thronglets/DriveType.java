package thronglets;
public enum DriveType {
    ENERGY(0,40,80,0.40,"Energie",0xFF44BB44),STRESS(1,0,30,-0.15,"Stress",0xFFDD4444),
    SOCIAL(2,35,70,0.20,"Sozial",0xFF44AADD),CURIOSITY(3,20,60,0.18,"Neugier",0xFFDDAA22),
    WARMTH(4,35,70,0.10,"Wärme",0xFFDD8833);
    public final int id; public final double optMin,optMax,decayRate; public final String label; public final int color;
    DriveType(int id,double mn,double mx,double dr,String lb,int col){this.id=id;optMin=mn;optMax=mx;decayRate=dr;label=lb;color=col;}
    public double discomfort(double v){if(v<optMin)return Math.min(1.0,(optMin-v)/Math.max(1,optMin));if(v>optMax)return Math.min(1.0,(v-optMax)/Math.max(1,100.0-optMax));return 0.0;}
}
