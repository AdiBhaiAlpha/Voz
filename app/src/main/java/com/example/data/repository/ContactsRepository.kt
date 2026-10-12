package com.example.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.example.domain.ContactItem
import com.example.domain.ContactNumber
import com.example.domain.PhoneNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class ContactsLoadResult {
    data class Success(val contacts: List<ContactItem>) : ContactsLoadResult()
    data object PermissionDenied : ContactsLoadResult()
    data class Error(val message: String) : ContactsLoadResult()
}

/**
 * Reads authorized local contacts from Android's Contacts Provider on Dispatchers.IO.
 * Deduplicates multiple phone numbers per contact using PhoneNormalizer while preserving
 * the exact original phone string for display and dialing.
 * Never fabricates or modifies system contacts.
 */
class ContactsRepository(private val context: Context) {

    fun hasContactsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun loadAuthorizedContacts(): ContactsLoadResult = withContext(Dispatchers.IO) {
        if (!hasContactsPermission()) {
            return@withContext ContactsLoadResult.PermissionDenied
        }

        try {
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.TYPE,
                ContactsContract.CommonDataKinds.Phone.LABEL,
                ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI
            )

            val sortOrder = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC"

            val groupedMap = LinkedHashMap<Long, MutableContactBuilder>()

            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val lookupIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY)
                val nameIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
                val numberIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val typeIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.TYPE)
                val labelIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.LABEL)
                val photoIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI)

                while (cursor.moveToNext()) {
                    val contactId = cursor.getLong(idIdx)
                    val lookupKey = cursor.getString(lookupIdx).orEmpty()
                    val displayName = cursor.getString(nameIdx)?.trim().orEmpty()
                    val rawNumber = cursor.getString(numberIdx)?.trim().orEmpty()
                    if (rawNumber.isEmpty()) continue

                    val phoneType = cursor.getInt(typeIdx)
                    val customTypeLabel = cursor.getString(labelIdx)
                    val typeLabel = ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                        context.resources,
                        phoneType,
                        customTypeLabel
                    )?.toString() ?: "Mobile"
                    val photoUri = cursor.getString(photoIdx)

                    val normalized = PhoneNormalizer.normalizeForComparison(rawNumber)
                        .ifBlank { rawNumber }

                    val builder = groupedMap.getOrPut(contactId) {
                        MutableContactBuilder(
                            contactId = contactId,
                            lookupKey = lookupKey,
                            displayName = displayName.ifEmpty { rawNumber },
                            photoUri = photoUri
                        )
                    }

                    if (builder.photoUri == null && photoUri != null) {
                        builder.photoUri = photoUri
                    }

                    // Avoid duplicate normalized numbers under the same contact
                    val alreadyHasNumber = builder.numbers.any { existing ->
                        existing.normalizedNumber == normalized ||
                            PhoneNormalizer.areNumbersEquivalent(existing.rawNumber, rawNumber)
                    }
                    if (!alreadyHasNumber) {
                        builder.numbers.add(
                            ContactNumber(
                                rawNumber = rawNumber,
                                normalizedNumber = normalized,
                                typeLabel = typeLabel
                            )
                        )
                    }
                }
            }

            val contacts = groupedMap.values
                .filter { it.numbers.isNotEmpty() }
                .map { it.build() }

            ContactsLoadResult.Success(contacts)
        } catch (se: SecurityException) {
            ContactsLoadResult.PermissionDenied
        } catch (e: Exception) {
            ContactsLoadResult.Error("Unable to read device contacts.")
        }
    }

    private class MutableContactBuilder(
        val contactId: Long,
        val lookupKey: String,
        val displayName: String,
        var photoUri: String?,
        val numbers: MutableList<ContactNumber> = mutableListOf()
    ) {
        fun build(): ContactItem = ContactItem(
            contactId = contactId,
            lookupKey = lookupKey,
            displayName = displayName,
            photoUri = photoUri,
            numbers = numbers.toList()
        )
    }
}
