package com.cevague.vindex.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.cevague.vindex.R
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

    private lateinit var navController: NavController
    private lateinit var appBarConfiguration: AppBarConfiguration

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Thème appliqué par VindexApplication ; langue persistée par autoStoreLocales.
        if (!settingsCache.isConfigured) {
            startActivity(Intent(this, WelcomeActivity::class.java))
            finish()
            return
        }

        setupUI()
    }

    private fun setupUI() {
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.navHostFragment) as NavHostFragment
        navController = navHostFragment.navController

        // Les 4 onglets sont les destinations « racine » (pas de flèche retour).
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.galleryFragment,
                R.id.albumsFragment,
                R.id.searchFragment,
                R.id.peopleFragment
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        binding.bottomNav.setupWithNavController(navController)

        // Bottom nav + engrenage uniquement sur les destinations racine ; sur les
        // écrans de détail et les Paramètres, seule la flèche retour est proposée.
        navController.addOnDestinationChangedListener { _, destination, _ ->
            val topLevel = destination.id in appBarConfiguration.topLevelDestinations
            binding.bottomNav.visibility = if (topLevel) View.VISIBLE else View.GONE
            invalidateOptionsMenu()
        }

        observeNavigateToTab()
        observeSyncProgress()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.top_app_bar_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_settings)?.isVisible =
            navController.currentDestination?.id in appBarConfiguration.topLevelDestinations
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == R.id.action_settings) {
            navController.navigate(R.id.settingsFragment)
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
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

                    val hasPlaceholder = workText.contains("%d")
                    val workTextFinal = if (hasPlaceholder) {
                        workText.replace("%d", progress.toString())
                    } else {
                        workText
                    }

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
