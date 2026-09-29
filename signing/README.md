# Signature de Chatty

`chatty-ci.jks` est une clé **de développement** utilisée par la CI pour signer les APK
(mot de passe : `chattyci`). Elle garantit que chaque build s'installe par-dessus le précédent.

Elle est publique dans ce dépôt : ne l'utilise pas pour le Play Store. Pour une publication,
crée une vraie clé et renseigne les secrets GitHub `CHATTY_KEYSTORE_PATH`,
`CHATTY_KEYSTORE_PASSWORD`, `CHATTY_KEY_ALIAS` et `CHATTY_KEY_PASSWORD`.
