//! Android-specific JNI exports for Amber signer integration.
//!
//! This module provides JNI functions that must be called from Kotlin/Java
//! to initialize the Android context required for Amber signer operations.
//!
//! # Usage
//!
//! Call `Java_org_parres_whitenoise_RustBridge_initAndroidContext` from your
//! Android app's MainActivity during startup, before any Amber signer operations.

use jni::objects::{JClass, JObject};
use jni::JNIEnv;

/// Initialize the Android context for Amber signer operations.
///
/// This function must be called from Kotlin/Java before using any Amber signer
/// functionality. It stores the ContentResolver reference needed to communicate
/// with the Amber app via NIP-55.
///
/// # Safety
///
/// This function is unsafe because it interfaces with JNI. The caller must ensure:
/// - `env` is a valid JNI environment pointer
/// - `content_resolver` is a valid Android ContentResolver object
///
/// # Arguments
///
/// * `env` - JNI environment
/// * `_class` - The Java class (unused)
/// * `content_resolver` - Android ContentResolver object
///
/// # Returns
///
/// Returns `true` if initialization succeeded, `false` otherwise.
#[no_mangle]
pub unsafe extern "C" fn Java_org_parres_whitenoise_RustBridge_initAndroidContext(
    mut env: JNIEnv,
    _class: JClass,
    content_resolver: JObject,
) -> bool {
    match whitenoise::Whitenoise::init_android_context(&mut env, &content_resolver) {
        Ok(()) => {
            tracing::info!(target: "whitenoise::android", "Android context initialized successfully");
            true
        }
        Err(e) => {
            tracing::error!(target: "whitenoise::android", "Failed to initialize Android context: {:?}", e);
            false
        }
    }
}

/// Check if the Android context has been initialized.
///
/// This can be used to verify that `initAndroidContext` was called successfully
/// before attempting Amber signer operations.
///
/// # Returns
///
/// Returns `true` if the Android context is initialized, `false` otherwise.
#[no_mangle]
pub unsafe extern "C" fn Java_org_parres_whitenoise_RustBridge_isAndroidContextInitialized(
    _env: JNIEnv,
    _class: JClass,
) -> bool {
    whitenoise::whitenoise::signers::android_context::get().is_some()
}
