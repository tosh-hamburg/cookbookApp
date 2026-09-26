package com.cookbook.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cookbook.app.R
import com.cookbook.app.data.models.AddDishCheck
import com.cookbook.app.data.models.MAX_SERVINGS
import com.cookbook.app.data.models.MIN_SERVINGS
import com.cookbook.app.data.models.MealType
import com.cookbook.app.data.models.Recipe
import com.cookbook.app.data.models.WeekPlan
import com.cookbook.app.data.models.canAddDish
import com.cookbook.app.data.models.dishesAt
import com.cookbook.app.data.repository.MealPlanRepository
import com.cookbook.app.databinding.DialogAddToWeekPlannerBinding
import com.cookbook.app.databinding.ItemDayMealSelectionBinding
import com.cookbook.app.util.ImageUtils
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch
import java.util.*

/**
 * Bottom sheet dialog for adding a recipe to the week planner
 */
class AddToWeekPlannerBottomSheet : BottomSheetDialogFragment() {
    
    companion object {
        private const val ARG_RECIPE_ID = "recipe_id"
        private const val ARG_RECIPE_TITLE = "recipe_title"
        private const val ARG_RECIPE_SERVINGS = "recipe_servings"
        private const val ARG_RECIPE_IMAGE = "recipe_image"
        
        fun newInstance(recipe: Recipe): AddToWeekPlannerBottomSheet {
            return AddToWeekPlannerBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_RECIPE_ID, recipe.id)
                    putString(ARG_RECIPE_TITLE, recipe.title)
                    putInt(ARG_RECIPE_SERVINGS, recipe.servings)
                    putString(ARG_RECIPE_IMAGE, recipe.firstImage)
                }
            }
        }
    }
    
    private var _binding: DialogAddToWeekPlannerBinding? = null
    private val binding get() = _binding!!
    
    private val mealPlanRepository by lazy { MealPlanRepository() }
    
    private var currentWeekStart: Date = WeekPlan.getCurrentWeekStart()
    private var weekPlan: WeekPlan = WeekPlan.createEmpty(currentWeekStart)
    private var isLoading: Boolean = false
    
    private var selectedDayIndex: Int? = null
    private var selectedMealType: MealType? = null
    
    private lateinit var recipeId: String
    private lateinit var recipeTitle: String
    private var recipeServings: Int = 2
    private var recipeImage: String? = null
    
    var onRecipeAdded: (() -> Unit)? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            recipeId = it.getString(ARG_RECIPE_ID) ?: ""
            recipeTitle = it.getString(ARG_RECIPE_TITLE) ?: ""
            recipeServings = it.getInt(ARG_RECIPE_SERVINGS, 2)
            recipeImage = it.getString(ARG_RECIPE_IMAGE)
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogAddToWeekPlannerBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecipeInfo()
        setupWeekNavigation()
        setupRecyclerView()
        setupAddButton()
        
        updateWeekDisplay()
    }
    
    override fun onStart() {
        super.onStart()
        // Set dialog width to 90% of screen width for better readability
        dialog?.window?.let { window ->
            val displayMetrics = resources.displayMetrics
            val width = (displayMetrics.widthPixels * 0.9).toInt()
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }
    
    private fun setupRecipeInfo() {
        binding.textRecipeTitle.text = recipeTitle
        
        val servingsText = if (recipeServings == 1) {
            "$recipeServings ${getString(R.string.portion_singular)}"
        } else {
            "$recipeServings ${getString(R.string.portion_plural)}"
        }
        binding.textRecipeServings.text = servingsText
        
        ImageUtils.loadImage(
            binding.imageRecipe,
            recipeImage,
            R.drawable.placeholder_recipe
        )
    }
    
    private fun setupWeekNavigation() {
        binding.btnPreviousWeek.setOnClickListener { navigateWeek(-7) }
        binding.btnNextWeek.setOnClickListener { navigateWeek(7) }
        binding.btnCurrentWeek.setOnClickListener {
            currentWeekStart = WeekPlan.getCurrentWeekStart()
            updateWeekDisplay()
        }
        binding.btnNextUpcomingWeek.setOnClickListener {
            currentWeekStart = WeekPlan.getNextWeekStart()
            updateWeekDisplay()
        }
    }
    
    private fun navigateWeek(days: Int) {
        val calendar = Calendar.getInstance().apply {
            time = currentWeekStart
            add(Calendar.DAY_OF_MONTH, days)
        }
        currentWeekStart = calendar.time
        updateWeekDisplay()
    }
    
    private fun setupRecyclerView() {
        binding.recyclerDayMealSlots.apply {
            layoutManager = GridLayoutManager(requireContext(), 2) // 2 columns for better readability
            adapter = DayMealAdapter()
        }
    }
    
    private fun setupAddButton() {
        binding.btnAddToSlot.setOnClickListener {
            addRecipeToSlot()
        }
    }
    
    private fun updateWeekDisplay() {
        // Update weekPlan with new currentWeekStart
        weekPlan = WeekPlan.createEmpty(currentWeekStart)
        updateWeekHeader()
        loadMealPlanData()
    }
    
    private fun updateWeekHeader() {
        binding.textWeekRange.text = weekPlan.getFormattedRange()
        binding.textCalendarWeek.text = getString(R.string.calendar_week, weekPlan.getWeekNumber())
        
        // Update button states
        val isCurrentWeek = isSameWeek(currentWeekStart, WeekPlan.getCurrentWeekStart())
        val isNextWeek = isSameWeek(currentWeekStart, WeekPlan.getNextWeekStart())
        
        binding.btnCurrentWeek.isEnabled = !isCurrentWeek
        binding.btnNextUpcomingWeek.isEnabled = !isNextWeek
    }
    
    private fun loadMealPlanData() {
        isLoading = true
        
        lifecycleScope.launch {
            try {
                val result = mealPlanRepository.getMealPlan(currentWeekStart)
                result.onSuccess { response ->
                    weekPlan = weekPlan.updateFromResponse(response)
                }.onFailure { _ ->
                    // Keep empty week plan on error
                    weekPlan = WeekPlan.createEmpty(currentWeekStart)
                }
            } catch (e: Exception) {
                weekPlan = WeekPlan.createEmpty(currentWeekStart)
            } finally {
                isLoading = false
                
                // Reset selection
                selectedDayIndex = null
                selectedMealType = null
                updateSelectedSlotDisplay()
                
                // Refresh adapter
                (binding.recyclerDayMealSlots.adapter as? DayMealAdapter)?.notifyDataSetChanged()
            }
        }
    }
    
    private fun isSameWeek(date1: Date, date2: Date): Boolean {
        val cal1 = Calendar.getInstance().apply { time = date1 }
        val cal2 = Calendar.getInstance().apply { time = date2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.WEEK_OF_YEAR) == cal2.get(Calendar.WEEK_OF_YEAR)
    }
    
    private fun updateSelectedSlotDisplay() {
        if (selectedDayIndex != null && selectedMealType != null) {
            binding.cardSelectedSlot.visibility = View.VISIBLE
            
            val dayName = weekPlan.days[selectedDayIndex!!].getDayName(requireContext())
            val mealName = selectedMealType!!.getLabel(requireContext())
            
            binding.textSelectedSlot.text = "$dayName - $mealName"
        } else {
            binding.cardSelectedSlot.visibility = View.GONE
        }
        
        (binding.recyclerDayMealSlots.adapter as? DayMealAdapter)?.notifyDataSetChanged()
    }
    
    /**
     * Rezept an den gewählten Slot anhängen. Der Slot darf schon belegt sein —
     * so kommt der Nachtisch zum Hauptgericht.
     */
    private fun addRecipeToSlot() {
        val dayIndex = selectedDayIndex ?: return
        val mealType = selectedMealType ?: return

        lifecycleScope.launch {
            try {
                val result = mealPlanRepository.appendDishToSlot(
                    weekStart = currentWeekStart,
                    dayIndex = dayIndex,
                    mealType = mealType,
                    recipeId = recipeId,
                    servings = recipeServings.coerceIn(MIN_SERVINGS, MAX_SERVINGS)
                )
                
                result.onSuccess {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.recipe_added_to_planner),
                        Toast.LENGTH_SHORT
                    ).show()
                    onRecipeAdded?.invoke()
                    dismiss()
                }.onFailure {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.error_adding_to_planner),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.error_adding_to_planner),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    // Adapter for day/meal grid
    private inner class DayMealAdapter : RecyclerView.Adapter<DayMealAdapter.ViewHolder>() {
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemDayMealSelectionBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return ViewHolder(binding)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(position)
        }
        
        override fun getItemCount(): Int = 7
        
        inner class ViewHolder(
            private val binding: ItemDayMealSelectionBinding
        ) : RecyclerView.ViewHolder(binding.root) {
            
            fun bind(dayIndex: Int) {
                val day = weekPlan.days[dayIndex]

                binding.textDayName.text = day.getDayName(itemView.context)
                binding.textDayDate.text = day.getFormattedDate()

                bindMealSlot(
                    dayIndex, MealType.BREAKFAST,
                    binding.slotBreakfast, binding.textBreakfast,
                    binding.textBreakfastCount, binding.checkBreakfast
                )
                bindMealSlot(
                    dayIndex, MealType.LUNCH,
                    binding.slotLunch, binding.textLunch,
                    binding.textLunchCount, binding.checkLunch
                )
                bindMealSlot(
                    dayIndex, MealType.DINNER,
                    binding.slotDinner, binding.textDinner,
                    binding.textDinnerCount, binding.checkDinner
                )
            }

            /**
             * Ein belegter Slot bleibt wählbar — das Rezept wird angehängt. Nur
             * wenn es dort schon liegt oder der Slot voll ist, geht es nicht.
             */
            private fun bindMealSlot(
                dayIndex: Int,
                mealType: MealType,
                slotView: View,
                labelView: android.widget.TextView,
                countView: android.widget.TextView,
                checkView: View
            ) {
                val dishCount = weekPlan.dishesAt(dayIndex, mealType).size
                val isBlocked = weekPlan.canAddDish(dayIndex, mealType, recipeId) != AddDishCheck.OK

                countView.visibility = if (dishCount > 0) View.VISIBLE else View.GONE
                countView.text = dishCount.toString()

                slotView.isEnabled = !isBlocked
                slotView.alpha = if (isBlocked) 0.5f else 1.0f
                slotView.setOnClickListener(
                    if (isBlocked) null else View.OnClickListener { onSlotClick(dayIndex, mealType) }
                )
                labelView.paintFlags = if (isBlocked) {
                    labelView.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                } else {
                    labelView.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
                }

                val isSelected = selectedDayIndex == dayIndex && selectedMealType == mealType
                checkView.visibility = if (isSelected && !isBlocked) View.VISIBLE else View.GONE
            }

            private fun onSlotClick(dayIndex: Int, mealType: MealType) {
                selectedDayIndex = dayIndex
                selectedMealType = mealType
                updateSelectedSlotDisplay()
            }
        }
    }
}
