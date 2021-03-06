package net.ivpn.client.common.extension

/*
 IVPN Android app
 https://github.com/ivpn/android-app
 <p>
 Created by Oleksandr Mykhailenko.
 Copyright (c) 2020 Privatus Limited.
 <p>
 This file is part of the IVPN Android app.
 <p>
 The IVPN Android app is free software: you can redistribute it and/or
 modify it under the terms of the GNU General Public License as published by the Free
 Software Foundation, either version 3 of the License, or (at your option) any later version.
 <p>
 The IVPN Android app is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 details.
 <p>
 You should have received a copy of the GNU General Public License
 along with the IVPN Android app. If not, see <https://www.gnu.org/licenses/>.
*/

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.VpnService
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import net.ivpn.client.ui.dialog.DialogBuilder
import net.ivpn.client.ui.dialog.Dialogs

private fun Fragment.openErrorDialog(dialogs: Dialogs) {
    DialogBuilder.createNotificationDialog(context, dialogs)
}

fun Fragment.checkVPNPermission(requestCode: Int) {
    val intent: Intent?
    intent = try {
        VpnService.prepare(context)
    } catch (exception: Exception) {
        exception.printStackTrace()
        openErrorDialog(Dialogs.FIRMWARE_ERROR)
        return
    }
    if (intent != null) {
        try {
            startActivityForResult(intent, requestCode)
        } catch (ane: ActivityNotFoundException) {
        }
    } else {
        onActivityResult(requestCode, Activity.RESULT_OK, null)
    }
}

fun Fragment.findNavControllerSafely(): NavController? {
    return if (isAdded) {
        findNavController()
    } else {
        null
    }
}