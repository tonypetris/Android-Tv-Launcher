# Lite Launcher per Android TV

Launcher minimale per Philips 50PUS8556/12 (e qualsiasi Android TV):
una sola schermata con orologio, data, pulsante Impostazioni e la griglia delle app.
Niente raccomandazioni, niente righe di contenuti, niente servizi in background, nessuna libreria esterna.

## Comandi sul telecomando
- **OK**: apre l'app
- **OK tenuto premuto** (o tasto **Menu**): sposta all'inizio / a sinistra / a destra, nascondi, info app
- **App nascoste** (in alto): rimette in griglia un'app nascosta
- **Home**: torna alla prima app

## 1. Compilare l'APK (gratis, senza installare nulla)
1. Crea un account su github.com, poi un nuovo repository (anche privato).
2. Carica tutti i file di questa cartella (su GitHub: "Add file" → "Upload files" e trascina il contenuto, compresa la cartella `.github`).
   Nota: la cartella `.github` inizia con un punto: su Mac/Linux può essere nascosta; su Windows è visibile.
3. Vai nella scheda **Actions**: la compilazione parte da sola (2–4 minuti).
4. Apri l'esecuzione completata e scarica l'artefatto **LiteLauncher-apk** (è uno zip con dentro `app-release.apk`).

In alternativa: apri la cartella con Android Studio → Build → Build APK.

## 2. Installarlo sul TV
```
adb connect IP_DEL_TV:5555
adb install app-release.apk
```

## 3. Impostarlo come Home
Prova prima così:
```
adb shell cmd package set-home-activity it.tony.litelauncher/.MainActivity
```
Premi Home sul telecomando. Se si apre ancora il launcher Google, disattivalo:
```
adb shell pm disable-user --user 0 com.google.android.tvlauncher
```
Per tornare indietro in qualsiasi momento:
```
adb shell pm enable com.google.android.tvlauncher
adb uninstall it.tony.litelauncher
```
