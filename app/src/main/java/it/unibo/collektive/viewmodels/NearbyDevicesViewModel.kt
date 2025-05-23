package it.unibo.collektive.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.unibo.collektive.Collektive
import it.unibo.collektive.aggregate.Field
import it.unibo.collektive.aggregate.api.mapNeighborhood
import it.unibo.collektive.aggregate.api.neighboring
import it.unibo.collektive.model.Params
import it.unibo.collektive.network.mqtt.MqttMailbox
import it.unibo.collektive.stdlib.util.Point3D
import it.unibo.collektive.stdlib.util.euclideanDistance3D
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import kotlin.Float.Companion.POSITIVE_INFINITY
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/**
 * A ViewModel that manages the list of nearby devices.
 */
class NearbyDevicesViewModel(private val dispatcher: CoroutineDispatcher = Dispatchers.IO) : ViewModel() {
    private val _dataFlow = MutableStateFlow<Set<Uuid>>(emptySet())
    private val _connectionFlow = MutableStateFlow(ConnectionState.DISCONNECTED)
    private val _userName = MutableStateFlow("User")
    private val _online = MutableStateFlow(true)
    private val _devicesInChat= MutableStateFlow(0)

    /**
     * The connection state.
     */
    enum class ConnectionState {
        /**
         * Connected to the broker.
         */
        CONNECTED,

        /**
         * Disconnected from the broker.
         */
        DISCONNECTED,
    }

    /**
     * The set of nearby devices.
     */
    val dataFlow: StateFlow<Set<Uuid>> = _dataFlow.asStateFlow()

    /**
     * The number of devices in the chat.
     */
    val devicesInChat: StateFlow<Int> = _devicesInChat.asStateFlow()

    /**
     * The connection state.
     */
    val connectionFlow: StateFlow<ConnectionState> = _connectionFlow.asStateFlow()

    /**
     * The user name of local device.
     */
    val userName: StateFlow<String> get() = _userName

    /**
     * The local device ID.
     */
    val deviceId = Uuid.random()

    /**
     * Change user name of local device.
     */
    fun setUserName(value: String){
        this._userName.value = value
    }

    /**
     * Online devices in the home page.
     */
    fun setOnlineStatus(flag: Boolean){
        this._online.value = flag

    }

    /**
     * TODO: doc
     */
     private suspend fun collektiveProgram(): Collektive<Uuid, Set<Uuid>> =
        Collektive(deviceId, MqttMailbox(deviceId, host = "broker.hivemq.com", dispatcher = dispatcher)) {
            neighboring(localId).neighbors.toSet()
        }

    /**
     * Start the Collektive program.
     */
    fun startCollektiveProgram() {
        viewModelScope.launch {
            val program = collektiveProgram()
            _connectionFlow.value = ConnectionState.CONNECTED
            while (_online.value) {
                val newResult = program.cycle()
                _dataFlow.value = newResult
                delay(1.seconds)
            }
        }
    }

    /**
     * TODO: doc
     */
    suspend fun getListOfDevices(sender: Map<Uuid, Triple<Float, String, String>>): Collektive<Uuid, Map<Uuid, Triple<Float, String, String>>> =
        Collektive(deviceId, MqttMailbox(deviceId, "broker.hivemq.com", dispatcher = dispatcher)) {
            mapNeighborhood { id ->
                sender[id] ?: Triple(-1f, userName.value, "")
            }.toMap()
        }.also {
            delay(1.seconds)
            _devicesInChat.value = it.cycle().size
        }

    /**
     * TODO: doc
     */
    suspend fun transformDistances(
        senders: Map<Uuid, Triple<Float, String, String>>,
        devicesValues: Map<Uuid, Triple<Float, String, String>>,
        position: Point3D,
        time: LocalDateTime,
        userName: String = _userName.value
    ) : Collektive<Uuid, Map<Uuid, List<Params>>> =
        Collektive(deviceId, MqttMailbox(deviceId, "broker.hivemq.com", dispatcher = dispatcher)) {
            neighboring(devicesValues).alignedMap(euclideanDistance3D(position)) { _: Uuid, deviceValues: Map<Uuid, Triple<Float, String, String>>, distance: Double ->
                deviceValues.entries.map { (sender, messagingParams) ->
                    Params(
                        sender to messagingParams.second,
                        localId to userName,
                        messagingParams.first,
                        distance,
                        messagingParams.third,
                        time,
                        senders.containsKey(sender) && messagingParams.first != -1f && sender != localId
                    )
                }
            }.toMap()
                .filterKeys { senders.containsKey(it) && it != localId }
                .mapValues { (key, list) ->
                    list.filter { it.isSenderValues && it.to.first == key }
                }
        }.also {
            delay(2.seconds)
        }
}
