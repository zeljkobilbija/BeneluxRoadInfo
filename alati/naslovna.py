"""
Naslovna grafika za Google Play (1024x500): levo ikona aplikacije (zastava i put), desno naziv i opis.
Ikona 512x512 se kopira iz alati/ikona_play_512.png.

  python alati/naslovna.py
"""
import os, shutil
from PIL import Image, ImageDraw, ImageFont

KOREN = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
CILJ = os.path.join(KOREN, 'play', 'grafika')
FONT = r'C:\Windows\Fonts\segoeuib.ttf'
FONT_TANAK = r'C:\Windows\Fonts\segoeui.ttf'
W, H, K = 1024, 500, 4

os.makedirs(CILJ, exist_ok=True)
izvor = os.path.join(KOREN, 'alati', 'ikona_play_512.png')
shutil.copy(izvor, os.path.join(CILJ, 'ikona_512.png'))
ik = Image.open(izvor).convert('RGB')
noc = ik.getpixel((20, 500))          # tamna boja puta sa ikone

s = Image.new('RGB', (W * K, H * K), noc)
s.paste(ik.resize((H * K, H * K), Image.LANCZOS), (0, 0))
d = ImageDraw.Draw(s)
x0 = (H + 34) * K
d.text((x0, 120 * K), 'Benelux Road Info', font=ImageFont.truetype(FONT, 52 * K), fill='white')
d.rectangle([x0, 200 * K, x0 + 110 * K, 206 * K], fill='#F5A623')
red = ImageFont.truetype(FONT_TANAK, 27 * K)
for i, t in enumerate(['Exits · Service areas · Fuel prices', 'Live traffic · Driving mode', 'NL · BE · LU · 56 motorways']):
    d.text((x0, (235 + i * 44) * K), t, font=red, fill='#DDE3F0')
s.resize((W, H), Image.LANCZOS).save(os.path.join(CILJ, 'naslovna_1024x500.png'))
print('play/grafika: ikona_512.png, naslovna_1024x500.png')
