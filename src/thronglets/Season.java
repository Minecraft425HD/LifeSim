package thronglets;
public enum Season {
    SPRING(0.35,"Frühling"),SUMMER(0.25,"Sommer"),AUTUMN(0.45,"Herbst"),WINTER(0.75,"Winter");
    public final double energyCost; public final String label;
    Season(double ec,String lb){energyCost=ec;label=lb;}
    public float toFloat(){return ordinal()/3f;}
}
