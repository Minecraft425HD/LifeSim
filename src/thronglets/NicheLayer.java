package thronglets;
public class NicheLayer {
    private static final int CS=10;
    private final int cX,cY;
    private final double[][][] grid;
    public NicheLayer(int w,int h){cX=w/CS+1;cY=h/CS+1;grid=new double[PheromoneType.values().length][cX][cY];}
    public void deposit(double x,double y,PheromoneType t,double s){int cx=Math.min(cX-1,(int)(x/CS)),cy=Math.min(cY-1,(int)(y/CS));grid[t.id][cx][cy]=Math.min(1,grid[t.id][cx][cy]+s);}
    public double sense(double x,double y,PheromoneType t,double r){int rd=(int)(r/CS)+1,cx=(int)(x/CS),cy=(int)(y/CS);double tot=0;int cnt=0;for(int dx=-rd;dx<=rd;dx++)for(int dy=-rd;dy<=rd;dy++){int nx=cx+dx,ny=cy+dy;if(nx>=0&&nx<cX&&ny>=0&&ny<cY){tot+=grid[t.id][nx][ny];cnt++;}}return cnt>0?tot/cnt:0;}
    public double[] gradient(double x,double y,PheromoneType t,double r){int rd=(int)(r/CS)+1,cx=(int)(x/CS),cy=(int)(y/CS);double gx=0,gy=0;for(int dx=-rd;dx<=rd;dx++)for(int dy=-rd;dy<=rd;dy++){if(dx==0&&dy==0)continue;int nx=cx+dx,ny=cy+dy;if(nx>=0&&nx<cX&&ny>=0&&ny<cY){double s=grid[t.id][nx][ny];if(s>0.01){gx+=dx*s;gy+=dy*s;}}}double len=Math.sqrt(gx*gx+gy*gy);return len>0?new double[]{gx/len,gy/len}:new double[]{0,0};}
    public void tick(){for(PheromoneType t:PheromoneType.values())for(int cx=0;cx<cX;cx++)for(int cy=0;cy<cY;cy++){grid[t.id][cx][cy]*=(1-t.decayPerTick);if(grid[t.id][cx][cy]<0.005)grid[t.id][cx][cy]=0;}}
    public double[][][] getGrid(){return grid;}
    public int getCellSize(){return CS;}
}
