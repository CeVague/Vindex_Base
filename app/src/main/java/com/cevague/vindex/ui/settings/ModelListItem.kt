package com.cevague.vindex.ui.settings

import com.cevague.vindex.data.database.entity.AiModel

/**
 * Élément de la liste Modèles : en-tête de type, ou modèle.
 *
 * Les sections ne sont pas cosmétiques. L'activation est exclusive **par type**, et
 * depuis les visages plusieurs types sont actifs en même temps (recherche +
 * détection + embedding) : sans en-têtes, trois radios cochés dans une même liste
 * se lisent comme un groupe à choix unique cassé. La section rend visible ce que le
 * radio ne peut pas dire — qu'il y a un choix **par groupe**.
 */
sealed interface ModelListItem {

    data class Header(val title: String) : ModelListItem

    data class Model(val model: AiModel) : ModelListItem
}
