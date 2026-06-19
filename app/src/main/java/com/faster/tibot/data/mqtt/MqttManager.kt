package com.faster.tibot.data.mqtt

import android.content.Context
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.*

class MqttManager(private val context: Context) {

    private val TAG = "MqttManager"
    private val brokerUrl = "tcp://127.0.0.1:1883"
    private val clientId = "tibot-android"
    private var client: MqttAndroidClient? = null

    private val _messages = Channel<MqttMessageEvent>(Channel.BUFFERED)
    val messages: Flow<MqttMessageEvent> = _messages.receiveAsFlow()

    private val _connectionState = Channel<Boolean>(Channel.CONFLATED)
    val connectionState: Flow<Boolean> = _connectionState.receiveAsFlow()

    data class MqttMessageEvent(val topic: String, val payload: String)

    fun connect() {
        if (client?.isConnected == true) return
        client = MqttAndroidClient(context, brokerUrl, clientId).apply {
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
                },
                null,
                object : IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttActionToken?) {
                        _connectionState.trySend(true)
                    }
                    override fun onFailure(asyncActionToken: IMqttActionToken?, exception: Throwable?) {
                        _connectionState.trySend(false)
                    }
                }
            )
        }
    }

    fun subscribe(topic: String, qos: Int = 1) {
        client?.subscribe(topic, qos)
    }

    fun publish(topic: String, payload: String, qos: Int = 1) {
        client?.publish(topic, MqttMessage(payload.toByteArray()).apply { this.qos = qos })
    }

    fun disconnect() {
        client?.disconnect()
        client?.close()
        client = null
    }
}
