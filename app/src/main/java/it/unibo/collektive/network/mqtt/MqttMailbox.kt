package it.unibo.collektive.network.mqtt

import android.util.Log
import it.nicolasfarabegoli.mktt.MkttClient
import it.nicolasfarabegoli.mktt.MqttQoS
import it.unibo.collektive.network.AbstractSerializerMailbox
import it.unibo.collektive.networking.Message
import it.unibo.collektive.networking.SerializedMessage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialFormat
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/**
 * A mailbox that uses MQTT as the underlying transport.
 */
class MqttMailbox(
    private val deviceId: Uuid,
    host: String,
    port: Int,
    private val serializer: SerialFormat,
    private val retentionTime: Duration,
    private val dispatcher: CoroutineDispatcher,
) : AbstractSerializerMailbox<Uuid>(deviceId, serializer, retentionTime) {
    private val mutex = Mutex()
    private val internalScope = CoroutineScope(dispatcher)
    private val mqttClient = MkttClient(dispatcher) {
        brokerUrl = host
        this.port = port
    }

    private suspend fun initializeMqttClient() {
        mqttClient.connect()
        internalScope.launch(dispatcher) { receiveHeartbeatPulse() }
        internalScope.launch(dispatcher) { sendHeartbeatPulse() }
        internalScope.launch { cleanHeartbeatPulse() }
        internalScope.launch(dispatcher) { receiveNeighborMessages() }
    }

    private suspend fun sendHeartbeatPulse() {
        mqttClient.publish(heartbeatTopic(deviceId), byteArrayOf())
        delay(1.seconds)
        sendHeartbeatPulse()
    }

    private suspend fun receiveHeartbeatPulse() {
        mqttClient.subscribe(HEARTBEAT_WILD_CARD).collect {
            val neighborDeviceId = Uuid.parse(it.topic.split("/").last())
            addNeighbor(neighborDeviceId)
        }
    }

    private suspend fun cleanHeartbeatPulse() {
        /**
         * Temporary workaround for concurrent access issue
         */
        mutex.withLock {
            cleanupNeighbors(retentionTime)
            delay(retentionTime)
            cleanHeartbeatPulse()
        }
    }

    private suspend fun receiveNeighborMessages() {
        mqttClient.subscribe(deviceTopic(deviceId)).collect {
            try {
                val deserialized = serializer.decodeSerialMessage<Uuid>(it.payload)
                deliverableReceived(deserialized)
            } catch (exception: SerializationException) {
                Log.e("MqttMailbox", "Failed to deserialize message from ${it.topic}: ${exception.message}")
            }
        }
    }

    override suspend fun close() {
        internalScope.cancel()
        mqttClient.disconnect()
    }

    override fun onDeliverableReceived(receiverId: Uuid, message: Message<Uuid, Any?>) {
        require(message is SerializedMessage<Uuid>)
        internalScope.launch(dispatcher) {
            mqttClient.publish(
                topic = deviceTopic(receiverId),
                qos = MqttQoS.AtLeastOnce,
                message = serializer.encodeSerialMessage(message),
            )
        }
    }

    /**
     * Companion object to create a new instance of [MqttMailbox].
     */
    companion object {
        /**
         * Create a new instance of [MqttMailbox].
         */
        suspend operator fun invoke(
            deviceId: Uuid,
            host: String,
            port: Int = 1883,
            serializer: SerialFormat = Json,
            retentionTime: Duration = 5.seconds,
            dispatcher: CoroutineDispatcher,
        ): MqttMailbox = coroutineScope {
            MqttMailbox(deviceId, host, port, serializer, retentionTime, dispatcher).apply {
                initializeMqttClient()
            }
        }

        private const val APP_NAMESPACE = "CollektiveExampleAndroid"
        private const val HEARTBEAT_WILD_CARD = "$APP_NAMESPACE/heartbeat/+"
        private fun deviceTopic(deviceId: Uuid) = "$APP_NAMESPACE/device/$deviceId"
        private fun heartbeatTopic(deviceId: Uuid) = "$APP_NAMESPACE/heartbeat/$deviceId"
    }
}
