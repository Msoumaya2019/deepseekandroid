# Compatibilité Supabase

L'application Android et l'application React Native partagent **le même projet Supabase**
(`npbwnvrqmajwqtnncuyv`). Ce n'est pas un choix de confort : deux projets distincts auraient
dupliqué les comptes et rompu la continuité d'apprentissage entre les deux clients. Un
utilisateur doit pouvoir installer l'application Android, se connecter avec son compte actuel,
et retrouver exactement sa progression.

---

## La règle qui prime sur toutes les autres

> **Le dépôt `Msoumaya2019/coran-memoire` doit continuer de fonctionner à l'identique.**

Toute évolution du schéma est donc **additive** ou n'a pas lieu. Concrètement, l'application
Android s'interdit :

- de supprimer ou renommer une table ou une colonne ;
- de changer le type d'une colonne utilisée ;
- de modifier une politique RLS existante de façon restrictive ;
- de changer la forme d'un champ de `data`.

Si une évolution présentait un risque pour le client React Native, elle est **arrêtée et
signalée** avant d'être appliquée, jamais appliquée puis documentée.

---

## Ce que l'application Android utilise

### Table `user_state` — l'état complet

```sql
create table public.user_state (
  user_id uuid primary key references auth.users(id) on delete cascade,
  data jsonb not null,
  updated_at timestamptz not null default now()
);
```

Une ligne par utilisateur, portant **tout** l'état applicatif dans `data` : maîtrise des
versets, programme, séances, révisions, consolidations, signets, difficultés, préférences
d'apparence, réglages de lecture, profil, notifications, historique du quiz.

Trois propriétés de ce schéma sont utilisées délibérément par le client Android :

| Propriété | Ce qu'elle permet |
|---|---|
| `user_id` est **clé primaire** | l'écriture est un `upsert`, donc **idempotente** : un renvoi après coupure réseau ne peut pas créer de doublon |
| `select` filtré sur la clé primaire | `decodeSingleOrNull` suffit ; aucune pagination, aucun tri à gérer |
| `data` est `jsonb` | l'instantané complet est l'unité d'échange, ce qui rend la fusion à trois voies possible |

### Opérations

| Opération | Requête | Où |
|---|---|---|
| Lire | `select user_id, data, updated_at from user_state where user_id = ?` | `SupabaseStateSource.loadState` |
| Écrire | `upsert` sur `user_id` | `SupabaseStateSource.saveState` |
| Supprimer | `delete where user_id = ?` | `SupabaseStateSource.deleteState` |

L'`upsert` inclut **toujours** `user_id` dans la ligne envoyée. Ce n'est pas une redondance :
la politique d'insertion porte `with check (auth.uid() = user_id)`, donc une ligne sans
identifiant serait refusée par le serveur.

### Authentification

Même projet, mêmes utilisateurs, mêmes `uuid`. L'application Android :

- utilise **la clé `anon`** (publique) et la session de l'utilisateur ;
- s'authentifie par courriel et mot de passe (`signInWith(Email)`, `signUpWith(Email)`),
  comme le client React Native ;
- conserve le jeton dans le **magasin de clés Android**, chiffré en AES/GCM ;
- rafraîchit le jeton automatiquement, et sait rester utilisable quand le rafraîchissement
  échoue.

Le `user.id` est donc **le même** sur React Native, iOS et Android, ce qui est la condition de
la continuité entre les clients.

---

## Row Level Security

```sql
alter table public.user_state enable row level security;
revoke all on public.user_state from anon;
grant select, insert, update, delete on public.user_state to authenticated;

create policy "own state select" on public.user_state for select to authenticated
  using ((select auth.uid()) = user_id);
create policy "own state insert" on public.user_state for insert to authenticated
  with check ((select auth.uid()) = user_id);
create policy "own state update" on public.user_state for update to authenticated
  using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create policy "own state delete" on public.user_state for delete to authenticated
  using ((select auth.uid()) = user_id);
```

Ces politiques sont la **seule** protection réelle des données. En conséquence :

- l'application Android ne vérifie pas elle-même que l'état lu appartient à l'utilisateur
  connecté : le serveur le garantit. Elle applique en plus un garde-fou local (le `userId`
  porté par l'état), parce qu'un fichier local n'est pas protégé par RLS ;
- **la clé `service_role` est interdite dans l'application.** Elle contourne RLS : quiconque
  l'extrairait du binaire aurait un accès total aux données de tous les utilisateurs. Seule la
  clé `anon` a sa place dans un client, et l'interdiction est inscrite dans le code
  (`SupabaseConfig`), pas seulement ici.

---

## Les autres tables du projet

L'application Android les utilisera pour les phases C à E. Elles existent déjà et ne sont pas
modifiées :

| Domaine | Tables |
|---|---|
| Notifications | `notification_preferences`, `push_devices`, `admin_notifications` |
| Quiz | `quiz_questions`, `quiz_sets`, `quiz_daily_responses`, `quiz_challenges`, `quiz_challenge_questions`, `quiz_challenge_answers` |
| Amis | `friend_profiles`, `friend_links`, `friend_groups`, `friend_group_members`, `friend_messages`, `friend_message_reads`, `friend_message_hidden`, `friend_message_reports`, `friend_progress`, `friend_review_appointments`, `friend_shared_goals` |
| Contenus | `daily_contents`, `daily_content_schedule`, `content_categories`, `content_favorites` |
| Récitations | `recitations`, `recitation_corrections`, `recitation_feedback` |
| Modération | `app_problem_reports`, `app_admins`, `social_suspensions` |
| Interne | `private.push_delivery_log`, `private.quiz_notification_events` |

---

## Ce qui n'est pas encore branché

| Élément | État | Remarque |
|---|---|---|
| `user_state` (lecture, écriture, fusion) | **fait et éprouvé** | 26 tests sur la couche de données |
| Authentification (connexion, inscription, déconnexion) | **code écrit**, éprouvé contre le serveur pour l'inscription | la clé `publisable` est en place ; reste à exercer une **connexion** avec un compte réel |
| Réinitialisation du mot de passe par courriel | **code écrit** | `resetPasswordForEmail`, sans `redirectUrl` tant que le lien profond n'existe pas. Le message affiché reste neutre : le serveur répond « succès » même pour une adresse inconnue, pour empêcher l'énumération des comptes |
| Renvoi du courriel de confirmation | **code écrit** | `resendEmail(OtpType.Email.SIGNUP, …)`, même réserve sur la redirection |
| Lien profond de confirmation | **à faire** | schéma d'URL à définir et à faire vérifier par le domaine. En attendant, le lien reçu ouvre la page du projet Supabase et non l'application |
| Notifications (FCM, `push_devices`) | **à faire** | phase D |
| Amis — profils, liens, invitations, cercles | **code écrit** | `SocialSource`, `SupabaseSocialSource` et `SocialRepository`, éprouvés contre une **source factice** (21 tests) : la lecture du profil, `friend_inbox()`, les six RPC de lien, la création de cercle et l'ouverture du contact administrateur. **Rien n'a encore été exercé contre le serveur** — ni `ensureProfile`, ni `friend_inbox` : le vérifier demande un **second compte réel**, et c'est le prochain pas |
| Conversation — messages, membres, objectifs partagés, rendez-vous | **code écrit, écran porté** | la même paire `SocialSource` / `SupabaseSocialSource` porte les vingt-deux opérations de la conversation, et `SocialRepository` porte ses règles d'ordonnancement. Éprouvées contre la **source factice** (23 tests) et tenues par un **contrôle de forme** : quelle page se lit à l'ouverture d'une pièce, le curseur de la page ancienne, les deux transitions d'historique, la remise à zéro d'une pièce ouverte, les compléments qui n'effacent pas les messages déjà affichés, et le compteur de la liste remis à zéro après lecture. **Rien n'a encore été exercé contre le serveur** : les tables `friend_messages`, `friend_message_hidden`, `friend_message_reads`, `friend_group_members`, `friend_shared_goals` et `friend_review_appointments` ne sont lues que par la doublure. L'**écran** est porté : `ConversationScreen.kt` dispose, `ConversationRenderer` calcule **sans appareil**, et les **trois portes** de la liste y mènent — l'appui sur la ligne, le bouton « Message », le bouton « Ouvrir » d'un cercle |
| Quiz — question du jour, défis, notifications | **code écrit** | `QuizSource` / `SupabaseQuizSource` portent les **cinq** fonctions du client (`quiz_snapshot`, `quiz_answer_daily`, `quiz_create_challenge`, `quiz_answer_challenge`, `quiz_set_notifications`), et `QuizRepository` ses règles d'ordonnancement : l'instantané du disque publié avant le réseau, la file vidée avant la lecture, la réponse faite hors ligne rangée puis renvoyée, la fusion qui ne l'efface pas, l'isolement des comptes et la déconnexion — éprouvées contre une **source factice** (18 tests). L'**écran** est porté **et** atteignable : la coquille sert sa route paramétrée, et ses **48** tests (40 pour de vrai, 8 contrôles de forme) vivent dans `feature:quiz`. **Rien n'a encore été exercé contre le serveur** : les six tables `quiz_*` ne sont lues que par la doublure, et la migration `quiz.sql` n'est **peut-être pas déployée** sur le projet partagé. Le client le dit alors en toutes lettres — le code `PGRST202` est traduit en « Le service Quiz doit être activé sur le serveur. » au lieu d'afficher le texte brut de PostgREST. Les six fonctions d'administration restent **hors périmètre** |
| Récitations — dépôt et file d'attente | **code écrit** | `RecitationUploader` / `SupabaseRecitationUploader` portent le dépôt du fichier et l'écriture de la ligne, et `RecitationRepository` ses règles d'ordonnancement : le fichier **avant** la ligne, un élément en échec qui ne bloque pas les suivants, une récitation restée en plein dépôt qui **repart**, et un second dépôt concurrent qui rend la main au lieu de renvoyer les mêmes octets — éprouvées contre une **source factice** (25 tests). **Rien n'a encore été exercé contre le serveur** : le compartiment et la table `recitations` ne sont lus que par la doublure. L'**enregistreur natif** est écrit (`MediaAudioRecorder`) et n'a pas encore d'écran : c'est la tranche suivante |
| Contenus du jour | **à faire** | phase C |

### Deux règles qui protègent le compte existant

Ces deux points ne concernent pas le schéma, mais ils décident si un utilisateur du client
React Native peut **continuer à utiliser son compte** depuis Android :

1. **La connexion ne juge pas la longueur du mot de passe.** `AuthInput.emptyPasswordProblem` ne
   refuse que le vide ; la longueur minimale n'est exigée qu'à l'inscription. Refuser localement
   un mot de passe court fermerait une porte que le serveur, lui, laisse ouverte — et le compte
   concerné fonctionne encore côté React Native.
2. **Un compte dont l'état n'a pas encore été lu n'ouvre pas l'application.** Sans cette règle,
   l'application afficherait un programme vide, et la première séance validée dans ce programme
   vide serait poussée au serveur : `reconcileState` donne raison au local dès que son
   `updatedAt` est le plus récent, ce qui serait le cas. C'est la seule façon connue de perdre
   un compte partagé, et elle est fermée par construction — voir `ARCHITECTURE.md`.

---

## Ce qui a été mesuré contre le serveur

La documentation d'un schéma ne vaut que si on l'a interrogé. Ces quatre appels ont été passés au
projet `npbwnvrqmajwqtnncuyv` avec la clé publisable, le 4 octobre 2026 :

| Appel | Résultat | Ce qu'il établit |
|---|---|---|
| `/auth/v1/health` **avec** la clé | `200` | la clé est acceptée par le projet |
| le même **sans** clé | `401` | le refus observé ailleurs n'est pas un problème de clé |
| `POST /auth/v1/signup` avec `"123"` | `422 weak_password` — « at least 6 characters » | la longueur minimale **du serveur** est 6, celle de `AuthInput.MIN_PASSWORD` : les deux seuils coïncident, l'inscription ne peut pas être refusée localement pour une raison que le serveur accepterait |
| `select user_id from user_state` en rôle `anon` | `42501 permission denied for table user_state` | conforme à `supabase/schema.sql`, qui fait `revoke all … from anon` : la table n'est lisible qu'**authentifié** |

### `account_state` n'est pas une table distante

Le serveur, interrogé sur `account_state`, a répondu `PGRST205` — « Could not find the table
`public.account_state` » — en suggérant `user_state`. C'est exact, et c'est une confusion facile :

| Nom | Où il vit réellement | Rôle |
|---|---|---|
| `account_state` | **SQLite locale** du client React Native (`src/services/storage.ts`) | l'état d'un compte, sur l'appareil, indexé par `user_id` |
| `app_state` | **SQLite locale** du client React Native, même fichier | l'état **anonyme**, en une seule ligne (`id = 1`) |
| `user_state` | **Postgres, côté Supabase** | le même état, une ligne par utilisateur, colonne `data` en `jsonb` |

Côté Android, l'équivalent de `account_state` est un fichier JSON par compte
(`state_account_<jeton>.json`) et l'équivalent de `app_state` est un fichier anonyme. Le seul nom
qui traverse le réseau est `user_state` — c'est la constante `SupabaseStateSource.TABLE`, et
aucun autre nom n'est envoyé.

---

## Vérifier la compatibilité

Le point de contrôle est simple et se fait sans modifier quoi que ce soit :

1. se connecter sur l'application Android avec un compte **existant** du client React Native ;
2. vérifier que la progression affichée est celle du compte — pas un état vide ;
3. valider une séance côté Android, puis ouvrir le client React Native et vérifier que la
   validation y apparaît ;
4. faire l'inverse : modifier côté React Native, synchroniser côté Android, vérifier que rien
   n'a été perdu de part et d'autre.

L'étape 4 est celle qui compte. C'est exactement ce que la fusion à trois voies et la règle
« on ne pousse jamais sans avoir lu » protègent, et c'est pour elle que les tests de
`AccountSync` portent un témoin.
