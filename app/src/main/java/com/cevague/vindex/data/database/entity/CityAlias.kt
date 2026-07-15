package com.cevague.vindex.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Variante anglaise d'une ville : « Bombay » pour Mumbai, « Saigon » pour Ho Chi
 * Minh City, « Calcutta » pour Kolkata, « Kiev » pour Kyiv.
 *
 * La colonne `name` de GeoNames est le nom **international** — en pratique
 * l'anglais quand il en existe un (« Rome », « Vienna », « Munich », et non
 * « Roma », « Wien », « München »). Mais **une seule** forme anglaise y est
 * canonique, alors que plusieurs ont cours. Les requêtes étant traduites en
 * anglais avant d'atteindre le parser, le traducteur — comme l'utilisateur — peut
 * sortir n'importe laquelle : sans cette table, « Bombay » ne trouve rien.
 *
 * Peuplée depuis l'asset (`tools/build_cities_db.py`), qui n'y met que les alias
 * **utiles** : ceux qui diffèrent du nom canonique une fois normalisés (3 670
 * lignes pour 69 473 villes — la plupart des villes n'ont qu'un nom).
 *
 * Table séparée plutôt qu'une colonne : un nom n'a pas un alias mais n, et les
 * concaténer imposerait de les re-découper à chaque recherche.
 */
@Entity(
    tableName = "city_aliases",
    primaryKeys = ["city_id", "alias"],
    indices = [Index(value = ["city_id"])],
    foreignKeys = [
        ForeignKey(
            entity = City::class,
            parentColumns = ["id"],
            childColumns = ["city_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class CityAlias(
    @ColumnInfo(name = "city_id")
    val cityId: Long,

    val alias: String
)
