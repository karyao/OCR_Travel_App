package com.karen_yao.chinesetravel.features.capture.ocr

/**
 * Configuration for PaddleOCR integration.
 * Contains API endpoints, settings, and fallback options.
 */
object PaddleOCRConfig {
    
    // API Configuration - Production endpoints
    const val PADDLE_OCR_API_URL = "https://your-paddleocr-api.com/v1/ocr" // TODO: Replace with actual API
    const val LOCAL_PADDLE_OCR_URL = "http://localhost:8866/predict/ocr_system"
    
    // Request Configuration
    const val CONNECT_TIMEOUT_SECONDS = 30L
    const val READ_TIMEOUT_SECONDS = 60L
    const val WRITE_TIMEOUT_SECONDS = 60L
    
    // Image Processing
    const val JPEG_QUALITY = 90
    const val MAX_IMAGE_SIZE_MB = 10
    
    // Language Settings
    const val CHINESE_LANGUAGE_CODE = "ch"
    const val ENABLE_TEXT_DETECTION = true
    const val ENABLE_TEXT_RECOGNITION = true
    const val ENABLE_TEXT_CLASSIFICATION = false
    
    // Production Configuration - No Fallbacks
    const val ENABLE_OFFLINE_FALLBACK = false
    const val ENABLE_MOCK_MODE = false
    
    // API Keys (if using paid services)
    const val API_KEY = "" // Add your API key here if using paid service
    
    /**
     * Get the appropriate API URL based on configuration.
     */
    fun getApiUrl(): String {
        return if (ENABLE_MOCK_MODE) {
            "mock://paddleocr" // Mock URL for testing
        } else {
            PADDLE_OCR_API_URL
        }
    }
    
    /**
     * Check if offline fallback is enabled.
     */
    fun isOfflineFallbackEnabled(): Boolean = ENABLE_OFFLINE_FALLBACK
    
    /**
     * Check if mock mode is enabled for testing.
     */
    fun isMockModeEnabled(): Boolean = ENABLE_MOCK_MODE
}
