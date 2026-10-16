package com.example.telecom

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import com.example.domain.PhoneNormalizer
import com.example.domain.SimSubscriptionItem

class TelecomDialerManager(private val context: Context) {

    private val telecomManager: TelecomManager? =
        context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager

    fun isDefaultDialer(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                    return roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
                }
            }
            telecomManager?.defaultDialerPackage == context.packageName
        } catch (_: Exception) {
            false
        }
    }

    fun isCallScreeningRoleHeld(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
                    return roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
                }
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    fun createRequestDefaultDialerIntent(): Intent? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                    return roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                }
            }
            @Suppress("DEPRECATION")
            Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, context.packageName)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun createRequestCallScreeningIntent(): Intent? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
                    return roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    fun hasCallPhonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CALL_PHONE
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    fun hasReadPhoneStatePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_PHONE_STATE
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    fun getAvailableSimAccounts(): List<SimSubscriptionItem> {
        val result = mutableListOf<SimSubscriptionItem>()
        try {
            if (hasReadPhoneStatePermission()) {
                val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                val subList = subManager?.activeSubscriptionInfoList
                if (!subList.isNullOrEmpty()) {
                    for (info in subList) {
                        result.add(
                            SimSubscriptionItem(
                                subscriptionId = info.subscriptionId,
                                displayName = info.displayName?.toString() ?: "SIM ${info.simSlotIndex + 1}",
                                carrierName = info.carrierName?.toString() ?: "Carrier",
                                slotIndex = info.simSlotIndex,
                                iccId = info.iccId ?: "",
                                color = info.iconTint
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}
        return result
    }

    fun initiateCellularCall(
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
            return CallInitiationOutcome.RequiresSimSelection(sanitized, sims)
        }

        if (!hasCallPhonePermission()) {
            return CallInitiationOutcome.Error("CALL_PHONE permission is required for cellular calls.")
        }

        val tm = telecomManager ?: return CallInitiationOutcome.Error("Telephony service unavailable on this device.")
        val telUri = Uri.fromParts("tel", sanitized, null)

        return try {
            val extras = Bundle()
            if (chosenSimId.isNotBlank() && hasReadPhoneStatePermission()) {
                val phoneAccounts = tm.callCapablePhoneAccounts
                val matchedHandle: PhoneAccountHandle? = phoneAccounts.firstOrNull { it.id == chosenSimId }
                if (matchedHandle != null) {
                    extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, matchedHandle)
                }
            }
            tm.placeCall(telUri, extras)
            CallInitiationOutcome.PlacedDirectly
        } catch (e: SecurityException) {
            CallInitiationOutcome.Error("Cellular calling permission denied by system.")
        } catch (e: Exception) {
            CallInitiationOutcome.Error("Could not place cellular call: ${e.message ?: "hardware error"}")
        }
    }
}
