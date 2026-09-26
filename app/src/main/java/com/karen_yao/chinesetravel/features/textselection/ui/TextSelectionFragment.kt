package com.karen_yao.chinesetravel.features.textselection.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.karen_yao.chinesetravel.R
import com.karen_yao.chinesetravel.core.repository.TravelRepository
import com.karen_yao.chinesetravel.features.capture.ui.CaptureViewModel
import com.karen_yao.chinesetravel.features.capture.camera.ImageProcessor
import com.karen_yao.chinesetravel.features.home.ui.HomeFragment
import com.karen_yao.chinesetravel.shared.utils.PinyinUtils
import com.karen_yao.chinesetravel.shared.extensions.repo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import android.location.Geocoder
import android.location.Address

/**
 * TextSelectionFragment allows users to choose which Chinese text line
 * represents the restaurant name when multiple lines are detected.
 */
class TextSelectionFragment : Fragment(R.layout.fragment_text_selection) {

    private var selectedTextIndex = -1
    private lateinit var detectedTexts: List<String>
    private lateinit var imagePath: String
    private lateinit var selectedText: String
    private var deleteImageIfUnsaved = false
    private var imageSaved = false
    private var managedFilesRoot: File? = null
    
    private lateinit var viewModel: CaptureViewModel
    private lateinit var imageProcessor: ImageProcessor

    companion object {
        private const val ARG_DETECTED_TEXTS = "detected_texts"
        private const val ARG_IMAGE_PATH = "image_path"
        private const val ARG_SELECTED_TEXT = "selected_text"
        private const val ARG_DELETE_IMAGE_IF_UNSAVED = "delete_image_if_unsaved"

        fun newInstance(
            detectedTexts: List<String>,
            imagePath: String,
            selectedText: String,
            deleteImageIfUnsaved: Boolean = false
        ): TextSelectionFragment {
            val fragment = TextSelectionFragment()
            val args = Bundle().apply {
                putStringArray(ARG_DETECTED_TEXTS, detectedTexts.toTypedArray())
                putString(ARG_IMAGE_PATH, imagePath)
                putString(ARG_SELECTED_TEXT, selectedText)
                putBoolean(ARG_DELETE_IMAGE_IF_UNSAVED, deleteImageIfUnsaved)
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
        deleteImageIfUnsaved = arguments?.getBoolean(ARG_DELETE_IMAGE_IF_UNSAVED) ?: false
        managedFilesRoot = requireContext().filesDir.canonicalFile

        // Debug logging
        android.util.Log.d("TextSelectionFragment", "📋 Received ${detectedTexts.size} text options:")
        detectedTexts.forEachIndexed { index, text ->
            android.util.Log.d("TextSelectionFragment", "   $index: '$text'")
        }

        // Initialize ViewModel and ImageProcessor
        val repository = repo()
        viewModel = CaptureViewModel(repository)
        imageProcessor = ImageProcessor()

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
            deleteUnsavedImage()
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupImage(view: View) {
        val imageView = view.findViewById<ImageView>(R.id.ivCapturedImage)
        
        // Load and display the image with proper rotation handling
        try {
            val imageFile = java.io.File(imagePath)
            if (imageFile.exists()) {
                // Use ImageProcessor to load image with rotation correction
                imageProcessor.loadImageWithRotation(imageView, imageFile.absolutePath)
            }
        } catch (e: Exception) {
            android.util.Log.e("TextSelectionFragment", "Error loading image: ${e.message}")
            // Handle error silently
        }
    }

    private fun setupTextOptions(view: View) {
        val recyclerView = view.findViewById<RecyclerView>(R.id.rvTextOptions)
        val confirmButton = view.findViewById<Button>(R.id.btnConfirmSelection)
        
        android.util.Log.d("TextSelectionFragment", "Setting up RecyclerView with ${detectedTexts.size} items")
        
        val adapter = TextOptionAdapter(detectedTexts) { index ->
            selectedTextIndex = index
            selectedText = detectedTexts[index]
            android.util.Log.d("TextSelectionFragment", "Item $index selected: '$selectedText'")
            // Enable confirm button when text is selected
            confirmButton.isEnabled = true
        }
        
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
        
        android.util.Log.d("TextSelectionFragment", "RecyclerView setup complete")
    }

    private fun setupButtons(view: View) {
        val cancelButton = view.findViewById<Button>(R.id.btnCancel)
        val confirmButton = view.findViewById<Button>(R.id.btnConfirmSelection)

        cancelButton.setOnClickListener {
            deleteUnsavedImage()
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        confirmButton.setOnClickListener {
            if (selectedTextIndex >= 0) {
                // Process the selected text
                processSelectedText(selectedText)
            } else {
                Toast.makeText(requireContext(), "Please select a text option", Toast.LENGTH_SHORT).show()
            }
        }

        // Initially disable confirm button
        confirmButton.isEnabled = false
        
        // Enable confirm button when text is selected
        if (selectedTextIndex >= 0) {
            confirmButton.isEnabled = true
        }
    }

    private fun processSelectedText(chineseText: String) {
        val pinyin = if (chineseText.isNotBlank()) PinyinUtils.toPinyin(chineseText) else ""

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val file = File(imagePath)
                val location = imageProcessor.extractLocationFromFile(file)
                val address = if (location != null) {
                    reverseGeocode(location.first, location.second)
                } else {
                    null
                }

                // The transaction may commit before cancellation is delivered back to this
                // fragment. Retain the image once persistence begins to protect any saved row.
                imageSaved = true
                val totalCount = viewModel.saveAndCount(
                    chineseText, pinyin, location?.first, location?.second,
                    address, imagePath
                )

                Toast.makeText(requireContext(), "Saved. Total rows: $totalCount", Toast.LENGTH_SHORT).show()
                
                // Navigate back to home
                parentFragmentManager.beginTransaction()
                    .replace(R.id.container, HomeFragment())
                    .commit()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                deleteUnsavedImage()
                Toast.makeText(requireContext(), "Error saving: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun reverseGeocode(lat: Double, lng: Double): String? {
        val applicationContext = context?.applicationContext ?: return null
        return withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(applicationContext, Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            
            if (addresses?.isNotEmpty() == true) {
                val address = addresses[0]
                val addressString = buildString {
                    address.getAddressLine(0)?.let { append(it) }
                    if (address.locality != null) {
                        if (isNotEmpty()) append(", ")
                        append(address.locality)
                    }
                    if (address.countryName != null) {
                        if (isNotEmpty()) append(", ")
                        append(address.countryName)
                    }
                }
                if (addressString.isNotEmpty()) addressString else null
            } else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        }
    }

    override fun onDestroy() {
        if (isRemoving) deleteUnsavedImage()
        super.onDestroy()
    }

    private fun deleteUnsavedImage() {
        if (!deleteImageIfUnsaved || imageSaved || !::imagePath.isInitialized) return
        val file = File(imagePath)
        val filesRoot = managedFilesRoot ?: return
        val candidate = runCatching { file.canonicalFile }.getOrNull() ?: return
        if (candidate.path.startsWith(filesRoot.path + File.separator)) {
            candidate.delete()
            deleteImageIfUnsaved = false
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
        android.util.Log.d("TextOptionAdapter", "onCreateViewHolder called for viewType $viewType")
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_text_option, parent, false)
        return TextOptionViewHolder(view)
    }

    override fun onBindViewHolder(holder: TextOptionViewHolder, position: Int) {
        val text = texts[position]
        android.util.Log.d("TextOptionAdapter", "Binding position $position: '$text'")
        holder.bind(text, position == selectedIndex)
    }

    override fun getItemCount(): Int {
        android.util.Log.d("TextOptionAdapter", "getItemCount() called, returning ${texts.size}")
        return texts.size
    }

    inner class TextOptionViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        private val textView = view.findViewById<TextView>(R.id.tvTextOption)
        private val selectedView = view.findViewById<TextView>(R.id.tvSelected)

        fun bind(text: String, isSelected: Boolean) {
            textView.text = text
            selectedView.visibility = if (isSelected) android.view.View.VISIBLE else android.view.View.GONE
            
            itemView.setOnClickListener {
                val oldIndex = selectedIndex
                selectedIndex = adapterPosition
                
                // Use notifyDataSetChanged for more reliable updates
                notifyDataSetChanged()
                onItemClick(selectedIndex)
            }
        }
    }
}
