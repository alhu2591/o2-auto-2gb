package com.o2.auto2gb

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.o2.auto2gb.databinding.FragmentTestBinding
import com.o2.auto2gb.databinding.ItemLogBinding

class TestFragment : Fragment() {

    private var _b: FragmentTestBinding? = null
    private val b get() = _b!!

    private val TRIGGER_KEYWORD = "weiter"
    private val REPLY_MESSAGE = "Weiter"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _b = FragmentTestBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        b.btnBack.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, HomeFragment())
                .commit()
            (activity as? MainActivity)?.let {
                it.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNav)
                    ?.selectedItemId = R.id.nav_home
            }
        }

        // Live match detection
        b.etTrigger.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { checkMatch(s?.toString() ?: "") }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        b.btnTestNow.setOnClickListener {
            val input = b.etTrigger.text?.toString() ?: ""
            if (input.isBlank()) return@setOnClickListener
            if (input.contains(TRIGGER_KEYWORD, ignoreCase = true)) {
                addLogEntry(input, REPLY_MESSAGE)
            }
        }

        b.btnClearLogs.setOnClickListener {
            b.logContainer.removeAllViews()
            b.tvEndOfLog.visibility = View.GONE
        }
    }

    private fun checkMatch(text: String) {
        val matches = text.contains(TRIGGER_KEYWORD, ignoreCase = true)
        b.cardMatchIndicator.visibility = if (matches) View.VISIBLE else View.GONE
        if (matches) {
            b.tvMatchText.text = getString(R.string.rule_matches)
        }
    }

    private fun addLogEntry(incoming: String, outgoing: String) {
        val ctx = requireContext()
        val logBinding = ItemLogBinding.inflate(layoutInflater, b.logContainer, false)
        logBinding.tvIncoming.text = incoming
        logBinding.tvOutgoing.text = outgoing
        logBinding.tvTime.text = getString(R.string.just_now)
        b.logContainer.addView(logBinding.root, 0)
        b.tvEndOfLog.visibility = View.VISIBLE
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
