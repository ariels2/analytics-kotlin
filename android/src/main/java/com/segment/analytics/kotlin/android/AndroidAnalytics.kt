package com.segment.analytics.kotlin.android

import android.content.Context
import android.util.Log
import com.segment.analytics.kotlin.android.plugins.AndroidContextPlugin
import com.segment.analytics.kotlin.android.plugins.AndroidLifecyclePlugin
import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.Configuration
import com.segment.analytics.kotlin.core.platform.plugins.logger.*

// A set of functions tailored to the Android implementation of analytics

@Suppress("FunctionName")
/**
 * constructor function to build android specific analytics in dsl format
 * Usage: Analytics("$writeKey", applicationContext, applicationScope)
 *
 * NOTE: this method should only be used for Android application. Context is required.
 */
public fun Analytics(
    writeKey: String,
    context: Context
): Analytics {
    require(writeKey.isNotBlank()) { "writeKey cannot be blank " }
    val conf = Configuration(
        writeKey = writeKey,
        application = context,
        storageProvider = AndroidStorageProvider
    )
    return Analytics(conf).apply {
        startup()
    }
}

@Suppress("FunctionName")
/**
 * constructor function to build android specific analytics in dsl format with config options
 * Usage: Analytics("$writeKey", applicationContext) {
 *            this.analyticsScope = applicationScope
 *            this.collectDeviceId = false
 *            this.flushAt = 10
 *        }
 *
 * NOTE: this method should only be used for Android application. Context is required.
 */
public fun Analytics(
    writeKey: String,
    context: Context,
    configs: Configuration.() -> Unit
): Analytics {
    require(writeKey.isNotBlank()) { "writeKey cannot be blank " }
    val conf = Configuration(
        writeKey = writeKey,
        application = context,
        storageProvider = AndroidStorageProvider
    )
    configs.invoke(conf)
    return Analytics(conf).apply {
        startup()
    }
}

// Android specific startup
private fun Analytics.startup() {
    add(AndroidContextPlugin())
    add(AndroidLifecyclePlugin())
    add(AndroidLogTarget(), LoggingType.log)
    remove(targetType = ConsoleTarget::class)
}

class AndroidLogTarget: LogTarget {
    override fun parseLog(log: LogMessage) {
        var metadata = ""
        val function = log.function
        val line = log.line
        if (function != null && line != null) {
            metadata = " - $function:$line"
        }

        var eventString = ""
        log.event.let {
            eventString = ", event=$it"
        }

        when (log.kind) {
            LogFilterKind.ERROR -> {
                Log.e("AndroidLog", "message=${log.message}$eventString")
            }
            LogFilterKind.WARNING -> {
                Log.w("AndroidLog", "message=${log.message}$eventString")
            }
            LogFilterKind.DEBUG -> {
                Log.d("AndroidLog", "message=${log.message}$eventString")
            }
        }
    }

    override fun flush() { }
}