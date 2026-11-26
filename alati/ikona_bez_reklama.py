"""Ikona proizvoda „Bez reklama“ (remove_ads) za Play Console: ikona aplikacije + crveni znak „AD“ precrtan.

    python alati/ikona_bez_reklama.py [ulaz.png] [izlaz.png]

Podrazumevano: alati/ikona_play_512.png → play/ikona_bez_reklama_512.png (512×512 PNG).
Radi za svaku aplikaciju koja ima alati/ikona_play_512.png (pokrenuti iz korena projekta).
"""
import sys, os
from PIL import Image, ImageDraw, ImageFont

ulaz = sys.argv[1] if len(sys.argv) > 1 else 'alati/ikona_play_512.png'
izlaz = sys.argv[2] if len(sys.argv) > 2 else 'play/ikona_bez_reklama_512.png'
S = 512
ik = Image.open(ulaz).convert('RGBA').resize((S, S), Image.LANCZOS)

# znak desno od sredine (ceo ostaje i kad Play iseče ikonu u šestougao): beli krug, crveni prsten, "AD", crvena kosa crta
K = 4  # crta se u većoj rezoluciji pa umanjuje (glatke ivice)
R = 100 * K
cx, cy = int(S * 0.60) * K, int(S * 0.60) * K   # Play iseca ikonu (šestougao, krug): znak mora biti blizu sredine
sloj = Image.new('RGBA', (S * K, S * K), (0, 0, 0, 0))
d = ImageDraw.Draw(sloj)
crvena = (211, 47, 47, 255)
d.ellipse((cx - R - 10 * K, cy - R - 10 * K, cx + R + 10 * K, cy + R + 10 * K), fill=(255, 255, 255, 255))
d.ellipse((cx - R, cy - R, cx + R, cy + R), fill=(255, 255, 255, 255), outline=crvena, width=22 * K)
font = None
for f in ('C:/Windows/Fonts/arialbd.ttf', 'C:/Windows/Fonts/segoeuib.ttf'):
    if os.path.exists(f):
        font = ImageFont.truetype(f, 92 * K)
        break
d.text((cx, cy), 'AD', fill=(40, 40, 40, 255), font=font, anchor='mm')
o = int(R * 0.70)
d.line((cx - o, cy - o, cx + o, cy + o), fill=crvena, width=22 * K)
sloj = sloj.resize((S, S), Image.LANCZOS)

out = Image.alpha_composite(ik, sloj).convert('RGB')
os.makedirs(os.path.dirname(izlaz) or '.', exist_ok=True)
out.save(izlaz)
print(izlaz)
