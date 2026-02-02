package com.tom.rv2ide.terminal

import android.content.Context
import com.termux.app.TermuxService
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession
import com.tom.rv2ide.utils.Environment
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import android.util.Log

class TerminalBridge(private val context: Context) {

    private var termuxService: TermuxService? = null
    private val agentSessionName = "AI_Agent_Session"
    private val outputFileName = "agent_cmd_output.txt"
    private val TAG = "TerminalBridge"

    fun setService(service: TermuxService) {
        this.termuxService = service
    }

    suspend fun executeCommand(command: String): String = withContext(Dispatchers.IO) {
        val service = termuxService
        if (service == null) {
            Log.e(TAG, "Service is null")
            return@withContext "Error: Terminal Service not connected"
        }

        // Find or create session
        var session = service.termuxSessions.find { it.terminalSession.title == agentSessionName }
        if (session == null) {
             val workingDir = Environment.HOME.absolutePath
             val shell = "/system/bin/sh"
             try {
                 // createTermuxSession arguments based on TermuxService.java:
                 // executablePath, arguments, stdin, workingDirectory, isFailSafe, sessionName
                 session = service.createTermuxSession(shell, emptyArray(), null, workingDir, false, agentSessionName)
             } catch (e: Exception) {
                 Log.e(TAG, "Failed to create session", e)
             }
        }

        if (session == null) return@withContext "Error: Could not create terminal session"

        val outputFile = File(context.filesDir, outputFileName)
        if (outputFile.exists()) outputFile.delete()
        outputFile.createNewFile()

        val marker = "AGENT_CMD_FINISHED_${System.currentTimeMillis()}"

        // Wrap command to capture output and append marker
        // We use parentheses to execute in a subshell/group so that we capture all output
        // We also ensure strict sequential execution with ;
        // We append the marker to the file at the end
        val wrappedCommand = "($command) > ${outputFile.absolutePath} 2>&1; echo \"$marker\" >> ${outputFile.absolutePath}\n"

        // Write to terminal
        Log.d(TAG, "Executing: $command")
        session.terminalSession.write(wrappedCommand.toByteArray(), 0, wrappedCommand.length)

        // Poll for completion
        var attempts = 0
        val maxAttempts = 1200 // 2 minutes roughly (1200 * 100ms)

        while (attempts < maxAttempts) {
            delay(100)
            if (outputFile.exists()) {
                val content = try { outputFile.readText() } catch (e: Exception) { "" }
                if (content.contains(marker)) {
                    Log.d(TAG, "Command finished")
                    return@withContext content.replace(marker, "").trim()
                }
            }
            attempts++
        }

        return@withContext "Error: Command timed out or failed to produce output."
    }
}
