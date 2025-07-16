package com.alarm.tool

import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlin.random.Random
import com.alarm.tool.dpToPx
import android.util.TypedValue

class MathTask : BaseTask {

    private var problem: String = ""
    private var answer: Int = 0
    private var answerInput: TextInputEditText? = null

    override fun generateTask(context: Context): View {
        val num1 = Random.nextInt(10, 50)
        val num2 = Random.nextInt(1, 10)
        val operation = Random.nextInt(4)

        when (operation) {
            0 -> {
                problem = "$num1 + $num2 = ?"
                answer = num1 + num2
            }
            1 -> {
                problem = "$num1 - $num2 = ?"
                answer = num1 - num2
            }
            2 -> {
                problem = "$num1 * $num2 = ?"
                answer = num1 * num2
            }
            3 -> {
                // Ensure division results in a whole number
                val divisibleNum1 = num1 * num2
                problem = "$divisibleNum1 / $num2 = ?"
                answer = divisibleNum1 / num2
            }
        }

        val linearLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT  // Changed to MATCH_PARENT
            )
            setBackgroundColor(context.getColor(android.R.color.white))  // Force white background
            setPadding(16.dpToPx(context), 32.dpToPx(context), 16.dpToPx(context), 32.dpToPx(context))
        }

        val problemTextView = TextView(context).apply {
            text = problem
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 32f)  // Increased text size
            gravity = Gravity.CENTER
            setTextColor(context.getColor(android.R.color.black))  // Force black text
            setPadding(0, 32.dpToPx(context), 0, 32.dpToPx(context))
        }

        val textInputLayout = TextInputLayout(context, null, com.google.android.material.R.style.Widget_MaterialComponents_TextInputLayout_OutlinedBox).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(32.dpToPx(context), 16.dpToPx(context), 32.dpToPx(context), 16.dpToPx(context))
            }
            hint = "请输入答案"
        }

        answerInput = TextInputEditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED  // Allow negative numbers
            maxLines = 1
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)  // Increased text size
            setTextColor(context.getColor(android.R.color.black))  // Force black text
        }
        textInputLayout.addView(answerInput)

        linearLayout.addView(problemTextView)
        linearLayout.addView(textInputLayout)

        // Request focus and show keyboard
        answerInput?.post {
            answerInput?.requestFocus()
        }

        return linearLayout
    }

    override fun verifyTask(): Boolean {
        val userAnswer = answerInput?.text.toString().toIntOrNull()
        return userAnswer != null && userAnswer == answer
    }

    override fun getTaskType(): Alarm.TaskType {
        return Alarm.TaskType.MATH
    }

    override fun restoreState(bundle: Bundle?) {
        bundle?.let {
            problem = it.getString("math_problem", "")
            answer = it.getInt("math_answer", 0)
            answerInput?.setText(it.getString("user_answer", ""))
        }
    }

    override fun saveState(bundle: Bundle) {
        bundle.putString("math_problem", problem)
        bundle.putInt("math_answer", answer)
        bundle.putString("user_answer", answerInput?.text.toString())
    }
} 