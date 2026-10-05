"""Compila todo lo que hace falta en cada versión y lo deja en la carpeta Release.

Uso: python compilar.py

Deja tres archivos, con el número de versión en el nombre:
  LumiGallery-X-play.aab       lo que se sube a Google Play
  LumiGallery-X-play.apk       lo mismo, para instalarlo a mano y probarlo
  LumiGallery-X-completa.apk   con las carpetas ocultas del sistema (no admitida en Google Play)

Antes de subir una versión nueva hay que aumentar versionCode en app/build.gradle.kts:
Google Play rechaza un número que ya se haya subido.
"""
import os
import re
import shutil
import subprocess
import sys

root = os.path.dirname(os.path.abspath(__file__))
gradle = open(os.path.join(root, "app", "build.gradle.kts"), encoding="utf-8").read()
version = re.search(r'versionName = "([^"]+)"', gradle).group(1)

tasks = [":app:bundlePlayRelease", ":app:assemblePlayRelease", ":app:assembleFullRelease"]
wrapper = os.path.join(root, "gradlew.bat" if os.name == "nt" else "gradlew")
if subprocess.call([wrapper, *tasks, "--console=plain", "-q"], cwd=root) != 0:
    sys.exit("La compilación ha fallado.")

outputs = os.path.join(root, "app", "build", "outputs")
release = os.path.join(root, "Release")
os.makedirs(release, exist_ok=True)
files = {
    os.path.join(outputs, "bundle", "playRelease", "app-play-release.aab"): f"LumiGallery-{version}-play.aab",
    os.path.join(outputs, "apk", "play", "release", "app-play-release.apk"): f"LumiGallery-{version}-play.apk",
    os.path.join(outputs, "apk", "full", "release", "app-full-release.apk"): f"LumiGallery-{version}-completa.apk",
}
for source, name in files.items():
    shutil.copyfile(source, os.path.join(release, name))
    print(f"{name}  {os.path.getsize(source) / 1e6:.0f} MB")
