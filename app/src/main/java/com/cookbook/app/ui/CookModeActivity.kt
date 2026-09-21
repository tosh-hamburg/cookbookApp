package com.cookbook.app.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.cookbook.app.R
import com.cookbook.app.data.models.Recipe
import com.cookbook.app.data.repository.RecipeRepository
import com.cookbook.app.databinding.ActivityCookModeBinding
import com.cookbook.app.ui.adapter.CookStepAdapter
import com.cookbook.app.util.Amounts
import com.cookbook.app.util.RecipeSteps
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Kochmodus — am Herd kochen: Vollbild, dunkel, ein Schritt pro Seite.
 *
 * Der Bildschirm bleibt an, solange der Modus offen ist. "Fertig" schließt ihn
 * und erhöht den Kochzähler des Rezepts, der wiederum das Rezept der Woche trägt.
 */
class CookModeActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_RECIPE_ID = "extra_recipe_id"
        const val EXTRA_SERVINGS = "extra_servings"

        private const val TIMER_CHANNEL_ID = "cook_timer"
        private const val TIMER_NOTIFICATION_ID = 4711
        private const val TICK_MS = 1000L
    }

    private lateinit var binding: ActivityCookModeBinding
    private val recipeRepository by lazy { RecipeRepository() }

    private var recipe: Recipe? = null
    private var steps: List<RecipeSteps.Step> = emptyList()
    private var servings: Int = 4

    private var timer: CountDownTimer? = null
    private var remainingMillis: Long = 0
    private var isTimerRunning = false
    private var isInForeground = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCookModeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Am Herd hat niemand freie Hände für den Bildschirm.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        createTimerChannel()

        val recipeId = intent.getStringExtra(EXTRA_RECIPE_ID)
        if (recipeId.isNullOrBlank()) {
            finish()
            return
        }
        servings = intent.getIntExtra(EXTRA_SERVINGS, 0).takeIf { it > 0 } ?: servings

        binding.btnClose.setOnClickListener { confirmExit() }
        binding.btnPrevious.setOnClickListener { goToStep(currentStep() - 1) }
        binding.btnNext.setOnClickListener { onNextClicked() }
        binding.btnTimer.setOnClickListener { toggleTimer() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = confirmExit()
        })

        loadRecipe(recipeId)
    }

    override fun onResume() {
        super.onResume()
        isInForeground = true
    }

    override fun onPause() {
        super.onPause()
        isInForeground = false
    }

    override fun onDestroy() {
        timer?.cancel()
        super.onDestroy()
    }

    private fun loadRecipe(recipeId: String) {
        lifecycleScope.launch {
            recipeRepository.getRecipe(recipeId)
                .onSuccess { loaded ->
                    recipe = loaded
                    if (intent.getIntExtra(EXTRA_SERVINGS, 0) <= 0) {
                        servings = loaded.servings.takeIf { it > 0 } ?: servings
                    }
                    steps = RecipeSteps.parse(loaded.instructions)
                    bindRecipe(loaded)
                }
                .onFailure {
                    Toast.makeText(this@CookModeActivity, it.message, Toast.LENGTH_LONG).show()
                    finish()
                }
        }
    }

    private fun bindRecipe(loaded: Recipe) {
        binding.textRecipeTitle.text = loaded.title
        binding.textServings.text = getString(R.string.servings_count, servings)

        if (steps.isEmpty()) {
            binding.viewPagerSteps.visibility = View.GONE
            binding.textNoSteps.visibility = View.VISIBLE
            binding.timerBlock.visibility = View.GONE
            binding.progressRow.visibility = View.GONE
            binding.textStepCounter.visibility = View.GONE
            binding.btnPrevious.visibility = View.GONE
            binding.btnNext.setText(R.string.cook_finish)
            return
        }

        binding.viewPagerSteps.adapter = CookStepAdapter(
            steps = steps,
            ingredients = loaded.ingredients,
            servingsFactorProvider = { Amounts.factor(servings, loaded.servings) }
        )
        binding.viewPagerSteps.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) = onStepChanged(position)
            }
        )

        buildProgressSegments()
        onStepChanged(0)
    }

    // ==================== Fortschritt ====================

    /** Ein 4dp-Balken pro Schritt, tippbar. */
    private fun buildProgressSegments() {
        binding.progressRow.removeAllViews()
        val gap = (6 * resources.displayMetrics.density).toInt()

        steps.indices.forEach { index ->
            val segment = View(this)
            val params = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            if (index > 0) params.marginStart = gap
            segment.layoutParams = params
            segment.setBackgroundResource(R.drawable.bg_progress_open)
            segment.setOnClickListener { goToStep(index) }
            binding.progressRow.addView(segment)
        }
    }

    private fun updateProgressSegments(current: Int) {
        binding.progressRow.children().forEachIndexed { index, view ->
            view.setBackgroundResource(
                if (index <= current) R.drawable.bg_progress_done else R.drawable.bg_progress_open
            )
        }
    }

    private fun android.widget.LinearLayout.children(): List<View> =
        (0 until childCount).map { getChildAt(it) }

    private fun currentStep(): Int = binding.viewPagerSteps.currentItem

    private fun goToStep(index: Int) {
        if (index < 0 || index >= steps.size) return
        binding.viewPagerSteps.currentItem = index
    }

    private fun onStepChanged(position: Int) {
        if (steps.isEmpty()) return

        updateProgressSegments(position)
        binding.textStepCounter.text =
            getString(R.string.step_of, position + 1, steps.size)
        binding.btnPrevious.isEnabled = position > 0
        binding.btnPrevious.alpha = if (position > 0) 1f else 0.4f

        val isLast = position == steps.size - 1
        binding.btnNext.setText(if (isLast) R.string.cook_finish else R.string.cook_next)

        // Der Timer wird mit der Zeit dieses Schritts vorbelegt — außer er läuft.
        val stepMinutes = steps[position].minutes
        binding.timerBlock.visibility = if (stepMinutes == null && !isTimerRunning) View.GONE else View.VISIBLE
        if (!isTimerRunning) {
            remainingMillis = (stepMinutes ?: 0) * 60_000L
            renderTimer()
        }
    }

    private fun onNextClicked() {
        val isLast = steps.isEmpty() || currentStep() == steps.size - 1
        if (isLast) finishCooking() else goToStep(currentStep() + 1)
    }

    // ==================== Timer ====================

    private fun toggleTimer() {
        if (isTimerRunning) {
            stopTimer()
        } else {
            if (remainingMillis <= 0) return
            startTimer()
        }
    }

    private fun startTimer() {
        isTimerRunning = true
        binding.btnTimer.setText(R.string.stop_timer)
        timer?.cancel()
        timer = object : CountDownTimer(remainingMillis, TICK_MS) {
            override fun onTick(millisUntilFinished: Long) {
                remainingMillis = millisUntilFinished
                renderTimer()
            }

            override fun onFinish() {
                remainingMillis = 0
                isTimerRunning = false
                renderTimer()
                binding.btnTimer.setText(R.string.start_timer)
                announceTimerFinished()
            }
        }.start()
    }

    private fun stopTimer() {
        isTimerRunning = false
        timer?.cancel()
        binding.btnTimer.setText(R.string.start_timer)
    }

    private fun renderTimer() {
        val totalSeconds = (remainingMillis / 1000).toInt()
        binding.textTimer.text = String.format(
            Locale.GERMANY, "%02d:%02d", totalSeconds / 60, totalSeconds % 60
        )
        binding.btnTimer.isEnabled = remainingMillis > 0 || isTimerRunning
    }

    /** Ton und Vibration; im Hintergrund zusätzlich eine Benachrichtigung. */
    private fun announceTimerFinished() {
        runCatching {
            RingtoneManager
                .getRingtone(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                ?.play()
        }
        vibrate()

        if (isInForeground) {
            Toast.makeText(this, R.string.timer_finished, Toast.LENGTH_LONG).show()
        } else {
            postTimerNotification()
        }
    }

    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        vibrator?.vibrate(VibrationEffect.createOneShot(600, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun createTimerChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            TIMER_CHANNEL_ID,
            getString(R.string.cook_mode),
            NotificationManager.IMPORTANCE_HIGH
        )
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun postTimerNotification() {
        val manager = NotificationManagerCompat.from(this)
        if (!manager.areNotificationsEnabled()) return

        val notification = NotificationCompat.Builder(this, TIMER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_cook_time)
            .setContentTitle(getString(R.string.timer_finished))
            .setContentText(recipe?.title.orEmpty())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        runCatching { manager.notify(TIMER_NOTIFICATION_ID, notification) }
    }

    // ==================== Abschluss ====================

    /**
     * "Fertig" zählt den Kochvorgang auf dem Server. Scheitert das, wird der
     * Modus trotzdem geschlossen — der Nutzer steht am Herd und soll nicht
     * festhängen; die Meldung sagt ihm, dass nichts gezählt wurde.
     */
    private fun finishCooking() {
        val id = recipe?.id ?: run { finish(); return }
        stopTimer()

        lifecycleScope.launch {
            recipeRepository.markCooked(id, servings)
                .onSuccess {
                    Toast.makeText(this@CookModeActivity, R.string.cook_counted, Toast.LENGTH_SHORT).show()
                }
                .onFailure {
                    Toast.makeText(this@CookModeActivity, R.string.cook_count_failed, Toast.LENGTH_LONG).show()
                }
            finish()
        }
    }

    /** Läuft ein Timer, wird das Verlassen bestätigt. */
    private fun confirmExit() {
        if (!isTimerRunning) {
            finish()
            return
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.cook_exit_title)
            .setMessage(R.string.cook_exit_message)
            .setPositiveButton(R.string.cook_exit_confirm) { _, _ ->
                stopTimer()
                finish()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
