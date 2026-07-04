# Vindex — Instructions pour agents de code

## Projet
Galerie photo Android 100 % locale avec recherche intelligente on-device
(sémantique, personnes, OCR). GPL-3.0, publication F-Droid visée.
Développement par phases : la phase 1 (socle) se termine, voir docs/BACKLOG.md.

## Documents de référence (lire avant toute tâche non triviale)
- docs/PROJET.md — vision, contraintes invariantes, décisions structurantes, roadmap
- docs/ARCHITECTURE.md — source de vérité technique ; §9 = plan de dette phase 1
- docs/PHASE_RECHERCHE_SEMANTIQUE.md — spécification phase 2
- docs/PHASE_ANALYSE_MULTI_IA.md — spécification phases 3-5
- docs/BACKLOG.md — état d'avancement courant, à mettre à jour en fin de tâche

## Commandes
- Build debug : `./gradlew assembleDebug`
- Tests unitaires : `./gradlew testDebugUnitTest`
- Tests instrumentés : `./gradlew connectedDebugAndroidTest` (appareil/émulateur requis)
- Lint : `./gradlew lint`

## Contraintes non négociables
- Aucune permission INTERNET, aucun appel réseau, aucune télémétrie.
- Aucune dépendance propriétaire : pas de Play Services, ML Kit, Firebase,
  ni runtime MediaPipe. Dépendances Maven Central de préférence (F-Droid).
- UI : Views/XML + ViewBinding + Material 3. Pas de Compose.
- i18n : aucune string en dur ; values/ et values-fr/ toujours synchrones.
- Room : `exportSchema = true`. Phase actuelle (pré-release, dev solo) :
  `fallbackToDestructiveMigration` assumé, le schéma v1 est modifié sur place
  sans bump de version (l'app est désinstallée/réinstallée à chaque changement).
  À partir de la première release : migration + test pour tout changement
  d'entité, fallback interdit.
- minSdk 26, targetSdk dernière stable.

## Règles de code
- Kotlin idiomatique, MVVM ; les ViewModels ne touchent jamais un DAO,
  uniquement les repositories.
- StateFlow/Flow exposés par les ViewModels, jamais de LiveData.
- Jamais `OnConflictStrategy.REPLACE` sur une table référencée par des FK
  (DELETE+INSERT → cascades destructrices) : utiliser `@Upsert`.
- Toute requête `IN (:liste)` est chunkée à 900 éléments.
- `photos.file_path` contient une URI `content://` (identité = MediaStore ID) ;
  toute construction d'URI passe par `MediaScanner.contentUriFor`.
- Tout résultat d'IA va dans `photo_analyses` (photo × type × modèle),
  jamais dans des colonnes de `photos`.
- Commentaires minimaux ; pas de commentaires de numérotation d'étapes.
- Workers : relancer `CancellationException`, `Log.e` avant tout
  `Result.retry()`/`failure()`, retry limité par `runAttemptCount < 3`.

## Workflow
- Un item de dette ou de feature = un commit isolé, avec son test.
- Mettre à jour docs/BACKLOG.md à la fin de chaque tâche terminée.
- Signaler explicitement toute nouvelle dépendance avant de l'ajouter.
- En cas de doute sur une décision d'architecture, docs/PROJET.md §4 tranche.