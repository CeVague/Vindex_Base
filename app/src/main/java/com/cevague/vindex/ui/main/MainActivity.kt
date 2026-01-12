package com.cevague.vindex.ui.main

import com.cevague.vindex.R
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
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

    private fun setupUI() {
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Configurer la navigation
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.navHostFragment) as NavHostFragment
        val navController = navHostFragment.navController

        binding.bottomNav.setupWithNavController(navController)
    }
}