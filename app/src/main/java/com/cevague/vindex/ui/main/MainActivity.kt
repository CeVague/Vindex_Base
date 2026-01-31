package com.cevague.vindex.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.cevague.vindex.R
import com.cevague.vindex.data.database.entity.Setting
import com.cevague.vindex.data.local.SettingsCache
import com.cevague.vindex.databinding.ActivityMainBinding
import com.cevague.vindex.databinding.LayoutSyncProgressBinding
import com.cevague.vindex.ui.welcome.WelcomeActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var settingsCache: SettingsCache
    private lateinit var binding: ActivityMainBinding
    private var syncBinding: LayoutSyncProgressBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        applyAppTheme(settingsCache.themeMode)
        applyAppLanguage(settingsCache.userLanguage)

        if (!settingsCache.isConfigured) {
            startActivity(Intent(this, WelcomeActivity::class.java))
            finish()
            return
        }

        setupUI()
    }

    // Dans une fonction utilitaire ou MainActivity
    fun applyAppTheme(theme: String) {
        when (theme) {
            Setting.THEME_LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            Setting.THEME_DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }


    fun applyAppLanguage(language: String) {
        when (language) {
            Setting.LANGUAGE_FRENCH -> AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags(
                    "fr"
                )
            )

            Setting.LANGUAGE_ENGLISH -> AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags(
                    "en"
                )
            )

            else -> AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
        }
    }

    private fun setupUI() {
        // On ne gonfle l'UI que si on est sûr de rester sur cette activité
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Configurer la navigation
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.navHostFragment) as NavHostFragment
        val navController = navHostFragment.navController

        binding.bottomNav.setupWithNavController(navController)

        observeNavigateToTab()

        observeSyncProgress()
    }

    private fun observeSyncProgress() {
        WorkManager.getInstance(applicationContext)
            .getWorkInfosByTagLiveData("SCAN_TAG")
            .observe(this) { workInfos ->
                val workInfo = workInfos?.firstOrNull { it.state == WorkInfo.State.RUNNING }

                if (workInfo != null) {
                    if (syncBinding == null) {
                        val view = binding.syncProgressStub.inflate()
                        syncBinding = LayoutSyncProgressBinding.bind(view)
                    }
                    syncBinding?.root?.visibility = View.VISIBLE

                    val progress = workInfo.progress.getInt("PROGRESS", 0)
                    val workText =
                        workInfo.progress.getString("WORK") ?: getString(R.string.progress_generic)

                    // 1. Détecter si le marqueur %d est présent
                    val hasPlaceholder = workText.contains("%d")

                    // 2. Remplacer de manière sécurisée (pas de crash possible avec replace)
                    val workTextFinal = if (hasPlaceholder) {
                        workText.replace("%d", progress.toString())
                    } else {
                        workText
                    }

                    // 3. Gérer la visibilité de la roue
                    syncBinding?.circularProgressBar?.visibility =
                        if (hasPlaceholder) View.GONE else View.VISIBLE

                    syncBinding?.textProgressBar?.text = workTextFinal
                    syncBinding?.syncProgressBar?.progress = progress
                } else {
                    syncBinding?.root?.visibility = View.GONE
                }
            }
    }

    private fun observeNavigateToTab() {
        val sharedViewModel: MainSharedViewModel by viewModels()

        lifecycleScope.launch {
            sharedViewModel.navigateToTab.collect { itemId ->
                binding.bottomNav.selectedItemId = itemId
            }
        }
    }
}
