package org.parres.whitenoise

import android.content.ContentResolver

/**
 * Bridge class for native Rust library functions.
 *
 * This class provides JNI bindings to the whitenoise Rust library,
 * specifically for Android-specific functionality like Amber signer integration.
 */
object RustBridge {
    init {
        // Load the native library
        // The library is already loaded by flutter_rust_bridge, but we declare it here
        // for documentation purposes. The actual loading happens in the Flutter engine.
        try {
            System.loadLibrary("rust_lib_whitenoise")
        } catch (e: UnsatisfiedLinkError) {
            // Library might already be loaded by Flutter, which is fine
        }
    }

    /**
     * Initialize the Android context for Amber signer operations.
     *
     * This must be called during app startup, before any Amber signer
     * operations are performed. It provides the ContentResolver needed
     * to communicate with the Amber app via NIP-55.
     *
     * @param contentResolver The Android ContentResolver
     * @return true if initialization succeeded, false otherwise
     */
    @JvmStatic
    external fun initAndroidContext(contentResolver: ContentResolver): Boolean

    /**
     * Check if the Android context has been initialized.
     *
     * @return true if the Android context is initialized, false otherwise
     */
    @JvmStatic
    external fun isAndroidContextInitialized(): Boolean
}
