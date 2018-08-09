package trudoc24x7.telehealth.permissions

import java.util.HashMap

interface PermissionCallBack {

    fun onResponseReceived(mapPermissionGrants: HashMap<String,PermissionState>)
}