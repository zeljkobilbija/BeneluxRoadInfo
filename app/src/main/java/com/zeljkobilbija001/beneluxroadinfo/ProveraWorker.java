package com.zeljkobilbija001.beneluxroadinfo;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/** Povremena provera stanja na praćenim autoputevima (pokreće je WorkManager). */
public class ProveraWorker extends Worker {

    public ProveraWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context c = getApplicationContext();
        if (!Obavestenja.otvorena(c)) {
            // korisnik je aplikaciju skroz zatvorio: ništa više ne stiže dok je ponovo ne otvori
            Obavestenja.ugasiSve(c);
            return Result.success();
        }
        for (String id : Obavestenja.praceni(c)) {
            if (isStopped()) break;
            // Vožnja zna autoput i smer vozača: ona javlja samo ono ispred u njegovom smeru
            if (VoznjaService.radi && id.equals(VoznjaService.sadAutoput) && VoznjaService.sadSmer != '?') {
                Dnevnik.upisi(c, "PRAĆENJE " + id + ": prepušteno Vožnji");
                continue;
            }
            try {
                java.util.List<Dogadjaj> l = StanjeNaPutu.preuzmi(c, id);
                int vaznih = 0;
                for (Dogadjaj d : l) if (d.zaObavestenje()) vaznih++;
                Dnevnik.upisi(c, "PRAĆENJE " + id + ": " + l.size() + " poruka, važnih " + vaznih);
                Obavestenja.obradi(c, id, l);
            } catch (Exception e) {
                Dnevnik.upisi(c, "PRAĆENJE " + id + ": greška " + e.getMessage());
                // nema mreže ili servis ne radi: pokušava se pri sledećoj proveri
            }
        }
        return Result.success();
    }
}
