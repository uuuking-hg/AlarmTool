package com.alarm.tool

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TaskTypeAdapter(
    private val onTaskTypeSelected: (Alarm.TaskType) -> Unit
) : RecyclerView.Adapter<TaskTypeAdapter.TaskTypeViewHolder>() {

    var selectedTaskType: Alarm.TaskType = Alarm.TaskType.MATH
        set(value) {
            val oldPosition = taskTypes.indexOf(field)
            field = value
            val newPosition = taskTypes.indexOf(field)
            if (oldPosition != -1) notifyItemChanged(oldPosition)
            if (newPosition != -1) notifyItemChanged(newPosition)
        }

    private val taskTypes = Alarm.TaskType.values().toList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskTypeViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_task_type, parent, false)
        return TaskTypeViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskTypeViewHolder, position: Int) {
        val taskType = taskTypes[position]
        holder.bind(taskType)
    }

    override fun getItemCount(): Int = taskTypes.size

    inner class TaskTypeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardLayout: LinearLayout = itemView.findViewById(R.id.ll_task_type_card)
        private val iconImageView: ImageView = itemView.findViewById(R.id.iv_task_icon)
        private val nameTextView: TextView = itemView.findViewById(R.id.tv_task_name)

        fun bind(taskType: Alarm.TaskType) {
            nameTextView.text = when (taskType) {
                Alarm.TaskType.MATH -> itemView.context.getString(R.string.math_task_name)
                Alarm.TaskType.SHAKE -> itemView.context.getString(R.string.shake_task_name)
                Alarm.TaskType.TTS_READ -> itemView.context.getString(R.string.tts_read_task_name)
            }
            iconImageView.setImageResource(when (taskType) {
                Alarm.TaskType.MATH -> R.drawable.ic_math
                Alarm.TaskType.SHAKE -> R.drawable.ic_shake
                Alarm.TaskType.TTS_READ -> R.drawable.ic_tts
            })

            cardLayout.isSelected = (taskType == selectedTaskType)

            itemView.setOnClickListener {
                selectedTaskType = taskType // Use the property setter
                onTaskTypeSelected(taskType)
            }
        }
    }
} 