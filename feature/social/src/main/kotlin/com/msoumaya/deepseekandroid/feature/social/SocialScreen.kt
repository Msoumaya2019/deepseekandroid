package com.msoumaya.deepseekandroid.feature.social

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.msoumaya.deepseekandroid.core.data.LocalAppContainer
import com.msoumaya.deepseekandroid.core.design.component.AppButton
import com.msoumaya.deepseekandroid.core.design.component.AppCard
import com.msoumaya.deepseekandroid.core.design.component.AppField
import com.msoumaya.deepseekandroid.core.design.component.AppHeading
import com.msoumaya.deepseekandroid.core.design.component.AppHero
import com.msoumaya.deepseekandroid.core.design.component.AppIconButton
import com.msoumaya.deepseekandroid.core.design.component.AppInlineIcon
import com.msoumaya.deepseekandroid.core.design.component.AppLabel
import com.msoumaya.deepseekandroid.core.design.component.AppSegmentedControl
import com.msoumaya.deepseekandroid.core.design.theme.AppTheme
import com.msoumaya.deepseekandroid.core.domain.SocialText

// ---------------------------------------------------------------------------
// Écran « Amis »
// ---------------------------------------------------------------------------
// Portage de la liste d'amis de `FriendsScreen` (`src/SocialScreens.tsx:144-167`), et de sa
// conversation (`:168-208`) — les deux vivent dans le même composant d'origine, et c'est cet
// écran qui bascule de l'une à l'autre.
//
// **L'écran ne calcule rien.** Le filtrage, le tri, la coupe à cinq entrées et la mise en forme
// des sous-titres sont déjà faits quand l'état arrive : ils vivent dans `SocialRenderer`, où ils
// s'éprouvent sans appareil. Ici il n'y a que la disposition — quel bloc va où, à quelle taille,
// et quelle icône lui correspond.
//
// **Les trois portes vers la conversation sont ouvertes.** Le client d'origine en portait trois :
// la ligne d'un ami entière, son bouton « Message », et le bouton « Ouvrir » d'un cercle. Elles
// avaient été **omises** tant que la conversation n'existait pas — un bouton qui n'ouvre rien est
// pire qu'un bouton absent —, et elles sont rétablies ici, chacune avec le geste de l'original.
//
// **Trois écarts assumés**, tous les trois parce que le contraire ferait dire à l'écran quelque
// chose de faux :
//
//  1. le titre « Invitations reçues » de l'original s'affichait **toujours**, même quand aucune
//     invitation n'existait — un titre suivi de rien. Il n'apparaît ici que lorsqu'il annonce
//     quelque chose ;
//  2. le pied « Contacter l'admin » s'affichait **même sans compte**, où le geste échouait
//     nécessairement. Il n'apparaît qu'avec un compte ;
//  3. le nombre de messages non lus était posé **dans** le bouton « Message ». Le bouton est
//     revenu, mais le compte reste sur le médaillon : c'est là que l'œil va en parcourant une
//     liste, et le mettre aussi dans le bouton ferait trois choses à lire — une icône, un mot, un
//     chiffre — dans un espace qui n'en tient pas trois.
//
// **Un quatrième écart, d'image celui-là.** Le médaillon d'un ami est toujours son initiale. Le
// client d'origine affichait l'avatar distant quand il y en avait un — ce qui suppose de signer
// une URL dans le stockage Supabase et de charger une image par-dessus le réseau. C'est l'affaire
// de l'écran de profil ; l'initiale est exactement ce que l'original affiche quand il n'y a pas
// d'image, et le repli n'est donc pas une invention.
// ---------------------------------------------------------------------------

/**
 * Retrait horizontal du contenu, sous la bande d'en-tête.
 *
 * Valeur du programme, du Coran, **et de la conversation** — c'est pourquoi elle est `internal`
 * et non `private` : les deux écrans qui la portent vivent dans deux fichiers, et deux copies de
 * la même marge finiraient par ne plus se ressembler sans que rien ne le dise.
 */
internal val SCREEN_PADDING = 18.dp

/** Côté du médaillon d'un ami (`size={52}` à la source). */
private val AVATAR_SIZE = 52.dp

/** Côté de la pastille de présence posée sur le médaillon. */
private val PRESENCE_DOT = 11.dp

/**
 * Épaisseur du liseré qui détache la pastille du médaillon.
 *
 * Le liseré est de la couleur du fond de la carte, et non du médaillon : c'est ce qui creuse la
 * pastille dans le médaillon au lieu de la fondre dedans. Valeur de la source, où le médaillon et
 * la pastille sont deux `View` superposées.
 */
private val PRESENCE_RING = 2.dp

/** Rayon du pavé qui porte le code d'invitation. */
private val CODE_RADIUS = 14.dp

/**
 * Teinte de la pastille d'un ami en ligne.
 *
 * **Littéral du client d'origine**, absent de `tokens.ts` : il y écrit `'#53A963'` en clair, à
 * côté de `colors.muted` pour le cas contraire. Elle est reprise telle quelle pour que les deux
 * clients peignent le même point — en faire un jeton de thème la ferait changer avec l'accent, et
 * la pastille cesserait de vouloir dire « en ligne ».
 */
private val ONLINE_DOT = Color(0xFF53A963)

/**
 * Mes amis : liste, invitations, cercles privés, contact de l'administration.
 *
 * @param modifier modificateur de disposition.
 * @param viewModel état de l'écran. Par défaut, celui du conteneur applicatif.
 */
@Composable
fun SocialScreen(
    modifier: Modifier = Modifier,
    viewModel: SocialViewModel = viewModel(
        factory = SocialViewModel.factory(LocalAppContainer.current),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Le dépôt charge déjà à la construction, mais il ne sait pas quand l'onglet s'ouvre : c'est
    // la seule chose que l'écran lui apprend. Le rappel est sans effet si un chargement est en
    // cours, donc l'ouverture immédiate après la construction ne coûte pas une seconde lecture.
    LaunchedEffect(Unit) { viewModel.onVisible() }

    // **Une pièce ouverte remplace la liste, elle ne s'y ajoute pas.** C'est la structure de
    // l'original, où `selected` décide du corps du même écran : l'en-tête, le défilement et le
    // compositeur d'une conversation n'ont rien à voir avec ceux de la liste, et les faire
    // cohabiter dans une même colonne défilante mettrait le champ d'écriture à la suite des
    // invitations.
    if (state.conversationOpen) {
        ConversationSection(modifier = modifier)
        return
    }

    SocialContent(
        state = state,
        modifier = modifier,
        onQueryChange = viewModel::onQueryChange,
        onFilterSelected = viewModel::onFilterSelected,
        onToggleAll = viewModel::onToggleAll,
        onToggleOptions = viewModel::onToggleOptions,
        onCodeChange = viewModel::onCodeChange,
        onSendInvitation = viewModel::onSendInvitation,
        onGroupNameChange = viewModel::onGroupNameChange,
        onCreateGroup = viewModel::onCreateGroup,
        onAccept = viewModel::onAccept,
        onDecline = viewModel::onDecline,
        onRemove = viewModel::onRemove,
        onBlock = viewModel::onBlock,
        onUnblock = viewModel::onUnblock,
        onCodeCopied = viewModel::onCodeCopied,
        onAdminContact = viewModel::onOpenAdminContact,
        onOpenConversation = viewModel::onOpenConversation,
    )
}

/**
 * L'écran, à partir d'un état déjà résolu.
 *
 * Séparé de [SocialScreen] pour la même raison qu'au Progrès : l'état se fabrique à la main dans
 * une prévisualisation, sans conteneur, sans réseau et sans compte.
 */
@Composable
internal fun SocialContent(
    state: SocialUiState,
    modifier: Modifier = Modifier,
    onQueryChange: (String) -> Unit = {},
    onFilterSelected: (String) -> Unit = {},
    onToggleAll: () -> Unit = {},
    onToggleOptions: () -> Unit = {},
    onCodeChange: (String) -> Unit = {},
    onSendInvitation: () -> Unit = {},
    onGroupNameChange: (String) -> Unit = {},
    onCreateGroup: () -> Unit = {},
    onAccept: (String) -> Unit = {},
    onDecline: (String) -> Unit = {},
    onRemove: (String) -> Unit = {},
    onBlock: (String) -> Unit = {},
    onUnblock: (String) -> Unit = {},
    onCodeCopied: () -> Unit = {},
    onAdminContact: () -> Unit = {},
    /**
     * Ouvre la conversation d'un ami ou d'un cercle : l'identifiant de la pièce, et son genre.
     *
     * **Deux paramètres, et non un objet.** C'est `ChatRoom` qui distingue un lien d'un groupe par
     * ses deux champs, et il appartient au module de données : l'écran ne peut pas le nommer sans
     * faire dépendre sa signature d'un type de transport. Un identifiant et un booléen suffisent,
     * et `SocialViewModel` traduit.
     */
    onOpenConversation: (String, Boolean) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current

    // L'amitié dont le menu est ouvert. **Non sauvegardé**, contrairement à la confirmation de
    // suppression des signets : ici rien n'est en attente. Une rotation referme le menu, et
    // personne n'a rien perdu — alors que sauvegarder l'objet demanderait un `Saver` pour une
    // ligne dont la seule raison d'être est d'être à l'écran pendant qu'on la regarde.
    var managed by remember { mutableStateOf<FriendRow?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // **Le bandeau passe avant la bande d'en-tête**, comme dans l'original : c'est un
        // message transitoire, et le repousser sous une illustration pleine largeur le rendrait
        // invisible tant qu'on n'a pas fait défiler.
        state.notice?.let { message ->
            Box(
                modifier = Modifier.padding(
                    horizontal = SCREEN_PADDING,
                    vertical = AppTheme.spacing.sm,
                ),
            ) {
                AppCard(spaced = false) {
                    AppLabel(text = message, selectable = false)
                }
            }
        }

        AppHero(title = SocialText.TITLE, subtitle = SocialText.SUBTITLE)

        Column(modifier = Modifier.padding(horizontal = SCREEN_PADDING)) {
            when {
                state.failure != null -> SocialFailure(message = state.failure)

                state.loading -> SocialInfoCard(message = SocialText.LOADING)

                !state.signedIn -> SocialInfoCard(message = SocialText.SIGN_IN)

                else -> SocialBody(
                    state = state,
                    onQueryChange = onQueryChange,
                    onFilterSelected = onFilterSelected,
                    onToggleAll = onToggleAll,
                    onToggleOptions = onToggleOptions,
                    onCodeChange = onCodeChange,
                    onSendInvitation = onSendInvitation,
                    onGroupNameChange = onGroupNameChange,
                    onCreateGroup = onCreateGroup,
                    onAccept = onAccept,
                    onDecline = onDecline,
                    onUnblock = onUnblock,
                    onCodeCopied = onCodeCopied,
                    onAdminContact = onAdminContact,
                    onOpenConversation = onOpenConversation,
                    onManage = { row -> managed = row },
                    context = context,
                )
            }
        }
    }

    managed?.let { row ->
        ManageDialog(
            row = row,
            onDismiss = { managed = null },
            onRemove = { linkId ->
                managed = null
                onRemove(linkId)
            },
            onBlock = { otherId ->
                managed = null
                onBlock(otherId)
            },
        )
    }
}

// ---------------------------------------------------------------------------
// Les trois états sans liste
// ---------------------------------------------------------------------------

/**
 * Panne de la lecture principale.
 *
 * Un écran distinct de la liste vide, et c'est tout l'intérêt : une liste vide se lit « tu n'as
 * pas d'amis », qui est une affirmation fausse sur des tiers quand le réseau a échoué.
 */
@Composable
private fun SocialFailure(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = AppTheme.spacing.section),
    ) {
        AppHeading(text = "Amis indisponibles", size = 21.sp)
        AppLabel(
            text = message,
            selectable = false,
            color = AppTheme.colors.muted,
            modifier = Modifier.padding(top = AppTheme.spacing.sm),
        )
    }
}

/**
 * Carte d'information neutre : « Chargement de tes amis… », ou l'invitation à se connecter.
 *
 * Les deux se ressemblent à dessein — ni l'une ni l'autre n'est une erreur —, et c'est le texte
 * qui les distingue.
 */
@Composable
private fun SocialInfoCard(message: String) {
    AppCard(modifier = Modifier.padding(top = AppTheme.spacing.sm)) {
        AppLabel(text = message, selectable = false)
    }
}

// ---------------------------------------------------------------------------
// Le corps : recherche, filtre, amis, invitations, cercles, administration
// ---------------------------------------------------------------------------

@Composable
private fun SocialBody(
    state: SocialUiState,
    onQueryChange: (String) -> Unit,
    onFilterSelected: (String) -> Unit,
    onToggleAll: () -> Unit,
    onToggleOptions: () -> Unit,
    onCodeChange: (String) -> Unit,
    onSendInvitation: () -> Unit,
    onGroupNameChange: (String) -> Unit,
    onCreateGroup: () -> Unit,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit,
    onUnblock: (String) -> Unit,
    onCodeCopied: () -> Unit,
    onAdminContact: () -> Unit,
    onOpenConversation: (String, Boolean) -> Unit,
    onManage: (FriendRow) -> Unit,
    context: Context,
) {
    // La suspension de la messagerie. Le client d'origine ne la montre que dans la conversation ;
    // sans cette carte, une personne suspendue ne l'apprendrait nulle part tant que la
    // conversation n'existe pas. Elle redescendra d'un cran quand elle existera.
    state.suspension?.let { message ->
        AppCard(modifier = Modifier.padding(top = AppTheme.spacing.sm), background = AppTheme.colors.soft) {
            AppLabel(text = message, selectable = false, color = AppTheme.colors.red)
        }
    }

    // Recherche et bascule des options, sur une même ligne. Le bouton « + » de l'original ouvre
    // le même bloc que la bascule du bas : c'est le même état, et il n'y en a qu'un.
    AppCard(modifier = Modifier.padding(top = AppTheme.spacing.sm), padding = 10.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                AppField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    placeholder = SocialText.SEARCH_PLACEHOLDER,
                )
            }
            AppIconButton(
                icon = Icons.Outlined.Add,
                label = SocialText.INVITE_SECTION,
                onClick = onToggleOptions,
            )
        }
    }

    AppSegmentedControl(
        options = state.filterLabels,
        value = state.filterLabel,
        onSelect = onFilterSelected,
    )

    FriendsSection(
        state = state,
        onToggleAll = onToggleAll,
        onOpen = onOpenConversation,
        onManage = onManage,
    )

    // Le bloc d'invitation est déplié par la bascule, **et** par le filtre « Demandes » : c'est
    // la règle de l'original, où choisir ce filtre vide la liste et ouvre à sa place de quoi
    // envoyer une invitation.
    if (state.optionsOpen || state.filterLabel == SocialText.FILTER_REQUESTS) {
        InviteBlock(
            state = state,
            onCodeChange = onCodeChange,
            onSendInvitation = onSendInvitation,
            onCodeCopied = onCodeCopied,
            context = context,
        )
    }

    InvitationsSection(
        state = state,
        onAccept = onAccept,
        onDecline = onDecline,
        onUnblock = onUnblock,
    )

    AppButton(
        text = if (state.optionsOpen) SocialText.TOGGLE_OPTIONS_CLOSE else SocialText.TOGGLE_OPTIONS,
        secondary = true,
        small = true,
        modifier = Modifier.fillMaxWidth(),
        onClick = onToggleOptions,
    )

    if (state.optionsOpen) {
        CirclesSection(
            state = state,
            onGroupNameChange = onGroupNameChange,
            onCreateGroup = onCreateGroup,
            onOpen = onOpenConversation,
        )
    }

    AdminFooter(busy = state.busy, onAdminContact = onAdminContact)
}

// ---------------------------------------------------------------------------
// La liste d'amis
// ---------------------------------------------------------------------------

@Composable
private fun FriendsSection(
    state: SocialUiState,
    onToggleAll: () -> Unit,
    onOpen: (String, Boolean) -> Unit,
    onManage: (FriendRow) -> Unit,
) {
    val colors = AppTheme.colors

    // Le client d'origine enveloppait cette section dans une carte **transparente, sans bordure
    // et sans ombre** : c'était un simple conteneur, pas une carte. Ici, c'est une colonne — la
    // carte n'aurait rien apporté qu'un cadre à retirer.
    Column(modifier = Modifier.padding(top = AppTheme.spacing.sm)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm),
        ) {
            AppInlineIcon(icon = Icons.Outlined.Groups, tint = colors.green, size = 20.dp)

            // Le titre est un `Label` de la source — graisse 800, police d'interface —, et non
            // un `Heading` : ce n'est pas la police des titres, et les confondre ferait
            // diverger deux écrans que rien ne distingue à la lecture.
            AppLabel(
                text = SocialText.friendsCount(state.friendCount),
                selectable = false,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.weight(1f),
            )

            Box(
                modifier = Modifier
                    .defaultMinSize(minHeight = 44.dp)
                    .clickable(role = Role.Button, onClick = onToggleAll),
                contentAlignment = Alignment.Center,
            ) {
                AppLabel(
                    text = if (state.allFriends) SocialText.COLLAPSE else SocialText.SEE_ALL,
                    selectable = false,
                    fontSize = 12.sp,
                    color = colors.green,
                )
            }
        }

        state.friends.forEach { row ->
            FriendCard(row = row, onOpen = onOpen, onManage = onManage)
        }

        // Le repli porte sur le nombre d'amis **acceptés**, et non sur la liste visible : une
        // recherche sans résultat ne doit pas annoncer qu'on n'a aucun ami. C'est exactement le
        // prédicat de l'original, qui regarde `links.some(l => l.status === 'accepted')`.
        if (state.friendCount == 0) {
            AppLabel(
                text = SocialText.NO_FRIENDS,
                selectable = false,
                color = colors.muted,
                modifier = Modifier.padding(10.dp),
            )
        }
    }
}

@Composable
private fun FriendCard(
    row: FriendRow,
    onOpen: (String, Boolean) -> Unit,
    onManage: (FriendRow) -> Unit,
) {
    val colors = AppTheme.colors

    AppCard(
        padding = 10.dp,
        // L'original pose 8 px entre deux lignes, là où la carte en pose 12 par défaut.
        bottomSpacing = AppTheme.spacing.sm,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            FriendMedallion(name = row.name, online = row.online, unread = row.unread)

            // **Toute la colonne ouvre la conversation**, comme dans l'original : c'est la cible
            // la plus large de la ligne, et la seule qui ne soit pas un geste de gestion. Elle ne
            // porte aucun mot qui annonce ce qu'elle fait — d'où le libellé d'accessibilité, que
            // l'original porte aussi.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 44.dp)
                    .clickable(role = Role.Button) { onOpen(row.id, false) }
                    .semantics { contentDescription = SocialText.openConversationWith(row.name) },
            ) {
                // `Heading size={21}` dans la source : police de titre, couleur principale.
                AppHeading(text = row.name, size = 21.sp)

                AppLabel(
                    text = row.subtitle,
                    selectable = false,
                    fontSize = 11.sp,
                    color = colors.muted,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 4.dp),
                )

                // Le dernier message, ou rien. Un résumé absent et un résumé vide s'affichent de
                // la même façon : dans les deux cas il n'y a rien à dire.
                AppLabel(
                    text = row.summary,
                    selectable = false,
                    fontSize = 11.sp,
                    color = colors.muted,
                    maxLines = 1,
                )
            }

            // Le bouton « Message », qui ouvre la **même** pièce que la colonne. C'est le doublon
            // de l'original, et il est gardé : le geste se voit mieux sur un bouton que sur une
            // ligne, et tout le monde ne devine pas qu'une ligne se touche.
            MessageButton(name = row.name, onClick = { onOpen(row.id, false) })

            AppIconButton(
                icon = Icons.Outlined.MoreVert,
                label = SocialText.optionsFor(row.name),
                onClick = { onManage(row) },
            )
        }
    }
}

/**
 * Médaillon d'un ami : son initiale, et — quand on les demande — sa présence et ses non-lus.
 *
 * @param size côté du médaillon. La liste pose 52 px ; l'en-tête d'une conversation, 44 — ce n'est
 *   pas une ligne de liste, et un médaillon de la taille d'une ligne y écraserait le titre.
 * @param online présence, ou `null` quand aucune pastille n'a de sens. C'est le cas dans l'en-tête
 *   d'une conversation : l'état y est écrit en toutes lettres juste en dessous, et un point de
 *   couleur le dirait deux fois.
 * @param unread nombre de messages non lus. Zéro n'affiche aucune pastille — une pastille « 0 »
 *   annoncerait quelque chose à lire là où il n'y a rien.
 *
 * `internal` parce que l'en-tête d'une conversation s'en sert aussi : c'est le **même** médaillon
 * dans les deux écrans de l'original, et en écrire un second ferait diverger deux initiales que
 * rien ne distingue à la lecture.
 */
@Composable
internal fun FriendMedallion(
    name: String,
    size: Dp = AVATAR_SIZE,
    online: Boolean? = null,
    unread: Int = 0,
) {
    val colors = AppTheme.colors

    Box(modifier = Modifier.size(size)) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(colors.soft)
                .border(1.dp, colors.softBorder, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            AppLabel(
                text = initial(name),
                selectable = false,
                color = colors.green,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        // La pastille est **grise quand on ne sait pas**, exactement comme quand l'ami est hors
        // ligne : c'est le ternaire de l'original, où la carte de présence rend `undefined` et
        // où le repli est `colors.muted`. Distinguer les deux demanderait un troisième état, que
        // l'original n'a pas — et un point d'une troisième couleur dirait autre chose que lui.
        //
        // Son **absence** est un troisième cas, et c'est une demande de l'appelant : `online` vaut
        // `null` là où une pastille n'annoncerait rien. L'en-tête d'une conversation est ce cas.
        if (online != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(PRESENCE_DOT)
                    .clip(CircleShape)
                    .background(if (online) ONLINE_DOT else colors.muted)
                    .border(PRESENCE_RING, colors.paper, CircleShape),
            )
        }

        if (unread > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                    .clip(CircleShape)
                    .background(colors.green)
                    .border(PRESENCE_RING, colors.paper, CircleShape)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                AppLabel(
                    text = unread.toString(),
                    selectable = false,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Le bloc d'invitation
// ---------------------------------------------------------------------------

@Composable
private fun InviteBlock(
    state: SocialUiState,
    onCodeChange: (String) -> Unit,
    onSendInvitation: () -> Unit,
    onCodeCopied: () -> Unit,
    context: Context,
) {
    val colors = AppTheme.colors

    // Le code n'existe qu'une fois le profil lu. Le bloc entier est déjà gardé par un compte
    // ouvert, mais le profil peut manquer sur un serveur qui n'a pas encore rendu la ligne :
    // la carte disparaît alors au lieu d'afficher un code vide.
    state.inviteCode?.let { code ->
        AppCard(background = colors.soft) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.md),
            ) {
                AppInlineIcon(icon = Icons.Outlined.CardGiftcard, tint = colors.green, size = 34.dp)

                Column(modifier = Modifier.weight(1f)) {
                    // `Label` de la source, graisse 800 — et non `Heading` : la police reste
                    // celle de l'interface, comme pour le titre de la liste.
                    AppLabel(
                        text = SocialText.INVITE_TITLE,
                        selectable = false,
                        color = colors.green,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    AppLabel(
                        text = SocialText.INVITE_SUBTITLE,
                        selectable = false,
                        fontSize = 13.sp,
                        color = colors.muted,
                        style = TextStyle(lineHeight = 21.sp),
                        modifier = Modifier.padding(top = 7.dp),
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(CODE_RADIUS))
                        .background(colors.paper)
                        .padding(10.dp),
                ) {
                    AppLabel(
                        text = code,
                        selectable = false,
                        color = colors.green,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        style = TextStyle(letterSpacing = 1.sp),
                    )
                }

                AppButton(
                    text = SocialText.COPY,
                    small = true,
                    onClick = {
                        copyInviteCode(context, code)
                        onCodeCopied()
                    },
                )
            }

            AppButton(
                text = SocialText.SHARE_LINK,
                secondary = true,
                small = true,
                modifier = Modifier.fillMaxWidth(),
                onClick = { shareInviteLink(context, code) },
            )
        }
    }

    // « Pourquoi inviter des amis ? » — carte d'explication, sur le fond doux du thème.
    AppCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.md),
        ) {
            AppInlineIcon(icon = Icons.Outlined.Groups, tint = colors.green, size = 20.dp)

            Column(modifier = Modifier.weight(1f)) {
                AppLabel(
                    text = SocialText.WHY_TITLE,
                    selectable = false,
                    color = colors.green,
                    fontWeight = FontWeight.Bold,
                )
                AppLabel(
                    text = SocialText.WHY_TEXT,
                    selectable = false,
                    fontSize = 13.sp,
                    color = colors.muted,
                    style = TextStyle(lineHeight = 21.sp),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }

    SectionHeading(text = SocialText.INVITE_SECTION)

    AppField(
        value = state.code,
        onValueChange = onCodeChange,
        placeholder = SocialText.CODE_PLACEHOLDER,
    )

    // Le bouton est désactivé tant que la saisie est vide **après rognage**, et tant qu'un geste
    // est en cours : c'est ce qui empêche la même invitation de partir deux fois.
    AppButton(
        text = SocialText.SEND_INVITATION,
        enabled = state.canSendInvitation,
        modifier = Modifier.fillMaxWidth(),
        onClick = onSendInvitation,
    )
}

// ---------------------------------------------------------------------------
// Invitations reçues, envoyées, blocages
// ---------------------------------------------------------------------------

@Composable
private fun InvitationsSection(
    state: SocialUiState,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit,
    onUnblock: (String) -> Unit,
) {
    // Le client d'origine affichait ce titre **toujours**, y compris sans aucune invitation : un
    // titre suivi de rien. Il n'apparaît donc ici que lorsqu'une des trois listes a quelque chose
    // à annoncer — c'est le premier des écarts déclarés en tête de fichier.
    if (state.invitations.isEmpty() && state.sent.isEmpty() && state.blocked.isEmpty()) return

    SectionHeading(text = SocialText.RECEIVED)

    state.invitations.forEach { invitation ->
        AppCard {
            AppLabel(text = invitation.label, selectable = false)
            AppButton(
                text = SocialText.ACCEPT,
                small = true,
                modifier = Modifier.fillMaxWidth(),
                onClick = { onAccept(invitation.linkId) },
            )
            AppButton(
                text = SocialText.DECLINE,
                secondary = true,
                small = true,
                modifier = Modifier.fillMaxWidth(),
                onClick = { onDecline(invitation.linkId) },
            )
        }
    }

    state.sent.forEach { invitation ->
        AppCard {
            AppLabel(text = invitation.label, selectable = false)
        }
    }

    state.blocked.forEach { blocked ->
        AppCard {
            AppLabel(text = blocked.label, selectable = false)
            AppButton(
                text = SocialText.UNBLOCK,
                secondary = true,
                small = true,
                modifier = Modifier.fillMaxWidth(),
                onClick = { onUnblock(blocked.otherId) },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Cercles privés
// ---------------------------------------------------------------------------

@Composable
private fun CirclesSection(
    state: SocialUiState,
    onGroupNameChange: (String) -> Unit,
    onCreateGroup: () -> Unit,
    onOpen: (String, Boolean) -> Unit,
) {
    SectionHeading(text = SocialText.CIRCLES)

    AppField(
        value = state.groupName,
        onValueChange = onGroupNameChange,
        placeholder = SocialText.GROUP_PLACEHOLDER,
    )

    AppButton(
        text = SocialText.CREATE_GROUP,
        secondary = true,
        enabled = state.canCreateGroup,
        modifier = Modifier.fillMaxWidth(),
        onClick = onCreateGroup,
    )

    state.circles.forEach { circle ->
        AppCard {
            AppLabel(
                text = circle.name,
                selectable = false,
                fontWeight = FontWeight.Bold,
            )

            // Le cercle de l'administration est un cercle comme les autres côté serveur — il vit
            // dans `friend_groups` et il est nommé « Contact · <ton nom> » —, et il se range donc
            // ici. Sa nature ne se lit pas dans son nom : elle est dite sous lui, sans quoi on ne
            // saurait pas à qui l'on parle. C'est le libellé que l'original emploie pour ce même
            // cercle dans sa conversation.
            if (circle.isAdminContact) {
                AppLabel(
                    text = SocialText.ADMIN_CONTACT_LABEL,
                    selectable = false,
                    fontSize = 12.sp,
                    color = AppTheme.colors.muted,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            // **Le troisième chemin vers la conversation**, avec la ligne d'un ami et son bouton
            // « Message ». Un cercle n'a ni ligne ni médaillon : sans ce bouton il n'aurait
            // aucune porte, et son contenu serait inatteignable.
            AppButton(
                text = SocialText.OPEN,
                small = true,
                modifier = Modifier.fillMaxWidth(),
                onClick = { onOpen(circle.id, true) },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Pied : contact de l'administration
// ---------------------------------------------------------------------------

@Composable
private fun AdminFooter(busy: Boolean, onAdminContact: () -> Unit) {
    val colors = AppTheme.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = AppTheme.spacing.xxl),
    ) {
        // L'original pose une bordure **supérieure** seule. Compose n'a pas de bordure partielle :
        // un trait d'un pixel au-dessus rend exactement le même séparateur, et il se pose sur
        // toute la largeur au lieu d'être une bordure qui n'en fait que trois côtés.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.line),
        )

        Column(modifier = Modifier.padding(top = 14.dp)) {
            AppButton(
                text = if (busy) SocialText.ADMIN_OPENING else SocialText.ADMIN_CONTACT,
                secondary = true,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                onClick = onAdminContact,
            )
            AppLabel(
                text = SocialText.ADMIN_NOTE,
                selectable = false,
                fontSize = 12.sp,
                color = colors.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Gérer une amitié
// ---------------------------------------------------------------------------

/**
 * Confirmation des deux gestes d'une amitié : retirer, bloquer.
 *
 * `AlertDialog` Material, comme la confirmation de suppression des signets : la boîte native du
 * client d'origine (`Alert.alert`) a exactement cette forme — un titre, un corps, et des boutons.
 *
 * **Ce que le portage ne peut pas rendre.** L'original marque les deux actions `destructive`, ce
 * que React Native peint en rouge. Le système de design n'a pas encore de variante destructive :
 * les deux boutons portent donc la teinte d'action, et rien à l'écran ne signale que ces gestes
 * défont quelque chose. C'est le seul point de cet écran que la source dit et qu'ici on taise.
 */
@Composable
private fun ManageDialog(
    row: FriendRow,
    onDismiss: () -> Unit,
    onRemove: (String) -> Unit,
    onBlock: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            AppLabel(
                text = row.name,
                fontSize = AppTheme.typeScale.card,
                fontWeight = FontWeight.Bold,
                selectable = false,
            )
        },
        text = {
            AppLabel(text = SocialText.MANAGE, selectable = false)
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm)) {
                AppButton(
                    text = SocialText.REMOVE,
                    small = true,
                    onClick = { onRemove(row.id) },
                )
                AppButton(
                    text = SocialText.BLOCK,
                    small = true,
                    onClick = { onBlock(row.otherId) },
                )
            }
        },
        dismissButton = {
            AppButton(
                text = SocialText.CANCEL,
                secondary = true,
                small = true,
                onClick = onDismiss,
            )
        },
        containerColor = AppTheme.colors.paper,
    )
}

// ---------------------------------------------------------------------------
// Intertitre et gestes du système
// ---------------------------------------------------------------------------

/**
 * Intertitre du client d'origine.
 *
 * `AppSectionTitle` ne convient pas : la source pose ici 18 px et une graisse 700, là où
 * l'intertitre de section en fait 21 et SemiBold. La marge est portée par le composant, comme
 * dans l'original, pour que deux intertitres consécutifs ne se collent pas.
 *
 * `internal` parce que la conversation s'en sert aussi : c'est le **même** intertitre dans les
 * deux écrans de l'original, et en écrire un second ferait diverger deux titres que rien ne
 * distingue à la lecture.
 */
@Composable
internal fun SectionHeading(text: String, modifier: Modifier = Modifier) {
    AppLabel(
        text = text,
        selectable = false,
        color = AppTheme.colors.green,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(top = 18.dp, bottom = 8.dp),
    )
}

/**
 * Copie le code d'invitation dans le presse-papiers du système.
 *
 * Passe par le service Android plutôt que par `LocalClipboardManager` de Compose : celui-ci est
 * en cours de remplacement par une interface suspendue, et l'écran n'a rien à gagner à dépendre
 * d'une API qui bouge. Le libellé du presse-papiers est celui du champ dont le code vient.
 */
private fun copyInviteCode(context: Context, code: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText(SocialText.CODE_PLACEHOLDER, code))
}

/**
 * Ouvre la feuille de partage du système sur le lien d'invitation.
 *
 * C'est exactement ce que faisait `Share.share` côté React Native : un `ACTION_SEND` de texte
 * présenté par le sélecteur. Le lien profond est celui de [SocialText.shareLink], schéma
 * `coranmemoire://` compris, pour que les liens déjà partagés continuent d'aboutir.
 *
 * Rien n'est rattrapé : le sélecteur du système répond à tous les coups, même quand aucune
 * application ne sait ouvrir un texte — il propose alors le presse-papiers.
 */
private fun shareInviteLink(context: Context, code: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, SocialText.shareLink(code))
    }
    context.startActivity(Intent.createChooser(send, SocialText.SHARE_LINK))
}

/**
 * Initiale affichée dans le médaillon.
 *
 * Le repli est le point d'interrogation de l'original, atteint quand le nom est vide ou fait
 * d'espaces : le client d'origine écrivait `(name.trim()[0] ?? '?')`, et en JavaScript un index
 * hors bornes rend `undefined`. La mise en majuscule est celle de la plateforme — sur une lettre
 * seule, elle ne dépend pas de la locale, et `toLocaleUpperCase('fr-FR')` n'aurait rien changé.
 */
private fun initial(name: String): String =
    (name.trim().firstOrNull()?.uppercaseChar() ?: '?').toString()
