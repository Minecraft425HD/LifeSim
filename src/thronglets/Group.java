package thronglets;
import java.util.*;
public class Group {
    private static int nextId=0;
    public final int id=nextId++;
    public final Set<Integer> memberIds=new HashSet<>();
    public int    alphaId=-1;
    public Signal sharedSignal=Signal.NONE;
    public double sharedFoodX=-1,sharedFoodY=-1;
    private int   foodBroadcastTick=0;
    public void addMember(int id){memberIds.add(id);}
    public int  size(){return memberIds.size();}
    public void broadcastFood(double x,double y){sharedFoodX=x;sharedFoodY=y;foodBroadcastTick=0;}
    public void tick(){
        foodBroadcastTick++;
        if(foodBroadcastTick>80){sharedFoodX=-1;sharedFoodY=-1;sharedSignal=Signal.NONE;}
    }
}
