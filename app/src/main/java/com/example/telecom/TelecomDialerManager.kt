package com.example.telecom

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat
import com.example.domain.PhoneNormalizer
import com.example.domain.SimSubscriptionItem

sealed class CallInitiationOutcome {
    data object PlacedDirectly : CallInitiationOutcome()
    data object OpenedSystemDialerFallback : CallInitiationOutcome()
    data class RequiresSimSelection(
        val sanitizedNumber: String,
        val availableSims: List<SimSubscriptionItem>
    ) : CallInitiationOutcome()
    data class Error(val reason: String) : CallInitiationOutcome()
}

/**
 * Encapsulates real Android TelecomManager and RoleManager interactions:
 * - Default Dialer role checking & request intents (`RoleManager.ROLE_DIALER`)
 * - Call Screening role checking & request intents (`RoleManager.ROLE_CALL_SCREENING`)
 * - Multi-SIM (`PhoneAccountHandle`) discovery
 * - Placing calls via `TelecomManager.placeCall` or safe `ACTION_DIAL` fallback
 */
class TelecomDialerManager(private val context: Context) {

    private val telecomManager: TelecomManager? =
        context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager

    fun isDefaultDialer(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                    roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
                } else {
                    telecomManager?.defaultDialerPackage == context.packageName
                }
            } else {
                telecomManager?.defaultDialerPackage == context.packageName
            }
        } catch (e: Exception) {
            false
        }
    }

    fun isCallScreeningRoleHeld(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
                roleManager != null &&
                    roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) &&
                    roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    fun createRequestDefaultDialerIntent(): Intent? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                    roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                } else {
                    Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                        putExtra(
                            TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME,
                            context.packageName
                        )
                    }
                }
            } else {
                Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                    putExtra(
                        TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME,
                        context.packageName
                    )
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    fun createRequestCallScreeningIntent(): Intent? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
                    roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun hasCallPhonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasReadPhoneStatePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Queries available SIM / PhoneAccount subscriptions when READ_PHONE_STATE is granted.
     */
    fun getAvailableSimAccounts(): List<SimSubscriptionItem> {
        if (!hasReadPhoneStatePermission()) return emptyList()
        val tm = telecomManager ?: return emptyList()
        return try {
            val handles: List<PhoneAccountHandle> = tm.callCapablePhoneAccounts
            handles.mapIndexed { idx, handle ->
                val account = tm.getPhoneAccount(handle)
                val label = account?.label?.toString()?.takeIf { it.isNotBlank() } ?: "SIM ${idx + 1}"
                val desc = account?.shortDescription?.toString()?.takeIf { it.isNotBlank() } ?: label
                SimSubscriptionItem(
                    id = handle.id ?: "sim_$idx",
                    label = label,
                    carrierName = desc,
                    slotIndex = idx + 1
                )
            }
        } catch (se: SecurityException) {
            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Initiates a real phone call using Android's TelecomManager or safe ACTION_DIAL fallback.
     * If multiple SIMs exist and no preferred or explicit SIM ID was selected, returns
     * [CallInitiationOutcome.RequiresSimSelection] so the user is never surprised by which SIM dials.
     */
    fun initiateCall(
        rawInput: String,
        preferredSimId: String = "",
        explicitSimId: String? = null
    ): CallInitiationOutcome {
        val sanitized = PhoneNormalizer.sanitizeDialInput(rawInput)
        if (!PhoneNormalizer.isValidDialableNumber(sanitized)) {
            return CallInitiationOutcome.Error("Please enter a valid phone number.")
        }

        val sims = getAvailableSimAccounts()
        val chosenSimId = explicitSimId ?: preferredSimId
        if (sims.size > 1 && chosenSimId.isBlank()) {
            return CallInitiationOutcome.RequiresSimSelection(
                sanitizedNumber = sanitized,
                availableSims = sims
            )
        }

        val telUri = Uri.fromParts("tel", sanitized, null)

        // If CALL_PHONE is granted, place call directly via TelecomManager
        if (hasCallPhonePermission() && telecomManager != null) {
            try {
                val extras = Bundle()
                if (chosenSimId.isNotBlank() && hasReadPhoneStatePermission()) {
                    val matchedHandle = telecomManager.callCapablePhoneAccounts.firstOrNull {
                        it.id == chosenSimId
                    }
                    if (matchedHandle != null) {
                        extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, matchedHandle)
                    }
                }
                telecomManager.placeCall(telUri, extras)
                return CallInitiationOutcome.PlacedDirectly
            } catch (se: SecurityException) {
                // Fall through to ACTION_DIAL fallback
            } catch (e: Exception) {
                // Fall through to ACTION_DIAL fallback
            }
        }

        // Safe fallback when VOZ lacks CALL_PHONE permission or device restricts direct placement
        return try {
            val dialIntent = Intent(Intent.ACTION_DIAL, telUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(dialIntent)
            CallInitiationOutcome.OpenedSystemDialerFallback
        } catch (e: Exception) {
            CallInitiationOutcome.Error("No telephone handler available on this device.")
        }
    }
}
