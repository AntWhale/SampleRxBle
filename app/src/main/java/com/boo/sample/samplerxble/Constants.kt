package com.boo.sample.samplerxble

import android.Manifest
import android.os.Build
import androidx.annotation.RequiresApi

const val REQUEST_ENABLE_BT = 1
const val REQUEST_ALL_PERMISSION = 2

@RequiresApi(Build.VERSION_CODES.S)
val PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)

//사용자 BLE UUID Service/Rx/Tx
const val SERVICE_STRING = "6E400001-B5A3-F393-E0A9-E50E24DCCA9E"
const val CHARACTERISTIC_COMMAND_STRING = "6E400002-B5A3-F393-E0A9-E50E24DCCA9E"
const val CHARACTERISTIC_RESPONSE_STRING = "6E400003-B5A3-F393-E0A9-E50E24DCCA9E"