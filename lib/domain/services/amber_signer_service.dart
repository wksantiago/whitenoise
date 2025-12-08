import 'dart:io';

import 'package:flutter/services.dart';
import 'package:logging/logging.dart';

/// Service for interacting with the Amber signer app on Android.
///
/// Amber is a NIP-55 compliant external signer that securely manages
/// Nostr private keys, ensuring they never enter the app's process memory.
///
/// This service provides methods to:
/// - Check if Amber is installed
/// - Get the user's public key from Amber
/// - Get the Amber package name
class AmberSignerService {
  static const _channel = MethodChannel('amber_signer');
  static final _logger = Logger('AmberSignerService');

  /// Check if the Amber signer app is installed on the device.
  ///
  /// Returns `true` if Amber is installed and available, `false` otherwise.
  /// Always returns `false` on non-Android platforms.
  static Future<bool> isAmberInstalled() async {
    if (!Platform.isAndroid) {
      return false;
    }

    try {
      final result = await _channel.invokeMethod<bool>('isAmberInstalled');
      return result ?? false;
    } on PlatformException catch (e) {
      _logger.warning('Error checking if Amber is installed: ${e.message}');
      return false;
    } catch (e) {
      _logger.warning('Unexpected error checking Amber installation: $e');
      return false;
    }
  }

  /// Get the user's public key from Amber.
  ///
  /// This launches the Amber app and prompts the user to authorize
  /// sharing their public key with this app.
  ///
  /// Returns the public key in hex format if successful, or `null` if:
  /// - Amber is not installed
  /// - User cancelled the operation
  /// - An error occurred
  ///
  /// Throws [AmberNotInstalledException] if Amber is not installed.
  /// Throws [AmberUserCancelledException] if the user cancelled.
  /// Throws [AmberException] for other errors.
  static Future<String> getPublicKey() async {
    if (!Platform.isAndroid) {
      throw AmberNotSupportedException('Amber is only available on Android');
    }

    try {
      final result = await _channel.invokeMethod<String>('getPublicKey');
      if (result == null) {
        throw AmberException('Amber returned null public key');
      }
      _logger.info('Successfully obtained public key from Amber');
      return result;
    } on PlatformException catch (e) {
      _logger.warning('Platform error getting public key from Amber: ${e.code} - ${e.message}');

      switch (e.code) {
        case 'AMBER_NOT_INSTALLED':
          throw AmberNotInstalledException(e.message ?? 'Amber is not installed');
        case 'USER_CANCELLED':
          throw AmberUserCancelledException(e.message ?? 'User cancelled the operation');
        case 'NO_PUBKEY':
          throw AmberException(e.message ?? 'Amber did not return a public key');
        case 'AMBER_LAUNCH_ERROR':
          throw AmberException(e.message ?? 'Failed to launch Amber');
        default:
          throw AmberException(e.message ?? 'Unknown error: ${e.code}');
      }
    } catch (e) {
      _logger.severe('Unexpected error getting public key from Amber: $e');
      rethrow;
    }
  }

  /// Get the Amber package name.
  ///
  /// Returns the package name of the Amber signer app.
  static Future<String?> getAmberPackageName() async {
    if (!Platform.isAndroid) {
      return null;
    }

    try {
      return await _channel.invokeMethod<String>('getAmberPackageName');
    } on PlatformException catch (e) {
      _logger.warning('Error getting Amber package name: ${e.message}');
      return null;
    }
  }
}

/// Base exception for Amber-related errors.
class AmberException implements Exception {
  final String message;
  AmberException(this.message);

  @override
  String toString() => 'AmberException: $message';
}

/// Exception thrown when Amber is not installed.
class AmberNotInstalledException extends AmberException {
  AmberNotInstalledException(super.message);

  @override
  String toString() => 'AmberNotInstalledException: $message';
}

/// Exception thrown when the user cancels the Amber operation.
class AmberUserCancelledException extends AmberException {
  AmberUserCancelledException(super.message);

  @override
  String toString() => 'AmberUserCancelledException: $message';
}

/// Exception thrown when Amber is not supported on the current platform.
class AmberNotSupportedException extends AmberException {
  AmberNotSupportedException(super.message);

  @override
  String toString() => 'AmberNotSupportedException: $message';
}
