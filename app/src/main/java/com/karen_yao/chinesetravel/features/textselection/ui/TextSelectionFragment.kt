package com.karen_yao.chinesetravel.features.textselection.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.karen_yao.chinesetravel.R
import com.karen_yao.chinesetravel.features.home.ui.HomeFragment

/**
 * TextSelectionFragment allows users to choose which Chinese text line
 * represents the restaurant name when multiple lines are detected.
 */
class TextSelectionFragment : Fragment(R.layout.fragment_text_selection) {

    private var selectedTextIndex = -1
    private lateinit var detectedTexts: List<String>
    private lateinit var imagePath: String
    private lateinit var selectedText: String

    companion object {
        private const val ARG_DETECTED_TEXTS = "detected_texts"
        private const val ARG_IMAGE_PATH = "image_path"
        private const val ARG_SELECTED_TEXT = "selected_text"

        fun newInstance(
            detectedTexts: List<String>,
            imagePath: String,
            selectedText: String
        ): TextSelectionFragment {
            val fragment = TextSelectionFragment()
            val args = Bundle().apply {
                putStringArray(ARG_DETECTED_TEXTS, detectedTexts.toTypedArray())
                putString(ARG_IMAGE_PATH, imagePath)
                putString(ARG_SELECTED_TEXT, selectedText)
            }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Get arguments
        val textsArray = arguments?.getStringArray(ARG_DETECTED_TEXTS) ?: emptyArray()
        detectedTexts = textsArray.toList()
        imagePath = arguments?.getString(ARG_IMAGE_PATH) ?: ""
        selectedText = arguments?.getString(ARG_SELECTED_TEXT) ?: ""

        setupHeader(view)
        setupImage(view)
        setupTextOptions(view)
        setupButtons(view)
    }

    private fun setupHeader(view: View) {
        val headerLayout = view.findViewById<View>(R.id.headerLayout)
        val backButton = headerLayout.findViewById<Button>(R.id.btnBack)
        val titleText = headerLayout.findViewById<TextView>(R.id.tvHeaderTitle)
        val rightText = headerLayout.findViewById<TextView>(R.id.tvHeaderRight)

        titleText.text = "📝 Select Text"
        rightText.visibility = View.GONE

        backButton.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupImage(view: View) {
        val imageView = view.findViewById<ImageView>(R.id.ivCapturedImage)
        
        // Load and display the image
        try {
            val imageFile = java.io.File(imagePath)
            if (imageFile.exists()) {
                val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                imageView.setImageBitmap(bitmap)
            }
        } catch (e: Exception) {
            // Handle error silently
        }
    }

    private fun setupTextOptions(view: View) {
        val recyclerView = view.findViewById<RecyclerView>(R.id.rvTextOptions)
        val adapter = TextOptionAdapter(detectedTexts) { index ->
            selectedTextIndex = index
            selectedText = detectedTexts[index]
        }
        
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
    }

    private fun setupButtons(view: View) {
        val cancelButton = view.findViewById<Button>(R.id.btnCancel)
        val confirmButton = view.findViewById<Button>(R.id.btnConfirmSelection)

        cancelButton.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        confirmButton.setOnClickListener {
            if (selectedTextIndex >= 0) {
                // Navigate back to home with selected text
                parentFragmentManager.beginTransaction()
                    .replace(R.id.container, HomeFragment())
                    .commit()
            }
        }

        // Initially disable confirm button
        confirmButton.isEnabled = false
        
        // Enable confirm button when text is selected
        if (selectedTextIndex >= 0) {
            confirmButton.isEnabled = true
        }
    }

    private fun navigateToHome() {
        // Navigate back to home fragment
        parentFragmentManager.beginTransaction()
            .replace(R.id.container, HomeFragment())
            .commit()
    }
}

class TextOptionAdapter(
    private val texts: List<String>,
    private val onItemClick: (Int) -> Unit
) : RecyclerView.Adapter<TextOptionAdapter.TextOptionViewHolder>() {

    private var selectedIndex = -1

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): TextOptionViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_text_option, parent, false)
        return TextOptionViewHolder(view)
    }

    override fun onBindViewHolder(holder: TextOptionViewHolder, position: Int) {
        holder.bind(texts[position], position == selectedIndex)
    }

    override fun getItemCount() = texts.size

    inner class TextOptionViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        private val textView = view.findViewById<TextView>(R.id.tvTextOption)
        private val selectedView = view.findViewById<TextView>(R.id.tvSelected)

        fun bind(text: String, isSelected: Boolean) {
            textView.text = text
            selectedView.visibility = if (isSelected) android.view.View.VISIBLE else android.view.View.GONE
            
            itemView.setOnClickListener {
                val oldIndex = selectedIndex
                selectedIndex = adapterPosition
                notifyItemChanged(oldIndex)
                notifyItemChanged(selectedIndex)
                onItemClick(selectedIndex)
            }
        }
    }
}