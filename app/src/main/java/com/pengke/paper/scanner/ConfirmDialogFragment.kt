package com.pengke.paper.scanner

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.view.setMargins
import androidx.fragment.app.DialogFragment
import butterknife.internal.ListenerMethod
import com.pengke.paper.scanner.R
import kotlinx.android.synthetic.main.fragment_confirm_dialog.*
import java.lang.IllegalStateException

class ConfirmDialogFragment : DialogFragment() {
    internal lateinit var listener: BtnListener

    interface BtnListener {
        fun onDecisionClick()
        fun onCancelClick()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        try {
            listener = context as BtnListener
        } catch (e: ClassCastException) {
            throw ClassCastException(("$context must implement NoticeDialogListener"))
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireContext())
        val inflater = requireActivity().layoutInflater
        val view = inflater.inflate(R.layout.fragment_confirm_dialog, container, false)
        val cancelBtn = view.findViewById<Button>(R.id.cancelBtn)
        val decisionBtn = view.findViewById<Button>(R.id.decisionBtn)

        builder.setView(view)
        val dialog = builder.create()
        dialog.show()

        cancelBtn.setOnClickListener {
            dialog.dismiss()
            listener.onCancelClick()
        }

        decisionBtn.setOnClickListener {
            dialog.dismiss()
            listener.onDecisionClick()
        }

        return dialog
    }
}