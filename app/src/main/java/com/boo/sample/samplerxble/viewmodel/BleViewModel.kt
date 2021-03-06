package com.boo.sample.samplerxble.viewmodel

import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.databinding.ObservableBoolean
import androidx.databinding.ObservableField
import androidx.lifecycle.*
import com.boo.sample.samplerxble.MyApplication
import com.boo.sample.samplerxble.SERVICE_STRING
import com.boo.sample.samplerxble.util.Event
import com.boo.sample.samplerxble.util.Util
import com.polidea.rxandroidble2.RxBleClient
import com.polidea.rxandroidble2.RxBleConnection
import com.polidea.rxandroidble2.RxBleDevice
import com.polidea.rxandroidble2.exceptions.BleScanException
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.disposables.Disposable
import kotlinx.coroutines.launch
import java.lang.StringBuilder
import java.nio.charset.Charset
import java.util.*
import javax.inject.Inject
import kotlin.collections.ArrayList
import kotlin.concurrent.schedule

@HiltViewModel
class BleViewModel @Inject constructor(private val repository: BleRepository): ViewModel() {

    class Factory(val repository: BleRepository): ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return BleViewModel(repository) as T
        }
    }

    private lateinit var mScanSubscription: Disposable
    private var mConnectSubscription: Disposable? = null
    private var mNotificationSubscription: Disposable? = null
    private var mWriteSubscription: Disposable? = null
    private lateinit var connectionStateDisposable: Disposable

    val TAG = "BleViewModel"

    //dataBinding
    var statusTxt = ObservableField("Press the Scan button to start Ble Scan.")
    var scanVisible = ObservableBoolean(true)
    var readTxt = MutableLiveData("")
    var connectedTxt = ObservableField("")
    var isScanning = ObservableBoolean(false)
    var isConnecting = ObservableBoolean(false)
    var isConnect = ObservableBoolean(false)

    var isRead = false

    private val _requestEnableBLE = MutableLiveData<Event<Int>>()
    val requestEnableBLE: LiveData<Event<Int>>
        get() = _requestEnableBLE

    private val _listUpdate = MutableLiveData<Event<ArrayList<com.polidea.rxandroidble2.scan.ScanResult>?>>()
    val listUpdate: LiveData<Event<ArrayList<com.polidea.rxandroidble2.scan.ScanResult>?>>
        get() = _listUpdate

    //scan results
    private var scanResults: ArrayList<com.polidea.rxandroidble2.scan.ScanResult>? = ArrayList()
    private val rxBleClient: RxBleClient = RxBleClient.create((MyApplication.applicationContext()))

    //Start BLE Scan
    @RequiresApi(Build.VERSION_CODES.M)
    fun onClickScan(){
        startScan()
    }

    private fun startScan() {
        scanVisible.set(true)
        //scan filter
        val scanFilter: com.polidea.rxandroidble2.scan.ScanFilter = com.polidea.rxandroidble2.scan.ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(UUID.fromString(SERVICE_STRING)))
            .build()
        //scan settings
        val settings: com.polidea.rxandroidble2.scan.ScanSettings = com.polidea.rxandroidble2.scan.ScanSettings.Builder()
            .setScanMode(com.polidea.rxandroidble2.scan.ScanSettings.SCAN_MODE_LOW_POWER)
            .build()

        scanResults = ArrayList()

        mScanSubscription = rxBleClient.scanBleDevices(settings)
            .subscribe({ scanResult ->
                addScanResult(scanResult)
                Log.d(TAG, "scan Result: $scanResult")
            },{ throwable ->
                if(throwable is BleScanException){
                    _requestEnableBLE.postValue(Event(throwable.reason))
                } else {
                    Util.showNotification("UNKNOWN ERROR")
                }
            })

        isScanning.set(true)

        Timer("SettingUp", false).schedule(4000){ stopScan() }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun stopScan() {
        mScanSubscription.dispose()
        isScanning.set(false)
        statusTxt.set("Scan finished. Click on the name to connect to the device.")

        scanResults = ArrayList()
    }

    private fun addScanResult(result: com.polidea.rxandroidble2.scan.ScanResult) {
        //get scanned device
        val device = result.bleDevice
        //get scanned device MAC address
        val deviceAddress = device.macAddress
        //add the device to the result list
        for(dev in scanResults!!){
            if(dev.bleDevice.macAddress == deviceAddress) return
        }
        scanResults?.add(result)

        statusTxt.set("add scanned device: $deviceAddress")
        _listUpdate.postValue(Event(scanResults))
    }

    fun onClickDisconnect() {
        mConnectSubscription?.dispose()
    }

    fun connectDevice(device: RxBleDevice) {
        //register connectionStateListener
        connectionStateDisposable = device.observeConnectionStateChanges()
            .subscribe({ connectionState ->
                connectionStateListener(device, connectionState)
            }, { throwable ->
                throwable.printStackTrace()
            })
        //connect
        mConnectSubscription = repository.bleConnectObservable(device)
    }

    private fun connectionStateListener(
        device: RxBleDevice,
        connectionState: RxBleConnection.RxBleConnectionState
    ){
        when(connectionState){
            RxBleConnection.RxBleConnectionState.CONNECTED -> {
                isConnect.set(true)
                isConnecting.set(false)
                scanVisible.set(false)
                connectedTxt.set("${device.macAddress} Connected.")
            }
            RxBleConnection.RxBleConnectionState.CONNECTING -> {
                isConnecting.set(true)
            }
            RxBleConnection.RxBleConnectionState.DISCONNECTED -> {
                isConnect.set(false)
                isConnect.set(false)
                isConnecting.set(false)
                scanVisible.set(true)
                scanResults = java.util.ArrayList()
                _listUpdate.postValue(Event(scanResults))
            }
            RxBleConnection.RxBleConnectionState.DISCONNECTING -> {
            }
        }
    }

    // notify toggle
    fun onClickRead(){
        if(!isRead){
            mNotificationSubscription = repository.bleNotification()
                ?.subscribe({ bytes ->
                    //Given characteristic has been changes, here is the value
                    readTxt.postValue(byteArrayToHex(bytes))
                    isRead = true
                },{ throwable ->
                    //Handle an error here
                    throwable.printStackTrace()
                    mConnectSubscription?.dispose()
                    isRead = false
                })
        } else {
            isRead = false
            mNotificationSubscription?.dispose()
        }
    }

    fun writeData(data: String, type: String){
        var sendByteData: ByteArray? = null
        when(type){
            "string" -> {
                sendByteData = data.toByteArray(Charset.defaultCharset())
            }
            "byte" -> {
                if(data.length % 2 != 0) {
                    Util.showNotification("Byte Size Error")
                    return
                }
                sendByteData = hexStringToByteArray(data)
            }
        }
        if(sendByteData != null) {
            mWriteSubscription = repository.writeData(sendByteData)?.subscribe({ writeBytes ->
                //Written data
                val str: String = byteArrayToHex(writeBytes)
                Log.d(TAG, "writtenBytes: $str")
                viewModelScope.launch {
                    Util.showNotification("`$str` is written.")
                }
            },{ throwable ->
                //Handle an error here.
                throwable.printStackTrace()
            })
        }
    }

    private fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        var i = 0
        while(i < len){
            data[i / 2] = ((Character.digit(s[i], 16) shl 4) + Character.digit(s[i+1], 16)).toByte()
            i += 2
        }
        return data
    }

    private fun byteArrayToHex(a: ByteArray): String {
        val sb = StringBuilder(a.size * 2)
        for(b in a) sb.append(String.format("%02x", b))
        return sb.toString()
    }

    override fun onCleared() {
        super.onCleared()
        mScanSubscription.dispose()
        mConnectSubscription?.dispose()
        mWriteSubscription?.dispose()
        connectionStateDisposable.dispose()
    }
}