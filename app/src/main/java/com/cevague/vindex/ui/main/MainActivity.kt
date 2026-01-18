package com.cevague.vindex.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.cevague.vindex.R
import com.cevague.vindex.data.database.entity.Setting
import com.cevague.vindex.data.local.FastSettings
import com.cevague.vindex.databinding.ActivityMainBinding
import com.cevague.vindex.databinding.LayoutSyncProgressBinding
import com.cevague.vindex.ui.welcome.WelcomeActivity

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var syncBinding: LayoutSyncProgressBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        applyAppTheme(FastSettings.themeMode)
        applyAppLanguage(FastSettings.userLanguage)
        super.onCreate(savedInstanceState)

        // Lecture immédiate du miroir
        val sourceFolder = FastSettings.sourceFolderUri

        if (sourceFolder == null) {
            startActivity(Intent(this, WelcomeActivity::class.java))
            finish()
            return
        }

        // Sinon on charge l'UI normale
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
                    val work =
                        workInfo.progress.getString("WORK") ?: getString(R.string.progress_generic)
                    syncBinding?.syncProgressBar?.progress = progress
                    syncBinding?.textProgressBar?.text = "$work $progress%"
                } else {
                    syncBinding?.root?.visibility = View.GONE
                }
            }
    }
}