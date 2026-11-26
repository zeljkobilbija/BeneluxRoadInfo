package com.zeljkobilbija001.beneluxroadinfo;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Redovi liste autoputa, a na kraju napomena o izvoru podataka. */
public class StavkaAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int RED = 0, IZVOR = 1, OVDE = 2;
    private final List<Stavka> stavke = new ArrayList<>();
    /** Red "Vi ste ovde": pre stavke sa ovim indeksom (-1 = nema ga). */
    private int ovde = -1;
    private double ovdeKm, ovdeKmTacan;
    private String ovdeTekst = "";
    private boolean ovdeSmer, ovdeNagore;

    /** Dodir na red (deljenje mesta). */
    public interface Klik {
        void izabrana(Stavka s);
    }

    /** Dodir na obojeni deo desne linije. */
    public interface KlikVest {
        void otvori(Dogadjaj d);
    }

    /** Vest postavljena na kilometre deonice: od {@code od} do {@code ka} (od ≤ ka). */
    public static final class Vest {
        public final Dogadjaj d;
        public final double od, ka;

        public Vest(Dogadjaj d, double od, double ka) {
            this.d = d;
            this.od = Math.min(od, ka);
            this.ka = Math.max(od, ka);
        }
    }

    private final Klik klik;
    private final KlikVest klikVest;
    private final List<Vest> vesti = new ArrayList<>();

    public StavkaAdapter(Klik klik, KlikVest klikVest) {
        this.klik = klik;
        this.klikVest = klikVest;
    }

    /** Vesti za desnu liniju (već samo za prikazanu deonicu i stranu). */
    @SuppressLint("NotifyDataSetChanged")
    public void postaviVesti(List<Vest> nove) {
        vesti.clear();
        vesti.addAll(nove);
        notifyDataSetChanged();
    }

    /**
     * Najvažnija vest na delu autoputa koji pokriva red sa kilometrom {@code km}: red pokriva put do
     * polovine razmaka do susednih redova, pa linija teče bez prekida, a vest u jednoj tački (udes)
     * oboji tačno jedan red.
     */
    private Dogadjaj vestZa(int i, double km) {
        if (vesti.isEmpty()) return null;
        double pre = i > 0 ? (stavke.get(i - 1).km + km) / 2 : km - 0.5;
        double posle = i + 1 < stavke.size() ? (stavke.get(i + 1).km + km) / 2 : km + 0.5;
        double lo = Math.min(pre, posle), hi = Math.max(pre, posle);
        Dogadjaj naj = null;
        for (Vest v : vesti) {
            if (v.ka < lo || v.od > hi) continue;
            if (naj == null || v.d.vrsta < naj.vrsta) naj = v.d;
        }
        return naj;
    }

    /** Najvažnija vest koja pokriva tačno ovaj kilometar (red "Vi ste ovde"). */
    private Dogadjaj vestNaKm(double km) {
        Dogadjaj naj = null;
        for (Vest v : vesti) {
            if (km < v.od || km > v.ka) continue;
            if (naj == null || v.d.vrsta < naj.vrsta) naj = v.d;
        }
        return naj;
    }

    /** Desna linija jednog reda: siva ili u boji vesti; dodir na obojenu otvara vest. */
    private void linija(View red, Dogadjaj d) {
        View traka = red.findViewById(R.id.vestTraka), linija = red.findViewById(R.id.vestLinija);
        if (traka == null) return;
        Context c = red.getContext();
        ViewGroup.LayoutParams lp = linija.getLayoutParams();
        if (d == null) {
            linija.setBackgroundColor(Izgled.boja(c, R.color.vest_sina));
            lp.width = Izgled.dp(c, 3);
            traka.setOnClickListener(null);
            traka.setClickable(false);
            traka.setContentDescription(null);
        } else {
            linija.setBackgroundColor(DogadjajAdapter.boja(d.vrsta));
            lp.width = Izgled.dp(c, 6);
            traka.setOnClickListener(v -> {
                if (klikVest != null) klikVest.otvori(d);
            });
            traka.setContentDescription(c.getString(DogadjajAdapter.naziv(d.vrsta)));
        }
        linija.setLayoutParams(lp);
    }

    /** Jedna oznaka (čip) ispod naziva. */
    public static final class Oznaka {
        public final String tekst;
        @ColorRes public final int pozadina, boja;

        Oznaka(String tekst, @ColorRes int pozadina, @ColorRes int boja) {
            this.tekst = tekst;
            this.pozadina = pozadina;
            this.boja = boja;
        }
    }

    public static String naziv(Context c, Stavka s) {
        return s.naziv.isEmpty() ? c.getString(R.string.tip_izlaz) : s.naziv;
    }

    public static String opis(Context c, Stavka s) {
        switch (s.tip) {
            case Stavka.IZLAZ: return c.getString(R.string.tip_izlaz);
            case Stavka.CVORISTE: return c.getString(R.string.tip_cvoriste);
            case Stavka.ODMORISTE: return c.getString(R.string.tip_odmoriste);
            case Stavka.AUTOHOF: return c.getString(R.string.tip_autohof);
            default: return c.getString(R.string.tip_parking);
        }
    }

    /** Sve oznake jednog mesta, istim redom kao na ekranu. */
    public static List<Oznaka> oznake(Context c, Stavka s) {
        List<Oznaka> l = new ArrayList<>();
        int ob = R.color.cip_obicno, obT = R.color.cip_obicno_tekst;
        for (String g : s.gorivo) l.add(new Oznaka(g, R.color.cip_gorivo, R.color.cip_gorivo_tekst));
        for (String h : s.hrana) l.add(new Oznaka(h, R.color.cip_hrana, R.color.cip_hrana_tekst));
        if (s.restoran && s.hrana.isEmpty())
            l.add(new Oznaka(c.getString(R.string.cip_restoran), R.color.cip_hrana, R.color.cip_hrana_tekst));
        if (s.wc) l.add(new Oznaka(c.getString(R.string.cip_wc), ob, obT));
        if (s.tus) l.add(new Oznaka(c.getString(R.string.cip_tus), ob, obT));
        if (!s.punjaci.isEmpty()) {
            int n = s.punjaci.size();
            String spisak = android.text.TextUtils.join(", ", s.punjaci.subList(0, Math.min(3, n)))
                    + (n > 3 ? " +" + (n - 3) : "");
            l.add(new Oznaka(c.getString(R.string.cip_punjac, spisak), R.color.cip_struja, R.color.cip_struja_tekst));
        }
        if (s.hotel) l.add(new Oznaka(c.getString(R.string.cip_hotel), ob, obT));
        if (s.prodavnica) l.add(new Oznaka(c.getString(R.string.cip_prodavnica), ob, obT));
        if (!s.kamioni.isEmpty()) l.add(new Oznaka(c.getString(R.string.cip_kamioni, s.kamioni), ob, obT));
        if ("24/7".equals(s.radnoVreme)) l.add(new Oznaka(c.getString(R.string.cip_0_24), ob, obT));
        return l;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void postavi(List<Stavka> nove) {
        stavke.clear();
        stavke.addAll(nove);
        notifyDataSetChanged();
    }

    /**
     * Red sa strelicom "Vi ste ovde" ispred stavke {@code indeks} (-1 = bez njega).
     * {@code sasmerom}: strelica pokazuje napred (niz listu); inače samo tačka.
     */
    @SuppressLint("NotifyDataSetChanged")
    public void postaviOvde(int indeks, double km, double kmTacan, String tekst, boolean saSmerom, boolean nagore) {
        ovdeNagore = nagore;
        ovde = indeks;
        ovdeKm = km;
        ovdeKmTacan = kmTacan;
        ovdeTekst = tekst;
        ovdeSmer = saSmerom;
        notifyDataSetChanged();
    }

    /** Pozicija reda "Vi ste ovde" u listi, ili -1. */
    public int pozicijaOvde() {
        return ovde;
    }

    private boolean imaOvde() {
        return ovde >= 0 && ovde <= stavke.size() && !stavke.isEmpty();
    }

    @Override
    public int getItemCount() {
        return stavke.isEmpty() ? 0 : stavke.size() + 1 + (imaOvde() ? 1 : 0);
    }

    @Override
    public int getItemViewType(int poz) {
        if (imaOvde() && poz == ovde) return OVDE;
        int i = imaOvde() && poz > ovde ? poz - 1 : poz;
        return i < stavke.size() ? RED : IZVOR;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup roditelj, int tip) {
        LayoutInflater inf = LayoutInflater.from(roditelj.getContext());
        if (tip == IZVOR) {
            return new RecyclerView.ViewHolder(inf.inflate(R.layout.item_izvor, roditelj, false)) { };
        }
        if (tip == OVDE) {
            return new RecyclerView.ViewHolder(inf.inflate(R.layout.item_ovde, roditelj, false)) { };
        }
        return new Drzac(inf.inflate(R.layout.item_stavka, roditelj, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int poz) {
        if (h instanceof Drzac) {
            int i = imaOvde() && poz > ovde ? poz - 1 : poz;
            Stavka s = stavke.get(i);
            ((Drzac) h).povezi(s);
            linija(h.itemView, vestZa(i, s.km));
        } else if (getItemViewType(poz) == OVDE) {
            View v = h.itemView;
            linija(v, vestNaKm(ovdeKmTacan));
            ((TextView) v.findViewById(R.id.ovdeKm)).setText(String.format(Locale.getDefault(), "%.1f", ovdeKm));
            ((TextView) v.findViewById(R.id.ovdeTekst)).setText(ovdeTekst);
            ImageView strelica = v.findViewById(R.id.ovdeStrelica);
            strelica.setImageResource(ovdeSmer ? R.drawable.ic_strelica_napred : R.drawable.ic_tacka_ovde);
            strelica.setRotation(ovdeNagore ? 180f : 0f);
        }
    }

    class Drzac extends RecyclerView.ViewHolder {
        final TextView km, znakTekst, naziv, opis;
        final ImageView znakIkona;
        final View znak;
        final ChipGroup oznake;

        Drzac(View v) {
            super(v);
            km = v.findViewById(R.id.km);
            znak = v.findViewById(R.id.znak);
            znakTekst = v.findViewById(R.id.znakTekst);
            znakIkona = v.findViewById(R.id.znakIkona);
            naziv = v.findViewById(R.id.naziv);
            opis = v.findViewById(R.id.opis);
            oznake = v.findViewById(R.id.oznake);
        }

        void povezi(Stavka s) {
            Context c = itemView.getContext();
            km.setText(String.format(Locale.getDefault(), "%.1f", s.kmPrikaz()));

            // Znak na šini: strelica izlaza, oznaka druge autostrade (čvorište), P, pumpa ili pribor za jelo
            boolean pumpa = !s.jeIzlaz() && s.tip != Stavka.PARKING && !s.gorivo.isEmpty();
            boolean hrana = !s.jeIzlaz() && s.tip != Stavka.PARKING && s.gorivo.isEmpty();
            int bela = 0xFFFFFFFF;
            int plava = Izgled.boja(c, R.color.plava);
            if (s.tip == Stavka.IZLAZ) {
                znak.setBackground(Izgled.oblik(c, plava, 7, 0, 0));
                if (s.ref.isEmpty()) ikonaZnaka(R.drawable.ic_izlaz, bela); else tekstZnaka(s.ref, bela);
            } else if (s.tip == Stavka.CVORISTE) {
                znak.setBackground(Izgled.oblik(c, bela, 7, plava, 2.5f));
                if (s.ref.isEmpty()) ikonaZnaka(R.drawable.ic_izlaz, plava); else tekstZnaka(s.ref, plava);
            } else if (s.tip == Stavka.PARKING) {
                znak.setBackground(Izgled.oblik(c, plava, 7, 0, 0));
                tekstZnaka("P", bela);
                znakTekst.setTextSize(19);
            } else if (pumpa) {
                znak.setBackground(Izgled.oblik(c, Izgled.boja(c, R.color.pumpa), 16, 0, 0));
                ikonaZnaka(R.drawable.ic_pumpa, Izgled.boja(c, R.color.pumpa_tekst));
            } else if (hrana) {
                int crvena = Izgled.boja(c, R.color.hrana);
                znak.setBackground(Izgled.oblik(c, Izgled.boja(c, R.color.povrsina), 16, crvena, 2));
                ikonaZnaka(R.drawable.ic_hrana, crvena);
            }

            naziv.setText(naziv(c, s));
            itemView.setOnClickListener(v -> {
                if (klik != null) klik.izabrana(s);
            });
            opis.setText(opis(c, s));

            // Oznake: gorivo, hrana, WC, tuš, punjači…
            oznake.removeAllViews();
            for (Oznaka o : oznake(c, s)) cip(c, o.tekst, o.pozadina, o.boja);
            oznake.setVisibility(oznake.getChildCount() > 0 ? View.VISIBLE : View.GONE);
        }

        private void tekstZnaka(String t, int boja) {
            znakIkona.setVisibility(View.GONE);
            znakTekst.setVisibility(View.VISIBLE);
            znakTekst.setText(t);
            znakTekst.setTextColor(boja);
            znakTekst.setTextSize(t.length() > 3 ? 13 : 16);
        }

        private void ikonaZnaka(int ikona, int boja) {
            znakTekst.setVisibility(View.GONE);
            znakIkona.setVisibility(View.VISIBLE);
            znakIkona.setImageResource(ikona);
            znakIkona.setImageTintList(Izgled.jedna(boja));
        }

        private void cip(Context c, String tekst, @ColorRes int pozadina, @ColorRes int boja) {
            TextView t = new TextView(c);
            t.setText(tekst);
            t.setTextSize(12.5f);
            t.setTextColor(Izgled.boja(c, boja));
            t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
            t.setBackground(Izgled.oblik(c, Izgled.boja(c, pozadina), 7, 0, 0));
            int h = Izgled.dp(c, 8), v = Izgled.dp(c, 3);
            t.setPadding(h, v, h, v);
            oznake.addView(t);
        }
    }
}
