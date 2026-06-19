package com.faster.tibot.data.mqtt

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withContext
import org.eclipse.paho.client.mqttv3.*

class MqttManager private constructor() {

    private val TAG = "MqttManager"
    private val brokerUrl = "tcp://127.0.0.1:1883"
    private val clientId = "tibot-android"
    private var client: MqttClient? = null

    private val _messages = Channel<MqttMessageEvent>(Channel.BUFFERED)
    val messages: Flow<MqttMessageEvent> = _messages.receiveAsFlow()

    private val _connectionState = Channel<Boolean>(Channel.CONFLATED)
    val connectionState: Flow<Boolean> = _connectionState.receiveAsFlow()

    data class MqttMessageEvent(val topic: String, val payload: String)

    companion object {
        @Volatile
        private var instance: MqttManager? = null

        fun getInstance(context: Context? = null): MqttManager {
            return instance ?: synchronized(this) {
                instance ?: MqttManager().also { instance = it }
            }
        }
    }

    suspend fun connect() {
        if (client?.isConnected == true) return
        withContext(Dispatchers.IO) {
            try {
                client = MqttClient(brokerUrl, clientId).apply {
                    setCallback(object : MqttCallback {
                        override fun connectionLost(cause: Throwable?) {
                            _connectionState.trySend(false)
                        }
                        override fun messageArrived(topic: String, message: MqttMessage) {
                            _messages.trySend(MqttMessageEvent(topic, String(message.payload)))
                        }
                        override fun deliveryComplete(token: IMqttDeliveryToken?) {}
                    })
                    connect(
                        MqttConnectOptions().apply {
                            isAutomaticReconnect = true
                            keepAliveInterval = 30
                        }
                    )
                }
                _connectionState.trySend(true)
            } catch (e: Exception) {
                _connectionState.trySend(false)
            }
        }
    }

    fun subscribe(topic: String, qos: Int = 1) {
        try {
            client?.subscribe(topic, qos)
        } catch (_: Exception) {}
    }

    fun unsubscribe(topic: String) {
        try {
            client?.unsubscribe(topic)
        } catch (_: Exception) {}
    }

    fun publish(topic: String, payload: String, qos: Int = 1) {
        try {
            client?.publish(topic, MqttMessage(payload.toByteArray()).apply { this.qos = qos })
        } catch (_: Exception) {}
    }

    fun disconnect() {
        try {
            client?.disconnect()
            client?.close()
        } catch (_: Exception) {}
        client = null
    }
}
