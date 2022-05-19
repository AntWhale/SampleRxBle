package com.boo.sample.samplerxble.viewmodel

import android.bluetooth.BluetoothGattService
import com.boo.sample.samplerxble.CHARACTERISTIC_RESPONSE_STRING
import com.polidea.rxandroidble2.RxBleConnection
import com.polidea.rxandroidble2.RxBleDevice
import com.polidea.rxandroidble2.RxBleDeviceServices
import io.reactivex.disposables.Disposable
import java.util.*

class BleRepository {
    var rxBleConnection: RxBleConnection? = null
    var rxBleDeviceServices: RxBleDeviceServices? = null
    var bleGattServices: List<BluetoothGattService>? = null
    var mDiscoverServiceSubscription: Disposable? = null

    //Connect & Discover Services
    fun bleConnectObservable(device: RxBleDevice): Disposable = device.establishConnection(false)
        .subscribe({ _rxBleConnection ->
            rxBleConnection = _rxBleConnection

            //Discover services
            mDiscoverServiceSubscription = _rxBleConnection.discoverServices().subscribe({ _rxBleDeviceServices ->
                rxBleDeviceServices = _rxBleDeviceServices
                bleGattServices = _rxBleDeviceServices.bluetoothGattServices
            },{ discoverServicesThrowable ->
                discoverServicesThrowable.printStackTrace()
            })
        },{ connectionThrowable ->
            connectionThrowable.printStackTrace()
        })

    //Notification
    fun bleNotification() = rxBleConnection
        ?.setupNotification(UUID.fromString(CHARACTERISTIC_RESPONSE_STRING))
        ?.doOnNext { notificationObservable ->
            //Notification has been set up
        }
        ?.flatMap { notificationObservable -> notificationObservable }

    //Write Data
    fun writeData(sendByteData: ByteArray) = rxBleConnection?.writeCharacteristic(
        UUID.fromString(CHARACTERISTIC_RESPONSE_STRING),
        sendByteData
    )
}