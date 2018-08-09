package trudoc24x7.telehealth.webrtc

import com.tokbox.android.otsdkwrapper.utils.OTConfig

interface VideoCallView {

    fun askForRequiredPermissions()
    fun initWrapper(config: OTConfig.OTConfigBuilder)
    fun dismissWithConfigError()
    fun dismissWithPermissionError()
}