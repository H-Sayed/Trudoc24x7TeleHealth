package trudoc24x7.telehealth.webrtc

import com.tokbox.android.otsdkwrapper.utils.OTConfig

class VideoCallPresenterImpl(private val callView: VideoCallView) : VideoCallPresenter {

    private lateinit var sessionId: String
    private lateinit var sessionToken: String
    private lateinit var sessionKey: String
    private lateinit var userName : String

    fun onCreateView(sessionId: String, sessionToken: String, sessionKey: String, userName: String = "") {
        if (sessionId.isEmpty() || sessionToken.isEmpty() || sessionKey.isEmpty()) {
            callView.dismissWithConfigError()
        } else {
            this.sessionId = sessionId
            this.sessionToken = sessionToken
            this.sessionKey = sessionKey
            this.userName = userName
            checkRequiredPermissions()
        }
    }

    private fun checkRequiredPermissions() {

    }

    fun onPermissionStateChange(granted: Boolean) {
        if (granted)
            callView.initWrapper(getOtConfig())
        else
            callView.dismissWithPermissionError()
    }

    private fun getOtConfig(): OTConfig.OTConfigBuilder {
        return OTConfig
                .OTConfigBuilder(sessionId, sessionToken, sessionKey)
                .subscribeAutomatically(true).subscribeToSelf(false)
    }

    fun getUserName(): String = userName

    override fun destroy() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }


    private fun getPateintDisplayName(firstName: String, lastName: String): String {
        return String.format("%s %s", firstName, lastName)
    }
}