package trudoc24x7.telehealth.webrtc

import android.util.Size


import com.tokbox.android.otsdkwrapper.utils.StreamStatus

class Participant {

    var type: Type
    lateinit var id: String
    var status: StreamStatus
    var container: Size? = null
    var participantContainer: ParticipantSize
    enum class Type {
        LOCAL,
        REMOTE
    }

    constructor(type: Type, status: StreamStatus, containerSize: ParticipantSize) {
        this.type = type
        this.status = status
        this.participantContainer = containerSize
    }

    constructor(type: Type, status: StreamStatus, containerSize: ParticipantSize, id: String) {
        this.type = type
        this.status = status
        this.participantContainer = containerSize
        this.id = id
    }

    fun setContainerSize(size: ParticipantSize) {
        this.participantContainer = size
    }

    fun getParticipantId(): String {
        return id
    }
}
