package com.yijing.chess

import org.junit.runner.Description
import org.junit.runner.JUnitCore
import org.junit.runner.notification.Failure
import org.junit.runner.notification.RunListener
import java.io.File
import javax.xml.stream.XMLOutputFactory
import kotlin.system.exitProcess

/** Keeps the repository's working JUnitCore path on Windows/JDK 17, with automatic discovery and reports. */
object RegressionRunner {
    private data class Case(val name: String, var started: Long = System.nanoTime(), var seconds: Double = 0.0,
        var failure: Failure? = null, var skipped: Boolean = false)

    @JvmStatic fun main(args: Array<String>) {
        require(args.size >= 2)
        val directory = File(args[0]).apply { mkdirs() }
        var total = 0; var failed = 0; var ignored = 0
        for (className in args.drop(1)) {
            val cases = linkedMapOf<String, Case>()
            val junit = JUnitCore()
            junit.addListener(object : RunListener() {
                override fun testStarted(description: Description) { cases[description.displayName] = Case(description.methodName ?: description.displayName) }
                override fun testFinished(description: Description) { cases[description.displayName]?.let { it.seconds = (System.nanoTime() - it.started) / 1e9 } }
                override fun testFailure(failure: Failure) { cases.getOrPut(failure.description.displayName) { Case(failure.description.methodName ?: "initializationError") }.failure = failure }
                override fun testIgnored(description: Description) { cases[description.displayName] = Case(description.methodName ?: description.displayName, skipped = true) }
                override fun testAssumptionFailure(failure: Failure) { cases.getOrPut(failure.description.displayName) { Case(failure.description.methodName ?: failure.description.displayName) }.skipped = true }
            })
            val result = junit.run(Class.forName(className))
            total += result.runCount; failed += result.failureCount; ignored += result.ignoreCount
            File(directory, "TEST-$className.xml").outputStream().buffered().use { output ->
                val xml = XMLOutputFactory.newFactory().createXMLStreamWriter(output, "UTF-8")
                xml.writeStartDocument("UTF-8", "1.0"); xml.writeStartElement("testsuite")
                for ((name, value) in mapOf("name" to className, "tests" to cases.size.toString(), "failures" to result.failureCount.toString(),
                    "errors" to "0", "skipped" to cases.values.count { it.skipped }.toString(), "time" to (result.runTime / 1000.0).toString())) xml.writeAttribute(name, value)
                for (case in cases.values) {
                    xml.writeStartElement("testcase"); xml.writeAttribute("classname", className); xml.writeAttribute("name", case.name); xml.writeAttribute("time", case.seconds.toString())
                    if (case.skipped) xml.writeEmptyElement("skipped")
                    case.failure?.let { failure -> xml.writeStartElement("failure"); xml.writeAttribute("message", failure.message ?: "Test failed"); xml.writeCharacters(failure.trace); xml.writeEndElement() }
                    xml.writeEndElement()
                }
                xml.writeEndElement(); xml.writeEndDocument(); xml.close()
            }
            println("$className: ${result.runCount} tests, ${result.failureCount} failures")
            result.failures.forEach { System.err.println(it.trace) }
        }
        println("REGRESSION RESULT: $total tests, $failed failures, $ignored ignored")
        if (failed != 0 || total == 0) exitProcess(1)
        NotationInteropFixtures.write(File(directory, "notation"))
    }
}
