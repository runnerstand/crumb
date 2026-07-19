package com.example.crumb

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import com.example.crumb.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var isUpdatingBottomNavigation = false
    private var lastSelectedMainDestinationId = R.id.homeFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        setupBottomNavigation(navHostFragment.navController)
    }

    private fun setupBottomNavigation(navController: NavController) {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            if (isUpdatingBottomNavigation) {
                return@setOnItemSelectedListener true
            }

            navigateToMainDestination(navController, item.itemId)
            true
        }
        binding.bottomNavigation.setOnItemReselectedListener { item ->
            if (!isUpdatingBottomNavigation) {
                navigateToMainDestination(navController, item.itemId)
            }
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val selectedDestinationId = when (destination.id) {
                R.id.homeFragment,
                R.id.addFragment,
                R.id.savedFragment,
                R.id.profileFragment -> destination.id
                R.id.searchFragment -> R.id.homeFragment
                else -> lastSelectedMainDestinationId
            }

            if (selectedDestinationId in MAIN_DESTINATION_IDS) {
                lastSelectedMainDestinationId = selectedDestinationId
                updateBottomNavigationSelection(selectedDestinationId)
            }
        }
    }

    private fun navigateToMainDestination(navController: NavController, destinationId: Int) {
        if (destinationId !in MAIN_DESTINATION_IDS) {
            return
        }

        if (navController.currentDestination?.id == destinationId) {
            return
        }

        if (navController.popBackStack(destinationId, false)) {
            return
        }

        val navOptions = NavOptions.Builder()
            .setLaunchSingleTop(true)
            .setPopUpTo(R.id.homeFragment, false)
            .build()

        runCatching {
            navController.navigate(destinationId, null, navOptions)
        }
    }

    private fun updateBottomNavigationSelection(destinationId: Int) {
        if (binding.bottomNavigation.selectedItemId == destinationId) {
            return
        }

        isUpdatingBottomNavigation = true
        binding.bottomNavigation.selectedItemId = destinationId
        isUpdatingBottomNavigation = false
    }

    companion object {
        private val MAIN_DESTINATION_IDS = setOf(
            R.id.homeFragment,
            R.id.addFragment,
            R.id.savedFragment,
            R.id.profileFragment
        )
    }
}
