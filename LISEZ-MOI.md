# Bagaly — application Android (APK)

Une vraie icône « Bagaly » sur le téléphone, en plein écran, sans barre de Chrome.
L'application démarre seule le cerveau de Bagaly dans Termux, gère le micro (« Hey Bagaly »),
les téléchargements (fiches PDF, images, vidéos) et ouvre WhatsApp, Maps ou le téléphone quand Bagaly le demande.

Le cerveau (Ollama + service Bagaly) continue de tourner dans **Termux** : l'APK en est la façade.

## Obtenir l'APK sans rien installer sur ton PC (GitHub, gratuit)
1. Crée un compte sur https://github.com et un dépôt vide (par exemple `bagaly-android`).
2. Mets dedans **le contenu de ce dossier `android`** (bouton « Add file → Upload files », glisse tous les fichiers et dossiers, y compris `.github`).
3. Onglet **Actions** → « Construire l'APK Bagaly » → attends environ 5 minutes.
4. Clique sur le résultat → **Artifacts → Bagaly-apk** → télécharge, décompresse, installe `app-debug.apk` sur le téléphone
   (Android demande d'autoriser l'installation depuis cette source).

Claude peut aussi le faire pour toi si tu relies ton compte GitHub à Claude.

## Première ouverture
1. Dans Termux, une seule fois : `bash install.sh` (il autorise les applications externes à démarrer Bagaly).
2. Réglages Android → Applications → **Bagaly** → Autorisations → autorise **« Exécuter des commandes dans Termux »** et le **micro**.
3. Quand Termux affiche le lien `http://localhost:8080/#k=…`, choisis **Bagaly** pour l'ouvrir : la clé secrète est enregistrée dans l'application.
