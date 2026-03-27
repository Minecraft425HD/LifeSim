package thronglets;
public enum LifeStage {
    EGG    (0,   20,  0.0, 2.0, "Ei",       0xFFBBBB88),
    BABY   (21,  120, 0.4, 1.8, "Baby",     0xFF88DDFF),
    YOUTH  (121, 450, 1.2, 1.2, "Jugend",   0xFF88FF88),
    ADULT  (451,1300, 1.0, 1.0, "Erwachsen",0xFF4488FF),
    ELDER  (1301,9999,0.6, 0.7, "Aeltester",0xFFAA88FF);
    public final int    minAge,maxAge; public final double speedMod,hungerMod;
    public final String label;        public final int color;
    LifeStage(int mn,int mx,double sp,double hu,String lb,int col){minAge=mn;maxAge=mx;speedMod=sp;hungerMod=hu;label=lb;color=col;}
    public boolean canMove()     {return this!=EGG;}
    public boolean canReproduce(){return this==ADULT||this==ELDER;}
    public static LifeStage forAge(int age){for(LifeStage s:values())if(age>=s.minAge&&age<=s.maxAge)return s;return ELDER;}
}
