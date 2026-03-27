package thronglets;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;

public class RendererV7 extends JPanel {
    private static final int WV=620, HT=660, HUD_W=300;
    private volatile List<AgentSnap>    agents  = new ArrayList<>();
    private volatile List<double[]>     food    = new ArrayList<>();
    private volatile List<double[]>     danger  = new ArrayList<>();
    private volatile List<double[]>     fires   = new ArrayList<>();
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

    // ── Interaktion & Emotionale Bindung ─────────────────────
    private final ConcurrentLinkedQueue<double[]> foodQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<Integer,String> names   = new ConcurrentHashMap<>();
    private volatile Integer selectedId = null;
    private final CopyOnWriteArrayList<DeathAnim> deathAnims = new CopyOnWriteArrayList<>();
    private volatile int extinctionCount = 0;
    private volatile long extinctionFlashUntil = 0;

    static class DeathAnim {
        final double x,y; final int startTick; final String cause;
        DeathAnim(double x,double y,int t,String c){this.x=x;this.y=y;startTick=t;cause=c;}
    }

    static class AgentSnap {
        double x,y,energy,fitness,valence,arousal,freeEnergy,predError,uncertainty;
        int id,groupId,age,speciesId;
        double[] homeoDrives;
        String stage,thought;
        boolean isAlpha;
        boolean[] hiddenSpikes,outputSpikes;
        Signal signal;
        int reprodCooldown;
        AgentSnap(ThrongletV7 t, boolean alpha) {
            x=t.x;y=t.y;fitness=t.fitness;id=t.id;groupId=t.groupId;age=t.memory.age;
            reprodCooldown=t.reprodCooldown;
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
        setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        addMouseListener(new java.awt.event.MouseAdapter(){
            @Override public void mouseClicked(java.awt.event.MouseEvent e){
                if(e.getX()>=WV) return;
                double wx=e.getX()/(WV/worldW), wy=e.getY()/(HT/worldH);
                // Nächsten Agenten suchen
                AgentSnap near=null; double best=22;
                for(AgentSnap a:agents){double d=Math.hypot(a.x-wx,a.y-wy);if(d<best){best=d;near=a;}}
                if(near!=null){
                    int aid=near.id;
                    if(e.getButton()==java.awt.event.MouseEvent.BUTTON3){
                        // Rechtsklick: Name löschen / abwählen
                        names.remove(aid); if(Integer.valueOf(aid).equals(selectedId)) selectedId=null;
                    } else {
                        selectedId=aid;
                        String cur=names.getOrDefault(aid,"");
                        String nm=JOptionPane.showInputDialog(RendererV7.this,
                            "<html><b>Name für "+near.stage+" #"+aid+"</b><br><small>Leer lassen = nur auswählen</small></html>",cur);
                        if(nm!=null&&!nm.trim().isEmpty()) names.put(aid,nm.trim());
                    }
                } else {
                    selectedId=null;
                    // Linksklick auf leeren Bereich: Nahrung platzieren
                    if(e.getButton()!=java.awt.event.MouseEvent.BUTTON3) foodQueue.add(new double[]{wx,wy});
                }
            }
        });
    }

    public ConcurrentLinkedQueue<double[]> getFoodQueue(){return foodQueue;}
    public void addDeath(double x,double y,int tick,String cause){deathAnims.add(new DeathAnim(x,y,tick,cause));}
    public void notifyExtinction(int count){extinctionCount=count;extinctionFlashUntil=System.currentTimeMillis()+6000;}

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
        agents=snap;food=new ArrayList<>(world.getFoodList());danger=new ArrayList<>(world.getDangerList());fires=new ArrayList<>(world.getFireList());
        // Abgestorbene Todes-Animationen und ungültige Selektion aufräumen
        deathAnims.removeIf(d->(tick-d.startTick)>90);
        if(selectedId!=null&&snap.stream().noneMatch(a->a.id==selectedId)) selectedId=null;
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
        // ── Lagerfeuer ──
        for(double[] f:fires){
            int px=(int)(f[0]*sx),py=(int)(f[1]*sy);
            int wr=(int)(f[2]*sx*0.55); // Wärme-Aura
            float flicker=0.75f+0.25f*(float)Math.sin(tick*0.18+f[0]*0.07);
            g2.setColor(new Color(1f,0.35f*flicker,0f,0.10f*flicker));
            g2.fillOval(px-wr,py-wr,wr*2,wr*2);
            int mr=(int)(f[2]*sx*0.18); // Mittlerer Glanz
            g2.setColor(new Color(1f,0.55f+0.15f*flicker,0.05f,0.30f));
            g2.fillOval(px-mr,py-mr,mr*2,mr*2);
            int cr=4+(int)(2*flicker); // Kern
            g2.setColor(new Color(1f,0.92f,0.45f,0.95f));
            g2.fillOval(px-cr,py-cr,cr*2,cr*2);
            g2.setColor(new Color(255,120,20,200));
            g2.setFont(new Font("SansSerif",Font.BOLD,9));
            g2.drawString("Feuer",px-13,py-cr-3);
        }
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
            // ── Name-Schild ──
            String aName=names.get(a.id);
            if(aName!=null){g2.setFont(new Font("SansSerif",Font.BOLD,10));FontMetrics fmn=g2.getFontMetrics();int tnw=fmn.stringWidth(aName)+8;int nlx=px-tnw/2,nly=py-r-22;g2.setColor(new Color(255,215,0,210));g2.fillRoundRect(nlx,nly,tnw,13,4,4);g2.setColor(new Color(0,0,0,220));g2.drawString(aName,nlx+4,nly+10);}
            // ── Selektionsring ──
            if(Integer.valueOf(a.id).equals(selectedId)){g2.setColor(new Color(255,255,255,200));g2.setStroke(new BasicStroke(2.5f));g2.drawOval(px-r-5,py-r-5,(r+5)*2,(r+5)*2);g2.setStroke(new BasicStroke(1f));}
        }
        // ── Todesanimationen ──
        for(DeathAnim da:deathAnims){
            int age=tick-da.startTick; if(age>70) continue;
            float al=Math.max(0f,1f-age/70f);
            int px=(int)(da.x*sx),py=(int)(da.y*sy),r2=(int)(5+age*0.45f);
            g2.setColor(new Color(1f,0.15f,0.1f,al*0.45f));g2.fillOval(px-r2,py-r2,r2*2,r2*2);
            g2.setColor(new Color(1f,0.9f,0.3f,al));g2.setStroke(new BasicStroke(2f));
            g2.drawLine(px-4,py,px+4,py);g2.drawLine(px,py-4,px,py+4);g2.setStroke(new BasicStroke(1f));
            if(age<30&&da.cause!=null){g2.setColor(new Color(1f,0.55f,0.55f,al));g2.setFont(new Font("SansSerif",Font.PLAIN,8));g2.drawString(da.cause,px+5,py-4);}
        }
        drawHUD(g2);
        drawSelectedPanel(g2);
        drawSurvivalWarning(g2);
    }

    private void drawPhero(Graphics2D g2,double sx,double sy){double[][][] p=ph;if(p==null)return;int cs=10;for(PheromoneType pt:PheromoneType.values()){Color base=new Color(pt.argbColor,true);for(int cx=0;cx<p[pt.id].length;cx++)for(int cy=0;cy<p[pt.id][cx].length;cy++){double s=p[pt.id][cx][cy];if(s<0.025)continue;int alpha = (int)(s * 110);if (alpha < 0) alpha = 0;if (alpha > 255) alpha = 255;g2.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha));g2.fillRect((int)(cx*cs*sx),(int)(cy*cs*sy),(int)(cs*sx)+1,(int)(cs*sy)+1);}}}

    private void drawHUD(Graphics2D g2){int ox=WV+8;g2.setColor(new Color(9,11,22));g2.fillRect(WV,0,HUD_W,HT);g2.setColor(new Color(120,200,255));g2.setFont(new Font("Monospaced",Font.BOLD,15));g2.drawString("THRONGLETS  V7",ox,26);g2.setColor(new Color(255,150,50));g2.setFont(new Font("Monospaced",Font.BOLD,10));g2.drawString("SNN + FEP (Friston) + NanoTransformer",ox,40);int y=60;g2.setFont(new Font("Monospaced",Font.PLAIN,12));hud(g2,ox,y,"Tick",""+tick);hud(g2,ox,y+18,"Gen",""+gen);hud(g2,ox,y+36,"Pop",""+popSize);hud(g2,ox,y+54,"Spezies",""+specCount);hud(g2,ox,y+72,"Jahresz",season);hud(g2,ox,y+90,"Ø Fit",String.format("%.1f",avgFit));hud(g2,ox,y+108,"Ø FE",String.format("%.3f",avgFE));hud(g2,ox,y+126,"Ø PredE",String.format("%.3f",avgPE));hud(g2,ox,y+144,"Ø Valenz",String.format("%.2f",avgVal));y=225;g2.setColor(new Color(200,220,150));g2.setFont(new Font("Monospaced",Font.BOLD,10));g2.drawString("── Innerer Monolog ──",ox,y);y+=13;g2.setFont(new Font("Monospaced",Font.PLAIN,10));for(String t:thoughtLog){g2.setColor(new Color(180,220,180));String line=t.length()>28?t.substring(0,28):t;g2.drawString(line,ox,y);y+=13;}y+=8;g2.setColor(new Color(60,60,100));g2.fillRect(ox,y,HUD_W-16,60);g2.setFont(new Font("Monospaced",Font.PLAIN,9));g2.setColor(new Color(180,180,180));g2.drawString("Ø Freie Energie (lila) | Ø Valenz (grün)",ox,y-3);if(feHist.size()>1){double[] arr=feHist.stream().mapToDouble(d->d).toArray();int gw=HUD_W-16;double maxV=java.util.Arrays.stream(arr).max().orElse(1);if(maxV<0.001)maxV=0.001;g2.setColor(new Color(180,80,255));g2.setStroke(new BasicStroke(1.3f));for(int i=1;i<arr.length;i++){int x1=ox+(i-1)*gw/arr.length,y1=y+60-(int)(arr[i-1]/maxV*58);int x2=ox+i*gw/arr.length,y2=y+60-(int)(arr[i]/maxV*58);g2.drawLine(x1,y1,x2,y2);}}if(valHist.size()>1){double[] arr=valHist.stream().mapToDouble(d->d).toArray();int gw=HUD_W-16;g2.setColor(new Color(80,220,100));g2.setStroke(new BasicStroke(1.2f));for(int i=1;i<arr.length;i++){int x1=ox+(i-1)*gw/arr.length,y1=y+30-(int)(arr[i-1]*28);int x2=ox+i*gw/arr.length,y2=y+30-(int)(arr[i]*28);g2.drawLine(x1,y1,x2,y2);}}y+=74;g2.setColor(new Color(40,50,80));g2.fillRect(ox,y,HUD_W-16,45);g2.setColor(new Color(160,160,160));g2.setFont(new Font("Monospaced",Font.PLAIN,9));g2.drawString("Population (300 Ticks)",ox,y-3);if(popHist.size()>1){int[] arr=popHist.stream().mapToInt(i->i).toArray();int gw=HUD_W-16;int maxP=java.util.Arrays.stream(arr).max().orElse(1);g2.setColor(new Color(100,200,255));g2.setStroke(new BasicStroke(1.3f));for(int i=1;i<arr.length;i++){int x1=ox+(i-1)*gw/arr.length,y1=y+45-(int)(arr[i-1]/(double)maxP*43);int x2=ox+i*gw/arr.length,y2=y+45-(int)(arr[i]/(double)maxP*43);g2.drawLine(x1,y1,x2,y2);}}y+=58;g2.setColor(new Color(180,180,220));g2.setFont(new Font("Monospaced",Font.BOLD,10));g2.drawString("── Gehirnschichten ──",ox,y);y+=13;String[][] layers={{"■","SNN Spike-Aura","255,255,120"},{"■","FEP-Unsicherheit","180,100,255"},{"■","Emotions-Valenz","50,220,50"},{"■","Homeostase-Ring","200,120,50"}};for(String[] l:layers){String[] rgb=l[2].split(",");g2.setColor(new Color(Integer.parseInt(rgb[0]),Integer.parseInt(rgb[1]),Integer.parseInt(rgb[2])));g2.setFont(new Font("Monospaced",Font.PLAIN,10));g2.drawString(l[0]+" "+l[1],ox,y);y+=13;}y+=5;g2.setColor(new Color(180,180,220));g2.setFont(new Font("Monospaced",Font.BOLD,10));g2.drawString("── Pheromone ──",ox,y);y+=13;for(PheromoneType pt:PheromoneType.values()){g2.setColor(new Color(pt.argbColor,true));g2.fillRect(ox,y-9,9,9);g2.setColor(new Color(190,190,200));g2.setFont(new Font("Monospaced",Font.PLAIN,10));g2.drawString(pt.label,ox+12,y);y+=13;}}

    private void drawSelectedPanel(Graphics2D g2){
        if(selectedId==null) return;
        AgentSnap sel=null; for(AgentSnap a:agents) if(a.id==selectedId){sel=a;break;}
        if(sel==null) return;
        double sx=WV/worldW,sy=HT/worldH;
        int px=(int)(sel.x*sx),py=(int)(sel.y*sy);
        int pw=165,ph=115;
        int panelX=Math.min(WV-pw-4,px+14);
        int panelY=Math.max(4,Math.min(HT-ph-4,py-ph/2));
        // Verbindungslinie
        g2.setColor(new Color(255,215,0,80));
        g2.setStroke(new BasicStroke(1f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND,0,new float[]{3,3},0));
        g2.drawLine(px,py,panelX,panelY+ph/2);
        g2.setStroke(new BasicStroke(1f));
        // Panel-Hintergrund
        g2.setColor(new Color(5,10,35,230)); g2.fillRoundRect(panelX,panelY,pw,ph,7,7);
        g2.setColor(new Color(255,215,0,160)); g2.drawRoundRect(panelX,panelY,pw,ph,7,7);
        // Name / ID
        String nm2=names.containsKey(sel.id)?"\""+names.get(sel.id)+"\"":"T#"+sel.id;
        g2.setFont(new Font("SansSerif",Font.BOLD,11)); g2.setColor(new Color(255,215,0));
        g2.drawString(nm2,panelX+5,panelY+14);
        // Basisinfos
        g2.setFont(new Font("SansSerif",Font.PLAIN,9)); g2.setColor(new Color(180,210,255));
        g2.drawString(sel.stage+" | Alter "+sel.age,panelX+5,panelY+27);
        g2.drawString(String.format("Fitness %.1f",sel.fitness),panelX+5,panelY+38);
        g2.drawString(String.format("FE %.3f  PE %.3f",sel.freeEnergy,sel.predError),panelX+5,panelY+49);
        // Drive-Balken: Energie, Stress, Sozial, Neugier, Wärme
        String[] dl={"E","St","So","Ne","W"};
        int[] dc={0xFF44BB44,0xFFDD4444,0xFF44AADD,0xFFDDAA22,0xFFDD8833};
        for(int i=0;i<5&&i<sel.homeoDrives.length;i++){
            int bx=panelX+4+i*31,by=panelY+56;
            double dv=sel.homeoDrives[i]/100.0;
            g2.setColor(new Color(30,30,55)); g2.fillRect(bx,by,27,5);
            g2.setColor(new Color(dc[i])); g2.fillRect(bx,by,(int)(27*dv),5);
            g2.setColor(new Color(140,140,180)); g2.setFont(new Font("SansSerif",Font.PLAIN,8));
            g2.drawString(dl[i],bx+8,by+16);
        }
        // Aktueller Gedanke
        if(sel.thought!=null&&!sel.thought.equals("…")){
            g2.setFont(new Font("SansSerif",Font.ITALIC,9)); g2.setColor(new Color(160,255,160));
            String th=sel.thought.length()>20?sel.thought.substring(0,20):sel.thought;
            g2.drawString("\u201e"+th+"\u201c",panelX+5,panelY+84);
        }
        // Reproduktions-Status
        g2.setFont(new Font("SansSerif",Font.PLAIN,9));
        if(sel.reprodCooldown>0){
            g2.setColor(new Color(180,120,120));
            g2.drawString("Reprod. in "+sel.reprodCooldown+" T",panelX+5,panelY+97);
        } else {
            double val=sel.valence;
            if(val>0.5){g2.setColor(new Color(120,255,120));g2.drawString("Bereit zur Paarung",panelX+5,panelY+97);}
            else{g2.setColor(new Color(160,160,200));g2.drawString(String.format("Valenz %.2f/0.5",val),panelX+5,panelY+97);}
        }
        // Hinweis
        g2.setFont(new Font("SansSerif",Font.PLAIN,7)); g2.setColor(new Color(90,90,120));
        g2.drawString("Rechtsklick=Namen entfernen",panelX+5,panelY+110);
    }

    private void drawSurvivalWarning(Graphics2D g2){
        long now=System.currentTimeMillis();
        boolean extinct=now<extinctionFlashUntil;
        boolean critical=popSize>0&&popSize<=3;
        if(!extinct&&!critical) return;

        if(extinct){
            // Rotes Aufblitzen: "AUSGESTORBEN"
            float phase=(float)((extinctionFlashUntil-now)/6000.0);
            float alpha=Math.min(0.7f,phase*2);
            g2.setColor(new Color(0.7f,0f,0f,alpha*0.4f));
            g2.fillRect(0,0,WV,HT);
            g2.setFont(new Font("Monospaced",Font.BOLD,22));
            String msg="AUSGESTORBEN #"+extinctionCount;
            FontMetrics fm=g2.getFontMetrics();
            int tx=(WV-fm.stringWidth(msg))/2,ty=HT/2;
            g2.setColor(new Color(0,0,0,180));
            g2.fillRoundRect(tx-10,ty-24,fm.stringWidth(msg)+20,36,8,8);
            g2.setColor(new Color(1f,0.2f,0.2f,alpha));
            g2.drawString(msg,tx,ty);
            g2.setFont(new Font("SansSerif",Font.PLAIN,12));
            String sub="Neue Generation wächst – Fitness-Strafe aktiv";
            int sw=g2.getFontMetrics().stringWidth(sub);
            g2.setColor(new Color(1f,0.6f,0.6f,alpha*0.9f));
            g2.drawString(sub,(WV-sw)/2,ty+22);
        } else {
            // Kritische Population: Pulsierender Rahmen
            float pulse=0.5f+0.5f*(float)Math.sin(now*0.006);
            g2.setColor(new Color(1f,0.3f,0f,pulse*0.5f));
            g2.setStroke(new java.awt.BasicStroke(6f));
            g2.drawRect(3,3,WV-6,HT-6);
            g2.setStroke(new java.awt.BasicStroke(1f));
            g2.setFont(new Font("Monospaced",Font.BOLD,11));
            String warn="POP KRITISCH: "+popSize;
            g2.setColor(new Color(1f,0.4f,0.1f,pulse));
            g2.drawString(warn,8,HT-8);
        }
    }

    private void hud(Graphics2D g,int x,int y,String k,String v){g.setColor(new Color(130,155,200));g.drawString(k+":",x,y);g.setColor(Color.WHITE);g.drawString(v,x+90,y);}
}
