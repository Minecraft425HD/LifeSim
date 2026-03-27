package thronglets;
import javax.swing.*;

public class MainV7 {
    public static void main(String[] args) {
        boolean headless="true".equals(System.getProperty("headless"));
        if(headless){new SimulationV7(42L,null).run();}
        else SwingUtilities.invokeLater(()->{
            RendererV7 r=new RendererV7();
            JFrame f=new JFrame("Thronglets V7 – Maximum Life: SNN + FEP + NanoTransformer");
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.add(r);f.pack();f.setLocationRelativeTo(null);f.setVisible(true);
            SimulationV7 sim=new SimulationV7(42L,r);
            Thread t=new Thread(sim,"V7");t.setDaemon(true);t.start();
            f.addWindowListener(new java.awt.event.WindowAdapter(){
                @Override public void windowClosing(java.awt.event.WindowEvent e){sim.stop();}
            });
        });
    }
}
