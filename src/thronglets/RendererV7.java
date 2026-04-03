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
    // Thread-sichere Snapshots für Paint-Thread (volatile Referenz auf unveränderliche Arrays)
    private volatile double[]  feHistSnap  = new double[0];
    private volatile double[]  valHistSnap = new double[0];
    private volatile int[]     popHistSnap = new int[0];
    private final Map<Integer,Color> specColors=new HashMap<>();
    private final Random cRng=new Random(77);
    private final LinkedList<String> thoughtLog = new LinkedList<>();
    private volatile String[] thoughtLogSnap = new String[0];

    // ── Interaktion & Emotionale Bindung ─────────────────────
    private final ConcurrentLinkedQueue<double[]> foodQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<Integer,String> names   = new ConcurrentHashMap<>();
    private volatile Integer selectedId = null;
    private final CopyOnWriteArrayList<DeathAnim> deathAnims = new CopyOnWriteArrayList<>();
    private volatile int extinctionCount = 0;
    private volatile long extinctionFlashUntil = 0;
    private volatile int nestCount = 0;
    private volatile List<Double> genFitHistory = new ArrayList<>();

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
        int reproductionCount;
        int exploredCount;
        float[] brainOut;
        java.util.List<String> recentEvents;
        AgentSnap(ThrongletV7 t, boolean alpha) {
            x=t.x;y=t.y;fitness=t.fitness;id=t.id;groupId=t.groupId;age=t.memory.age;
            reprodCooldown=t.reprodCooldown;
            reproductionCount=t.reproductionCount;
            exploredCount=t.exploredCount;
            speciesId=t.genome.speciesId;stage=t.stage.label;isAlpha=alpha;signal=t.signalOut;
            energy=t.homeostasis.drives[0];valence=t.homeostasis.valence;arousal=t.homeostasis.arousal;
            homeoDrives=t.homeostasis.drives.clone();thought=t.lastThought;
            brainOut=t.lastBrainOut.clone();
            freeEnergy=t.brain.getLastFE();predError=t.brain.getPredError();
            uncertainty=t.brain.getUncertainty();
            hiddenSpikes=t.brain.getHiddenSpikes().clone();
            outputSpikes=t.brain.getOutputSpikes().clone();
            recentEvents=t.memory.recentEvents(5);
        }
    }

    public RendererV7() {
        setPreferredSize(new Dimension(WV+HUD_W,HT));
        setBackground(new Color(6,8,18));
        setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        setFocusable(true);
        addKeyListener(new java.awt.event.KeyAdapter(){
            @Override public void keyPressed(java.awt.event.KeyEvent e){
                SimConfig cfg=SimConfig.INSTANCE;
                switch(e.getKeyChar()){
                    case 'f','F' -> cfg.foodEnabled    = !cfg.foodEnabled;
                    case 'r','R' -> cfg.fireEnabled    = !cfg.fireEnabled;
                    case 's','S' -> cfg.seasonsEnabled = !cfg.seasonsEnabled;
                    case '+','=' -> cfg.speedFactor = Math.min(4.0, cfg.speedFactor + 0.1);
                    case '-'     -> cfg.speedFactor = Math.max(0.1, cfg.speedFactor - 0.1);
                    case '1'     -> cfg.speedFactor = 0.3;
                    case '2'     -> cfg.speedFactor = 0.6;
                    case '3'     -> cfg.speedFactor = 1.2;
                    case '4'     -> cfg.speedFactor = 2.5;
                }
            }
        });
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
    public void setNestCount(int n){nestCount=n;}

    public synchronized void update(List<ThrongletV7> pop, WorldV6 world,
                                     Map<Integer,Group> gmap, int g, int sp, List<Double> gfh) {
        genFitHistory = new ArrayList<>(gfh);
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
                    thoughtLogSnap = thoughtLog.toArray(new String[0]);
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
        // Snapshots für Paint-Thread (kein ConcurrentModificationException mehr)
        feHistSnap  = feHist.stream().mapToDouble(d->d).toArray();
        valHistSnap = valHist.stream().mapToDouble(d->d).toArray();
        popHistSnap = popHist.stream().mapToInt(i->i).toArray();
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
        drawWater(g2);
        drawPhero(g2,sx,sy);
        drawNestZone(g2,sx,sy);
        if(SimConfig.INSTANCE.foodEnabled)
            for(double[] f:food){if(f[2]<=0)continue;int px=(int)(f[0]*sx),py=(int)(f[1]*sy),sz=(int)(2+f[2]/f[3]*7);g2.setColor(new Color(45,200,65,(int)(90+f[2]/f[3]*165)));g2.fillOval(px-sz/2,py-sz/2,sz,sz);}
        g2.setStroke(new BasicStroke(1.5f));
        for(double[] d:danger){int px=(int)(d[0]*sx),py=(int)(d[1]*sy),r=(int)(d[2]*sx);g2.setColor(new Color(200,30,30,32));g2.fillOval(px-r,py-r,r*2,r*2);g2.setColor(new Color(255,60,60,90));g2.drawOval(px-r,py-r,r*2,r*2);}
        // ── Lagerfeuer ──
        if(SimConfig.INSTANCE.fireEnabled) for(double[] f:fires){
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
            // ── Brain-Intent-Icon: zeigt dominante Absicht ──
            if(a.brainOut!=null&&a.brainOut.length>=10){
                // out[2]=Nahrung(grün), out[3]=Feuer(orange), out[4]=Fliehen(rot), out[5]=Sozial(pink), out[9]=Fortpflanzung(magenta)
                int[] idx={2,3,4,5,9};
                Color[] ic={new Color(60,230,60),new Color(255,140,0),new Color(230,40,40),new Color(255,100,200),new Color(200,0,255)};
                int best=-1; float bv=0.55f;
                for(int i=0;i<idx.length;i++) if(a.brainOut[idx[i]]>bv){bv=a.brainOut[idx[i]];best=i;}
                if(best>=0){g2.setColor(ic[best]);g2.fillOval(px+r-2,py-r-2,5,5);}
            }
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
        drawToggles(g2);
        drawSelectedPanel(g2);
        drawSurvivalWarning(g2);
    }

    private void drawToggles(Graphics2D g2){
        SimConfig cfg=SimConfig.INSTANCE;
        // ── Generations-Fitness-Verlauf ──
        List<Double> gfh=genFitHistory;
        if(gfh.size()>1){
            int ox2=WV+8,gy=HT-75,gw=HUD_W-16,gh=35;
            g2.setColor(new Color(20,30,50));g2.fillRect(ox2,gy,gw,gh);
            g2.setColor(new Color(160,160,180));g2.setFont(new Font("Monospaced",Font.PLAIN,8));
            g2.drawString("Beste Fitness/Generation ("+gfh.size()+")",ox2,gy-2);
            double maxF=gfh.stream().mapToDouble(d->d).max().orElse(1);if(maxF<1)maxF=1;
            g2.setColor(new Color(255,200,50));g2.setStroke(new BasicStroke(1.3f));
            for(int i=1;i<gfh.size();i++){
                int x1=ox2+(i-1)*gw/gfh.size(),y1=gy+gh-(int)(gfh.get(i-1)/maxF*gh);
                int x2=ox2+i*gw/gfh.size(),     y2=gy+gh-(int)(gfh.get(i)/maxF*gh);
                g2.drawLine(x1,y1,x2,y2);
            }
            g2.setStroke(new BasicStroke(1f));
        }
        int ox=WV+8, y=HT-30;
        g2.setFont(new Font("Monospaced",Font.BOLD,11));
        String[][] toggles={{"[F] Nahrung",cfg.foodEnabled?"1":"0"},
                            {"[R] Feuer",  cfg.fireEnabled?"1":"0"},
                            {"[S] Saison", cfg.seasonsEnabled?"1":"0"}};
        for(String[] t:toggles){
            boolean on=t[1].equals("1");
            g2.setColor(on?new Color(80,220,80):new Color(180,60,60));
            g2.fillRoundRect(ox,y-11,100,14,5,5);
            g2.setColor(Color.WHITE);
            g2.drawString(t[0]+" "+(on?"AN":"AUS"),ox+3,y);
            ox+=108;
        }
        // Geschwindigkeits-Kontrolle
        g2.setColor(new Color(60,120,220)); g2.fillRoundRect(WV+8,y-26,HUD_W-16,13,4,4);
        g2.setColor(Color.WHITE);
        g2.drawString(String.format("[+/-] Geschw: %.1fx  [1-4]=Voreinst.",cfg.speedFactor),WV+11,y-16);
    }

    private void drawPhero(Graphics2D g2,double sx,double sy){double[][][] p=ph;if(p==null)return;int cs=10;for(PheromoneType pt:PheromoneType.values()){Color base=new Color(pt.argbColor,true);for(int cx=0;cx<p[pt.id].length;cx++)for(int cy=0;cy<p[pt.id][cx].length;cy++){double s=p[pt.id][cx][cy];if(s<0.025)continue;int alpha = (int)(s * 110);if (alpha < 0) alpha = 0;if (alpha > 255) alpha = 255;g2.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha));g2.fillRect((int)(cx*cs*sx),(int)(cy*cs*sy),(int)(cs*sx)+1,(int)(cs*sy)+1);}}}

    private void drawHUD(Graphics2D g2){int ox=WV+8;g2.setColor(new Color(9,11,22));g2.fillRect(WV,0,HUD_W,HT);g2.setColor(new Color(120,200,255));g2.setFont(new Font("Monospaced",Font.BOLD,15));g2.drawString("THRONGLETS  V9",ox,26);g2.setColor(new Color(255,150,50));g2.setFont(new Font("Monospaced",Font.BOLD,10));g2.drawString("SNN + FEP (Friston) + NanoTransformer",ox,40);int y=60;g2.setFont(new Font("Monospaced",Font.PLAIN,12));hud(g2,ox,y,"Tick",""+tick);hud(g2,ox,y+18,"Gen",""+gen);hud(g2,ox,y+36,"Pop",""+popSize);hud(g2,ox,y+54,"Spezies",""+specCount);hud(g2,ox,y+72,"Jahresz",season);hud(g2,ox,y+90,"Ø Fit",String.format("%.1f",avgFit));hud(g2,ox,y+108,"Ø FE",String.format("%.3f",avgFE));hud(g2,ox,y+126,"Ø PredE",String.format("%.3f",avgPE));hud(g2,ox,y+144,"Ø Valenz",String.format("%.2f",avgVal));hud(g2,ox,y+162,"Geschw",String.format("%.1fx",SimConfig.INSTANCE.speedFactor));y=238;g2.setColor(new Color(200,220,150));g2.setFont(new Font("Monospaced",Font.BOLD,10));g2.drawString("── Innerer Monolog ──",ox,y);y+=13;g2.setFont(new Font("Monospaced",Font.PLAIN,10));for(String t:thoughtLogSnap){g2.setColor(new Color(180,220,180));String line=t.length()>28?t.substring(0,28):t;g2.drawString(line,ox,y);y+=13;}y+=8;g2.setColor(new Color(60,60,100));g2.fillRect(ox,y,HUD_W-16,60);g2.setFont(new Font("Monospaced",Font.PLAIN,9));g2.setColor(new Color(180,180,180));g2.drawString("Ø Freie Energie (lila) | Ø Valenz (grün)",ox,y-3);if(feHistSnap.length>1){double[] arr=feHistSnap;int gw=HUD_W-16;double maxV=java.util.Arrays.stream(arr).max().orElse(1);if(maxV<0.001)maxV=0.001;g2.setColor(new Color(180,80,255));g2.setStroke(new BasicStroke(1.3f));for(int i=1;i<arr.length;i++){int x1=ox+(i-1)*gw/arr.length,y1=y+60-(int)(arr[i-1]/maxV*58);int x2=ox+i*gw/arr.length,y2=y+60-(int)(arr[i]/maxV*58);g2.drawLine(x1,y1,x2,y2);}}if(valHistSnap.length>1){double[] arr=valHistSnap;int gw=HUD_W-16;g2.setColor(new Color(80,220,100));g2.setStroke(new BasicStroke(1.2f));for(int i=1;i<arr.length;i++){int x1=ox+(i-1)*gw/arr.length,y1=y+30-(int)(arr[i-1]*28);int x2=ox+i*gw/arr.length,y2=y+30-(int)(arr[i]*28);g2.drawLine(x1,y1,x2,y2);}}y+=74;g2.setColor(new Color(40,50,80));g2.fillRect(ox,y,HUD_W-16,45);g2.setColor(new Color(160,160,160));g2.setFont(new Font("Monospaced",Font.PLAIN,9));g2.drawString("Population (300 Ticks)",ox,y-3);if(popHistSnap.length>1){int[] arr=popHistSnap;int gw=HUD_W-16;int maxP=java.util.Arrays.stream(arr).max().orElse(1);g2.setColor(new Color(100,200,255));g2.setStroke(new BasicStroke(1.3f));for(int i=1;i<arr.length;i++){int x1=ox+(i-1)*gw/arr.length,y1=y+45-(int)(arr[i-1]/(double)maxP*43);int x2=ox+i*gw/arr.length,y2=y+45-(int)(arr[i]/(double)maxP*43);g2.drawLine(x1,y1,x2,y2);}}y+=58;g2.setColor(new Color(180,180,220));g2.setFont(new Font("Monospaced",Font.BOLD,10));g2.drawString("── Gehirnschichten ──",ox,y);y+=13;String[][] layers={{"■","SNN Spike-Aura","255,255,120"},{"■","FEP-Unsicherheit","180,100,255"},{"■","Emotions-Valenz","50,220,50"},{"■","Homeostase-Ring","200,120,50"}};for(String[] l:layers){String[] rgb=l[2].split(",");g2.setColor(new Color(Integer.parseInt(rgb[0]),Integer.parseInt(rgb[1]),Integer.parseInt(rgb[2])));g2.setFont(new Font("Monospaced",Font.PLAIN,10));g2.drawString(l[0]+" "+l[1],ox,y);y+=13;}y+=5;g2.setColor(new Color(180,180,220));g2.setFont(new Font("Monospaced",Font.BOLD,10));g2.drawString("── Pheromone ──",ox,y);y+=13;for(PheromoneType pt:PheromoneType.values()){g2.setColor(new Color(pt.argbColor,true));g2.fillRect(ox,y-9,9,9);g2.setColor(new Color(190,190,200));g2.setFont(new Font("Monospaced",Font.PLAIN,10));g2.drawString(pt.label,ox+12,y);y+=13;}}

    private void drawSelectedPanel(Graphics2D g2){
        if(selectedId==null) return;
        AgentSnap sel=null; for(AgentSnap a:agents) if(a.id==selectedId){sel=a;break;}
        if(sel==null) return;
        double sx=WV/worldW,sy=HT/worldH;
        int px=(int)(sel.x*sx),py=(int)(sel.y*sy);
        int pw=168,ph=280;
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
        int cy=panelY+14;
        // Name / ID
        String nm2=names.containsKey(sel.id)?"\""+names.get(sel.id)+"\"":"T#"+sel.id;
        g2.setFont(new Font("SansSerif",Font.BOLD,11)); g2.setColor(new Color(255,215,0));
        g2.drawString(nm2,panelX+5,cy); cy+=13;
        // Basisinfos
        g2.setFont(new Font("SansSerif",Font.PLAIN,9)); g2.setColor(new Color(180,210,255));
        g2.drawString(sel.stage+" | Alter "+sel.age,panelX+5,cy); cy+=11;
        g2.drawString(String.format("Fitness %.1f",sel.fitness),panelX+5,cy); cy+=11;
        g2.drawString(String.format("FE %.3f  PE %.3f",sel.freeEnergy,sel.predError),panelX+5,cy); cy+=13;
        // Drive-Balken: Energie, Stress, Sozial, Neugier, Wärme
        String[] dl={"E","St","So","Ne","W"};
        int[] dc={0xFF44BB44,0xFFDD4444,0xFF44AADD,0xFFDDAA22,0xFFDD8833};
        for(int i=0;i<5&&i<sel.homeoDrives.length;i++){
            int bx=panelX+4+i*31,by=cy;
            double dv=sel.homeoDrives[i]/100.0;
            g2.setColor(new Color(30,30,55)); g2.fillRect(bx,by,27,5);
            g2.setColor(new Color(dc[i])); g2.fillRect(bx,by,(int)(27*dv),5);
            g2.setColor(new Color(140,140,180)); g2.setFont(new Font("SansSerif",Font.PLAIN,8));
            g2.drawString(dl[i],bx+8,by+16);
        }
        cy+=22;
        // Vitalitäts-Balken (volle Breite, lila/gold)
        if(sel.homeoDrives.length>5){
            double vit=sel.homeoDrives[5]/100.0;
            int vx=panelX+4,vw=pw-8,vh=4;
            g2.setColor(new Color(30,20,50)); g2.fillRect(vx,cy,vw,vh);
            Color vitCol=vit>0.7?new Color(200,80,255):vit>0.4?new Color(150,60,200):new Color(100,40,140);
            g2.setColor(vitCol); g2.fillRect(vx,cy,(int)(vw*vit),vh);
            g2.setFont(new Font("SansSerif",Font.PLAIN,8)); g2.setColor(new Color(200,150,255));
            g2.drawString(String.format("Vi %.0f%%",vit*100),vx+vw+3,cy+5);
            cy+=10;
        }
        // Aktueller Gedanke
        if(sel.thought!=null&&!sel.thought.equals("…")){
            g2.setFont(new Font("SansSerif",Font.ITALIC,9)); g2.setColor(new Color(160,255,160));
            String th=sel.thought.length()>22?sel.thought.substring(0,22):sel.thought;
            g2.drawString("\u201e"+th+"\u201c",panelX+5,cy); cy+=11;
        }
        // Reproduktions-Status
        g2.setFont(new Font("SansSerif",Font.PLAIN,9));
        if(sel.reprodCooldown>0){
            g2.setColor(new Color(180,120,120));
            g2.drawString("Reprod. in "+sel.reprodCooldown+" T",panelX+5,cy);
        } else {
            if(sel.valence>0.3){g2.setColor(new Color(120,255,120));g2.drawString("Bereit zur Paarung ♥",panelX+5,cy);}
            else{g2.setColor(new Color(160,160,200));g2.drawString(String.format("Valenz %.2f",sel.valence),panelX+5,cy);}
        }
        cy+=11;
        // Meilensteine
        g2.setFont(new Font("SansSerif",Font.PLAIN,8)); g2.setColor(new Color(200,150,255));
        int exPct=(int)(sel.exploredCount/4.0);
        g2.drawString(String.format("Erforsch %d%%  Nachk x%d",exPct,sel.reproductionCount),panelX+5,cy); cy+=12;
        // ── Gehirn-Ausgaben (10 Balken) ────────────────────────────
        g2.setColor(new Color(200,220,255,180)); g2.setFont(new Font("SansSerif",Font.BOLD,8));
        g2.drawString("── Gehirnausgaben ──",panelX+5,cy); cy+=10;
        String[] outLabels={"Dir↗","Spd▶","Nahr","Feu","Fli!","Soz","Sig?","Typ.","Nest","Rep!"};
        Color[] outColors={new Color(180,180,180),new Color(200,200,100),new Color(60,220,60),new Color(255,160,40),
                           new Color(220,60,60),new Color(255,120,200),new Color(120,180,255),new Color(120,180,255),
                           new Color(200,180,80),new Color(200,60,255)};
        int barW=pw-46; // Breite des Balkens
        g2.setFont(new Font("Monospaced",Font.PLAIN,8));
        if(sel.brainOut!=null){
            for(int i=0;i<Math.min(10,sel.brainOut.length);i++){
                float v=Math.max(0f,Math.min(1f,sel.brainOut[i]));
                g2.setColor(new Color(25,25,45)); g2.fillRect(panelX+32,cy-7,barW,8);
                g2.setColor(outColors[i]); g2.fillRect(panelX+32,cy-7,(int)(barW*v),8);
                g2.setColor(new Color(160,160,190)); g2.drawString(outLabels[i],panelX+4,cy);
                g2.setColor(new Color(200,200,200)); g2.drawString(String.format("%.2f",v),panelX+32+barW+2,cy);
                cy+=10;
            }
        }
        // ── Letzte Ereignisse ──────────────────────────────────────
        cy+=2;
        g2.setColor(new Color(200,220,255,180)); g2.setFont(new Font("SansSerif",Font.BOLD,8));
        g2.drawString("── Letzte Ereignisse ──",panelX+5,cy); cy+=10;
        g2.setFont(new Font("SansSerif",Font.PLAIN,8));
        if(sel.recentEvents!=null){
            for(String ev:sel.recentEvents){
                g2.setColor(new Color(180,230,180)); g2.drawString(ev,panelX+5,cy); cy+=10;
            }
        }
        // Hinweis
        g2.setFont(new Font("SansSerif",Font.PLAIN,7)); g2.setColor(new Color(90,90,120));
        g2.drawString("Rechtsklick=Namen entfernen",panelX+5,cy+2);
    }

    private void drawWater(Graphics2D g2){
        // Wassertiefe 70px vom Rand: Flachwasser (hellblau) → Tiefwasser (dunkelblau)
        double sx=WV/worldW,sy=HT/worldH;
        int wl=(int)(70*sx),wt=(int)(70*sy);
        // Flaches Wasser (halbe Tiefe, helleres Blau)
        g2.setPaint(new java.awt.GradientPaint(0,0,new Color(30,100,220,120),wl,0,new Color(30,100,220,0)));
        g2.fillRect(0,0,wl,HT);
        g2.setPaint(new java.awt.GradientPaint(WV,0,new Color(30,100,220,120),WV-wl,0,new Color(30,100,220,0)));
        g2.fillRect(WV-wl,0,wl,HT);
        g2.setPaint(new java.awt.GradientPaint(0,0,new Color(30,100,220,120),0,wt,new Color(30,100,220,0)));
        g2.fillRect(0,0,WV,wt);
        g2.setPaint(new java.awt.GradientPaint(0,HT,new Color(30,100,220,120),0,HT-wt,new Color(30,100,220,0)));
        g2.fillRect(0,HT-wt,WV,wt);
        // Tiefes Wasser (äußerste 35px, dunkleres Blau)
        int dl=(int)(35*sx),dt=(int)(35*sy);
        g2.setPaint(new java.awt.GradientPaint(0,0,new Color(8,40,160,170),dl,0,new Color(8,40,160,0)));
        g2.fillRect(0,0,dl,HT);
        g2.setPaint(new java.awt.GradientPaint(WV,0,new Color(8,40,160,170),WV-dl,0,new Color(8,40,160,0)));
        g2.fillRect(WV-dl,0,dl,HT);
        g2.setPaint(new java.awt.GradientPaint(0,0,new Color(8,40,160,170),0,dt,new Color(8,40,160,0)));
        g2.fillRect(0,0,WV,dt);
        g2.setPaint(new java.awt.GradientPaint(0,HT,new Color(8,40,160,170),0,HT-dt,new Color(8,40,160,0)));
        g2.fillRect(0,HT-dt,WV,dt);
        g2.setPaint(null);
        // Beschriftung
        g2.setFont(new Font("SansSerif",Font.PLAIN,8));
        g2.setColor(new Color(80,160,255,180)); g2.drawString("~Flachwasser",4,HT/2);
        g2.setColor(new Color(40,80,200,180));  g2.drawString("~Tiefwasser",4,HT/2+11);
    }

    private void drawNestZone(Graphics2D g2,double sx,double sy){
        int cx=(int)(worldW/2*sx),cy=(int)(worldH/2*sy),nr=(int)(120*Math.min(sx,sy));
        if(nestCount>=3){
            float pulse=0.55f+0.45f*(float)Math.sin(tick*0.05);
            g2.setColor(new Color(1f,0.85f,0.1f,0.13f*pulse));
            g2.fillOval(cx-nr,cy-nr,nr*2,nr*2);
            g2.setColor(new Color(1f,0.85f,0.1f,0.55f));
            g2.setStroke(new BasicStroke(2f));
            g2.drawOval(cx-nr,cy-nr,nr*2,nr*2);
            g2.setStroke(new BasicStroke(1f));
            g2.setFont(new Font("SansSerif",Font.BOLD,10));
            g2.setColor(new Color(255,220,50));
            g2.drawString("NEST ("+nestCount+")",cx-25,cy-nr-4);
        } else {
            g2.setColor(new Color(1f,0.85f,0.1f,0.05f));
            g2.fillOval(cx-nr,cy-nr,nr*2,nr*2);
            g2.setColor(new Color(1f,0.85f,0.1f,0.18f));
            g2.setStroke(new BasicStroke(1f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND,0,new float[]{4,4},0));
            g2.drawOval(cx-nr,cy-nr,nr*2,nr*2);
            g2.setStroke(new BasicStroke(1f));
        }
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
