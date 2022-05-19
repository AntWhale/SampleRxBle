package com.boo.sample.samplerxble.util

import android.widget.Toast
import com.boo.sample.samplerxble.MyApplication

class Util {
    companion object{
        fun showNotification(msg: String){
            Toast.makeText(MyApplication.applicationContext(),msg,Toast.LENGTH_SHORT).show()
        }
    }
}