package com.karen_yao.chinesetravel

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.karen_yao.chinesetravel.core.database.AppDatabase
import com.karen_yao.chinesetravel.core.repository.TravelRepository
import com.karen_yao.chinesetravel.shared.utils.TestDataUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Test activity for development and testing purposes.
 * Provides buttons to test OCR, location extraction, and database functionality.
 */
class TestActivity : AppCompatActivity() {
    
    private lateinit var repository: TravelRepository
    private lateinit var resultText: TextView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test)
        
        // Initialize repository
        repository = TravelRepository(AppDatabase.getDatabase(this))
        
        // Setup UI
        setupViews()
    }
    
    private fun setupViews() {
        resultText = findViewById(R.id.tvTestResults)
        
        // Test OCR button
        findViewById<Button>(R.id.btnTestOCR).setOnClickListener {
            testOCR()
        }
        
        // Test Location button
        findViewById<Button>(R.id.btnTestLocation).setOnClickListener {
            testLocation()
        }
        
        // Test Database button
        findViewById<Button>(R.id.btnTestDatabase).setOnClickListener {
            testDatabase()
        }
        
        // Export and Test All button
        findViewById<Button>(R.id.btnTestAll).setOnClickListener {
            testAll()
        }
    }
    
    private fun testOCR() {
        Log.d("TestActivity", "Testing OCR...")
        resultText.text = "Testing OCR... Check logs for results"
        
        TestDataUtils.testOCRWithImage(this, "chinese_character.jpg") { result ->
            runOnUiThread {
                resultText.text = "OCR Result: $result"
                Log.d("TestActivity", "OCR Result: $result")
            }
        }
    }
    
    private fun testLocation() {
        Log.d("TestActivity", "Testing Location...")
        resultText.text = "Testing Location... Check logs for results"
        
        TestDataUtils.testLocationExtraction(this, "IMG_3849.JPG") { result ->
            runOnUiThread {
                resultText.text = "Location Result: $result"
                Log.d("TestActivity", "Location Result: $result")
            }
        }
    }
    
    private fun testDatabase() {
        Log.d("TestActivity", "Testing Database...")
        resultText.text = "Testing Database..."
        
        // Test saving a sample entry
        val testSnap = com.karen_yao.chinesetravel.core.database.entities.PlaceSnap(
            imagePath = "/test/path.jpg",
            nameCn = "测试数据库",
            namePinyin = "ce shi shu ju ku",
            lat = 39.9042,
            longitude = 116.4074,
            address = "测试地址",
            translation = "Test Database",
            googleMapsLink = "https://www.google.com/maps/search/?api=1&query=39.9042,116.4074"
        )
        
        // Save to database using coroutine
        CoroutineScope(Dispatchers.IO).launch {
            try {
                repository.saveSnap(testSnap)
                val count = repository.getSnapCount()
                runOnUiThread {
                    resultText.text = "Database test successful! Total entries: $count"
                    Log.d("TestActivity", "Database test successful! Total entries: $count")
                }
            } catch (e: Exception) {
                runOnUiThread {
                    resultText.text = "Database test failed: ${e.message}"
                    Log.e("TestActivity", "Database test failed", e)
                }
            }
        }
    }
    
    private fun testAll() {
        Log.d("TestActivity", "Testing All Features...")
        resultText.text = "Testing all features... Check logs for results"
        
        TestDataUtils.exportAndTestImages(this, repository)
        
        // Update UI after a delay
        resultText.postDelayed({
            resultText.text = "All tests completed! Check logs for detailed results."
        }, 2000)
    }
}
