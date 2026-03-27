package thronglets;

import java.util.Random;

public class WorldV6 extends World {
    public final NicheLayer niche;
    public WorldV6(double w,double h,int food,int danger,Random rng){
        super(w,h,food,danger,rng);
        niche=new NicheLayer((int)w,(int)h);
    }
    @Override public void tick(){super.tick();niche.tick();}
}
