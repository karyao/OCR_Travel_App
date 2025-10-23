package com.karen_yao.chinesetravel.features.capture.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.karen_yao.chinesetravel.R
import kotlinx.coroutines.launch
import android.util.Log

/**
 * Test activity for PaddleOCR integration with real data processing.
 * Tests actual PaddleOCR API calls with sample Chinese text images.
 */
class PaddleOCRTestActivity : AppCompatActivity() {
    
    private lateinit var resultTextView: TextView
    private lateinit var testButton: Button
    private lateinit var testButton2: Button
    private lateinit var testButton3: Button
    private lateinit var paddleOCRService: PaddleOCRService
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_paddleocr_test)
        
        initializeViews()
        setupPaddleOCR()
        setupTestButtons()
    }
    
    private fun initializeViews() {
        resultTextView = findViewById(R.id.tvResult)
        testButton = findViewById(R.id.btnTestOCR)
        testButton2 = findViewById(R.id.btnTestOCR2)
        testButton3 = findViewById(R.id.btnTestOCR3)
    }
    
    private fun setupPaddleOCR() {
        // Always use real PaddleOCR service - no mock mode
        paddleOCRService = PaddleOCRServiceImpl()
    }
    
    private fun setupTestButtons() {
        testButton.setOnClickListener { testPaddleOCRWithSample1() }
        testButton2.setOnClickListener { testPaddleOCRWithSample2() }
        testButton3.setOnClickListener { testPaddleOCRWithSample3() }
    }
    
    private fun testPaddleOCRWithSample1() {
        resultTextView.text = "Testing PaddleOCR with Sample 1..."
        testWithAssetImage("chinese_character.jpg", "Sample 1")
    }
    
    private fun testPaddleOCRWithSample2() {
        resultTextView.text = "Testing PaddleOCR with Sample 2..."
        testWithAssetImage("sample1.jpg", "Sample 2")
    }
    
    private fun testPaddleOCRWithSample3() {
        resultTextView.text = "Testing PaddleOCR with Sample 3..."
        testWithAssetImage("IMG_3849.JPG", "Sample 3")
    }
    
    private fun testWithAssetImage(assetName: String, testName: String) {
        lifecycleScope.launch {
            try {
                // Load image from assets
                val bitmap = loadBitmapFromAssets(assetName)
                if (bitmap != null) {
                    Log.d("PaddleOCRTest", "🔄 Testing $testName with image: $assetName")
                    resultTextView.text = "🔄 Processing $testName with real PaddleOCR API...\n\nImage: $assetName\nSize: ${bitmap.width}x${bitmap.height}px"
                    
                    // Process with real PaddleOCR - this will make actual API calls
                    paddleOCRService.recognizeText(bitmap).collect { result ->
                        Log.d("PaddleOCRTest", "✅ $testName result: $result")
                        
                        // Show the actual OCR result from the image
                        val resultText = """
                            ✅ $testName - REAL OCR Results
                            
                            📸 Image: $assetName
                            📏 Size: ${bitmap.width}x${bitmap.height}px
                            
                            🔍 ACTUAL TEXT FOUND IN IMAGE:
                            "$result"
                            
                            📊 API Details:
                            • Endpoint: ${PaddleOCRConfig.getApiUrl()}
                            • Processing: Real PaddleOCR API
                            • No Fallbacks: Production mode
                            
                            🎯 Status: SUCCESS - Real text recognition completed
                        """.trimIndent()
                        
                        resultTextView.text = resultText
                    }
                } else {
                    resultTextView.text = "❌ Failed to load image: $assetName\n\nMake sure the image exists in assets folder."
                }
            } catch (e: Exception) {
                Log.e("PaddleOCRTest", "❌ $testName failed: ${e.message}")
                resultTextView.text = """
                    ❌ $testName - REAL API Test Failed
                    
                    🚨 Error: ${e.message}
                    
                    📸 Image: $assetName
                    🌐 API: ${PaddleOCRConfig.getApiUrl()}
                    
                    🔧 This means:
                    • PaddleOCR API is not available
                    • Network connection failed
                    • API endpoint is incorrect
                    • No fallbacks - real API required
                    
                    💡 To fix:
                    1. Deploy PaddleOCR server
                    2. Update API URL in PaddleOCRConfig.kt
                    3. Check internet connection
                    4. Verify API key (if required)
                """.trimIndent()
            }
        }
    }
    
    private fun loadBitmapFromAssets(assetName: String): Bitmap? {
        return try {
            val inputStream = assets.open(assetName)
            BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            Log.e("PaddleOCRTest", "❌ Failed to load asset: $assetName", e)
            null
        }
    }
}
