# Chinese Travel App

## 🎯 What This App Does

A mobile app I built to solve a real problem I faced when traveling in China - not being able to read Chinese text or remember where I'd been.

### The Problem I Wanted to Solve

When I went back to China, I visited so many places but couldn't remember their names because I couldn't read Chinese. I'd take photos of signs and menus, but then forget what they said or where I took them. I wanted a way to:

- Instantly understand Chinese text I encountered
- Remember where I'd been with location context
- Build a personal travel log of places I visited

## 🚀 How It Works

### 📸 **Take Photos of Chinese Text**

- Point your camera at any Chinese text (signs, menus, documents)
- The app uses Google's ML Kit to recognize Chinese characters
- Works with both camera and gallery photos
- Switch between front/back cameras easily

### 🧠 **Get Instant Translations**

- Automatically translates Chinese to English
- Shows Pinyin pronunciation to help you learn
- Uses Google Translate for accurate results
- Saves translations with location context

### 🗺️ **Remember Where You Were**

- Captures your GPS location when you take photos
- Shows addresses in readable English
- Links to Google Maps for navigation
- Builds a map of all the places you've visited

### 📚 **Keep Your Travel History**

- All translations saved locally on your phone
- Search through your past captures
- Export your travel log
- Works offline for basic text recognition

## 🛠️ Technical Details

### **What I Built This With**

- **Kotlin** - Modern Android development
- **MVVM Architecture** - Clean separation of UI and business logic
- **Room Database** - Local storage for all your translations
- **CameraX** - Google's latest camera library for smooth photo capture
- **Google ML Kit** - AI-powered Chinese text recognition
- **Google Translate** - Accurate translation service
- **Location Services** - GPS and address lookup

### **Technical Challenges I Solved**

- **Async Operations** - Managing OCR and translation without blocking the UI
- **Error Handling** - Graceful fallbacks when things go wrong
- **Performance** - Optimizing image processing and memory usage

## 📱 How to Use It

### **Simple 3-Step Process**

1. **Take a photo** of Chinese text (sign, menu, document)
2. **Get instant translation** with pronunciation
3. **Save with location** - automatically remembers where you were

### **Cool Features I Added**

- **Gesture Controls** - Double-tap to switch cameras
- **Smart Text Selection** - Choose which lines to translate when multiple detected
- **Location Memory** - Every translation remembers where you took it
- **Google Maps Integration** - Tap to navigate back to places you've been

## 🔒 Privacy & Permissions

The app only asks for what it needs:

- **Camera** - To take photos of text
- **Location** - To remember where you found the text
- **Storage** - To save your translations locally

Everything is processed on your device when possible - only translation uses Google's service.

## 🚀 What I'd Add Next

- Add a new page with a visible map to showcase pins on where you have travelled
- Support for other languages (Japanese, Korean)
- Share your travel discoveries with friends
- Better image processing for tricky lighting