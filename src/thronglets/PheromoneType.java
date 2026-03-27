package thronglets;
public enum PheromoneType {
    FOOD_TRAIL(0,0.005,"Nahrung",0x8800CC44),DANGER(1,0.008,"Gefahr",0x88CC2222),
    NEST(2,0.002,"Nest",0x88FFCC00),TRAIL(3,0.010,"Spur",0x44FFFFFF),SOCIAL(4,0.007,"Sozial",0x884488DD);
    public final int id; public final double decayPerTick; public final String label; public final int argbColor;
    PheromoneType(int id,double d,String lb,int col){this.id=id;decayPerTick=d;label=lb;argbColor=col;}
}
