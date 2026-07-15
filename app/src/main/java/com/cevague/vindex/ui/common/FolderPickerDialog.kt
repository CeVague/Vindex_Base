package com.cevague.vindex.ui.common

import android.content.Context
import android.widget.Toast
import com.cevague.vindex.R
import com.cevague.vindex.util.MediaScanner
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Sélecteur multi-choix des dossiers à indexer, partagé par l'accueil (premier
 * lancement) et les Paramètres (édition). [preChecked] = dossiers déjà cochés ;
 * [onConfirm] reçoit l'ensemble validé (jamais vide — au moins un exigé).
 */
object FolderPickerDialog {

    fun show(
        context: Context,
        folders: List<MediaScanner.FolderInfo>,
        preChecked: Set<String>,
        onConfirm: (Set<String>) -> Unit
    ) {
        val labels = folders.map {
            context.getString(R.string.welcome_folder_item, it.relativePath, it.photoCount)
        }.toTypedArray()
        val checked = BooleanArray(folders.size) { folders[it].relativePath in preChecked }
        val selected = folders.filterIndexed { i, _ -> checked[i] }
            .map { it.relativePath }
            .toMutableSet()

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.welcome_select_folders_title)
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                val path = folders[which].relativePath
                if (isChecked) selected.add(path) else selected.remove(path)
            }
            .setPositiveButton(R.string.welcome_validate) { _, _ ->
                if (selected.isEmpty()) {
                    Toast.makeText(
                        context,
                        R.string.welcome_select_at_least_one,
                        Toast.LENGTH_SHORT
                    )
                        .show()
                } else {
                    onConfirm(selected)
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }
}
