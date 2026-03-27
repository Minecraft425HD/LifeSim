package thronglets;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

public class RendererV7 extends JPanel {
    private static final int WV=620, HT=660, HUD_W=300;
    private volatile List<AgentSnap>    agents  = new ArrayList<>();
    private volatile List<double[]>     food    = new ArrayList<>();
    private volatile List<double[]>     danger  = new ArrayList<>();
    private volatile double[][][]       ph      = null;
    private volatile int  tick=0,gen=0,popSize=0,specCount=0;
    private volatile String season="";
    private volatile double avgFit=0,avgFE=0,avgPE=0,avgVal=0,worldW=500,worldH=500;
    private final LinkedList<Double> feHist  = new LinkedList<>();
    private final LinkedList<Double> valHist = new LinkedList<>();
    private final LinkedList<Integer> popHist= new LinkedList<>();
    private final Map<Integer,Color> specColors=new HashMap<>();
    private final Random cRng=new Random(77);
    private final LinkedList<String> thoughtLog = new LinkedList<>();

    static class AgentSnap {
        double x,y,energy,fitness,valence,arousal,freeEnergy,predError,uncertainty;
        int id,groupId,age,speciesId;
        double[] homeoDrives;
        String stage,thought;
        boolean isAlpha;
        boolean[] hiddenSpikes,outputSpikes;
        Signal signal;
        AgentSnap(ThrongletV7 t, boolean alpha) {
            x=t.x;y=t.y;fitness=t.fitness;id=t.id;groupId=t.groupId;age=t.memory.age;
            speciesId=t.genome.speciesId;stage=t.stage.label;isAlpha=alpha;signal=t.signalOut;
            energy=t.homeostasis.drives[0];valence=t.homeostasis.valence;arousal=t.homeostasis.arousal;
            homeoDrives=t.homeostasis.drives.clone();thought=t.lastThought;
            freeEnergy=t.brain.getLastFE();predError=t.brain.getPredError();
            uncertainty=t.brain.getUncertainty();
            hiddenSpikes=t.brain.getHiddenSpikes().clone();
            outputSpikes=t.brain.getOutputSpikes().clone();
        }
    }

    public RendererV7() {
        setPreferredSize(new Dimension(WV+HUD_W,HT));
        setBackground(new Color(6,8,18));
    }

    public synchronized void update(List<ThrongletV7> pop, WorldV6 world,
                                     Map<Integer,Group> gmap, int g, int sp) {
        tick=world.getTick();gen=g;popSize=pop.size();specCount=sp;
        season=world.currentSeason.label;worldW=world.width;worldH=world.height;
        avgFit=pop.stream().mapToDouble(t->t.fitness).average().orElse(0);
        avgFE=pop.stream().mapToDouble(t->t.brain.getLastFE()).average().orElse(0);
        avgPE=pop.stream().mapToDouble(t->t.brain.getPredError()).average().orElse(0);
        avgVal=pop.stream().mapToDouble(t->t.homeostasis.valence).average().orElse(0);
        Set<Integer> alphas=new HashSet<>();gmap.values().forEach(gr->{if(gr.alphaId>=0)alphas.add(gr.alphaId);});
        List<AgentSnap> snap=new ArrayList<>();
        for(ThrongletV7 t:pop){
            AgentSnap s=new AgentSnap(t,alphas.contains(t.id));
            snap.add(s);
            if(!t.lastThought.equals("…") && !t.lastThought.isEmpty()) {
                String entry = "T#" + t.id + ": \"" + t.lastThought + "\"";
                if(thoughtLog.isEmpty() || !thoughtLog.peekLast().equals(entry)) {
                    thoughtLog.addLast(entry);
                    if(thoughtLog.size()>8) thoughtLog.pollFirst();
                }
            }
        }
        agents=snap;food=new ArrayList<>(world.getFoodList());danger=new ArrayList<>(world.getDangerList());
        ph=world.niche.getGrid();
        feHist.addLast(avgFE);if(feHist.size()>300)feHist.pollFirst();
        valHist.addLast(avgVal);if(valHist.size()>300)valHist.pollFirst();
        popHist.addLast(popSize);if(popHist.size()>300)popHist.pollFirst();
        for(AgentSnap a:snap) if(!specColors.containsKey(a.speciesId))
            specColors.put(a.speciesId,Color.getHSBColor(cRng.nextFloat(),0.75f+cRng.nextFloat()*0.25f,0.85f));
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2=(Graphics2D)g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        double sx=WV/worldW,sy=HT/worldH;
        g2.setColor(new Color(7,9,20));g2.fillRect(0,0,WV,HT);
        drawPhero(g2,sx,sy);
        for(double[] f:food){if(f[2]<=0)continue;int px=(int)(f[0]*sx),py=(int)(f[1]*sy),sz=(int)(2+f[2]/f[3]*7);g2.setColor(new Color(45,200,65,(int)(90+f[2]/f[3]*165)));g2.fillOval(px-sz/2,py-sz/2,sz,sz);}
        g2.setStroke(new BasicStroke(1.5f));
        for(double[] d:danger){int px=(int)(d[0]*sx),py=(int)(d[1]*sy),r=(int)(d[2]*sx);g2.setColor(new Color(200,30,30,32));g2.fillOval(px-r,py-r,r*2,r*2);g2.setColor(new Color(255,60,60,90));g2.drawOval(px-r,py-r,r*2,r*2);}
        g2.setStroke(new BasicStroke(1f));
        for(AgentSnap a:agents){
            int px=(int)(a.x*sx),py=(int)(a.y*sy);
            int r=switch(a.stage){case "Ei"->4;case "Baby"->5;case "Jugend"->7;case "Aeltester"->8;default->9;};
            if(a.freeEnergy>0.1f){int halo=(int)(r+a.freeEnergy*30);g2.setColor(new Color(180,100,255,Math.min(255,(int)(a.freeEnergy*60))));g2.fillOval(px-halo,py-halo,halo*2,halo*2);}
            int spikeCount=0;if(a.hiddenSpikes!=null)for(boolean s:a.hiddenSpikes)if(s)spikeCount++;
            if(spikeCount>4){float spikeAlpha=Math.min(0.7f,spikeCount/20f);g2.setColor(new Color(1f,1f,0.5f,spikeAlpha));g2.fillOval(px-r-2,py-r-2,(r+2)*2,(r+2)*2);}
            float vv=(float)((a.valence+1)/2.0);g2.setColor(new Color(1f-vv,vv*0.8f,0.2f,0.3f));g2.fillOval(px-r-3,py-r-3,(r+3)*2,(r+3)*2);
            Color sc=specColors.getOrDefault(a.speciesId,Color.GRAY);g2.setColor(sc);g2.fillOval(px-r,py-r,r*2,r*2);
            if(a.isAlpha){g2.setColor(Color.YELLOW);g2.setStroke(new BasicStroke(2f));g2.drawOval(px-r-2,py-r-2,(r+2)*2,(r+2)*2);g2.setStroke(new BasicStroke(1f));}
            DriveType[] drives=DriveType.values();
            for(int d=0;d<drives.length;d++){double discomf=drives[d].discomfort(a.homeoDrives[d]);if(discomf<0.12)continue;int arc=360/drives.length,startAngle=d*arc;Color dc=new Color(new Color(drives[d].color,true).getRed()/255f,new Color(drives[d].color,true).getGreen()/255f,new Color(drives[d].color,true).getBlue()/255f,(float)Math.min(0.95,discomf));g2.setColor(dc);g2.setStroke(new BasicStroke(2.5f));g2.drawArc(px-r-3,py-r-3,(r+3)*2,(r+3)*2,startAngle,arc-2);}g2.setStroke(new BasicStroke(1f));
            double ep=Math.min(1,a.energy/100);int bx=px-r-1,by=py+r+2,bw=r*2+2;g2.setColor(new Color(40,40,50));g2.fillRect(bx,by,bw,3);g2.setColor(ep>0.5?new Color(60,220,60):ep>0.25?new Color(220,180,30):new Color(220,50,50));g2.fillRect(bx,by,(int)(bw*ep),3);
            if(a.thought!=null && !a.thought.equals("…") && !a.thought.equals("Beobachte") && tick%10<3){g2.setFont(new Font("SansSerif",Font.PLAIN,9));FontMetrics fm=g2.getFontMetrics();int tw=fm.stringWidth(a.thought)+8;g2.setColor(new Color(0,0,0,160));g2.fillRoundRect(px+r+2,py-r-2,tw,14,5,5);g2.setColor(new Color(220,255,180));g2.drawString(a.thought,px+r+6,py-r+9);}
        }
        drawHUD(g2);
    }

    private void drawPhero(Graphics2D g2,double sx,double sy){double[][][] p=ph;if(p==null)return;int cs=10;for(PheromoneType pt:PheromoneType.values()){Color base=new Color(pt.argbColor,true);for(int cx=0;cx<p[pt.id].length;cx++)for(int cy=0;cy<p[pt.id][cx].length;cy++){double s=p[pt.id][cx][cy];if(s<0.025)continue;int alpha = (int)(s * 110);if (alpha < 0) alpha = 0;if (alpha > 255) alpha = 255;g2.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha));g2.fillRect((int)(cx*cs*sx),(int)(cy*cs*sy),(int)(cs*sx)+1,(int)(cs*sy)+1);}}}

    private void drawHUD(Graphics2D g2){int ox=WV+8;g2.setColor(new Color(9,11,22));g2.fillRect(WV,0,HUD_W,HT);g2.setColor(new Color(120,200,255));g2.setFont(new Font("Monospaced",Font.BOLD,15));g2.drawString("THRONGLETS  V7",ox,26);g2.setColor(new Color(255,150,50));g2.setFont(new Font("Monospaced",Font.BOLD,10));g2.drawString("SNN + FEP (Friston) + NanoTransformer",ox,40);int y=60;g2.setFont(new Font("Monospaced",Font.PLAIN,12));hud(g2,ox,y,"Tick",""+tick);hud(g2,ox,y+18,"Gen",""+gen);hud(g2,ox,y+36,"Pop",""+popSize);hud(g2,ox,y+54,"Spezies",""+specCount);hud(g2,ox,y+72,"Jahresz",season);hud(g2,ox,y+90,"Ø Fit",String.format("%.1f",avgFit));hud(g2,ox,y+108,"Ø FE",String.format("%.3f",avgFE));hud(g2,ox,y+126,"Ø PredE",String.format("%.3f",avgPE));hud(g2,ox,y+144,"Ø Valenz",String.format("%.2f",avgVal));y=225;g2.setColor(new Color(200,220,150));g2.setFont(new Font("Monospaced",Font.BOLD,10));g2.drawString("── Innerer Monolog ──",ox,y);y+=13;g2.setFont(new Font("Monospaced",Font.PLAIN,10));for(String t:thoughtLog){g2.setColor(new Color(180,220,180));String line=t.length()>28?t.substring(0,28):t;g2.drawString(line,ox,y);y+=13;}y+=8;g2.setColor(new Color(60,60,100));g2.fillRect(ox,y,HUD_W-16,60);g2.setFont(new Font("Monospaced",Font.PLAIN,9));g2.setColor(new Color(180,180,180));g2.drawString("Ø Freie Energie (lila) | Ø Valenz (grün)",ox,y-3);if(feHist.size()>1){double[] arr=feHist.stream().mapToDouble(d->d).toArray();int gw=HUD_W-16;double maxV=java.util.Arrays.stream(arr).max().orElse(1);if(maxV<0.001)maxV=0.001;g2.setColor(new Color(180,80,255));g2.setStroke(new BasicStroke(1.3f));for(int i=1;i<arr.length;i++){int x1=ox+(i-1)*gw/arr.length,y1=y+60-(int)(arr[i-1]/maxV*58);int x2=ox+i*gw/arr.length,y2=y+60-(int)(arr[i]/maxV*58);g2.drawLine(x1,y1,x2,y2);}}if(valHist.size()>1){double[] arr=valHist.stream().mapToDouble(d->d).toArray();int gw=HUD_W-16;g2.setColor(new Color(80,220,100));g2.setStroke(new BasicStroke(1.2f));for(int i=1;i<arr.length;i++){int x1=ox+(i-1)*gw/arr.length,y1=y+30-(int)(arr[i-1]*28);int x2=ox+i*gw/arr.length,y2=y+30-(int)(arr[i]*28);g2.drawLine(x1,y1,x2,y2);}}y+=74;g2.setColor(new Color(40,50,80));g2.fillRect(ox,y,HUD_W-16,45);g2.setColor(new Color(160,160,160));g2.setFont(new Font("Monospaced",Font.PLAIN,9));g2.drawString("Population (300 Ticks)",ox,y-3);if(popHist.size()>1){int[] arr=popHist.stream().mapToInt(i->i).toArray();int gw=HUD_W-16;int maxP=java.util.Arrays.stream(arr).max().orElse(1);g2.setColor(new Color(100,200,255));g2.setStroke(new BasicStroke(1.3f));for(int i=1;i<arr.length;i++){int x1=ox+(i-1)*gw/arr.length,y1=y+45-(int)(arr[i-1]/(double)maxP*43);int x2=ox+i*gw/arr.length,y2=y+45-(int)(arr[i]/(double)maxP*43);g2.drawLine(x1,y1,x2,y2);}}y+=58;g2.setColor(new Color(180,180,220));g2.setFont(new Font("Monospaced",Font.BOLD,10));g2.drawString("── Gehirnschichten ──",ox,y);y+=13;String[][] layers={{"■","SNN Spike-Aura","255,255,120"},{"■","FEP-Unsicherheit","180,100,255"},{"■","Emotions-Valenz","50,220,50"},{"■","Homeostase-Ring","200,120,50"}};for(String[] l:layers){String[] rgb=l[2].split(",");g2.setColor(new Color(Integer.parseInt(rgb[0]),Integer.parseInt(rgb[1]),Integer.parseInt(rgb[2])));g2.setFont(new Font("Monospaced",Font.PLAIN,10));g2.drawString(l[0]+" "+l[1],ox,y);y+=13;}y+=5;g2.setColor(new Color(180,180,220));g2.setFont(new Font("Monospaced",Font.BOLD,10));g2.drawString("── Pheromone ──",ox,y);y+=13;for(PheromoneType pt:PheromoneType.values()){g2.setColor(new Color(pt.argbColor,true));g2.fillRect(ox,y-9,9,9);g2.setColor(new Color(190,190,200));g2.setFont(new Font("Monospaced",Font.PLAIN,10));g2.drawString(pt.label,ox+12,y);y+=13;}}

    private void hud(Graphics2D g,int x,int y,String k,String v){g.setColor(new Color(130,155,200));g.drawString(k+":",x,y);g.setColor(Color.WHITE);g.drawString(v,x+90,y);}    
}
