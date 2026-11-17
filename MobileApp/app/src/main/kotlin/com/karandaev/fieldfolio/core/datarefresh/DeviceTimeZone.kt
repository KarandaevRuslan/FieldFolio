package com.karandaev.fieldfolio.core.datarefresh

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.time.ZoneId

/** Device time zone comes from Android, never from GPS. */
@Composable
fun rememberDeviceTimeZone(): ZoneId {
  val context = LocalContext.current
  val owner = LocalLifecycleOwner.current
  var zone by remember { mutableStateOf(ZoneId.systemDefault()) }
  DisposableEffect(context, owner) {
    val receiver =
      object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
          zone = ZoneId.systemDefault()
        }
      }
    val filter = IntentFilter(Intent.ACTION_TIMEZONE_CHANGED)
    if (Build.VERSION.SDK_INT >= 33) {
      context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
    } else {
      @Suppress("DEPRECATION") context.registerReceiver(receiver, filter)
    }
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) zone = ZoneId.systemDefault()
    }
    owner.lifecycle.addObserver(observer)
    onDispose {
      context.unregisterReceiver(receiver)
      owner.lifecycle.removeObserver(observer)
    }
  }
  return zone
}
