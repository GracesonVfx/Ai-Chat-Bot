package com.example.chatbot

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class MainActivity : AppCompatActivity() {
    private val client = OkHttpClient()

    // Replace this with your NEW API key (create a new one since the old one was exposed)
    private val apiKey = "sk-API_KEY_HERE"

    // Correct API endpoint for all OpenRouter models
    private val modelUrl = "https://openrouter.ai/api/v1/chat/completions"

    // The model you want to use
    private val modelName = "meta-llama/llama-3.3-8b-instruct:free"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val userInput = findViewById<EditText>(R.id.userInput)
        val sendButton = findViewById<ImageButton>(R.id.sendButton)
        val chatOutput = findViewById<TextView>(R.id.chatOutput)
        val chatScroll = findViewById<ScrollView>(R.id.chatScroll)

        sendButton.setOnClickListener {
            val userMessage = userInput.text.toString()
            if (userMessage.isNotBlank()) {
                chatOutput.append("You: $userMessage\n\n")
                scrollToBottom(chatScroll)
                getBotReply(userMessage, chatOutput, chatScroll)
                userInput.text.clear()
            }
        }
    }

    private fun getBotReply(message: String, chatOutput: TextView, chatScroll: ScrollView) {
        // Show loading message
        runOnUiThread {
            chatOutput.append("Bot: Thinking...\n\n")
            scrollToBottom(chatScroll)
        }

        // Create the JSON request body
        val requestJson = JSONObject()
        requestJson.put("model", modelName)

        // Create messages array
        val messagesArray = JSONArray()
        val messageObject = JSONObject()
        messageObject.put("role", "user")
        messageObject.put("content", message)
        messagesArray.put(messageObject)

        requestJson.put("messages", messagesArray)
        requestJson.put("max_tokens", 150)
        requestJson.put("temperature", 0.7)

        // Debug output
        println("Request URL: $modelUrl")
        println("Request Body: ${requestJson.toString()}")
        println("API Key (first 10 chars): ${apiKey.take(10)}...")

        val requestBody = requestJson.toString().toRequestBody("application/json".toMediaTypeOrNull())

        val request = Request.Builder()
            .url(modelUrl)
            .post(requestBody)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("HTTP-Referer", "https://chatbot-android.com")
            .addHeader("X-Title", "ChatBot Android App")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    // Remove thinking message
                    val currentText = chatOutput.text.toString()
                    val newText = currentText.replace("Bot: Thinking...\n\n", "")
                    chatOutput.text = newText

                    chatOutput.append("Bot: Connection failed. Error: ${e.message}\n\n")
                    scrollToBottom(chatScroll)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string() ?: ""

                // Debug output
                println("Response Code: ${response.code}")
                println("Response Body: ${responseBody.take(500)}")

                val botReply = try {
                    if (response.isSuccessful) {
                        if (responseBody.trim().startsWith("{")) {
                            val responseJson = JSONObject(responseBody)
                            if (responseJson.has("choices")) {
                                val choices = responseJson.getJSONArray("choices")
                                if (choices.length() > 0) {
                                    val firstChoice = choices.getJSONObject(0)
                                    val messageObj = firstChoice.getJSONObject("message")
                                    messageObj.getString("content").trim()
                                } else {
                                    "No response generated."
                                }
                            } else {
                                "Invalid response format: $responseJson"
                            }
                        } else {
                            "Received HTML instead of JSON. Check your API key and endpoint."
                        }
                    } else {
                        when (response.code) {
                            400 -> "Bad request. Check model name: $modelName"
                            401 -> "Authentication failed. Check your API key."
                            403 -> "Access forbidden. Check API key permissions."
                            404 -> "API endpoint not found."
                            429 -> "Rate limit exceeded. Try again later."
                            else -> "API Error ${response.code}: ${responseBody.take(200)}"
                        }
                    }
                } catch (e: Exception) {
                    "Parse error: ${e.message}"
                }

                runOnUiThread {
                    // Remove thinking message
                    val currentText = chatOutput.text.toString()
                    val newText = currentText.replace("Bot: Thinking...\n\n", "")
                    chatOutput.text = newText

                    chatOutput.append("Bot: $botReply\n\n")
                    scrollToBottom(chatScroll)
                }
            }
        })
    }

    private fun scrollToBottom(scrollView: ScrollView) {
        scrollView.post {
            scrollView.fullScroll(View.FOCUS_DOWN)
        }
    }
}