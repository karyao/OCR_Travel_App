package com.karen_yao.chinesetravel.features.capture.ocr

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * PaddleOCR processor for Chinese text recognition.
 * Uses REST API approach for better accuracy and easier integration.
 */
class PaddleOCRProcessor {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    
    // Production PaddleOCR API endpoint - replace with your actual API
    private val paddleOCRUrl = "https://your-paddleocr-api.com/v1/ocr" // TODO: Replace with actual API
    
    // For testing, you can use a local server or cloud service
    // Examples:
    // Local: "http://localhost:8866/predict/ocr_system"
    // Cloud: "https://your-domain.com/api/ocr"
    
    /**
     * Process image with PaddleOCR for Chinese text recognition.
     * 
     * @param image InputImage from camera/gallery
     * @return Recognized text or empty string if failed
     */
    suspend fun recognizeText(image: InputImage): String = withContext(Dispatchers.IO) {
        try {
            val bitmap = image.bitmapInternal ?: return@withContext ""
            recognizeText(bitmap)
        } catch (e: Exception) {
            Log.e("PaddleOCR", "❌ Failed to convert InputImage to Bitmap: ${e.message}")
            ""
        }
    }
    
    /**
     * Process image with PaddleOCR for Chinese text recognition.
     * 
     * @param bitmap The bitmap image to process
     * @return Recognized text or empty string if failed
     */
    suspend fun recognizeText(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        try {
            Log.d("PaddleOCR", "🔄 Starting PaddleOCR text recognition")
            
            // Convert bitmap to base64
            val base64Image = bitmapToBase64(bitmap)
            
            // Create request body
            val requestBody = createRequestBody(base64Image)
            
            // Make API call
            val response = makeOCRRequest(requestBody)
            
            Log.d("PaddleOCR", "✅ PaddleOCR response: $response")
            response
            
        } catch (e: Exception) {
            Log.e("PaddleOCR", "❌ PaddleOCR failed: ${e.message}")
            ""
        }
    }
    
    /**
     * Alternative method using local PaddleOCR server (if you deploy one).
     */
    suspend fun recognizeTextLocal(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        try {
            val base64Image = bitmapToBase64(bitmap)
            
            // Use local server endpoint
            val localUrl = "http://localhost:8866/predict/ocr_system"
            val requestBody = createRequestBody(base64Image)
            
            makeOCRRequest(requestBody, localUrl)
            
        } catch (e: Exception) {
            Log.e("PaddleOCR", "❌ Local PaddleOCR failed: ${e.message}")
            ""
        }
    }
    
    /**
     * Convert bitmap to base64 string for API transmission.
     */
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.DEFAULT)
    }
    
    /**
     * Create request body for PaddleOCR API.
     */
    private fun createRequestBody(base64Image: String): RequestBody {
        val json = JSONObject().apply {
            put("image", base64Image)
            put("lang", "ch") // Chinese language
            put("det", true) // Text detection
            put("rec", true) // Text recognition
            put("cls", false) // Text classification (optional)
        }
        
        val mediaType = "application/json".toMediaType()
        return json.toString().toRequestBody(mediaType)
    }
    
    /**
     * Make HTTP request to PaddleOCR API.
     */
    private suspend fun makeOCRRequest(requestBody: RequestBody, url: String = paddleOCRUrl): String {
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .build()
        
        return try {
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: ""
                parseOCRResponse(responseBody)
            } else {
                Log.e("PaddleOCR", "❌ API request failed: ${response.code}")
                ""
            }
        } catch (e: IOException) {
            Log.e("PaddleOCR", "❌ Network error: ${e.message}")
            ""
        }
    }
    
    /**
     * Parse PaddleOCR API response to extract text.
     */
    private fun parseOCRResponse(responseBody: String): String {
        return try {
            val jsonResponse = JSONObject(responseBody)
            
            if (jsonResponse.has("results") && jsonResponse.getJSONArray("results").length() > 0) {
                val results = jsonResponse.getJSONArray("results")
                val textLines = mutableListOf<String>()
                
                for (i in 0 until results.length()) {
                    val result = results.getJSONObject(i)
                    if (result.has("text")) {
                        textLines.add(result.getString("text"))
                    }
                }
                
                textLines.joinToString("\n")
            } else {
                Log.w("PaddleOCR", "⚠️ No text detected in response")
                ""
            }
        } catch (e: Exception) {
            Log.e("PaddleOCR", "❌ Failed to parse response: ${e.message}")
            ""
        }
    }
    
    /**
     * Production method - no fallbacks, only real PaddleOCR processing.
     * Throws exception if API is not available to ensure proper error handling.
     */
    suspend fun recognizeTextOffline(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        Log.e("PaddleOCR", "❌ Offline fallback disabled - PaddleOCR API required")
        throw Exception("PaddleOCR API not available - offline fallback disabled")
    }
    
    /**
     * Clean up resources.
     */
    fun cleanup() {
        client.dispatcher.executorService.shutdown()
    }
}
