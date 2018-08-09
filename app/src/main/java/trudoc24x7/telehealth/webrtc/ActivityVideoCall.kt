package trudoc24x7.telehealth.webrtc

import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.DisplayMetrics
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import com.opentok.android.OpentokError
import com.tokbox.android.annotations.utils.AnnotationsVideoRenderer
import com.tokbox.android.otsdkwrapper.listeners.AdvancedListener
import com.tokbox.android.otsdkwrapper.listeners.BasicListener
import com.tokbox.android.otsdkwrapper.listeners.ListenerException
import com.tokbox.android.otsdkwrapper.utils.MediaType
import com.tokbox.android.otsdkwrapper.utils.OTConfig
import com.tokbox.android.otsdkwrapper.utils.PreviewConfig
import com.tokbox.android.otsdkwrapper.wrapper.OTWrapper
import java.util.ArrayList
import java.util.HashMap
import java.util.Locale
import butterknife.BindView
import butterknife.ButterKnife
import butterknife.Unbinder
import trudoc24x7.telehealth.R
import trudoc24x7.telehealth.TeleMedicineEventsBus
import trudoc24x7.telehealth.events.*
import trudoc24x7.telehealth.permissions.PermissionCallBack
import trudoc24x7.telehealth.permissions.PermissionState
import trudoc24x7.telehealth.permissions.PermissionsHelper


class ActivityVideoCall : AppCompatActivity(), VideoCallView, UIFragment.PreviewControlCallbacks, ParticipantsAdapter.ParticipantAdapterListener {
    override fun dismissWithConfigError() {
        showError(getString(R.string.config_rejected))
        finish()
    }

    override fun dismissWithPermissionError() {
        showError(getString(R.string.permission_rejected))
        finish()
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    internal lateinit var wrapper: OTWrapper
    private lateinit var mParticipantsGrid: RecyclerView
    lateinit var mLayoutManager: GridLayoutManager
    private lateinit var mParticipantsAdapter: ParticipantsAdapter
    lateinit var mParticipantsList: ArrayList<Participant>
    private lateinit var mControlsFragment: UIFragment
    private lateinit var mRemoteRenderer: AnnotationsVideoRenderer
    private lateinit var mAlert: TextView
    private lateinit var mCallDurationView: TextView
    private lateinit var mCallStatus: TextView

    private var isCallConnected = false
    private var isConnected = false
    private var isClosedByUser = false

    private lateinit var mCurrentRemote: String
    private lateinit var mPermissionHelper: PermissionsHelper
    private lateinit var mRingTonePlayer: MediaPlayer
    private lateinit var mSessionHandler: Handler
    private lateinit var mSessionRunnable: Runnable
    private var mCallTimerRunnable: Runnable? = null
    private val mCallTimeOut = 60000
    private val mCallDurationTick = 1000
    private var mCallDuration = 0
    private lateinit var loadingProgress: ProgressDialog
    private lateinit var mPresenter: VideoCallPresenterImpl

    companion object {

        @JvmStatic
        val SESSION_ID: String = "VIDEO_SESSION_ID"
        @JvmStatic
        val SESSION_TOKEN: String = "VIDEO_SESSION_TOKEN"
        @JvmStatic
        val SESSION_KEY: String = "VIDEO_SESSION_KEY"
        @JvmStatic
        val SESSION_USER_NAME: String = "VIDEO_SESSION_USER_NAME"

        @JvmStatic
        fun newIntent(context: Context, sessionId: String, sessionToken: String, sessionKey: String, userName: String): Intent {
            val intent = Intent(context, ActivityVideoCall::class.java)
            intent.putExtra(SESSION_ID, sessionId)
            intent.putExtra(SESSION_TOKEN, sessionToken)
            intent.putExtra(SESSION_KEY, sessionKey)
            intent.putExtra(SESSION_USER_NAME, userName)

            return intent
        }
    }


    private val participantSize: ParticipantSize
        get() {
            val metrics = DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(metrics)
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            if (mParticipantsList.size == 2) {
                return ParticipantSize(width, height / 2)
            } else {
                if (mParticipantsList.size > 2) {
                    return ParticipantSize(width / 2, height / 2)
                }
            }
            return ParticipantSize(width, height)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_call)
        initUI()
        onInitStarted()
    }

    private fun initUI() {
        mParticipantsGrid = findViewById(R.id.grid_container)
        mAlert = findViewById(R.id.quality_warning)
        mCallDurationView = findViewById(R.id.duration)
        mCallStatus = findViewById(R.id.status)
    }

    private fun onInitStarted() {
        mPresenter = VideoCallPresenterImpl(this)
        mRingTonePlayer = MediaPlayer.create(this, R.raw.twilio_dial)
        mRingTonePlayer.isLooping = true
        mPermissionHelper = PermissionsHelper(this)
        mSessionHandler = Handler()
        mLayoutManager = GridLayoutManager(this, 2, GridLayoutManager.VERTICAL, false)
        setupMultipartyLayout()
        mParticipantsAdapter = ParticipantsAdapter(mParticipantsList, this)
        mParticipantsGrid.adapter = mParticipantsAdapter
        mParticipantsGrid.layoutManager = mLayoutManager
        val sessionId = intent.extras.getString(SESSION_ID)
        val sessionToken = intent.extras.getString(SESSION_KEY)
        val sessionKey = intent.extras.getString(SESSION_TOKEN)
        val sessionUserName = intent.extras.getString(SESSION_USER_NAME)
        mPresenter.onCreateView(sessionId, sessionToken, sessionKey, sessionUserName)
    }

    override fun askForRequiredPermissions() {
        mPermissionHelper.requestPermissions(VideoCallUtils.defaultPermissions, object : PermissionCallBack {
            override fun onResponseReceived(mapPermissionGrants: HashMap<String, PermissionState>) {
                mPresenter.onPermissionStateChange(VideoCallUtils.isPermissionsGranted(mPermissionHelper))
            }
        })
    }

    override fun initWrapper(config: OTConfig.OTConfigBuilder) {
        config.name(getString(R.string.app_name))
        wrapper = OTWrapper(this, config.build())
        addWrapperBasicListener()
        addWrapperAdvancedListener()
        startWrapperConnection()
    }

    private fun addWrapperBasicListener() {
        wrapper.addBasicListener(object : BasicListener<OTWrapper> {

            override fun onConnected(otWrapper: OTWrapper, participantsCount: Int, connId: String, data: String) {
                if (wrapper.ownConnId === connId) {
                    onWrapperConnected()
                }
            }

            override fun onDisconnected(otWrapper: OTWrapper, participantsCount: Int, connId: String, data: String) {
                if (connId === wrapper.ownConnId) {
                    if (!isClosedByUser) {
                        TeleMedicineEventsBus.getInstance().sendEvent(CallFailEvent("Session Connection Dropped"))
                        TeleMedicineEventsBus.getInstance().sendEvent(CallEndedEvent())
                        finish()
                    } else
                        isConnected = false
                }
            }

            override fun onPreviewViewReady(otWrapper: OTWrapper, localView: View) {
                val participant = Participant(Participant.Type.LOCAL, wrapper.localStreamStatus, participantSize)
                addNewParticipant(participant)
            }

            override fun onPreviewViewDestroyed(otWrapper: OTWrapper, localView: View) {
                removeParticipant(Participant.Type.LOCAL, null)
            }

            override fun onRemoteViewReady(otWrapper: OTWrapper, remoteView: View, remoteId: String, data: String) {
                val newParticipant = Participant(Participant.Type.REMOTE, wrapper.getRemoteStreamStatus(remoteId), participantSize, remoteId)
                addNewParticipant(newParticipant)
            }

            override fun onRemoteViewDestroyed(otWrapper: OTWrapper, remoteView: View, remoteId: String) {
                removeParticipant(Participant.Type.REMOTE, remoteId)
            }

            override fun onStartedPublishingMedia(otWrapper: OTWrapper, screensharing: Boolean) {
                mControlsFragment.setCallButtonEnabled(true)
            }

            override fun onStoppedPublishingMedia(otWrapper: OTWrapper, isScreensharing: Boolean) {
                mControlsFragment.setCallButtonEnabled(true)
            }

            override fun onRemoteJoined(otWrapper: OTWrapper, remoteId: String) {
            }

            override fun onRemoteLeft(otWrapper: OTWrapper, remoteId: String) {
            }

            override fun onRemoteVideoChanged(otWrapper: OTWrapper, remoteId: String, reason: String, videoActive: Boolean, subscribed: Boolean) {
                if (reason == "quality") {
                    showRemoteVideoQualityError()
                }
                updateParticipant(Participant.Type.REMOTE, remoteId, videoActive)

            }

            override fun onError(otWrapper: OTWrapper, error: OpentokError) {
                endCallWithError(error)
            }
        })
    }

    private fun showRemoteVideoQualityError() {
        mAlert.setBackgroundResource(R.color.quality_alert)
        mAlert.setTextColor(ContextCompat.getColor(this, R.color.white))
        mAlert.bringToFront()
        mAlert.visibility = View.VISIBLE
        mAlert.postDelayed({ mAlert.visibility = View.GONE }, 7000)
    }

    private fun showLocalVideoQualityError() {
        mAlert.setBackgroundResource(R.color.quality_warning)
        mAlert.setTextColor(ContextCompat.getColor(this, R.color.warning_text))
        mAlert.bringToFront()
        mAlert.visibility = View.VISIBLE
        mAlert.postDelayed({ mAlert.visibility = View.GONE }, 7000)
    }

    private fun onWrapperConnected() {
        mCallStatus.text = getString(R.string.video_call_connecting)
        isConnected = true
        loadingProgress.dismiss()
        mRingTonePlayer.start()
        onCallStarted()
        startCallTimeOut()
    }

    private fun addWrapperAdvancedListener() {

        wrapper.addAdvancedListener(object : AdvancedListener<OTWrapper> {

            override fun onCameraChanged(otWrapper: OTWrapper) {
            }

            @Throws(ListenerException::class)
            override fun onReconnecting(otWrapper: OTWrapper) {
                mCallStatus.setText(R.string.reconnecting)
            }

            override fun onReconnected(otWrapper: OTWrapper) {
                mCallStatus.setText(R.string.video_call_connected)
            }

            override fun onVideoQualityWarning(otWrapper: OTWrapper, remoteId: String) {
                showLocalVideoQualityError()
            }

            override fun onVideoQualityWarningLifted(otWrapper: OTWrapper, remoteId: String) {
            }

            override fun onError(otWrapper: OTWrapper, error: OpentokError) {
                endCallWithError(error)
            }
        })
    }

    private fun startWrapperConnection() {
        mRemoteRenderer = AnnotationsVideoRenderer(this)
        wrapper.setRemoteVideoRenderer(mRemoteRenderer, true)
        showProgressDialog()
        initControlsView()
        wrapper.connect()
    }

    private fun showProgressDialog() {
        loadingProgress = ProgressDialog(this)
        loadingProgress.setTitle(getString(R.string.connection_wait_title))
        loadingProgress.setMessage(getString(R.string.connection_wait_message))
        loadingProgress.setCancelable(false)
        loadingProgress.show()
    }

    private fun initControlsView() {
        initControlsFragment()
        supportFragmentManager.beginTransaction().commitAllowingStateLoss()
    }

    private fun stopMediaPlayer() {
        mRingTonePlayer.stop()
    }

    override fun onPause() {
        super.onPause()
        wrapper.pause()
    }

    override fun onResume() {
        super.onResume()
        wrapper.resume(true)
    }

    override fun mediaControlChanged(remoteId: String) {
        mCurrentRemote = remoteId
    }

    override fun onCameraSwitch() {
        wrapper.cycleCamera()
    }

    override fun onDisableLocalVideo(video: Boolean) {
        wrapper.enableLocalMedia(MediaType.VIDEO, video)
        updateParticipant(Participant.Type.LOCAL, null, video)
    }

    override fun onDisableLocalAudio(audio: Boolean) {
        wrapper.enableLocalMedia(MediaType.AUDIO, audio)
    }

    override fun onCall() {
        isClosedByUser = true
        if (isCallConnected) {
            wrapper.stopPublishingMedia(false)
            endCallWithSuccess()
        } else {
            wrapper.disconnect()
            stopMediaPlayer()
            mSessionHandler.removeCallbacksAndMessages(null)
            TeleMedicineEventsBus.getInstance().sendEvent(CallEndedEvent())
            finish()
        }
    }

    private fun onCallStarted() {
        if (isConnected) {
            wrapper.startPublishingMedia(PreviewConfig.PreviewConfigBuilder()
                    .name(mPresenter.getUserName()).build(), false)
            mControlsFragment.setEnabled(true)
        }
    }

    private fun initControlsFragment() {
        mControlsFragment = UIFragment()
        supportFragmentManager.beginTransaction()
                .add(R.id.actionbar_fragment_container, mControlsFragment).commit()
    }

    private fun addNewParticipant(newParticipant: Participant) {
        mParticipantsList.add(newParticipant)
        updateParticipantList()
        if (mParticipantsList.size > 1) {
            mCallStatus.text = getString(R.string.video_call_connected)
            isCallConnected = true
            stopMediaPlayer()
            if (mCallTimerRunnable == null) {
                mCallTimerRunnable = object : Runnable {
                    override fun run() {
                        mCallDuration++
                        setDurationValue()
                        mSessionHandler.postDelayed(this, mCallDurationTick.toLong())
                    }
                }
                mSessionHandler.postDelayed(mCallTimerRunnable, mCallDurationTick.toLong())
            }
        }
    }

    private fun setDurationValue() {
        val result = String.format(Locale.ENGLISH, "%02d:%02d", mCallDuration / 60 % 60, mCallDuration % 60)
        mCallDurationView.text = result
    }

    private fun startCallTimeOut() {
        mSessionRunnable = Runnable { checkCallStatus() }
        mSessionHandler.postDelayed(mSessionRunnable, mCallTimeOut.toLong())
    }

    private fun checkCallStatus() {
        if (!isCallConnected) {
            isClosedByUser = true
            wrapper.disconnect()
            TeleMedicineEventsBus.getInstance().sendEvent(AgentBusyEvent())
            stopMediaPlayer()
            TeleMedicineEventsBus.getInstance().sendEvent(CallEndedEvent())
            finish()
        }
    }

    private fun endCallWithError(error: OpentokError) {
        loadingProgress.dismiss()
        wrapper.disconnect()
        stopMediaPlayer()
        TeleMedicineEventsBus.getInstance().sendEvent(CallFailEvent(error.message))
        mSessionHandler.removeCallbacksAndMessages(null)
        TeleMedicineEventsBus.getInstance().sendEvent(CallTimeOutEvent())
        finish()
    }

    private fun endCallWithSuccess() {
        mSessionHandler.removeCallbacksAndMessages(null)
        wrapper.disconnect()
        TeleMedicineEventsBus.getInstance().sendEvent(SuccessCallEvent(mCallDuration))
        mCallDuration = 0
        mCallStatus.text = getString(R.string.video_call_ended)
        isCallConnected = false
        TeleMedicineEventsBus.getInstance().sendEvent(CallEndedEvent())
        finish()
    }

    private fun removeParticipant(type: Participant.Type, id: String?) {
        for (i in mParticipantsList.indices) {
            val participant = mParticipantsList[i]
            if (participant.type == type) {
                if (type == Participant.Type.REMOTE) {
                    if (participant.id == id) {
                        mParticipantsList.removeAt(i)
                    }
                } else {
                    mParticipantsList.removeAt(i)
                }
            }
        }
        updateParticipantList()
        mParticipantsList.reverse()
        mParticipantsAdapter.notifyDataSetChanged()
        if ((mParticipantsList.isEmpty() || mParticipantsList.size == 1) && isCallConnected) {
            if (!isClosedByUser) {
                endCallWithSuccess()
            }
        }
    }

    private fun updateParticipantList() {
        for (i in mParticipantsList.indices) {
            val participant = mParticipantsList[i]
            if (i == 0) {
                val metrics = DisplayMetrics()
                windowManager.defaultDisplay.getMetrics(metrics)
                val width = metrics.widthPixels
                val (_, height) = participantSize
                participant.setContainerSize(ParticipantSize(width, height))
            } else {
                participant.setContainerSize(participantSize)
            }
            mParticipantsList[i] = participant
        }
        mParticipantsList.reverse()
        mParticipantsAdapter.notifyDataSetChanged()
    }

    private fun updateParticipant(type: Participant.Type, id: String?, audioOnly: Boolean) {
        for (i in mParticipantsList.indices) {
            val participant = mParticipantsList[i]
            if (participant.type == type) {
                if (type == Participant.Type.REMOTE) {
                    if (participant.id == id) {
                        participant.status.setHas(MediaType.VIDEO, audioOnly)
                        mParticipantsList[i] = participant
                    }
                } else {
                    participant.status.setHas(MediaType.VIDEO, audioOnly)
                    mParticipantsList[i] = participant
                }
            }
        }
        mParticipantsAdapter.notifyDataSetChanged()
    }

    private fun setupMultipartyLayout() {
        mLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                if (mParticipantsList.size == 1) {
                    return 2
                } else {
                    if (mParticipantsList.size == 2) {
                        return if (position == 0) {
                            2
                        } else 1
                    } else {
                        if (mParticipantsList.size == 3) {
                            return if (position == 0 || position == 1) {
                                1
                            } else {
                                2
                            }
                        } else {
                            if (mParticipantsList.size == 4) {
                                return 1
                            } else {
                                if (mParticipantsList.size > 4) {
                                    return if (mParticipantsList.size % 2 != 0) {
                                        if (position == mParticipantsList.size - 1) {
                                            2
                                        } else {
                                            1
                                        }
                                    } else {
                                        1
                                    }
                                }
                            }
                        }
                    }
                }
                return 1
            }
        }
    }

    fun onRemoteVideoChanged(v: View) {
        if (wrapper.getRemoteStreamStatus(mCurrentRemote).subscribedTo(MediaType.VIDEO)) {
            wrapper.enableReceivedMedia(mCurrentRemote, MediaType.VIDEO, false)
            (v as ImageButton).setImageResource(R.drawable.no_video_icon)
            updateParticipant(Participant.Type.REMOTE, mCurrentRemote, true)

        } else {
            wrapper.enableReceivedMedia(mCurrentRemote, MediaType.VIDEO, true)
            (v as ImageButton).setImageResource(R.drawable.video_icon)
            updateParticipant(Participant.Type.REMOTE, mCurrentRemote, false)
        }
    }

    fun onRemoteAudioChanged(v: View) {
        if (wrapper.getRemoteStreamStatus(mCurrentRemote).subscribedTo(MediaType.AUDIO)) {
            wrapper.enableReceivedMedia(mCurrentRemote, MediaType.AUDIO, false)
            (v as ImageButton).setImageResource(R.drawable.no_audio_icon)
        } else {
            wrapper.enableReceivedMedia(mCurrentRemote, MediaType.AUDIO, true)
            (v as ImageButton).setImageResource(R.drawable.audio_icon)
        }
    }

    override fun onRequestPermissionsResult(permsRequestCode: Int, permissions: Array<String>,
                                            grantResults: IntArray) {
        mPermissionHelper.onRequestPermissionsResult(permissions, grantResults)
    }

    override fun onDestroy() {
        mPresenter.destroy()
        stopMediaPlayer()
        super.onDestroy()
    }

    override fun onBackPressed() {
    }


}
