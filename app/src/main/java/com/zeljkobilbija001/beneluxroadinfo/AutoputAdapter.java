package com.zeljkobilbija001.beneluxroadinfo;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/** Kvadratne kartice autoputeva na početnom ekranu. */
public class AutoputAdapter extends RecyclerView.Adapter<AutoputAdapter.Drzac> {

    public interface Klik {
        void otvori(Autoput a, View kartica);
    }

    private final List<Autoput> autoputevi;
    private final Klik klik;

    public AutoputAdapter(List<Autoput> autoputevi, Klik klik) {
        this.autoputevi = autoputevi;
        this.klik = klik;
    }

    @NonNull
    @Override
    public Drzac onCreateViewHolder(@NonNull ViewGroup roditelj, int tip) {
        View v = LayoutInflater.from(roditelj.getContext()).inflate(R.layout.item_autoput, roditelj, false);
        return new Drzac(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Drzac d, int poz) {
        Autoput a = autoputevi.get(poz);
        View v = d.itemView;
        boolean ima = a.dostupan();

        d.stit.setText(a.oznaka());
        d.stit.setBackgroundResource(ima ? a.stit() : R.drawable.bg_stit_sivi);
        d.zemlja.setText(a.zemlja);
        d.ruta.setText(a.ruta);

        if (ima) {
            Podaci p = Podaci.za(v.getContext(), a);
            d.status.setText(p == null ? "" : v.getContext().getString(R.string.broj_tacaka,
                    p.stavke.size(), p.duzinaKm()));
            d.status.setTextColor(Izgled.boja(v.getContext(), R.color.plava_tekst));
        } else {
            d.status.setText(R.string.uskoro);
            d.status.setTextColor(Izgled.boja(v.getContext(), R.color.tekst_sekundarni));
        }
        v.setContentDescription(a.zemlja + " " + a.oznaka() + ", " + a.ruta + ", " + d.status.getText());
        v.setOnClickListener(x -> klik.otvori(a, x));
    }

    @Override
    public int getItemCount() {
        return autoputevi.size();
    }

    static class Drzac extends RecyclerView.ViewHolder {
        final TextView stit, zemlja, ruta, status;

        Drzac(View v) {
            super(v);
            stit = v.findViewById(R.id.stit);
            zemlja = v.findViewById(R.id.zemlja);
            ruta = v.findViewById(R.id.ruta);
            status = v.findViewById(R.id.status);
        }
    }
}
