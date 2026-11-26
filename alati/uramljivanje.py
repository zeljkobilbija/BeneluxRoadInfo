"""
Snimci ekrana za Google Play: naslov gore, ekran telefona (bez statusne trake i reklame) sa senkom, gradijent u bojama ikone.
Ulaz: sirovi snimci 1080x2340 u play/snimci/sirovo/ (s1…s5), izlaz: play/snimci/<jezik>_<n>.png (1080x1920).

  python alati/uramljivanje.py
"""
import os
from PIL import Image, ImageDraw, ImageFont, ImageFilter

KOREN = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'play', 'snimci')
SIROVO = os.path.join(KOREN, 'sirovo')
# (fajl, donja ivica isečka: liste imaju reklamu na dnu, detalji nemaju)
SNIMCI = [(1, 2000), (2, 2000), (3, 2000), (4, 2000), (5, 2000)]   # sirovo/<jezik><n>.png (aplikacija na tom jeziku)
NASLOVI = {
    'en': ['Netherlands, Belgium, Luxembourg – even offline', 'Exits and service areas by kilometre',
           'Live traffic news', 'Fuel prices, brands and facilities',
           'Filter: fuel, food, parking'],
    'nl': ['Nederland, België, Luxemburg – ook offline', 'Afritten en verzorgingsplaatsen per kilometer',
           'Actuele verkeersinformatie', 'Brandstofprijzen, merken en voorzieningen',
           'Filter: tanken, eten, parkeren'],
    'fr': ['Pays-Bas, Belgique, Luxembourg – même hors ligne', 'Sorties et aires de service par kilomètre',
           'Infos trafic en direct', 'Prix des carburants, enseignes et services',
           'Filtres : carburant, repas, parkings'],
    'de': ['Niederlande, Belgien, Luxemburg – auch offline', 'Ausfahrten und Raststätten nach Kilometer',
           'Aktuelle Verkehrsmeldungen', 'Spritpreise, Marken und Ausstattung',
           'Filter: Tanken, Essen, Parken'],
}
W, H = 1080, 1920
font = ImageFont.truetype(r'C:\Windows\Fonts\segoeuib.ttf', 64)


def gradijent():
    im = Image.new('RGB', (W, H)); d = ImageDraw.Draw(im)
    for y in range(H):
        t = y / H
        d.line([(0, y), (W, y)], fill=tuple(int(a + (b - a) * t) for a, b in zip((33, 70, 139), (20, 24, 36))))
    for i, boja in enumerate([(174, 28, 40), (255, 255, 255), (33, 70, 139)]):  # zastava Holandije
        d.rectangle([0, i * 6, W, i * 6 + 5], fill=boja)   # vodoravne pruge kao zastava
    return im


def prelom(txt, maxw, d):
    redovi, r = [], ''
    for w in txt.split():
        p = (r + ' ' + w).strip()
        if d.textlength(p, font=font) <= maxw: r = p
        else: redovi.append(r); r = w
    redovi.append(r)
    return redovi


for jezik, naslovi in NASLOVI.items():
    for i, ((f, dno), nas) in enumerate(zip(SNIMCI, naslovi), 1):
        s = Image.open(os.path.join(SIROVO, f'{jezik}{f}.png')).convert('RGB').crop((0, 100, 1080, dno))
        can = gradijent(); d = ImageDraw.Draw(can)
        y = 80
        for r in prelom(nas, W - 140, d):
            tw = d.textlength(r, font=font); d.text(((W - tw) / 2, y), r, font=font, fill='white'); y += 82
        # ekran telefona: što veći, ispod naslova
        vis = H - (y + 40) - 50
        sc = min(vis / s.height, 0.86)
        s = s.resize((int(s.width * sc), int(s.height * sc)), Image.LANCZOS)
        maska = Image.new('L', s.size, 0)
        ImageDraw.Draw(maska).rounded_rectangle([0, 0, s.width - 1, s.height - 1], radius=36, fill=255)
        x0, y0 = (W - s.width) // 2, H - s.height - 50
        senka = Image.new('RGBA', (s.width + 80, s.height + 80), (0, 0, 0, 0))
        ImageDraw.Draw(senka).rounded_rectangle([40, 40, s.width + 40, s.height + 40], radius=40, fill=(0, 0, 0, 150))
        senka = senka.filter(ImageFilter.GaussianBlur(22))
        can.paste(senka, (x0 - 40, y0 - 30), senka)
        can.paste(s, (x0, y0), maska)
        can.save(os.path.join(KOREN, f'{jezik}_{i}.png'))
print('play/snimci: en, nl, fr, de _1..5')
