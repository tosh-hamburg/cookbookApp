package com.cookbook.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.cookbook.app.R
import com.cookbook.app.data.models.MealType
import com.cookbook.app.databinding.BottomSheetMealSlotBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Slot-Detail im Wochenplaner.
 *
 * Die Portions-Steuerung sitzt hier und nicht in der Slot-Zeile — dadurch bleibt
 * die Tageskarte ruhig und scanbar.
 */
class MealSlotBottomSheet : BottomSheetDialogFragment() {

    companion object {
        private const val MIN_SERVINGS = 1
        private const val MAX_SERVINGS = 12

        fun newInstance(
            dayName: String,
            mealType: MealType,
            recipeTitle: String,
            servings: Int,
            onServingsChanged: (Int) -> Unit,
            onOpenRecipe: () -> Unit,
            onRemove: () -> Unit
        ): MealSlotBottomSheet = MealSlotBottomSheet().apply {
            this.dayName = dayName
            this.mealType = mealType
            this.recipeTitle = recipeTitle
            this.servings = servings
            this.onServingsChanged = onServingsChanged
            this.onOpenRecipe = onOpenRecipe
            this.onRemove = onRemove
        }
    }

    private var _binding: BottomSheetMealSlotBinding? = null
    private val binding get() = _binding!!

    private var dayName: String = ""
    private var mealType: MealType = MealType.DINNER
    private var recipeTitle: String = ""
    private var servings: Int = 2
    private var onServingsChanged: ((Int) -> Unit)? = null
    private var onOpenRecipe: (() -> Unit)? = null
    private var onRemove: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetMealSlotBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.textSlotLabel.text = getString(
            R.string.slot_label_format, dayName, mealType.getLabel(requireContext())
        )
        binding.textSlotTitle.text = recipeTitle
        renderServings()

        binding.btnDecreaseServings.setOnClickListener { changeServings(-1) }
        binding.btnIncreaseServings.setOnClickListener { changeServings(1) }
        binding.btnOpenRecipe.setOnClickListener { onOpenRecipe?.invoke(); dismiss() }
        binding.btnRemoveSlot.setOnClickListener { onRemove?.invoke(); dismiss() }
    }

    private fun changeServings(delta: Int) {
        val next = (servings + delta).coerceIn(MIN_SERVINGS, MAX_SERVINGS)
        if (next == servings) return
        servings = next
        renderServings()
        onServingsChanged?.invoke(servings)
    }

    private fun renderServings() {
        binding.textServings.text = servings.toString()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
