package trudoc24x7.telehealth.webrtc

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import com.tokbox.android.otsdkwrapper.utils.MediaType
import trudoc24x7.telehealth.R


class UIFragment : Fragment() {
    private lateinit var mActivity: ActivityVideoCall
    private lateinit var mAudioBtn: ImageButton
    private lateinit var mVideoBtn: ImageButton
    private lateinit var mCallBtn: ImageButton
    lateinit var mSwitchCam: ImageButton

    private var mControlCallbacks: PreviewControlCallbacks = previewCallbacks

    companion object {

        private val LOGTAG = UIFragment::class.java.name

        private val previewCallbacks: PreviewControlCallbacks = object : PreviewControlCallbacks {
            override fun onDisableLocalAudio(audio: Boolean) {}

            override fun onDisableLocalVideo(video: Boolean) {}

            override fun onCall() {}

            override fun onCameraSwitch() {}
        }
    }

    private val mBtnClickListener = View.OnClickListener { v ->
        when (v.id) {
            R.id.mic_action_fab -> updateLocalAudio()
            R.id.video_action_fab -> updateLocalVideo()
            R.id.call_action_fab -> updateCall()
            R.id.switch_camera_action_fab -> cycleCamera()
        }
    }

    interface PreviewControlCallbacks {
        fun onDisableLocalAudio(audio: Boolean)

        fun onDisableLocalVideo(video: Boolean)

        fun onCall()

        fun onCameraSwitch()
    }

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        this.mActivity = context as ActivityVideoCall
        this.mControlCallbacks = context
    }

    override fun onAttach(activity: Activity?) {
        super.onAttach(activity)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            this.mActivity = activity as ActivityVideoCall
            this.mControlCallbacks = activity
        }
    }

    override fun onDetach() {
        super.onDetach()
        mControlCallbacks = previewCallbacks
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.actionbar_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        init(view)
    }

    private fun init(view: View) {
        mAudioBtn = view.findViewById(R.id.mic_action_fab)
        mVideoBtn = view.findViewById(R.id.video_action_fab)
        mCallBtn = view.findViewById(R.id.call_action_fab)
        mSwitchCam = view.findViewById(R.id.switch_camera_action_fab)
        mCallBtn.setOnClickListener(mBtnClickListener)
        setEnabled(false)
    }


    fun updateLocalAudio() {
        if (!mActivity.wrapper.isLocalMediaEnabled(MediaType.AUDIO)) {
            mControlCallbacks.onDisableLocalAudio(true)
            mAudioBtn.setBackgroundResource(R.drawable.ic_mic)
        } else {
            mControlCallbacks.onDisableLocalAudio(false)
            mAudioBtn.setBackgroundResource(R.drawable.ic_mic_closed)
        }
    }

    fun updateLocalVideo() {
        if (!mActivity.wrapper.isLocalMediaEnabled(MediaType.VIDEO)) {
            mControlCallbacks.onDisableLocalVideo(true)
            mVideoBtn.setBackgroundResource(R.drawable.ic_video)
        } else {
            mControlCallbacks.onDisableLocalVideo(false)
            mVideoBtn.setBackgroundResource(R.drawable.ic_vid_closed)
        }
    }

    fun updateCall() {
        mControlCallbacks.onCall()
    }


    private fun cycleCamera() {
        mControlCallbacks.onCameraSwitch()
    }

    fun setEnabled(enabled: Boolean) {
        if (enabled) {
            mAudioBtn.setOnClickListener(mBtnClickListener)
            mVideoBtn.setOnClickListener(mBtnClickListener)
            mSwitchCam.setOnClickListener(mBtnClickListener)

        } else {
            mAudioBtn.setOnClickListener(null)
            mVideoBtn.setOnClickListener(null)
            mSwitchCam.setOnClickListener(null)
        }
    }

    fun setCallButtonEnabled(enabled: Boolean) {
        mCallBtn.isEnabled = enabled
    }
}