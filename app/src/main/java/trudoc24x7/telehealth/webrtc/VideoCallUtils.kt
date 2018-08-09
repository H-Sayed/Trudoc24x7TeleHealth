package trudoc24x7.telehealth.webrtc

import android.Manifest

import trudoc24x7.telehealth.permissions.PermissionsHelper

object VideoCallUtils {

    val defaultPermissions = arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)

    fun isPermissionsGranted(helper: PermissionsHelper): Boolean {
        var granted = true
        for (permission in defaultPermissions) {
            if (!helper.isPermissionGranted(permission)) {
                granted = false
                return granted
            }
        }
        return granted
    }

}
