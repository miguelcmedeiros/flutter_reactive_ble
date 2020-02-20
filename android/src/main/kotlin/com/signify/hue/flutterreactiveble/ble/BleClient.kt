package com.signify.hue.flutterreactiveble.ble

import android.os.ParcelUuid
import com.signify.hue.flutterreactiveble.model.ScanMode
import com.signify.hue.flutterreactiveble.utils.Duration
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.subjects.BehaviorSubject
import java.util.UUID

@Suppress("TooManyFunctions")
interface BleClient {

    val connectionUpdateSubject: BehaviorSubject<ConnectionUpdate>
    val hasEstablishedConnections: Boolean

    fun initializeClient()
    fun clearAllConnections()
    fun scanForDevices(service: ParcelUuid, scanMode: ScanMode): Observable<ScanInfo>
    fun connectToDevice(deviceId: String, timeout: Duration)
    fun disconnectDevice(deviceId: String)
    fun disconnectAllDevices()
    fun clearGattCache(deviceId: String): Completable
    fun readCharacteristic(deviceId: String, characteristic: UUID): Single<CharOperationResult>
    fun setupNotification(deviceId: String, characteristic: UUID): Observable<ByteArray>
    fun writeCharacteristicWithResponse(
        deviceId: String,
        characteristic: UUID,
        value: ByteArray
    ): Single<CharOperationResult>
    fun writeCharacteristicWithoutResponse(
        deviceId: String,
        characteristic: UUID,
        value: ByteArray
    ): Single<CharOperationResult>
    fun negotiateMtuSize(deviceId: String, size: Int): Single<MtuNegotiateResult>
    fun observeBleStatus(): Observable<BleStatus>
    fun requestConnectionPriority(deviceId: String, priority: ConnectionPriority):
            Single<RequestConnectionPriorityResult>
}
