package com.alarm.tool

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class AlarmAdapter(
    private val onItemClick: (Alarm) -> Unit,
    private val onItemLongClick: (Alarm) -> Unit,
    private val onToggle: (Alarm, Boolean) -> Unit
) : ListAdapter<Alarm, AlarmAdapter.AlarmViewHolder>(AlarmDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlarmViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_alarm, parent, false)
        return AlarmViewHolder(view)
    }

    override fun onBindViewHolder(holder: AlarmViewHolder, position: Int) {
        val alarm = getItem(position)
        holder.bind(alarm)
    }

    inner class AlarmViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val timeTextView: TextView = itemView.findViewById(R.id.tv_alarm_time)
        private val repeatDaysTextView: TextView = itemView.findViewById(R.id.tv_repeat_days)
        private val taskTypeTextView: TextView = itemView.findViewById(R.id.tv_task_type)
        private val enableSwitch: SwitchCompat = itemView.findViewById(R.id.switch_alarm_enabled)

        fun bind(alarm: Alarm) {
            timeTextView.text = alarm.getFormattedTime()
            repeatDaysTextView.text = alarm.getRepeatDaysString()
            taskTypeTextView.text = when (alarm.taskType) {
                Alarm.TaskType.MATH -> "数学题"
                Alarm.TaskType.SHAKE -> "摇晃"
                Alarm.TaskType.TTS_READ -> "朗读文本"
                // Add more task type descriptions here
            }
            enableSwitch.isChecked = alarm.isEnabled

            enableSwitch.setOnCheckedChangeListener { _, isChecked ->
                onToggle(alarm, isChecked)
            }

            itemView.setOnClickListener { onItemClick(alarm) }
            itemView.setOnLongClickListener { 
                onItemLongClick(alarm)
                true
            }
        }
    }

    private class AlarmDiffCallback : DiffUtil.ItemCallback<Alarm>() {
        override fun areItemsTheSame(oldItem: Alarm, newItem: Alarm): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Alarm, newItem: Alarm): Boolean {
            return oldItem == newItem
        }
    }
} 