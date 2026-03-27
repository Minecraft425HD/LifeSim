package thronglets;

import java.util.*;

public class WorldV6 extends World {
    public final NicheLayer niche;
    private final List<double[]> fireList = new ArrayList<>(); // x, y, radius, warmth/tick

    public WorldV6(double w, double h, int food, int danger, Random rng) {
        super(w, h, food, danger, rng);
        niche = new NicheLayer((int)w, (int)h);
        // 4 Lagerfeuer verteilt innerhalb der Wand-Grenzen
        for (int i = 0; i < 4; i++) {
            double fx = 80 + rng.nextDouble() * (w - 160);
            double fy = 80 + rng.nextDouble() * (h - 160);
            fireList.add(new double[]{fx, fy, 50.0, 1.2}); // x, y, warmradius, warmth/tick
        }
    }

    @Override
    public void tick() { super.tick(); niche.tick(); }

    public List<double[]> getFireList() { return fireList; }

    /** Position des nächsten Lagerfeuers (null wenn keine existieren) */
    public double[] nearestFirePos(double x, double y) {
        double best = Double.MAX_VALUE; double[] bf = null;
        for (double[] f : fireList) {
            double d = dist(x, y, f[0], f[1]);
            if (d < best) { best = d; bf = f; }
        }
        return bf != null ? new double[]{bf[0], bf[1]} : null;
    }

    public double nearestFireDist(double x, double y) {
        double best = Double.MAX_VALUE;
        for (double[] f : fireList) best = Math.min(best, dist(x, y, f[0], f[1]));
        return best;
    }

    /** Wärmemenge die ein Agent an Position (x,y) pro Tick erhält */
    public double warmthAt(double x, double y) {
        double w = 0;
        for (double[] f : fireList) {
            double d = dist(x, y, f[0], f[1]);
            if (d < f[2]) w += f[3] * (1 - d / f[2]);
        }
        return w;
    }
}
