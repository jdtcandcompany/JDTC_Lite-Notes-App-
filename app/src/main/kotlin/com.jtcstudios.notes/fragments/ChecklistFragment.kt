package com.simplemobiletools.notes.pro.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.SORT_BY_CUSTOM
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.notes.pro.R
import com.simplemobiletools.notes.pro.activities.SimpleActivity
import com.simplemobiletools.notes.pro.adapters.ChecklistAdapter
import com.simplemobiletools.notes.pro.dialogs.NewChecklistItemDialog
import com.simplemobiletools.notes.pro.extensions.config
import com.simplemobiletools.notes.pro.extensions.updateWidgets
import com.simplemobiletools.notes.pro.helpers.NOTE_ID
import com.simplemobiletools.notes.pro.helpers.NotesHelper
import com.simplemobiletools.notes.pro.interfaces.ChecklistItemsListener
import com.simplemobiletools.notes.pro.models.ChecklistItem
import com.simplemobiletools.notes.pro.models.Note
import kotlinx.android.synthetic.main.fragment_checklist.view.*
import java.io.File

class ChecklistFragment : NoteFragment(), ChecklistItemsListener {

    private var noteId = 0L

    lateinit var view: ViewGroup

    var items = ArrayList<ChecklistItem>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        view = inflater.inflate(R.layout.fragment_checklist, container, false) as ViewGroup
        noteId = requireArguments().getLong(NOTE_ID, 0L)
        return view
    }

    override fun onResume() {
        super.onResume()
        loadNoteById(noteId)
    }

    override fun setMenuVisibility(menuVisible: Boolean) {
        super.setMenuVisibility(menuVisible)

        if (menuVisible) {
            activity?.hideKeyboard()
        }
    }

    private fun loadNoteById(noteId: Long) {
        NotesHelper(requireActivity()).getNoteWithId(noteId) { storedNote ->
            if (storedNote != null && activity?.isDestroyed == false) {
                note = storedNote

                try {
                    val checklistItemType = object : TypeToken<List<ChecklistItem>>() {}.type
                    items = Gson().fromJson<ArrayList<ChecklistItem>>(storedNote.getNoteStoredValue(requireActivity()), checklistItemType) ?: ArrayList(1)

                    // checklist title can be null only because of the glitch in upgrade to 6.6.0, remove this check in the future
                    items = items.filter { it.title != null }.toMutableList() as ArrayList<ChecklistItem>
                    val sorting = config?.sorting ?: 0
                    if (sorting and SORT_BY_CUSTOM == 0 && config?.moveDoneChecklistItems == true) {
                        items.sortBy { it.isDone }
                    }

                    setupFragment()
                } catch (e: Exception) {
                    migrateCheckListOnFailure(storedNote)
                }
            }
        }
    }

    private fun migrateCheckListOnFailure(note: Note) {
        items.clear()

        note.getNoteStoredValue(requireActivity())?.split("\n")?.map { it.trim() }?.filter { it.isNotBlank() }?.forEachIndexed { index, value ->
            items.add(
                ChecklistItem(
                    id = index,
                    title = value,
                    isDone = false
                )
            )
        }

        saveChecklist()
    }

    private fun setupFragment() {
        if (activity == null || requireActivity().isFinishing) {
            return
        }

        val adjustedPrimaryColor = requireActivity().getProperPrimaryColor()
        view.checklist_fab.apply {
            setColors(
                requireActivity().getProperTextColor(),
                adjustedPrimaryColor,
                adjustedPrimaryColor.getContrastColor()
            )

            setOnClickListener {
                showNewItemDialog()
                (view.checklist_list.adapter as? ChecklistAdapter)?.finishActMode()
            }
        }

        view.fragment_placeholder.setTextColor(requireActivity().getProperTextColor())
        view.fragment_placeholder_2.apply {
            setTextColor(adjustedPrimaryColor)
            underlineText()
            setOnClickListener {
                showNewItemDialog()
            }
        }

        checkLockState()
        setupAdapter()
    }

    override fun checkLockState() {
        if (note == null) {
            return
        }

        view.apply {
            checklist_content_holder.beVisibleIf(!note!!.isLocked() || shouldShowLockedContent)
            checklist_fab.beVisibleIf(!note!!.isLocked() || shouldShowLockedContent)
            setupLockedViews(this, note!!)
        }
    }

    private fun showNewItemDialog() {
        NewChecklistItemDialog(activity as SimpleActivity) { titles ->
            var currentMaxId = items.maxByOrNull { item -> item.id }?.id ?: 0
            val newItems = ArrayList<ChecklistItem>()

            titles.forEach { title ->
                title.split("\n").map { it.trim() }.filter { it.isNotBlank() }.forEach { row ->
                    newItems.add(ChecklistItem(currentMaxId + 1, System.currentTimeMillis(), row, false))
                    currentMaxId++
                }
            }

            items.addAll(newItems)
            saveNote()
            setupAdapter()
        }
    }

    private fun setupAdapter() {
        updateUIVisibility()
        ChecklistItem.sorting = requireContext().config.sorting
        if (ChecklistItem.sorting and SORT_BY_CUSTOM == 0) {
            items.sort()
            if (context?.config?.moveDoneChecklistItems == true) {
                items.sortBy { it.isDone }
            }
        }
        ChecklistAdapter(
            activity = activity as SimpleActivity,
            items = items,
            listener = this,
            recyclerView = view.checklist_list,
            showIcons = true
        ) { item ->
            val clickedNote = item as ChecklistItem
            clickedNote.isDone = !clickedNote.isDone

            saveNote(items.indexOfFirst { it.id == clickedNote.id })
            context?.updateWidgets()
        }.apply {
            view.checklist_list.adapter = this
        }
    }

    private fun saveNote(refreshIndex: Int = -1) {
        if (note == null) {
            return
        }

        if (note!!.path.isNotEmpty() && !note!!.path.startsWith("content://") && !File(note!!.path).exists()) {
            return
        }

        if (context == null || activity == null) {
            return
        }

        if (note != null) {
            if (refreshIndex != -1) {
                view.checklist_list.post {
                    view.checklist_list.adapter?.notifyItemChanged(refreshIndex)
                }
            }

            note!!.value = getChecklistItems()

            ensureBackgroundThread {
                saveNoteValue(note!!, note!!.value)
                context?.updateWidgets()
            }
        }
    }

    fun removeDoneItems() {
        items = items.filter { !it.isDone }.toMutableList() as ArrayList<ChecklistItem>
        saveNote()
        setupAdapter()
    }

    private fun updateUIVisibility() {
        view.apply {
            fragment_placeholder.beVisibleIf(items.isEmpty())
            fragment_placeholder_2.beVisibleIf(items.isEmpty())
            checklist_list.beVisibleIf(items.isNotEmpty())
        }
    }

    fun getChecklistItems() = Gson().toJson(items)

    override fun saveChecklist() {
        saveNote()
    }

    override fun refreshItems() {
        loadNoteById(noteId)
        setupAdapter()
    }
}
