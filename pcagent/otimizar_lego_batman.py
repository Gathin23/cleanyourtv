"""Perfil do LEGO Batman: O Legado do Cavaleiro das Trevas para i5-9600KF + GTX 1660 Super (6 GB), 1080p 60 Hz na TV.
Guarda as configurações originais em *.original.ini na primeira vez; para desfazer: python otimizar_lego_batman.py --desfazer
"""
import os
import re
import shutil
import sys

DIR = os.path.expandvars(r"%LOCALAPPDATA%\Dinner\Saved\Config\Windows")   # "Dinner" é o nome interno do jogo

CHANGES = {
    "GameUserSettings.ini": {
        "sg.ResolutionQuality": "85",           # 71% deixava a imagem borrada na TV; 85% fica nítido e cabe nos 60 FPS
        "sg.TextureQuality": "2",               # texturas altas: os 6 GB da placa comportam
        "Anisotropy": "16",                     # chão e paredes nítidos à distância, quase sem custo
        "DistanceFieldAmbientOcclusion": "0",   # sombra de ambiente pesada: compensa o aumento da resolução
        "FrameRateLimit": "60.000000",          # trava em 60, igual à TV: imagem estável, sem tremer
        "bUseVSync": "True",
        "FullscreenMode": "1",                  # tela cheia sem borda (a Central de Jogos depende disso)
    },
    "TtGameSettingsUser.ini": {
        "bMotionBlurEnabled": "False",          # desfoque de movimento: pesa e borra na TV
        "bChromaticAberrationEnabled": "False",
        "bDepthOfFieldEnabled": "False",
        "PerformanceMode": "Performance",
    },
}


def main():
    undo = "--desfazer" in sys.argv
    for name, vals in CHANGES.items():
        path = os.path.join(DIR, name)
        orig = path.replace(".ini", ".original.ini")
        if undo:
            if os.path.exists(orig):
                shutil.copyfile(orig, path)
                print("restaurado", name)
            continue
        if not os.path.exists(orig):
            shutil.copyfile(path, orig)
        text = open(path, encoding="utf-8").read()
        for k, v in vals.items():
            text, n = re.subn(r"(?m)^%s=.*$" % re.escape(k), "%s=%s" % (k, v), text)
            print("%-32s %s" % (k, v if n else v + "  (não existia; ignorado)"))
        open(path, "w", encoding="utf-8").write(text)


if __name__ == "__main__":
    main()
