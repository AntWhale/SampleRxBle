package com.boo.sample.samplerxble

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.boo.sample.samplerxble.databinding.ActivityMainBinding
import com.boo.sample.samplerxble.ui.adapter.BleListAdapter
import com.boo.sample.samplerxble.ui.dialog.WriteDialog
import com.boo.sample.samplerxble.util.Util
import com.boo.sample.samplerxble.viewmodel.BleRepository
import com.boo.sample.samplerxble.viewmodel.BleViewModel
import com.polidea.rxandroidble2.RxBleClient
import com.polidea.rxandroidble2.exceptions.BleScanException
import com.polidea.rxandroidble2.scan.ScanResult
import com.polidea.rxandroidble2.scan.ScanSettings
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.disposables.Disposable
import io.reactivex.exceptions.UndeliverableException
import io.reactivex.plugins.RxJavaPlugins
import javax.inject.Inject

//힐트를 적용해보자!!
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    val TAG = "MainActivity"

    //private val bleRepository = BleRepository()
    //private var mViewModel:BleViewModel? = null
    /*private val viewModel by lazy {
        ViewModelProvider(this, BleViewModel.Factory(bleRepository)).get(BleViewModel::class.java)
    }*/
    private val viewModel: BleViewModel by viewModels()

    private var adapter: BleListAdapter? = null
    private var requestEnableBluetooth = false
    private var askGrant = false

    @RequiresApi(Build.VERSION_CODES.S)
    private val blePermissions = arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_ADVERTISE
    )

    private val ble_permission_request_code = 10

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = DataBindingUtil.setContentView<ActivityMainBinding>(this, R.layout.activity_main)
        binding.viewModel = viewModel

        binding.rvBleList.setHasFixedSize(true)
        val layoutManager: RecyclerView.LayoutManager = LinearLayoutManager(this)
        binding.rvBleList.layoutManager = layoutManager

        adapter = BleListAdapter()
        binding.rvBleList.adapter = adapter
        adapter?.setItemClickListener(object: BleListAdapter.ItemClickListener{
            override fun onClick(view: View, scanResult: ScanResult?) {
                val device = scanResult?.bleDevice
                if(device != null){
                    viewModel.connectDevice(device)
                }
            }
        })

        initObserver(binding)

        if(!hasPermissions(this, PERMISSIONS)){
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S){
                requestPermissions(PERMISSIONS, REQUEST_ALL_PERMISSION)
            }
        }
    }

    private fun initObserver(binding: ActivityMainBinding){
        viewModel.apply{
            requestEnableBLE.observe(this@MainActivity, Observer {
                it.getContentIfNotHandled()?.let{ reason ->
                    viewModel.stopScan()
                    when(reason) {
                        BleScanException.BLUETOOTH_CANNOT_START -> Util.showNotification("BLUETOOTH CANNOT START")
                        BleScanException.BLUETOOTH_DISABLED -> {
                            requestEnableBluetooth = true
                            requestEnableBLE()
                        }
                        BleScanException.BLUETOOTH_NOT_AVAILABLE -> Util.showNotification("블루투스 지원하지 않는 기기입니다.")
                        BleScanException.LOCATION_PERMISSION_MISSING, BleScanException.LOCATION_SERVICES_DISABLED -> {
                            if (!askGrant) {
                                askGrant = true
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    requestPermissions(PERMISSIONS, REQUEST_ALL_PERMISSION)
                                }
                            }
                        }
                        BleScanException.SCAN_FAILED_ALREADY_STARTED -> Util.showNotification("SCAN FAILED ALREADY STARTED")
                        BleScanException.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> Util.showNotification(
                            "SCAN FAILED APPLICATION REGISTRATION FAILED"
                        )
                        BleScanException.SCAN_FAILED_INTERNAL_ERROR -> Util.showNotification("SCAN FAILED INTERNAL ERROR")
                        BleScanException.SCAN_FAILED_FEATURE_UNSUPPORTED -> Util.showNotification("SCAN FAILED FEATURE UNSUPPORTED")
                        BleScanException.SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES -> Util.showNotification(
                            "SCAN FAILED OUT OF HARDWARE RESOURCES"
                        )
                        BleScanException.UNDOCUMENTED_SCAN_THROTTLE -> Util.showNotification("UNDOCUMENTED SCAN THROTTLE")
                        else -> Util.showNotification("UNKNOWN ERROR CODE")
                    }
                }
            })

            listUpdate.observe(this@MainActivity, Observer {
                it.getContentIfNotHandled()?.let { scanResults ->
                    adapter?.setItem(scanResults)
                }
            })

            readTxt.observe(this@MainActivity, Observer {
                binding.txtRead.append("$it\n")
                if((binding.txtRead.measuredHeight - binding.scroller.scrollY) <= (binding.scroller.height + binding.txtRead.lineHeight)){
                    binding.scroller.post {
                        binding.scroller.scrollTo(0, binding.txtRead.bottom)
                    }
                }
            })
        }
    }

    fun onClickWrite(view: View){
        val writeDialog = WriteDialog(this@MainActivity, object : WriteDialog.WriteDialogListener {
            override fun onClickSend(data: String, type: String) {
                viewModel.writeData(data, type)
            }
        })
        writeDialog.show()
    }

    //request BLE enable
    private fun requestEnableBLE() {
        val bleEnableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        requestEnableBleResult.launch(bleEnableIntent)
    }

    private val requestEnableBleResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){ result ->
        if(result.resultCode == Activity.RESULT_OK){
            val intent = result.data
            Util.showNotification("Bluetooth기능을 허용하였습니다.")
        } else {
            Util.showNotification("Bluetooth기능을 켜주세요.")
        }
        requestEnableBluetooth = false
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == ble_permission_request_code && grantResults.isNotEmpty()) {
            for (resultInt in grantResults) {
                if (resultInt == -1) return
            }
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, 1)
            Log.d(TAG, "onRequestPermissionsResult: permission request ok")

        }
    }

    private fun hasPermissions(context: Context?, permissions: Array<String>): Boolean {
        if(context != null){
            for(permission in permissions){
                if(ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) return false
            }
        }
        return true
    }

    override fun onResume() {
        super.onResume()
        if(!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)){
            finish()
        }
    }

}