package com.karen_yao.chinesetravel.features.capture.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import android.util.Log

/**
 * Service interface for PaddleOCR text recognition.
 * Provides a clean abstraction for different OCR implementations.
 */
interface PaddleOCRService {
    
    /**
     * Recognize Chinese text from an image.
     * 
     * @param image The input image to process
     * @return Flow of recognized text
     */
    fun recognizeText(image: InputImage): Flow<String>
    
    /**
     * Recognize Chinese text from a bitmap.
     * 
     * @param bitmap The input bitmap to process
     * @return Flow of recognized text
     */
    fun recognizeText(bitmap: Bitmap): Flow<String>
    
    /**
     * Check if the OCR service is available.
     * 
     * @return True if service is ready, false otherwise
     */
    suspend fun isAvailable(): Boolean
}

/**
 * Implementation of PaddleOCRService using REST API.
 */
class PaddleOCRServiceImpl : PaddleOCRService {
    
    private val processor = PaddleOCRProcessor()
    
    override fun recognizeText(image: InputImage): Flow<String> = flow {
        try {
            val text = processor.recognizeText(image)
            emit(text)
        } catch (e: Exception) {
            // No fallback - throw exception to ensure proper error handling
            Log.e("PaddleOCRService", "❌ PaddleOCR API failed: ${e.message}")
            throw e
        }
    }
    
    override fun recognizeText(bitmap: Bitmap): Flow<String> = flow {
        try {
            val text = processor.recognizeText(bitmap)
            emit(text)
        } catch (e: Exception) {
            // No fallback - throw exception to ensure proper error handling
            Log.e("PaddleOCRService", "❌ PaddleOCR API failed: ${e.message}")
            throw e
        }
    }
    
    override suspend fun isAvailable(): Boolean {
        return try {
            // You could implement a health check here
            true
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * Mock implementation for testing without actual PaddleOCR API.
 */
class MockPaddleOCRService : PaddleOCRService {
    
    override fun recognizeText(image: InputImage): Flow<String> = flow {
        // Return mock Chinese text for testing
        emit("欢迎光临")
    }
    
    override fun recognizeText(bitmap: Bitmap): Flow<String> = flow {
        // Return mock Chinese text for testing
        emit("欢迎光临")
    }
    
    override suspend fun isAvailable(): Boolean = true
}
