package com.segment.analytics.platform.plugins.android

import android.app.Activity
import android.app.Application
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.lifecycle.*
import com.segment.analytics.Analytics
import com.segment.analytics.Storage
import com.segment.analytics.platform.Plugin
import com.segment.analytics.platform.plugins.LogType
import com.segment.analytics.platform.plugins.log
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

// Android specific class that mediates lifecycle plugin callbacks
class AndroidLifecyclePlugin() : Application.ActivityLifecycleCallbacks, DefaultLifecycleObserver, Plugin {

    override val type: Plugin.Type = Plugin.Type.Utility
    override val name: String = "AnalyticsActivityLifecycleCallbacks"
    override lateinit var analytics: Analytics
    private lateinit var packageInfo: PackageInfo
    private lateinit var application: Application

    // config properties
    private var shouldTrackApplicationLifecycleEvents: Boolean = true
    private var trackDeepLinks: Boolean = true
    private var shouldRecordScreenViews: Boolean = true
    private var useLifecycleObserver: Boolean = false

    // state properties
    private val trackedApplicationLifecycleEvents = AtomicBoolean(false)
    private val numberOfActivities = AtomicInteger(1)
    private val firstLaunch = AtomicBoolean(false)
    private val isChangingActivityConfigurations = AtomicBoolean(false)
    private lateinit var lifecycle: Lifecycle
    private lateinit var storage: Storage

    override fun setup(analytics: Analytics) {
        super.setup(analytics)
        application = analytics.configuration.application as? Application
            ?: error("no android application context registered")
        storage = analytics.storage

        // setup lifecycle listeners
        application.registerActivityLifecycleCallbacks(this)
        if (useLifecycleObserver) {
            lifecycle = ProcessLifecycleOwner.get().lifecycle
            lifecycle.addObserver(this)
        }

        val packageManager: PackageManager = application.packageManager
        packageInfo = try {
            packageManager.getPackageInfo(application.packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            throw AssertionError("Package not found: " + application.packageName)
        }

        analytics.configuration.let {
            shouldTrackApplicationLifecycleEvents = it.trackApplicationLifecycleEvents
            trackDeepLinks = it.trackDeepLinks
            useLifecycleObserver = it.useLifecycleObserver
        }
    }

    private fun runOnAnalyticsThread(block: () -> Unit) = with(analytics) {
        analyticsScope.launch(processingDispatcher) {
            block()
        }
    }


    /* OLD LIFECYCLE HOOKS */
    override fun onActivityCreated(activity: Activity, bundle: Bundle?) {
        runOnAnalyticsThread {
            analytics.timeline.applyClosure { plugin: Plugin? ->
                if (plugin is AndroidLifecycle) {
                    plugin.onActivityCreated(activity, bundle)
                }
            }
        }
        if (!useLifecycleObserver) {
            onCreate(stubOwner)
        }
        if (trackDeepLinks) {
            trackDeepLink(activity)
        }
    }

    override fun onActivityStarted(activity: Activity) {
        runOnAnalyticsThread {
            analytics.timeline.applyClosure { plugin: Plugin? ->
                if (plugin is AndroidLifecycle) {
                    plugin.onActivityStarted(activity)
                }
            }
        }
    }

    override fun onActivityResumed(activity: Activity) {
        runOnAnalyticsThread {
            analytics.timeline.applyClosure { plugin: Plugin? ->
                if (plugin is AndroidLifecycle) {
                    plugin.onActivityResumed(activity)
                }
            }
        }
        if (!useLifecycleObserver) {
            onStart(stubOwner)
        }
    }

    override fun onActivityPaused(activity: Activity) {
        runOnAnalyticsThread {
            analytics.timeline.applyClosure { plugin: Plugin? ->
                if (plugin is AndroidLifecycle) {
                    plugin.onActivityPaused(activity)
                }
            }
        }
        if (!useLifecycleObserver) {
            onPause(stubOwner)
        }
    }

    override fun onActivityStopped(activity: Activity) {
        runOnAnalyticsThread {
            analytics.timeline.applyClosure { plugin: Plugin? ->
                if (plugin is AndroidLifecycle) {
                    plugin.onActivityStopped(activity)
                }
            }
        }
        if (!useLifecycleObserver) {
            onStop(stubOwner)
        }
    }

    override fun onActivitySaveInstanceState(activity: Activity, bundle: Bundle) {
        runOnAnalyticsThread {
            analytics.timeline.applyClosure { plugin: Plugin? ->
                if (plugin is AndroidLifecycle) {
                    plugin.onActivitySaveInstanceState(activity, bundle)
                }
            }
        }
    }

    override fun onActivityDestroyed(activity: Activity) {
        runOnAnalyticsThread {
            analytics.timeline.applyClosure { plugin: Plugin? ->
                if (plugin is AndroidLifecycle) {
                    plugin.onActivityDestroyed(activity)
                }
            }
        }
        if (!useLifecycleObserver) {
            onDestroy(stubOwner)
        }
    }

    /* NEW LIFECYCLE HOOKS (These get called alongside the old ones) */
    override fun onStop(owner: LifecycleOwner) {
        // App in background
        if (shouldTrackApplicationLifecycleEvents
            && numberOfActivities.decrementAndGet() == 0 && !isChangingActivityConfigurations.get()
        ) {
            analytics.track("Application Backgrounded")
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        // App in foreground
        if (shouldTrackApplicationLifecycleEvents
            && numberOfActivities.incrementAndGet() == 1 && !isChangingActivityConfigurations.get()
        ) {
            val properties = buildJsonObject {
                if (firstLaunch.get()) {
                    put("version", packageInfo.versionName)
                    put("build", packageInfo.getVersionCode().toString())
                }
                put("from_background", !firstLaunch.getAndSet(false))
            }
            analytics.track("Application Opened", properties)
        }
    }

    override fun onCreate(owner: LifecycleOwner) {
        // App created
        if (!trackedApplicationLifecycleEvents.getAndSet(true)
            && shouldTrackApplicationLifecycleEvents
        ) {
            numberOfActivities.set(0)
            firstLaunch.set(true)
            trackApplicationLifecycleEvents()
        }
    }

    override fun onResume(owner: LifecycleOwner) {}
    override fun onPause(owner: LifecycleOwner) {}
    override fun onDestroy(owner: LifecycleOwner) {}

    private fun trackDeepLink(activity: Activity?) {
        val intent = activity?.intent
        if (intent == null || intent.data == null) {
            return
        }
        val properties = buildJsonObject {
            val uri = intent.data
            uri?.let {
                for (parameter in uri.queryParameterNames) {
                    val value = uri.getQueryParameter(parameter)
                    if (value != null && value.trim().isNotEmpty()) {
                        put(parameter, value)
                    }
                }
                put("url", uri.toString())
            }
        }
        analytics.track("Deep Link Opened", properties)
    }

    internal fun trackApplicationLifecycleEvents() {
        // Get the current version.
        val packageInfo = packageInfo
        val currentVersion = packageInfo.versionName
        val currentBuild = packageInfo.getVersionCode().toString()

        // Get the previous recorded version.
        val previousVersion = storage.read(Storage.Constants.AppVersion)
        val previousBuild = storage.read(Storage.Constants.AppBuild)

        // Check and track Application Installed or Application Updated.
        if (previousBuild == null) {
            analytics.track(
                "Application Installed",
                buildJsonObject {
                    put(VERSION_KEY, currentVersion)
                    put(BUILD_KEY, currentBuild)
                })
        } else if (currentBuild != previousBuild) {
            analytics.track(
                "Application Updated",
                buildJsonObject {  //
                    put(VERSION_KEY, currentVersion)
                    put(BUILD_KEY, currentBuild)
                    put("previous_$VERSION_KEY", previousVersion)
                    put("previous_$BUILD_KEY", previousBuild.toString())
                })
        }

        // Update the recorded version.
        storage.write(Storage.Constants.AppVersion, currentVersion)
        storage.write(Storage.Constants.AppBuild, currentBuild)
    }

    fun unregisterListeners() {
        application.unregisterActivityLifecycleCallbacks(this)
        if (useLifecycleObserver) {
            // only unregister if feature is enabled
            lifecycle.removeObserver(this)
        }
    }

    companion object {
        private const val VERSION_KEY = "version"
        private const val BUILD_KEY = "build"

        // This is just a stub LifecycleOwner which is used when we need to call some lifecycle
        // methods without going through the actual lifecycle callbacks
        private val stubOwner: LifecycleOwner = object : LifecycleOwner {
            var stubLifecycle: Lifecycle = object : Lifecycle() {
                override fun addObserver(observer: LifecycleObserver) {
                    // NO-OP
                }

                override fun removeObserver(observer: LifecycleObserver) {
                    // NO-OP
                }

                override fun getCurrentState(): State {
                    return State.DESTROYED
                }
            }

            override fun getLifecycle(): Lifecycle {
                return stubLifecycle
            }
        }
    }
}

// Safely fetch version code managing deprecations across OS versions
private fun PackageInfo.getVersionCode(): Number =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        this.longVersionCode
    } else {
        @Suppress("DEPRECATION")
        this.versionCode
    }

// Basic interface for a plugin to consume lifecycle callbacks
interface AndroidLifecycle {
    fun onActivityCreated(activity: Activity?, savedInstanceState: Bundle?) {}
    fun onActivityStarted(activity: Activity?) {}
    fun onActivityResumed(activity: Activity?) {}
    fun onActivityPaused(activity: Activity?) {}
    fun onActivityStopped(activity: Activity?) {}
    fun onActivitySaveInstanceState(activity: Activity?, outState: Bundle?) {}
    fun onActivityDestroyed(activity: Activity?) {}
}