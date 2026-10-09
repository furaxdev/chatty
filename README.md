# Chatty 💬

Une appli SMS pour Android, dans l'esprit de Google Messages, avec en plus les **effets d'envoi façon iMessage**.

## Fonctionnalités

### ✨ Effets d'envoi (restez appuyé sur Envoyer)
Comme sur iPhone : un appui long sur le bouton Envoyer ouvre l'écran « Envoyer avec effet ».

- **Bulle** : Claquer 💥, Fort 📢, Doux 🪶, Encre invisible 🫥 (touchez pour révéler), Secousse 🫨
- **Écran** : Écho, Projecteur, Ballons, Confettis, Amour, Lasers, Feux d'artifice, Célébration, Neige

Pour les iPhone et téléphones sans Chatty, l'option « Compatibilité iPhone » ajoute un lien
(`https://furaxdev.github.io/chatty/e/#effet.texte`) : en le touchant, ils voient le message avec
son animation (page web du dossier `docs/`, publiée par GitHub Pages).

L'effet voyage dans le SMS sous forme de caractères invisibles : les autres téléphones voient
le texte normal, Chatty rejoue l'animation à la réception. (Ces caractères font passer le SMS en
encodage Unicode, soit 70 caractères par SMS au lieu de 160, uniquement quand un effet est choisi.)

### 📱 Comme Google Messages
- Liste des conversations avec contacts, photos, non-lus, recherche (contacts + contenu des messages)
- Bulles groupées, séparateurs de jours, liens cliquables, gros emojis
- Statut d'envoi : Envoi… / Envoyé / Distribué / Échec (touchez pour réessayer)
- Compteur de caractères et de SMS
- Notifications avec **réponse rapide** et « Marquer comme lu »
- Couleurs Material You, mode sombre

### ➕ En plus
- ⏰ **Envoi programmé** (raccourcis ou date/heure au choix, annulable ou envoyable tout de suite)
- ❤️ **Réactions** aux messages (appui long sur une bulle)
- ⭐ Messages favoris, copier, supprimer, rejouer un effet
- 📌 Épingler, 🗄️ archiver (swipe), 🔕 mettre en sourdine, marquer lu / non lu, sélection multiple
- 👥 Envoi groupé (un SMS individuel par destinataire)
- 🎨 Couleur des bulles au choix, signature automatique, brouillons conservés
- Liens `sms:` / `smsto:` et partage de texte depuis d'autres applis

## Télécharger

Chaque push déclenche la CI GitHub (tests, lint, APK signé) puis publie l'APK dans
les **Releases** du dépôt : `Chatty-1.N.apk` (Android 10+) et `Chatty-1.N-android8.apk` (Android 8-9). Les versions s'installent par-dessus les
précédentes (clé de signature fixe, voir `signing/README.md`).

Identifiant de l'appli : `com.chatty.fr`.

## Compiler

Prérequis : JDK 17+ et le SDK Android (API 35).

```bash
./gradlew assembleRelease
# APK : app/build/outputs/apk/release/app-release.apk
```

La CI GitHub produit aussi l'APK à chaque push (Releases, ou onglet *Actions* → artefact `chatty-apk`).

Au premier lancement, Chatty demande à devenir l'**appli SMS par défaut** (obligatoire sur Android
pour envoyer et recevoir des SMS). On peut revenir à Google Messages à tout moment dans les réglages.

## Limites actuelles
- MMS : photos et groupes pris en charge ; vidéos et messages audio reçus s'ouvrent dans une autre appli.
- Les réactions et favoris sont stockés sur le téléphone uniquement (ils ne sont pas envoyés).

## Stack
Kotlin · Jetpack Compose · Material 3 · WorkManager · deux APK : Android 10+ (minSdk 29) et Android 8-9 (minSdk 26) · targetSdk 35
