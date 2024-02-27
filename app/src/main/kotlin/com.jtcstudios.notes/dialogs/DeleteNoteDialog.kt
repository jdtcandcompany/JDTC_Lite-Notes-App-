package com.simplemobiletools.notes.pro.dialogs

import androidx.appcompat.app.AlertDialog
import com.simplemobiletools.commons.extensions.beVisible
import com.simplemobiletools.commons.extensions.getAlertDialogBuilder
import com.simplemobiletools.commons.extensions.setupDialogStuff
import com.simplemobiletools.notes.pro.R
import com.simplemobiletools.notes.pro.activities.SimpleActivity
import com.simplemobiletools.notes.pro.models.Note
import kotlinx.android.synthetic.main.dialog_delete_note.view.*

class DeleteNoteDialog(val activity: SimpleActivity, val note: Note, val callback: (deleteFile: Boolean) -> Unit) {
    var dialog: AlertDialog? = null

    init {
        val message = String.format(activity.getString(R.string.delete_note_prompt_message), note.title)
        val view = activity.layoutInflater.inflate(R.layout.dialog_delete_note, null).apply {
            if (note.path.isNotEmpty()) {
                delete_note_checkbox.text = String.format(activity.getString(R.string.delete_file_itself), note.path)
                delete_note_checkbox_holder.beVisible()
                delete_note_checkbox_holder.setOnClickListener {
                    delete_note_checkbox.toggle()
                }
            }
            delete_note_description.text = message
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(R.string.ok) { dialog, which -> dialogConfirmed(view.delete_note_checkbox.isChecked) }
            .setNegativeButton(R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(view, this)
            }
    }

    private fun dialogConfirmed(deleteFile: Boolean) {
        callback(deleteFile && note.path.isNotEmpty())
        dialog?.dismiss()
    }
}
