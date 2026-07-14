# Publication Android sécurisée

## Une seule fois

Créer une clé dédiée hors du dépôt :

```bash
keytool -genkeypair -v -keystore branlly-pocket-release.jks -alias branlly-pocket -keyalg RSA -keysize 4096 -validity 10000
```

Conserver deux sauvegardes chiffrées hors ligne. La perte de cette clé empêche les mises à jour directes des installations existantes.

Créer dans l’environnement GitHub `production` les secrets suivants :

- `ANDROID_KEYSTORE_BASE64` : contenu Base64 monoligne du fichier JKS ;
- `ANDROID_KEYSTORE_PASSWORD` ;
- `ANDROID_KEY_ALIAS` ;
- `ANDROID_KEY_PASSWORD`.

Protéger l’environnement `production` avec validation manuelle. Ne jamais placer une clé ou un mot de passe dans Git, une issue ou un journal.

## Publier

Après validation de la branche principale :

```bash
git tag -s v0.1.0 -m "Branlly Pocket 0.1.0"
git push origin v0.1.0
```

Le workflow exécute tests et lint, construit un APK R8 signé, calcule SHA-256, puis crée la GitHub Release. Un tag signé nécessite une clé Git configurée ; à défaut, ne pas publier avant d’avoir établi une politique de signature.
