#!/usr/bin/env python3
"""Gera a pré-visualização do widget (mock HTML com os mesmos SVGs/medidas do layout) em PNG.
Precisa do Google Chrome/Chromium. Uso: python3 tools/render_preview.py"""
import os, subprocess, random

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ICONS = os.path.join(ROOT, "docs/icons")
S = 4  # px por dp


def widget(date, temp, icon, bg, extra=""):
    stars = ""
    if "night" in bg:
        random.seed(3)
        stars = "".join(f'<i style="left:{random.randint(2,98)}%;top:{random.randint(2,98)}%;width:{random.choice([2,3,4])}px;height:{random.choice([2,3,4])}px"></i>' for _ in range(22))
    return f'''<div class="wp {bg}">{stars}<div class="w">
<div class="date">{date}</div>
<div class="row"><img src="file://{ICONS}/{icon}.svg"><span class="temp">{temp}</span></div></div></div>'''


html = f'''<html><head><style>
@font-face{{font-family:R;src:url(file:///workspace/fonts/roboto.ttf)}}
body{{margin:0;background:#111;font-family:R,Roboto,sans-serif;display:flex;gap:16px;padding:16px;width:1700px}}
.wp{{position:relative;width:540px;height:{110*S+80}px;border-radius:36px;overflow:hidden;flex:none;display:flex;align-items:center}}
.wp i{{position:absolute;background:#fff;border-radius:50%;opacity:.7}}
.night{{background:linear-gradient(180deg,#0b1424,#141f36)}}
.day{{background:linear-gradient(160deg,#f3e9d2,#9bd0f5 60%,#5aa7e6)}}
.rain{{background:linear-gradient(160deg,#6f7f93,#3d4b5e)}}
.w{{padding:{6*S}px {12*S}px;position:relative}}
.date{{color:#fff;font-size:{28*S/2.2}px;line-height:1.17;text-shadow:0 {1.5*S/2.2}px {4*S/2.2}px rgba(0,0,0,.5);white-space:nowrap}}
.row{{display:flex;align-items:center;margin-top:{4*S/2.2}px}}
.row img{{width:{36*S/2.2}px;height:{36*S/2.2}px}}
.temp{{color:#fff;font-size:{22*S/2.2}px;margin-left:{8*S/2.2}px;text-shadow:0 {1.5*S/2.2}px {4*S/2.2}px rgba(0,0,0,.5)}}
</style></head><body>
{widget("Seg., 2 de nov.", "18°C", "ic_w_partly_night", "wp night")}
{widget("Ter., 3 de nov.", "27°C", "ic_w_clear_day", "wp day")}
{widget("Qua., 4 de nov.", "15°C", "ic_w_rain", "wp rain")}
</body></html>'''
open("/tmp/preview.html", "w").write(html)

def shot(out, w, h, html_path="/tmp/preview.html"):
    subprocess.run(["google-chrome", "--headless=new", "--no-sandbox", "--disable-gpu", "--allow-file-access-from-files",
                    f"--screenshot={out}", f"--window-size={w},{h}", "--hide-scrollbars", "file://" + html_path], check=True, capture_output=True)

shot("/workspace/glance-clima-preview.png", 1732, 552)


# imagem do seletor de widgets (previewImage): painel noturno recortado
try:
    from PIL import Image
    im = Image.open("/workspace/glance-clima-preview.png").crop((16, 16, 556, 16 + 110 * S + 80))
    # recorta uma faixa central, onde fica o widget
    im = im.crop((0, 110, 540, 410)).resize((432, 240))
    im.save(os.path.join(ROOT, "app/src/main/res/drawable-nodpi/widget_preview.png"))
except ImportError:
    print("Pillow não instalado: widget_preview.png não foi atualizado")
