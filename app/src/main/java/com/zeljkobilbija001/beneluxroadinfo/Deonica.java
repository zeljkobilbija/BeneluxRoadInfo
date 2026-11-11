package com.zeljkobilbija001.beneluxroadinfo;

/** Neprekinuti deo autoputa. Kilometri se računaju od njegovog početka. */
public final class Deonica {
    public final String od, doMesta;
    /** Kuda se ide kada kilometri rastu (+) i kada opadaju (-). */
    public final String plus, minus;

    public Deonica(String od, String doMesta, String plus, String minus) {
        this.od = od;
        this.doMesta = doMesta;
        this.plus = plus;
        this.minus = minus;
    }

    public String naziv() {
        return od + " – " + doMesta;
    }
}
