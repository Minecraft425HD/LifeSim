package thronglets;
public class NEATGene {
    public final int fromNode,toNode,innovation; public float weight; public boolean enabled;
    public NEATGene(int f,int t,float w,int i){fromNode=f;toNode=t;weight=w;innovation=i;enabled=true;}
    public NEATGene copy(){NEATGene c=new NEATGene(fromNode,toNode,weight,innovation);c.enabled=enabled;return c;}
}
