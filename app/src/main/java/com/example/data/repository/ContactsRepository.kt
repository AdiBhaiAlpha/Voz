package com.example.data.repository

import android.content.Context
import android.provider.ContactsContract
import com.example.domain.ContactItem
import com.example.domain.ContactNumber
import com.example.domain.PhoneNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface ContactsLoadResult {
    data class Success(val contacts: List<ContactItem>) : ContactsLoadResult
    data class PermissionDenied(val reason: String) : ContactsLoadResult
    data class Error(val message: String) : ContactsLoadResult
}

class ContactsRepository(private val context: Context) {

    suspend fun loadAuthorizedContacts(): ContactsLoadResult = withContext(Dispatchers.IO) {
        val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CONTACTS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            return@withContext ContactsLoadResult.PermissionDenied(
                "READ_CONTACTS permission is required to search and view your device contacts."
            )
        }

        try {
            val contactsMap = mutableMapOf<Long, ContactBuilder>()
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
                ContactsContract.CommonDataKinds.Phone.TYPE,
                ContactsContract.CommonDataKinds.Phone.LABEL
            )
            val sortOrder = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE NOCASE ASC"

            context.contentResolver.query(uri, projection, null, null, sortOrder)?.use { cursor ->
                val idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val lookupIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY)
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val photoIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
                val typeIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)
                val labelIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LABEL)

                while (cursor.moveToNext()) {
                    val contactId = if (idIdx >= 0) cursor.getLong(idIdx) else 0L
                    val lookupKey = if (lookupIdx >= 0) cursor.getString(lookupIdx) ?: "" else ""
                    val number = if (numberIdx >= 0) cursor.getString(numberIdx) ?: "" else ""
                    val rawName = if (nameIdx >= 0) cursor.getString(nameIdx)?.trim() else null
                    val name = if (!rawName.isNullOrBlank() && rawName != "null") {
                        rawName
                    } else if (number.isNotBlank()) {
                        PhoneNormalizer.formatForDisplay(number)
                    } else {
                        "Unknown"
                    }
                    val photoUri = if (photoIdx >= 0) cursor.getString(photoIdx) else null
                    val type = if (typeIdx >= 0) cursor.getInt(typeIdx) else 2
                    val label = if (labelIdx >= 0) cursor.getString(labelIdx) ?: "Mobile" else "Mobile"

                    if (number.isNotBlank()) {
                        val normalized = PhoneNormalizer.normalizeForComparison(number)
                        val contactNumber = ContactNumber(
                            rawNumber = number,
                            normalizedNumber = normalized,
                            label = label,
                            type = type
                        )

                        val builder = contactsMap.getOrPut(contactId) {
                            ContactBuilder(
                                contactId = contactId,
                                lookupKey = lookupKey,
                                displayName = name,
                                photoUri = photoUri
                            )
                        }
                        builder.numbers.add(contactNumber)
                    }
                }
            }

            val list = contactsMap.values.map { it.build() }
            ContactsLoadResult.Success(list)
        } catch (e: Exception) {
            ContactsLoadResult.Error(e.message ?: "Failed to read contacts")
        }
    }

    private class ContactBuilder(
        val contactId: Long,
        val lookupKey: String,
        val displayName: String,
        val photoUri: String?
    ) {
        val numbers = mutableListOf<ContactNumber>()

        fun build(): ContactItem {
            val primary = numbers.firstOrNull()?.rawNumber ?: ""
            val primaryNorm = numbers.firstOrNull()?.normalizedNumber ?: ""
            return ContactItem(
                contactId = contactId,
                lookupKey = lookupKey,
                displayName = displayName,
                primaryNumber = primary,
                primaryNormalizedNumber = primaryNorm,
                numbers = numbers,
                photoUri = photoUri
            )
        }
    }
}
