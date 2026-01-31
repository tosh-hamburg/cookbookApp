package com.cookbook.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cookbook.app.R
import com.cookbook.app.databinding.BottomSheetFilterSelectionBinding
import com.cookbook.app.databinding.ItemFilterCheckboxBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Generic bottom sheet for multi-select filter selection.
 * Can be used for collections, categories, etc.
 */
class FilterSelectionBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetFilterSelectionBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: FilterItemAdapter

    private var title: String = ""
    private var emptyIcon: String = "📚"
    private var emptyMessage: String = ""
    private var items: List<FilterItem> = emptyList()
    private var selectedIds: MutableSet<String> = mutableSetOf()
    private var showCounts: Boolean = false
    
    // Callback when selection is applied
    var onSelectionApplied: ((Set<String>) -> Unit)? = null

    data class FilterItem(
        val id: String,
        val name: String,
        val count: Int? = null
    )

    companion object {
        fun newInstance(
            title: String,
            items: List<FilterItem>,
            selectedIds: Set<String>,
            emptyIcon: String = "📚",
            emptyMessage: String = "",
            showCounts: Boolean = false
        ): FilterSelectionBottomSheet {
            return FilterSelectionBottomSheet().apply {
                this.title = title
                this.items = items
                this.selectedIds = selectedIds.toMutableSet()
                this.emptyIcon = emptyIcon
                this.emptyMessage = emptyMessage
                this.showCounts = showCounts
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetFilterSelectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.tvTitle.text = title
        binding.tvEmptyIcon.text = emptyIcon
        binding.tvEmptyMessage.text = emptyMessage.ifEmpty { getString(R.string.no_collections) }
        
        setupRecyclerView()
        setupButtons()
        updateUI()
    }

    private fun setupRecyclerView() {
        adapter = FilterItemAdapter(
            items = items,
            selectedIds = selectedIds,
            showCounts = showCounts
        ) { item, isSelected ->
            if (isSelected) {
                selectedIds.add(item.id)
            } else {
                selectedIds.remove(item.id)
            }
        }
        
        binding.recyclerViewItems.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@FilterSelectionBottomSheet.adapter
        }
    }

    private fun setupButtons() {
        binding.btnSelectAll.setOnClickListener {
            selectedIds.clear()
            selectedIds.addAll(items.map { it.id })
            adapter.updateSelection(selectedIds)
        }
        
        binding.btnDeselectAll.setOnClickListener {
            selectedIds.clear()
            adapter.updateSelection(selectedIds)
        }
        
        binding.btnApply.setOnClickListener {
            onSelectionApplied?.invoke(selectedIds.toSet())
            dismiss()
        }
    }

    private fun updateUI() {
        if (items.isEmpty()) {
            binding.emptyStateContainer.visibility = View.VISIBLE
            binding.recyclerViewItems.visibility = View.GONE
            binding.btnSelectAll.visibility = View.GONE
            binding.btnDeselectAll.visibility = View.GONE
        } else {
            binding.emptyStateContainer.visibility = View.GONE
            binding.recyclerViewItems.visibility = View.VISIBLE
            binding.btnSelectAll.visibility = View.VISIBLE
            binding.btnDeselectAll.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * Adapter for filter items with checkboxes
     */
    private class FilterItemAdapter(
        private val items: List<FilterItem>,
        private var selectedIds: MutableSet<String>,
        private val showCounts: Boolean,
        private val onItemToggled: (FilterItem, Boolean) -> Unit
    ) : RecyclerView.Adapter<FilterItemAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemFilterCheckboxBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemFilterCheckboxBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            val isSelected = selectedIds.contains(item.id)
            
            holder.binding.apply {
                tvItemName.text = item.name
                checkbox.isChecked = isSelected
                
                if (showCounts && item.count != null) {
                    tvItemCount.visibility = View.VISIBLE
                    tvItemCount.text = root.context.getString(R.string.recipes_count, item.count)
                } else {
                    tvItemCount.visibility = View.GONE
                }
                
                root.setOnClickListener {
                    val newState = !checkbox.isChecked
                    checkbox.isChecked = newState
                    onItemToggled(item, newState)
                }
            }
        }

        override fun getItemCount(): Int = items.size

        fun updateSelection(newSelectedIds: Set<String>) {
            selectedIds.clear()
            selectedIds.addAll(newSelectedIds)
            notifyDataSetChanged()
        }
    }
}
