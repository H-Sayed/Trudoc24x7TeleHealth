package trudoc24x7.telehealth.permissions

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import java.util.ArrayList
import java.util.HashMap

class PermissionsHelper(context: Context) {
    private lateinit var permissionCallBack: PermissionCallBack
    private val activityContext: Context = context
    private lateinit var mapPermissionsGrants: HashMap<String, PermissionState>

    companion object {
        private val PERMISSION_REQUEST_CODE = 100
    }

    fun isPermissionGranted(permission: String): Boolean {
        return ContextCompat
                .checkSelfPermission(activityContext, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun requestPermissions(permissions: Array<String>, permissionCallback: PermissionCallBack) {
        this.permissionCallBack = permissionCallback
        this.mapPermissionsGrants = HashMap()

        val lstToBeRequestedPermissions = ArrayList<String>()
        for (requestedPermission in permissions) {
            if (!isPermissionGranted(requestedPermission)) {
                lstToBeRequestedPermissions.add(requestedPermission)
                mapPermissionsGrants[requestedPermission] = PermissionState.DENIED
            } else if (isPermissionGranted(requestedPermission)) {
                mapPermissionsGrants[requestedPermission] = PermissionState.GRANTED
            } else if (!ActivityCompat.shouldShowRequestPermissionRationale(
                            activityContext as Activity, requestedPermission)) {
                mapPermissionsGrants[requestedPermission] = PermissionState.NEVERSHOW
            }
        }

        if (!lstToBeRequestedPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(activityContext as Activity,
                    lstToBeRequestedPermissions.toTypedArray(),
                    PERMISSION_REQUEST_CODE)
        } else {
            permissionCallback.onResponseReceived(mapPermissionsGrants)
        }
    }

    fun onRequestPermissionsResult(permissions: Array<String>, grantResults: IntArray) {
        for ((index, s) in permissions.withIndex()) {
            if (grantResults[index] == PackageManager.PERMISSION_GRANTED) {
                mapPermissionsGrants[s] = PermissionState.GRANTED
            } else if (!ActivityCompat.shouldShowRequestPermissionRationale(
                            activityContext as Activity, s)) {
                mapPermissionsGrants[s] = PermissionState.NEVERSHOW
            } else if (grantResults[index] == PackageManager.PERMISSION_DENIED) {
                mapPermissionsGrants[s] = PermissionState.DENIED
            }
        }
        mapPermissionsGrants.let { permissionCallBack.onResponseReceived(it) }
    }
}
