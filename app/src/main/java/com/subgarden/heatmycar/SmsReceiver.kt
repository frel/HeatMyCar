package com.subgarden.heatmycar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.ContactsContract
import android.support.v4.content.ContextCompat.startActivity
import android.telephony.PhoneNumberUtils
import android.telephony.SmsMessage
import android.util.Log
import android.widget.Toast


/**
 * @author Fredrik Larsen (f@subgarden.com)
 */
class SmsReceiver : BroadcastReceiver() {

    data class Contact(val name: String, val number: String, val starred: Boolean)

    override fun onReceive(context: Context, intent: Intent) {
        Log.i("####", "onReceive called.")
        val bundle = intent.extras ?: return

        val pdus = bundle.get("pdus") as Array<*>

        val messages = Array(pdus.size) { index ->
            SmsMessage.createFromPdu(pdus[index] as ByteArray)
        }

        // One SMS can be split into multiple messages. For short commands this will always be 1.
        val messageBody = messages.joinToString { it.messageBody }
        val address = messages[0].originatingAddress

        // Check if number is from favourite contact
        val allContacts = getAllContacts(context)
        val noFavourites = allContacts.none { it.starred }
        if (noFavourites) {
            Toast.makeText(context, "No favourite contacts added", Toast.LENGTH_LONG).show()
            return
        }

        val contact = allContacts.find { PhoneNumberUtils.compare(it.number, address) && it.starred }
                      ?: run { Log.d("####", "Declining message from $address"); return }
        Log.d("####", "Accepting message from $address - ${contact.name} (${contact.number})")


        Toast.makeText(context, "($address) $messageBody", Toast.LENGTH_LONG).show()

        val outlanderAppIntent = context.packageManager.getLaunchIntentForPackage("com.inventec.iMobile1")
        if (outlanderAppIntent == null) {
            Toast.makeText(context, "Unable to open Outlander Remote App", Toast.LENGTH_LONG).show()
            // TODO: Add state for missing app?
            return
        }
        try {
            startActivity(context, outlanderAppIntent, null)
        } catch (e: RuntimeException) {
            Log.d("####", "Unable to open Outlander app ${e.message}")
        }
    }

    private fun getAllContacts(context: Context): List<Contact> {
        val projections = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                                  ContactsContract.CommonDataKinds.Phone.NUMBER,
                                  ContactsContract.CommonDataKinds.Phone.STARRED)

        val phones = context.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                                                   projections,
                                                   null,
                                                   null,
                                                   null)
        return phones.use { cursor ->
            generateSequence {
                if (cursor.moveToNext()) cursor else null }
                    .map {
                        Contact(
                                it.getString(phones.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)),
                                it.getString(phones.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)),
                                it.getInt(phones.getColumnIndex(ContactsContract.CommonDataKinds.Phone.STARRED)) == 1)

                    }.toList()
        }

    }

}