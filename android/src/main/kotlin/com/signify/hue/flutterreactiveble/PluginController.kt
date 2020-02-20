package com.signify.hue.flutterreactiveble

import com.polidea.rxandroidble2.exceptions.BleException
import com.signify.hue.flutterreactiveble.ble.BleClient
import com.signify.hue.flutterreactiveble.ble.CharOperationFailed
import com.signify.hue.flutterreactiveble.ble.CharOperationResult
import com.signify.hue.flutterreactiveble.ble.CharOperationSuccessful
import com.signify.hue.flutterreactiveble.ble.MtuNegotiateFailed
import com.signify.hue.flutterreactiveble.ble.ReactiveBleClient
import com.signify.hue.flutterreactiveble.ble.RequestConnectionPriorityFailed
import com.signify.hue.flutterreactiveble.channelhandlers.BleStatusHandler
import com.signify.hue.flutterreactiveble.channelhandlers.CharNotificationHandler
import com.signify.hue.flutterreactiveble.channelhandlers.DeviceConnectionHandler
import com.signify.hue.flutterreactiveble.channelhandlers.ScanDevicesHandler
import com.signify.hue.flutterreactiveble.converters.ProtobufMessageConverter
import com.signify.hue.flutterreactiveble.converters.UuidConverter
import com.signify.hue.flutterreactiveble.model.ClearGattCacheErrorType
import com.signify.hue.flutterreactiveble.utils.discard
import com.signify.hue.flutterreactiveble.utils.toConnectionPriority
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.exceptions.UndeliverableException
import io.reactivex.plugins.RxJavaPlugins
import timber.log.Timber
import java.util.UUID
import com.signify.hue.flutterreactiveble.ProtobufModel as pb

@Suppress("TooManyFunctions")
class PluginController {
    private val pluginMethods = mapOf<String, (call: MethodCall, result: Result) -> Unit>(
            "initialize" to this::initializeClient,
            "deinitialize" to this::deinitializeClient,
            "scanForDevices" to this::scanForDevices,
            "connectToDevice" to this::connectToDevice,
            "clearGattCache" to this::clearGattCache,
            "disconnectFromDevice" to this::disconnectFromDevice,
            "readCharacteristic" to this::readCharacteristic,
            "writeCharacteristicWithResponse" to this::writeCharacteristicWithResponse,
            "writeCharacteristicWithoutResponse" to this::writeCharacteristicWithoutResponse,
            "readNotifications" to this::readNotifications,
            "stopNotifications" to this::stopNotifications,
            "negotiateMtuSize" to this::negotiateMtuSize,
            "requestConnectionPriority" to this::requestConnectionPriority
    )

    lateinit var bleClient: BleClient

    lateinit var scanchannel: EventChannel
    lateinit var deviceConnectionChannel: EventChannel
    lateinit var charNotificationChannel: EventChannel

    lateinit var scandevicesHandler: ScanDevicesHandler
    lateinit var deviceConnectionHandler: DeviceConnectionHandler
    lateinit var charNotificationHandler: CharNotificationHandler

    private val uuidConverter = UuidConverter()
    private val protoConverter = ProtobufMessageConverter()

    internal fun initialize(registrar: PluginRegistry.Registrar) {
        bleClient = ReactiveBleClient(registrar.context())

        scanchannel = EventChannel(registrar.messenger(), "flutter_reactive_ble_scan")
        deviceConnectionChannel = EventChannel(registrar.messenger(), "flutter_reactive_ble_connected_device")
        charNotificationChannel = EventChannel(registrar.messenger(), "flutter_reactive_ble_char_update")
        val bleStatusChannel = EventChannel(registrar.messenger(), "flutter_reactive_ble_status")

        scandevicesHandler = ScanDevicesHandler(bleClient)
        deviceConnectionHandler = DeviceConnectionHandler(bleClient)
        charNotificationHandler = CharNotificationHandler(bleClient)
        val bleStatusHandler = BleStatusHandler(bleClient)

        scanchannel.setStreamHandler(scandevicesHandler)
        deviceConnectionChannel.setStreamHandler(deviceConnectionHandler)
        charNotificationChannel.setStreamHandler(charNotificationHandler)
        bleStatusChannel.setStreamHandler(bleStatusHandler)

        /*Workaround for issue undeliverable https://github.com/Polidea/RxAndroidBle/wiki/FAQ:-UndeliverableException
        note that this not override the onError for the observable only the RXJAVA error handler like described in:
        https://github.com/ReactiveX/RxJava/wiki/What's-different-in-2.0#error-handling
        */
        RxJavaPlugins.setErrorHandler { throwable ->
            if (throwable is UndeliverableException && throwable.cause is BleException) {
                return@setErrorHandler // ignore BleExceptions as they were surely delivered at least once
            }
            // add other custom handlers if needed
            @Suppress("TooGenericExceptionThrown")
            throw RuntimeException("Unexpected Throwable in RxJavaPlugins error handler", throwable)
        }
    }

    internal fun execute(call: MethodCall, result: Result) {
        pluginMethods[call.method]?.invoke(call, result) ?: result.notImplemented()
    }

    private fun initializeClient(call: MethodCall, result: Result) {
        if (bleClient.hasEstablishedConnections) {
            bleClient.clearAllConnections()
        }

        bleClient.initializeClient()
        result.success(null)
    }

    private fun deinitializeClient(call: MethodCall, result: Result) {
        scandevicesHandler.stopDeviceScan()
        deviceConnectionHandler.disconnectAll()
        result.success(null)
    }

    private fun scanForDevices(call: MethodCall, result: Result) {
        Timber.d("start scanning")
        scandevicesHandler.prepareScan(pb.ScanForDevicesRequest.parseFrom(call.arguments as ByteArray))
        result.success(null)
    }

    private fun connectToDevice(call: MethodCall, result: Result) {
        result.success(null)
        val connectDeviceMessage = pb.ConnectToDeviceRequest.parseFrom(call.arguments as ByteArray)
        Timber.d("Start connecting for device ${connectDeviceMessage.deviceId}")
        deviceConnectionHandler.connectToDevice(connectDeviceMessage)
    }

    private fun clearGattCache(call: MethodCall, result: Result) {
        val args = pb.ClearGattCacheRequest.parseFrom(call.arguments as ByteArray)
        bleClient.clearGattCache(args.deviceId)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        {
                            val info = pb.ClearGattCacheInfo.getDefaultInstance()
                            result.success(info.toByteArray())
                        },
                        {
                            val info = protoConverter.convertClearGattCacheError(
                                    ClearGattCacheErrorType.UNKNOWN,
                                    it.message
                            )
                            result.success(info.toByteArray())
                        }
                )
                .discard()
    }

    private fun disconnectFromDevice(call: MethodCall, result: Result) {
        result.success(null)
        val connectDeviceMessage = pb.DisconnectFromDeviceRequest.parseFrom(call.arguments as ByteArray)
        Timber.d("Disconnect device: ${connectDeviceMessage.deviceId}")
        deviceConnectionHandler.disconnectDevice(connectDeviceMessage.deviceId)
    }

    private fun readCharacteristic(call: MethodCall, result: Result) {
        result.success(null)

        val readCharMessage = pb.ReadCharacteristicRequest.parseFrom(call.arguments as ByteArray)
        val deviceId = readCharMessage.characteristic.deviceId
        val characteristic = uuidConverter.uuidFromByteArray(readCharMessage.characteristic.characteristicUuid.data.toByteArray())

        Timber.d("Read req dev=$deviceId, uuid=$characteristic")

        bleClient.readCharacteristic(
                readCharMessage.characteristic.deviceId, characteristic
        )
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { charResult ->
                            when (charResult) {
                                is CharOperationSuccessful -> {
                                    val charInfo = protoConverter.convertCharacteristicInfo(
                                            readCharMessage.characteristic,
                                            charResult.value.toByteArray()
                                    )
                                    charNotificationHandler.addSingleReadToStream(charInfo)
                                }
                                is CharOperationFailed -> {
                                    Timber.d("read value failed} ${charResult.errorMessage}")
                                    protoConverter.convertCharacteristicError(readCharMessage.characteristic,
                                            "Failed to connect")
                                    charNotificationHandler.addSingleErrorToStream(
                                            readCharMessage.characteristic,
                                            charResult.errorMessage
                                    )
                                }
                            }
                        },
                        { throwable ->
                            Timber.d("whoops deviceid= $deviceId char=$characteristic  message= ${throwable.message}")
                            protoConverter.convertCharacteristicError(
                                    readCharMessage.characteristic,
                                    throwable.message)
                            charNotificationHandler.addSingleErrorToStream(
                                    readCharMessage.characteristic,
                                    throwable?.message ?: "Failure")
                        }
                )
                .discard()
    }

    private fun writeCharacteristicWithResponse(call: MethodCall, result: Result) {
        executeWriteAndPropagateResultToChannel(call, result, BleClient::writeCharacteristicWithResponse)
    }

    private fun writeCharacteristicWithoutResponse(call: MethodCall, result: Result) {
        executeWriteAndPropagateResultToChannel(call, result, BleClient::writeCharacteristicWithoutResponse)
    }

    private fun executeWriteAndPropagateResultToChannel(
        call: MethodCall,
        result: Result,
        writeOperation: BleClient.(
            deviceId: String,
            characteristic: UUID,
            value: ByteArray
        ) -> Single<CharOperationResult>
    ) {
        val writeCharMessage = pb.WriteCharacteristicRequest.parseFrom(call.arguments as ByteArray)
        bleClient.writeOperation(writeCharMessage.characteristic.deviceId,
                uuidConverter.uuidFromByteArray(writeCharMessage.characteristic.characteristicUuid.data.toByteArray()),
                writeCharMessage.value.toByteArray())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ operationResult ->
                    when (operationResult) {
                        is CharOperationSuccessful -> {
                            Timber.d("Value succesfully written, $writeOperation")
                            result.success(protoConverter.convertWriteCharacteristicInfo(writeCharMessage,
                                    null).toByteArray())
                        }
                        is CharOperationFailed -> {
                            Timber.d("Value write failed ${operationResult.errorMessage}")
                            result.success(protoConverter.convertWriteCharacteristicInfo(writeCharMessage,
                                    operationResult.errorMessage).toByteArray())
                        }
                    }
                },
                        { throwable ->
                            Timber.d("whoops: ${throwable.message}")
                            result.success(protoConverter.convertWriteCharacteristicInfo(writeCharMessage,
                                    throwable.message).toByteArray())
                        }
                )
                .discard()
    }

    private fun readNotifications(call: MethodCall, result: Result) {
        Timber.d("read notifications")
        val request = pb.NotifyCharacteristicRequest.parseFrom(call.arguments as ByteArray)
        charNotificationHandler.subscribeToNotifications(request)
        result.success(null)
    }

    private fun stopNotifications(call: MethodCall, result: Result) {
        Timber.d("stop notifications")
        val request = pb.NotifyNoMoreCharacteristicRequest.parseFrom(call.arguments as ByteArray)
        charNotificationHandler.unsubscribeFromNotifications(request)
        result.success(null)
    }

    private fun negotiateMtuSize(call: MethodCall, result: Result) {
        val request = pb.NegotiateMtuRequest.parseFrom(call.arguments as ByteArray)
        bleClient.negotiateMtuSize(request.deviceId, request.mtuSize)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ mtuResult ->
                    result.success(protoConverter.convertNegotiateMtuInfo(mtuResult).toByteArray())
                }, { throwable ->
                    result.success(protoConverter.convertNegotiateMtuInfo(MtuNegotiateFailed(request.deviceId,
                            throwable.message ?: "")).toByteArray())
                }
                )
                .discard()
    }

    private fun requestConnectionPriority(call: MethodCall, result: Result) {
        val request = pb.ChangeConnectionPriorityRequest.parseFrom(call.arguments as ByteArray)

        bleClient.requestConnectionPriority(request.deviceId, request.priority.toConnectionPriority())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ requestResult ->
                    result.success(protoConverter
                            .convertRequestConnectionPriorityInfo(requestResult).toByteArray())
                },
                        { throwable ->
                            result.success(protoConverter.convertRequestConnectionPriorityInfo(
                                    RequestConnectionPriorityFailed(request.deviceId, throwable?.message
                                            ?: "Unknown error")).toByteArray())
                        })
                .discard()
    }
}
