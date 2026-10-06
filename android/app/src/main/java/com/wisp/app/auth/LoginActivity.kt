package com.wisp.app.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.wisp.app.databinding.ActivityLoginBinding
import com.wisp.app.mainapp.MainActivity
import com.wisp.app.sync.SupabaseManager
import kotlinx.coroutines.launch

/**
 * Clean login & registration screen for Wisp.
 * Reference: PRD.md Section 7.1.4 & DESIGN.md Section 7.6
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private var isSignUpMode: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If already authenticated, proceed to MainActivity immediately
        if (SupabaseManager.isAuthenticated) {
            proceedToMain()
            return
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
    }

    private fun setupUI() {
        binding.tvToggleSignUp.setOnClickListener {
            isSignUpMode = !isSignUpMode
            if (isSignUpMode) {
                binding.tvAuthModeTitle.text = "Create a Wisp Account"
                binding.btnLoginSubmit.text = "Create Account"
                binding.tvToggleSignUp.text = "Already have an account? Sign in"
            } else {
                binding.tvAuthModeTitle.text = "Sign In to Your Account"
                binding.btnLoginSubmit.text = "Sign In"
                binding.tvToggleSignUp.text = "Don't have an account? Sign up"
            }
            binding.tvLoginError.visibility = View.GONE
        }

        binding.btnLoginSubmit.setOnClickListener {
            val email = binding.etLoginEmail.text.toString().trim()
            val password = binding.etLoginPassword.text.toString().trim()

            if (email.isBlank() || password.isBlank()) {
                showError("Please enter your email and password")
                return@setOnClickListener
            }

            binding.btnLoginSubmit.isEnabled = false
            binding.tvLoginError.visibility = View.GONE

            lifecycleScope.launch {
                val result = if (isSignUpMode) {
                    SupabaseManager.signUp(email, password)
                } else {
                    SupabaseManager.signIn(email, password)
                }

                binding.btnLoginSubmit.isEnabled = true

                result.onSuccess {
                    proceedToMain()
                }.onFailure { err ->
                    showError(err.message ?: "Authentication failed")
                }
            }
        }

        binding.btnGoogleSignIn.setOnClickListener {
            Toast.makeText(this, "Google Sign-In ready for OAuth provider configuration", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showError(message: String) {
        binding.tvLoginError.text = message
        binding.tvLoginError.visibility = View.VISIBLE
    }

    private fun proceedToMain() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}
