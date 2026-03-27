package thronglets;
public enum Signal {
    NONE(0,"—"),FOOD_NEAR(1,"Nahrung"),DANGER_NEAR(2,"Gefahr"),
    MATE_CALL(3,"Partner"),DISTRESS(4,"Hilfe"),TERRITORY(5,"Revier");
    public final int id; public final String label;
    Signal(int id,String lb){this.id=id;this.label=lb;}
    public float toFloat(){return id/5f;}
    public static Signal fromFloat(float v){int i=Math.round(v*5);return values()[Math.max(0,Math.min(values().length-1,i))];}
}
