package thronglets;
import java.util.*;
public class NEATInnovation {
    private static int nextInnov=1000,nextNode=200;
    private static final Map<Long,Integer> cc=new HashMap<>(),nc=new HashMap<>();
    public static synchronized int getConnection(int f,int t){long k=((long)f<<32)|(t&0xFFFFFFFFL);return cc.computeIfAbsent(k,x->nextInnov++);}
    public static synchronized int newNode(int fromConn){return nc.computeIfAbsent((long)fromConn,x->nextNode++);}
    public static synchronized int allocNode(){return nextNode++;}
    public static synchronized void newGeneration(){cc.clear();}
    public static int getNextNode(){return nextNode;}
}
