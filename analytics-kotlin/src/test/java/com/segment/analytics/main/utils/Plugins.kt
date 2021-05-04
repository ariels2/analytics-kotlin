package com.segment.analytics.main.utils

import com.segment.analytics.*
import com.segment.analytics.platform.EventPlugin
import com.segment.analytics.platform.Plugin

class TestRunPlugin(override val name: String = "TestRunPlugin", var closure: (BaseEvent?) -> Unit): EventPlugin {
    override val type: Plugin.Type = Plugin.Type.Before
    override lateinit var analytics: Analytics
    var ran = false

    fun reset() {
        ran = false
    }

    override fun execute(event: BaseEvent): BaseEvent {
        ran = true
        return event
    }

    override fun track(payload: TrackEvent): BaseEvent {
        closure(payload)
        ran = true
        return payload
    }

    override fun identify(payload: IdentifyEvent): BaseEvent {
        closure(payload)
        ran = true
        return payload
    }

    override fun screen(payload: ScreenEvent): BaseEvent {
        closure(payload)
        ran = true
        return payload
    }

    override fun group(payload: GroupEvent): BaseEvent {
        closure(payload)
        ran = true
        return payload
    }

    override fun alias(payload: AliasEvent): BaseEvent {
        closure(payload)
        ran = true
        return payload
    }
}

class StubPlugin(override val name: String = "StubPlugin") : EventPlugin {
    override val type: Plugin.Type = Plugin.Type.Before
    override lateinit var analytics: Analytics
}