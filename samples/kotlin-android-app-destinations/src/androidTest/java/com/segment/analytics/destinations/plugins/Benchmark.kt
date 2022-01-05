package com.segment.analytics.destinations.plugins

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eclipsesource.v8.V8
import com.eclipsesource.v8.V8Array
import com.eclipsesource.v8.V8Function
import com.eclipsesource.v8.V8Object
import com.eclipsesource.v8.utils.V8ObjectUtils
import com.segment.analytics.kotlin.core.BaseEvent
import com.segment.analytics.kotlin.core.TrackEvent
import com.segment.analytics.kotlin.core.emptyJsonObject
import com.segment.analytics.kotlin.core.utilities.toContent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.BufferedReader
import java.util.Date

@RunWith(AndroidJUnit4::class)
class Benchmark {
    val e1 = TrackEvent(
        event = "App Opened",
        properties = buildJsonObject { put("new", true); put("click", true) }
    ).apply {
        messageId = "qwerty-1234"
        anonymousId = "anonId"
        integrations = emptyJsonObject
        context = emptyJsonObject
        timestamp = Date(0).toInstant().toString()
    }
    val e2 = TrackEvent(
        event = "Screen Opened",
        properties = buildJsonObject { put("new", false); put("click", false) }
    ).apply {
        messageId = "qwerty-1234"
        anonymousId = "anonId"
        integrations = emptyJsonObject
        context = emptyJsonObject
        timestamp = Date(0).toInstant().toString()
    }

    val e3 = TrackEvent(
        event = "Screen Closed",
        properties = buildJsonObject { put("new", false); put("click", false) }
    ).apply {
        messageId = "qwerty-1234"
        anonymousId = "anonId"
        integrations = emptyJsonObject
        context = emptyJsonObject
        timestamp = Date(0).toInstant().toString()
    }

    val e4 = TrackEvent(
        event = "App Closed",
        properties = buildJsonObject { put("new", false); put("click", true) }
    ).apply {
        messageId = "qwerty-1234"
        anonymousId = "anonId"
        integrations = emptyJsonObject
        context = emptyJsonObject
        timestamp = Date(0).toInstant().toString()
    }

    val ir1 =
        "[\"!\",[\"or\",[\"=\",\"event\",{\"value\":\"App Opened\"}],[\"=\",\"event\",{\"value\":\"App Closed\"}]]]"
    val ir2 =
        "[\"or\",[\"=\",\"event\",{\"value\":\"App Opened\"}],[\"=\",\"event\",{\"value\":\"App Closed\"}]]"
    val ir3 =
        "[\"or\",[\"=\",\"event\",{\"value\":\"Screen Opened\"}],[\"or\",[\"=\",\"event\",{\"value\":\"Search - Select Location\"}],[\"or\",[\"=\",\"event\",{\"value\":\"Search - View\"}],[\"or\",[\"=\",\"event\",{\"value\":\"Screen Closed\"}],[\"or\",[\"=\",\"event\",{\"value\":\"Search - Select Keyword\"}],[\"or\",[\"=\",\"event\",{\"value\":\"Search - View Map\"}],[\"=\",\"event\",{\"value\":\"App Opened\"}]]]]]]]"

    private val appContext = InstrumentationRegistry.getInstrumentation().context

    @Test
    fun usingExpressions() {
        println("[TimeTest] Using Predicates")
        // Setup
        val q1 = Json.decodeFromString(JsonArray.serializer(), ir1)
        val q2 = Json.decodeFromString(JsonArray.serializer(), ir2)
        val q3 = Json.decodeFromString(JsonArray.serializer(), ir3)
        val p1 = compile(q1)
        val p2 = compile(q2)
        val p3 = compile(q3)

        println("========================= 2")
        simpleMeasureTest(
            ITERATIONS = 1000,
            TEST_COUNT = 50,
            WARM_COUNT = 2
        ) {
            p1(e1)
            p1(e2)
            p1(e3)
            p1(e4)
        }
        println("========================= 2")
        simpleMeasureTest(
            ITERATIONS = 1000,
            TEST_COUNT = 50,
            WARM_COUNT = 2
        ) {
            p2(e1)
            p2(e2)
            p2(e3)
            p2(e4)
        }
        println("========================= 3")
        simpleMeasureTest(
            ITERATIONS = 1000,
            TEST_COUNT = 50,
            WARM_COUNT = 2
        ) {
            p3(e1)
            p3(e2)
            p3(e3)
            p3(e4)
        }

        val test = {
            assertFalse(p1(e1))
            assertTrue(p1(e2))
            assertTrue(p1(e3))
            assertFalse(p1(e4))

            assertTrue(p2(e1))
            assertFalse(p2(e2))
            assertFalse(p2(e3))
            assertTrue(p2(e4))

            assertTrue(p3(e1))
            assertTrue(p3(e2))
            assertTrue(p3(e3))
            assertFalse(p3(e4))
        }
    }

    @Test
    fun usingFQlQuery() {
        println("[TimeTest] Using Runtime Evaluator")
        // Setup
        val q1 = Json.decodeFromString(JsonArray.serializer(), ir1)
        val q2 = Json.decodeFromString(JsonArray.serializer(), ir2)
        val q3 = Json.decodeFromString(JsonArray.serializer(), ir3)

        println("========================= 2")
        simpleMeasureTest(
            ITERATIONS = 1000,
            TEST_COUNT = 50,
            WARM_COUNT = 2
        ) {
            fqlEvaluate(q1, e1)
            fqlEvaluate(q1, e2)
            fqlEvaluate(q1, e3)
            fqlEvaluate(q1, e4)
        }
        println("========================= 2")
        simpleMeasureTest(
            ITERATIONS = 1000,
            TEST_COUNT = 50,
            WARM_COUNT = 2
        ) {
            fqlEvaluate(q2, e1)
            fqlEvaluate(q2, e2)
            fqlEvaluate(q2, e3)
            fqlEvaluate(q2, e4)
        }
        println("========================= 3")
        simpleMeasureTest(
            ITERATIONS = 1000,
            TEST_COUNT = 50,
            WARM_COUNT = 2
        ) {
            fqlEvaluate(q3, e1)
            fqlEvaluate(q3, e2)
            fqlEvaluate(q3, e3)
            fqlEvaluate(q3, e4)
        }

        val test = {
            assertFalse(fqlEvaluate(q1, e1))
            assertTrue(fqlEvaluate(q1, e2))
            assertTrue(fqlEvaluate(q1, e3))
            assertFalse(fqlEvaluate(q1, e4))

            assertTrue(fqlEvaluate(q2, e1))
            assertFalse(fqlEvaluate(q2, e2))
            assertFalse(fqlEvaluate(q2, e3))
            assertTrue(fqlEvaluate(q2, e4))

            assertTrue(fqlEvaluate(q3, e1))
            assertTrue(fqlEvaluate(q3, e2))
            assertTrue(fqlEvaluate(q3, e3))
            assertFalse(fqlEvaluate(q3, e4))
        }
    }

    @Test
    fun benchmark() {
        // Setup
        val q1 = Json.decodeFromString(JsonArray.serializer(), ir1)
        val q2 = Json.decodeFromString(JsonArray.serializer(), ir2)
        val q3 = Json.decodeFromString(JsonArray.serializer(), ir3)
        val p1 = compile(q1)
        val p2 = compile(q2)
        val p3 = compile(q3)

        println("========================= 1")
        compare(
            ITERATIONS = 10000,
            TEST_COUNT = 5,
            WARM_COUNT = 2,
            callback1 = {
                p1(e1)
                p1(e2)
                p1(e3)
                p1(e4)
            },
            callback2 = {
                fqlEvaluate(q1, e1)
                fqlEvaluate(q1, e2)
                fqlEvaluate(q1, e3)
                fqlEvaluate(q1, e4)
            }
        )
        println("========================= 2")
        compare(
            ITERATIONS = 10000,
            TEST_COUNT = 5,
            WARM_COUNT = 2,
            callback1 = {
                p2(e1)
                p2(e2)
                p2(e3)
                p2(e4)
            },
            callback2 = {
                fqlEvaluate(q2, e1)
                fqlEvaluate(q2, e2)
                fqlEvaluate(q2, e3)
                fqlEvaluate(q2, e4)
            }
        )
        println("========================= 3")
        compare(
            ITERATIONS = 10000,
            TEST_COUNT = 5,
            WARM_COUNT = 2,
            callback1 = {
                p3(e1)
                p3(e2)
                p3(e3)
                p3(e4)
            },
            callback2 = {
                fqlEvaluate(q3, e1)
                fqlEvaluate(q3, e2)
                fqlEvaluate(q3, e3)
                fqlEvaluate(q3, e4)
            }
        )
    }

    @Test
    fun usingJsRuntime() {
        println("[TimeTest] Using JSRuntime")
        val localJSMiddlewareInputStream = appContext.assets.open("sample.js")
        val script = localJSMiddlewareInputStream.bufferedReader().use(BufferedReader::readText)
        val runtime = JSRuntime(script)
        val m1 = Json.decodeFromString(JsonObject.serializer(), """{
        "ir": "[\"!\",[\"or\",[\"=\",\"event\",{\"value\":\"App Opened\"}],[\"=\",\"event\",{\"value\":\"App Closed\"}]]]",
        "type": "fql",
        "config": {
          "expr": "!(event = \"CP VOD - Start video\" or event = \"CP VOD - Track video\")"
        }
      }""").toContent()

        val m2 = Json.decodeFromString(JsonObject.serializer(), """{
        "ir": "[\"or\",[\"=\",\"event\",{\"value\":\"App Opened\"}],[\"=\",\"event\",{\"value\":\"App Closed\"}]]",
        "type": "fql",
        "config": {
          "expr": "!(event = \"CP VOD - Start video\" or event = \"CP VOD - Track video\")"
        }
      }""").toContent()
        val m3 = Json.decodeFromString(JsonObject.serializer(), """{
        "ir": "[\"or\",[\"=\",\"event\",{\"value\":\"Screen Opened\"}],[\"or\",[\"=\",\"event\",{\"value\":\"Search - Select Location\"}],[\"or\",[\"=\",\"event\",{\"value\":\"Search - View\"}],[\"or\",[\"=\",\"event\",{\"value\":\"Screen Closed\"}],[\"or\",[\"=\",\"event\",{\"value\":\"Search - Select Keyword\"}],[\"or\",[\"=\",\"event\",{\"value\":\"Search - View Map\"}],[\"=\",\"event\",{\"value\":\"App Opened\"}]]]]]]]",
        "type": "fql",
        "config": {
          "expr": "!(event = \"CP VOD - Start video\" or event = \"CP VOD - Track video\")"
        }
      }""").toContent()

        println("========================= 2")
        simpleMeasureTest(
            ITERATIONS = 1000,
            TEST_COUNT = 50,
            WARM_COUNT = 2
        ) {
            runtime.match(m1, e1)
            runtime.match(m1, e2)
            runtime.match(m1, e3)
            runtime.match(m1, e4)
        }
        println("========================= 2")
        simpleMeasureTest(
            ITERATIONS = 1000,
            TEST_COUNT = 50,
            WARM_COUNT = 2
        ) {
            runtime.match(m2,e1)
            runtime.match(m2,e2)
            runtime.match(m2,e3)
            runtime.match(m2,e4)
        }
        println("========================= 3")
        simpleMeasureTest(
            ITERATIONS = 1000,
            TEST_COUNT = 50,
            WARM_COUNT = 2
        ) {
            runtime.match(m3,e1)
            runtime.match(m3,e2)
            runtime.match(m3,e3)
            runtime.match(m3,e4)
        }


        val test = {
            assertFalse(runtime.match(m1, e1))
            assertTrue(runtime.match(m1, e2))
            assertTrue(runtime.match(m1, e3))
            assertFalse(runtime.match(m1, e4))

            assertTrue(runtime.match(m2, e1))
            assertFalse(runtime.match(m2, e2))
            assertFalse(runtime.match(m2, e3))
            assertTrue(runtime.match(m2, e4))

            assertTrue(runtime.match(m3, e1))
            assertTrue(runtime.match(m3, e2))
            assertTrue(runtime.match(m3, e3))
            assertFalse(runtime.match(m3, e4))
        }

//        runtime.close()
    }
}

class JSRuntime(private val script: String) {

    internal class Console {
        fun log(message: String) {
            println("[INFO] $message")
        }

        fun error(message: String) {
            println("[ERROR] $message")
        }
    }

    private val runtime: V8 = V8.createV8Runtime().also {
//        val console = Console()
//        val v8Console = V8Object(it)
//         // todo Allow string array
//        v8Console.registerJavaMethod(console, "log", "log", arrayOf<Class<*>>(String::class.java))
//        v8Console.registerJavaMethod(console, "error", "err", arrayOf<Class<*>>(String::class.java))
//        it.add("console", v8Console)
        it.executeScript(script)
    }

    fun getObject(key: String): V8Object {
        var result = runtime.getObject(key)
        if (result.isUndefined) {
            result = runtime.executeObjectScript(key) // Blows up when the key does not exist
        }
        return result
    }

    fun match(matcherJson: Map<String, Any?>, payload: BaseEvent): Boolean {
        payload as TrackEvent

        val fn = getObject("edge_function.fnMatch") as V8Function

        val params = V8Array(runtime.runtime)

        val payloadJson = (Json.encodeToJsonElement(payload) as JsonObject).toContent()
        params.push(V8ObjectUtils.toV8Object(runtime.runtime, payloadJson))
        params.push(V8ObjectUtils.toV8Object(runtime.runtime, matcherJson))

        // call it and pick up the result
        val fnResult = fn.call(null, params) as String
//        println(fnResult)
        return fnResult == payload.event
    }

    fun close() {
        runtime.close()
    }
}