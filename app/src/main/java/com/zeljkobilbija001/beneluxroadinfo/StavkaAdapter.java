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

    private static final int RED = 0, IZVOR = 1;
    private final List<Stavka> stavke = new ArrayList<>();

    /** Dodir na red (deljenje mesta). */
    public interface Klik {
        void izabrana(Stavka s);
    }

    private final Klik klik;

    public StavkaAdapter(Klik klik) {
        this.klik = klik;
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

    @Override
    public int getItemCount() {
        return stavke.isEmpty() ? 0 : stavke.size() + 1;
    }

    @Override
    public int getItemViewType(int poz) {
        return poz < stavke.size() ? RED : IZVOR;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup roditelj, int tip) {
        LayoutInflater inf = LayoutInflater.from(roditelj.getContext());
        if (tip == IZVOR) {
            return new RecyclerView.ViewHolder(inf.inflate(R.layout.item_izvor, roditelj, false)) { };
        }
        return new Drzac(inf.inflate(R.layout.item_stavka, roditelj, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int poz) {
        if (h instanceof Drzac) ((Drzac) h).povezi(stavke.get(poz));
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
            km.setText(String.format(Locale.getDefault(), "%.1f", s.km));

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
