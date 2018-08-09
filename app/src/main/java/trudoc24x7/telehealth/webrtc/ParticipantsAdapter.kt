package trudoc24x7.telehealth.webrtc

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.TableRow
import com.tokbox.android.otsdkwrapper.utils.MediaType
import butterknife.BindView
import butterknife.ButterKnife
import trudoc24x7.telehealth.R

internal class ParticipantsAdapter(
        participantsList: List<Participant>,
        private val mListener: ParticipantAdapterListener
) : RecyclerView.Adapter<ParticipantsAdapter.ParticipantViewHolder>() {
    private var mParticipantsList: List<Participant> = participantsList

    interface ParticipantAdapterListener {
        fun mediaControlChanged(remoteId: String)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ParticipantViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(viewType, parent, false)
        return ParticipantViewHolder(view)
    }

    override fun getItemViewType(position: Int): Int {
        return R.layout.grid_item
    }

    override fun onBindViewHolder(holder: ParticipantViewHolder, position: Int) {
        val participant = mParticipantsList[position]
        holder.container.removeAllViews()
        holder.id = participant.getParticipantId()
        holder.type = participant.type
        holder.listener = mListener
        val params = TableRow.LayoutParams(
                participant.participantContainer.width,
                participant.participantContainer.height
        )
        holder.container.layoutParams = params
        if (!participant.status.has(MediaType.VIDEO)
                || participant.type == Participant.Type.REMOTE
                && !participant.status.subscribedTo(MediaType.VIDEO)
        ) {
            holder.audiOnlyView.visibility = View.VISIBLE
            holder.container.addView(holder.audiOnlyView, params)
        } else {
            holder.audiOnlyView.visibility = View.GONE
            if (participant.status.view != null) {
                val parent = participant.status.view.parent as ViewGroup
                parent.removeView(participant.status.view)
                holder.container.addView(participant.status.view)
            }
        }
    }

    override fun getItemCount(): Int {
        return mParticipantsList.size
    }

    internal inner class ParticipantViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        var audiOnlyView: RelativeLayout = view.findViewById(R.id.audioOnlyView)
        var container: RelativeLayout = view.findViewById(R.id.itemView)
        var controls: RelativeLayout = view.findViewById(R.id.remoteControls)

        lateinit var id: String
        lateinit var type: Participant.Type
        lateinit var listener: ParticipantAdapterListener

        init {
            view.setOnClickListener {
                if (type == Participant.Type.REMOTE) {
                    container.removeView(controls)
                    if (controls.visibility == View.GONE) {
                        controls.visibility = View.VISIBLE
                        container.addView(controls)
                    } else {
                        controls.visibility = View.GONE
                    }
                    listener.mediaControlChanged(id)
                }
            }
        }


    }


}