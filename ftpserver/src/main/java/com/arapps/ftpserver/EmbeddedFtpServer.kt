package com.arapps.ftpserver.internal

import android.content.Context
import android.os.Environment
import android.util.Log
import org.apache.ftpserver.FtpServer
import org.apache.ftpserver.FtpServerFactory
import org.apache.ftpserver.ftplet.*
import org.apache.ftpserver.listener.ListenerFactory
import org.apache.ftpserver.usermanager.impl.BaseUser
import org.apache.ftpserver.usermanager.impl.WritePermission
import java.net.InetAddress

class EmbeddedFtpServer(private val context: Context) {

    private var ftpServer: FtpServer? = null
    private val port = 2121
    private val logTag = "FTPServer"

    fun startServer(onSuccess: () -> Unit, onError: (Throwable) -> Unit) {
        try {
            if (ftpServer?.isStopped == false) {
                Log.i(logTag, "FTP server already running.")
                onSuccess()
                return
            }

            val storageRoot = Environment.getExternalStorageDirectory()
            if (!storageRoot.exists() || !storageRoot.canRead()) {
                throw IllegalStateException("Storage directory is not accessible.")
            }

            val serverFactory = FtpServerFactory()

            val listenerFactory = ListenerFactory().apply {
                serverAddress = getLocalIpAddress()
                port = this@EmbeddedFtpServer.port

                // ✅ Configure passive ports
                val dataConnectionConfig = org.apache.ftpserver.DataConnectionConfigurationFactory().apply {
                    passivePorts = "50000-50010"
                }
                this.dataConnectionConfiguration = dataConnectionConfig.createDataConnectionConfiguration()
            }


            serverFactory.addListener("default", listenerFactory.createListener())

            val user = BaseUser().apply {
                name = "anonymous"
                homeDirectory = storageRoot.absolutePath
                authorities = listOf(WritePermission())
            }

            serverFactory.userManager.save(user)

            // Secure Ftplet implementation
            val secureFtplet = object : Ftplet {
                override fun init(ftpletContext: FtpletContext?) {
                    Log.i(logTag, "Ftplet initialized")
                }

                override fun destroy() {
                    Log.i(logTag, "Ftplet destroyed")
                }

                override fun beforeCommand(session: FtpSession?, request: FtpRequest?): FtpletResult {
                    val arg = request?.argument ?: return FtpletResult.DEFAULT
                    if (arg.contains("/Android", ignoreCase = true) || arg.contains("/data", ignoreCase = true)) {
                        Log.w("FTPServer", "Blocked access to restricted folder: $arg")
                        return FtpletResult.SKIP
                    }
                    return FtpletResult.DEFAULT
                }



                override fun onConnect(session: FtpSession?): FtpletResult = FtpletResult.DEFAULT
                override fun onDisconnect(session: FtpSession?): FtpletResult = FtpletResult.DEFAULT
                override fun afterCommand(session: FtpSession?, request: FtpRequest?, reply: FtpReply?): FtpletResult = FtpletResult.DEFAULT
            }

            serverFactory.ftplets = mapOf("SafetyFilter" to secureFtplet)

            ftpServer = serverFactory.createServer().apply { start() }

            Log.i(logTag, "FTP Server started on port $port at ${listenerFactory.serverAddress}")
            onSuccess()
        } catch (e: Exception) {
            Log.e(logTag, "FTP Server start failed: ${e.message}", e)
            onError(e)
        }
    }

    fun stopServer() {
        try {
            ftpServer?.let {
                if (!it.isStopped) {
                    it.stop()
                    Log.i(logTag, "FTP Server stopped.")
                }
            }
        } catch (e: Exception) {
            Log.e(logTag, "Error stopping FTP server: ${e.message}", e)
        }
    }

    private fun getLocalIpAddress(): String {
        return try {
            InetAddress.getLocalHost().hostAddress ?: "0.0.0.0"
        } catch (e: Exception) {
            "0.0.0.0" // fallback
        }
    }
}
