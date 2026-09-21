package com.cookbook.app.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.cookbook.app.R
import com.cookbook.app.data.repository.RecipeRepository
import com.cookbook.app.databinding.BottomSheetAddRecipeBinding
import com.cookbook.app.databinding.ItemAddRecipeOptionBinding
import com.cookbook.app.util.CollectionColors
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

/**
 * "Rezept hinzufügen" — ersetzt den direkten Sprung in die Import-Activity.
 *
 * Der Import per Link steht zuerst, weil er der häufigste Fall ist; Fortschritt
 * und Fehler bleiben im Sheet, damit der Nutzer den Link nicht neu eintippen muss.
 */
class AddRecipeBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetAddRecipeBinding? = null
    private val binding get() = _binding!!

    private val recipeRepository by lazy { RecipeRepository() }
    private var isImporting = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetAddRecipeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupOptions()
        setupLinkField()
        prefillFromClipboard()
    }

    private fun setupLinkField() {
        binding.btnSubmitLink.setOnClickListener { startImport() }
        binding.editLink.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                startImport(); true
            } else {
                false
            }
        }
        binding.editLink.doAfterTextChanged {
            if (!isImporting) binding.textHint.visibility = View.GONE
        }
    }

    /** Liegt eine URL in der Zwischenablage, ist sie vorbelegt. */
    private fun prefillFromClipboard() {
        val clipboard = context?.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clipText = clipboard?.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(requireContext())
            ?.toString()
            ?.trim()
            .orEmpty()

        if (clipText.isEmpty() || !isValidUrl(clipText)) return

        binding.editLink.setText(clipText)
        binding.editLink.setSelection(clipText.length)
        showHint(getString(R.string.from_clipboard), R.color.text_hint)
    }

    private fun setupOptions() {
        bindOption(
            ItemAddRecipeOptionBinding.bind(binding.optionPhoto.root),
            R.string.option_photo_title,
            R.string.option_photo_subtitle,
            R.drawable.ic_camera,
            ContextCompat.getColor(requireContext(), R.color.secondary)
        ) { openEditor(RecipeEditActivity.PICKER_CAMERA) }

        bindOption(
            ItemAddRecipeOptionBinding.bind(binding.optionGallery.root),
            R.string.option_gallery_title,
            R.string.option_gallery_subtitle,
            R.drawable.ic_gallery,
            ContextCompat.getColor(requireContext(), R.color.coll_nudeln)
        ) { openEditor(RecipeEditActivity.PICKER_GALLERY) }

        bindOption(
            ItemAddRecipeOptionBinding.bind(binding.optionManual.root),
            R.string.option_manual_title,
            R.string.option_manual_subtitle,
            R.drawable.ic_edit,
            ContextCompat.getColor(requireContext(), R.color.primary)
        ) { openEditor(null) }
    }

    private fun bindOption(
        option: ItemAddRecipeOptionBinding,
        titleRes: Int,
        subtitleRes: Int,
        @DrawableRes iconRes: Int,
        @ColorInt color: Int,
        onClick: () -> Unit
    ) {
        option.optionTitle.setText(titleRes)
        option.optionSubtitle.setText(subtitleRes)
        option.optionIcon.setImageResource(iconRes)
        option.optionIcon.imageTintList = ColorStateList.valueOf(color)
        // Die Farbe trägt nur das Tile, und dort mit geringer Deckung.
        option.optionTile.backgroundTintList = ColorStateList.valueOf(CollectionColors.tint(color))
        option.optionRoot.setOnClickListener { onClick() }
    }

    // ==================== Import ====================

    private fun startImport() {
        if (isImporting) return

        val url = binding.editLink.text.toString().trim()
        if (!isValidUrl(url)) {
            showHint(getString(R.string.invalid_link), R.color.error)
            return
        }

        setImporting(true)
        showHint(getString(R.string.import_running), R.color.text_hint)

        lifecycleScope.launch {
            recipeRepository.importRecipe(url)
                .onSuccess { imported ->
                    if (_binding == null) return@onSuccess
                    setImporting(false)
                    val intent = Intent(requireContext(), RecipeEditActivity::class.java)
                    intent.putExtra(RecipeEditActivity.EXTRA_IMPORTED_DATA, imported)
                    startActivity(intent)
                    dismiss()
                }
                .onFailure {
                    if (_binding == null) return@onFailure
                    setImporting(false)
                    // Fehler als Zeile im Sheet — der Link bleibt stehen.
                    showHint(getString(R.string.import_failed_hint), R.color.error)
                }
        }
    }

    private fun setImporting(importing: Boolean) {
        isImporting = importing
        binding.progressImport.visibility = if (importing) View.VISIBLE else View.GONE
        binding.btnSubmitLink.isEnabled = !importing
        binding.editLink.isEnabled = !importing
    }

    private fun showHint(text: String, colorRes: Int) {
        binding.textHint.text = text
        binding.textHint.setTextColor(ContextCompat.getColor(requireContext(), colorRes))
        binding.textHint.visibility = View.VISIBLE
    }

    private fun isValidUrl(value: String): Boolean =
        value.startsWith("http", ignoreCase = true) && Patterns.WEB_URL.matcher(value).matches()

    private fun openEditor(picker: String?) {
        val intent = Intent(requireContext(), RecipeEditActivity::class.java)
        picker?.let { intent.putExtra(RecipeEditActivity.EXTRA_LAUNCH_PICKER, it) }
        startActivity(intent)
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
