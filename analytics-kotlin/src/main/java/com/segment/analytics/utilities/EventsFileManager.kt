package com.segment.analytics.utilities

import android.content.SharedPreferences
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.time.Instant

/**
 * Responsible for storing events in a batch payload style
 *
 * Contents format
 * {
 *      "batch": [
 *      ...
 *      ],
 *      "sentAt": "2021-04-30T22:06:11"
 * }
 * Each file stored is a batch of events. When uploading events the contents of the file can be
 * sent as-is to the Segment batch upload endpoint.
 *
 * Some terms:
 * - Current open file: the most recent temporary batch file that is being used to store events
 * - Closing the file: ending the batch payload, and renaming the temporary file to a permanent one
 * - Stored file paths: list of file paths that are not temporary and match the write-key of the manager
 *
 * How it works:
 * storeEvent() will store the event in the current open file, ensuring that batch size
 * does not go over the 475KB limit. It will close the current file and create new temp ones
 * when appropriate
 *
 * When read() is called the current file is closed, and a list of stored file paths is returned
 *
 * remove() will delete the file path specified
 */
class EventsFileManager(
    private val directory: File,
    private val writeKey: String,
    private val sharedPreferences: SharedPreferences
) {

    init {
        createDirectory(directory)
    }

    private val fileIndexKey = "segment.events.file.index.$writeKey"

    companion object {
        const val MAX_FILE_SIZE = 475_000 // 475KB
    }

    /**
     * closes existing file, if at capacity
     * opens a new file, if current file is full or uncreated
     * stores the event
     */
    fun storeEvent(event: String) {
        var newFile = false
        var curFile = currentFile()
        if (!curFile.exists()) {
            // create it
            curFile.createNewFile()
            start(curFile)
            newFile = true
        }

        // check if file is at capacity
        if (curFile.length() > MAX_FILE_SIZE) {
            finish(curFile)
            // update index
            curFile = currentFile()
            curFile.createNewFile()
            start(curFile)
            newFile = true
        }

        var contents = ""
        if (!newFile) {
            contents += ","
        }
        contents += event
        writeToFile(contents.toByteArray(), curFile)
    }

    private fun incrementFileIndex(): Boolean {
        val index = sharedPreferences.getInt(fileIndexKey, 0)
        return sharedPreferences.edit().putInt(fileIndexKey, index + 1).commit()
    }

    /**
     * closes the current file, and returns a comma-separated list of file paths that are not yet uploaded
     */
    fun read(): List<String> {
        finish(currentFile())
        val fileList = directory.listFiles { _, name ->
            name.contains(writeKey) && !name.endsWith(".tmp")
        } ?: emptyArray()
        return fileList.map {
            it.absolutePath
        }
    }

    /**
     * deletes the file at filePath
     */
    fun remove(filePath: String): Boolean {
        return File(filePath).delete()
    }

    private fun start(file: File) {
        // start batch object and events array
        val contents = """{"batch":["""
        writeToFile(contents.toByteArray(), file)
    }

    private fun finish(file: File) {
        if (!file.exists()) {
            // if tmp file doesnt exist then we dont need to do anything
            return
        }
        // close events array and batch object
        val contents = """],"sentAt":"${Instant.now()}"}"""
        writeToFile(contents.toByteArray(), file)
        file.renameTo(File(directory, file.nameWithoutExtension))
        incrementFileIndex()
    }

    // return the current tmp file
    private fun currentFile(): File {
        val index = sharedPreferences.getInt(fileIndexKey, 0)
        return File(directory, "$writeKey-$index.tmp")
    }

    // Atomic write to underlying file
    // TODO make atomic
    private fun writeToFile(content: ByteArray, file: File) {
        val atomicFile = AtomicFile(file)
        val os = atomicFile.startWrite(true)
        try {
            os.write(content)
            os.flush()
            atomicFile.finishWrite(os)
        } catch (ex: IOException) {
            atomicFile.failWrite(os)
        }
    }
}