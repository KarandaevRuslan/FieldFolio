package com.karandaev.fieldfolio.core.document

import android.content.Context

object ReportViewPreferences {
  private fun prefs(context: Context) =
    context.getSharedPreferences("report_view", Context.MODE_PRIVATE)

  fun headerVisible(context: Context, report: Long) =
    prefs(context).getBoolean("header:$report", true)

  fun setHeaderVisible(context: Context, report: Long, visible: Boolean) {
    prefs(context).edit().putBoolean("header:$report", visible).apply()
  }
}
