package com.cevague.vindex.ui.main

import com.cevague.vindex.R
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.observe
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.cevague.vindex.VindexApplication
import com.cevague.vindex.databinding.ActivityMainBinding
import com.cevague.vindex.ui.welcome.WelcomeActivity
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Vérifier si un dossier est configuré
        checkSourceFolder()
    }

    private fun checkSourceFolder() {
        val app = application as VindexApplication

        lifecycleScope.launch {
            val sourceFolder = app.settingsRepository.getSourceFolderUriOnce()

            if (sourceFolder == null) {
                // Pas de dossier configuré → WelcomeActivity
                startActivity(Intent(this@MainActivity, WelcomeActivity::class.java))
                finish()
            } else {
                // Dossier configuré → Afficher l'UI normale
                setupUI()
            }
        }
    }

    private fun observeSyncProgress() {
        WorkManager.getInstance(applicationContext)
            .getWorkInfosByTagLiveData("SCAN_TAG")
            .observe(this) { workInfos ->
                val workInfo = workInfos?.firstOrNull { it.state == WorkInfo.State.RUNNING }

                if (workInfo != null) {
                    val progress = workInfo.progress.getInt("PROGRESS", 0)
                    val work =
                        workInfo.progress.getString("WORK") ?: getString(R.string.progress_generic)
                    binding.layoutProgressBar.visibility = View.VISIBLE
                    binding.syncProgressBar.progress = progress
                    binding.textProgressBar.text = "$work $progress%"
                } else {
                    binding.layoutProgressBar.visibility = View.GONE
                }
            }
    }

    private fun setupUI() {
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Configurer la navigation
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.navHostFragment) as NavHostFragment
        val navController = navHostFragment.navController

        binding.bottomNav.setupWithNavController(navController)

        observeSyncProgress()
    }
}