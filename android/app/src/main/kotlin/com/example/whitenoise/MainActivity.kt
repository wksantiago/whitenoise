package org.parres.whitenoise

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PersistableBundle
import android.os.Build
import android.util.Log
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity: FlutterActivity() {
    private val CLIPBOARD_CHANNEL = "clipboard_sensitive"
    private val AMBER_CHANNEL = "amber_signer"

    // Default Amber package name
    private val AMBER_PACKAGE = "com.greenart7c3.nostrsigner"

    // Request code for Amber intent
    private val AMBER_REQUEST_CODE = 1001

    // Pending result for Amber operations
    private var pendingAmberResult: MethodChannel.Result? = null

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        // Initialize Rust Android context for Amber signer
        initializeRustAndroidContext()

        // Clipboard sensitive channel
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CLIPBOARD_CHANNEL).setMethodCallHandler { call, result ->
            if (call.method == "setSensitive") {
                val text = call.argument<String>("text")
                if (text != null) {
                    setSensitiveClipboard(text)
                    result.success(null)
                } else {
                    result.error("NO_TEXT", "Text was null", null)
                }
            } else {
                result.notImplemented()
            }
        }

        // Amber signer channel
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, AMBER_CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "isAmberInstalled" -> {
                    result.success(isAmberInstalled())
                }
                "getPublicKey" -> {
                    getPublicKeyFromAmber(result)
                }
                "getAmberPackageName" -> {
                    result.success(AMBER_PACKAGE)
                }
                else -> {
                    result.notImplemented()
                }
            }
        }
    }

    /**
     * Initialize the Rust Android context with ContentResolver.
     * This is required for Amber signer operations.
     */
    private fun initializeRustAndroidContext() {
        try {
            val success = RustBridge.initAndroidContext(contentResolver)
            if (success) {
                Log.i(TAG, "Rust Android context initialized successfully")
            } else {
                Log.w(TAG, "Failed to initialize Rust Android context")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Rust Android context", e)
        }
    }

    /**
     * Check if the Amber signer app is installed.
     */
    private fun isAmberInstalled(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(AMBER_PACKAGE, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(AMBER_PACKAGE, 0)
            }
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Launch Amber to get the user's public key using NIP-55 intent.
     */
    private fun getPublicKeyFromAmber(result: MethodChannel.Result) {
        if (!isAmberInstalled()) {
            result.error("AMBER_NOT_INSTALLED", "Amber signer is not installed", null)
            return
        }

        try {
            pendingAmberResult = result

            // Create NIP-55 intent to get public key
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("nostrsigner:")
                `package` = AMBER_PACKAGE
                putExtra("type", "get_public_key")
                // Add permissions we need
                putExtra("permissions", "sign_event,nip44_encrypt,nip44_decrypt,nip04_encrypt,nip04_decrypt")
            }

            startActivityForResult(intent, AMBER_REQUEST_CODE)
        } catch (e: Exception) {
            Log.e(TAG, "Error launching Amber", e)
            pendingAmberResult = null
            result.error("AMBER_LAUNCH_ERROR", "Failed to launch Amber: ${e.message}", null)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == AMBER_REQUEST_CODE) {
            val result = pendingAmberResult
            pendingAmberResult = null

            if (result == null) {
                Log.w(TAG, "No pending result for Amber callback")
                return
            }

            if (resultCode == RESULT_OK && data != null) {
                // Extract the public key from the result
                val signature = data.getStringExtra("signature")
                val pubkey = data.getStringExtra("pubkey")

                when {
                    pubkey != null -> {
                        Log.i(TAG, "Got public key from Amber: $pubkey")
                        result.success(pubkey)
                    }
                    signature != null -> {
                        // Some versions return the pubkey in signature field for get_public_key
                        Log.i(TAG, "Got public key from Amber (signature field): $signature")
                        result.success(signature)
                    }
                    else -> {
                        Log.w(TAG, "Amber returned OK but no pubkey found")
                        result.error("NO_PUBKEY", "Amber did not return a public key", null)
                    }
                }
            } else {
                Log.w(TAG, "Amber request cancelled or failed. resultCode=$resultCode")
                result.error("USER_CANCELLED", "User cancelled or Amber request failed", null)
            }
        }
    }

    private fun setSensitiveClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("label", text)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val extras = PersistableBundle()
            extras.putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            clip.description.extras = extras
        }

        clipboard.setPrimaryClip(clip)
    }
}