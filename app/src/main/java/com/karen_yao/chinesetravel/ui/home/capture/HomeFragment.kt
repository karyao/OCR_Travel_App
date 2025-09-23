package com.karen_yao.chinesetravel.ui.home

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.karen_yao.chinesetravel.R
import com.karen_yao.chinesetravel.repo
import com.karen_yao.chinesetravel.ui.capture.CaptureFragment
import kotlinx.coroutines.launch
import androidx.recyclerview.widget.LinearLayoutManager
import android.util.Log

class HomeFragment : Fragment(R.layout.fragment_home) {
    private val vm by lazy {
        ViewModelProvider(this, HomeVMFactory(repo()))[HomeViewModel::class.java]
    }

    override fun onViewCreated(v: View, s: Bundle?) {
        val rv = v.findViewById<RecyclerView>(R.id.recycler)
        val ad = SnapsAdapter()
        rv.adapter = ad
        rv.layoutManager = LinearLayoutManager(requireContext())

        viewLifecycleOwner.lifecycleScope.launch {
            vm.snaps.collect { list ->
                Log.d("HomeFragment", "snaps size=${list.size}")
                ad.submitList(list)
            }
        }

        v.findViewById<FloatingActionButton>(R.id.fabCapture).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.container, CaptureFragment())
                .addToBackStack(null)
                .commit()
        }
    }
}
