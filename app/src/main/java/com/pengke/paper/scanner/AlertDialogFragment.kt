package com.pengke.paper.scanner

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import kotlinx.android.synthetic.main.fragment_confirm_dialog.*

class AlertDialogFragment : DialogFragment() {
    private lateinit var listener: BtnListener

    interface BtnListener {
        fun onDecisionClick()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        try {
            listener = context as BtnListener
        } catch (e: ClassCastException) {
            throw ClassCastException(("$context must implement BtnListener"))
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireContext())
        val inflater = requireActivity().layoutInflater
        val view = inflater.inflate(R.layout.fragment_alert_dialog, container, false)
        val confirmBtn = view.findViewById<Button>(R.id.confirmBtn)

        builder.setView(view)
        val dialog = builder.create()
        dialog.show()

        confirmBtn.setOnClickListener {
            dialog.dismiss()
            listener.onDecisionClick()
        }

        return dialog
    }
}