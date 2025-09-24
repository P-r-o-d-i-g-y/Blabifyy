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
import com.test.blabify.domain.usecases.ResortSuggestion
import com.test.blabify.presentation.adapters.ResortSuggestionAdapter

class ResortBottomSheet : BottomSheetDialogFragment() {

    companion object {
        private const val KEY_ITEMS = "items"

        fun newInstance(items: ArrayList<ResortSuggestion>): ResortBottomSheet =
            ResortBottomSheet().apply {
                arguments = bundleOf(KEY_ITEMS to items)
            }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.bottom_sheet_resort, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val list = (arguments?.getSerializable(KEY_ITEMS) as? ArrayList<*>)?.filterIsInstance<ResortSuggestion>()
            ?: emptyList()

        view.findViewById<RecyclerView>(R.id.rvSuggestions).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = ResortSuggestionAdapter(list)
        }

        view.findViewById<TextView>(R.id.btnCancel).setOnClickListener { dismiss() }

        view.findViewById<TextView>(R.id.btnAgree).setOnClickListener {
            // пока просто тост (ты потом заменишь на реальное применение)
            android.widget.Toast.makeText(requireContext(), "Ок, применить (заглушка)", android.widget.Toast.LENGTH_SHORT).show()
            dismiss()
        }
    }
}