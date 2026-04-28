package com.test.blabify.presentation.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.os.bundleOf
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.test.blabify.R
import com.test.blabify.domain.usecases.ResortSuggestionV2
import com.test.blabify.presentation.adapters.ResortSuggestionAdapter

class ResortBottomSheet : BottomSheetDialogFragment() {

    companion object {
        private const val KEY_ITEMS = "items"
        const val RESULT_KEY = "resort_sheet_result"
        const val RESULT_ITEMS = "accepted_items"

        fun newInstance(items: ArrayList<ResortSuggestionV2>): ResortBottomSheet =
            ResortBottomSheet().apply {
                arguments = bundleOf(KEY_ITEMS to items)
            }
    }
    private lateinit var adapter: ResortSuggestionAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.bottom_sheet_resort, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val list = (arguments?.getSerializable(KEY_ITEMS) as? ArrayList<*>)
            ?.filterIsInstance<ResortSuggestionV2>()
            ?: emptyList()

        val btnToggleSelectAll = view.findViewById<TextView>(R.id.btnToggleSelectAll)
        val btnAgree = view.findViewById<TextView>(R.id.btnAgree)

        adapter = ResortSuggestionAdapter(list) { selectedCount, totalCount ->
            btnAgree.isEnabled = selectedCount > 0
            btnAgree.alpha = if (selectedCount > 0) 1f else 0.5f

            btnToggleSelectAll.text =
                if (selectedCount == totalCount && totalCount > 0) {
                    "Убрать выделение"
                } else {
                    "Выделить все"
                }
        }

        //выделить все / убрать все
        view.findViewById<RecyclerView>(R.id.rvSuggestions).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@ResortBottomSheet.adapter
        }
        btnToggleSelectAll.setOnClickListener {
            if (adapter.areAllSelected()) {
                adapter.clearSelection()
            } else {
                adapter.selectAll()
            }
        }

        view.findViewById<TextView>(R.id.btnCancel).setOnClickListener { dismiss() }

        btnAgree.setOnClickListener {
            val selected = adapter.getSelectedItems()

            parentFragmentManager.setFragmentResult(
                RESULT_KEY,
                bundleOf(RESULT_ITEMS to ArrayList(selected))
            )

            dismiss()
        }
        btnToggleSelectAll.text = "Убрать выделение"
        btnAgree.isEnabled = list.isNotEmpty()
        btnAgree.alpha = if (list.isNotEmpty()) 1f else 0.5f
    }
}