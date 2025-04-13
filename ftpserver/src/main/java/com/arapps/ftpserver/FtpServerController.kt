package com.arapps.ftpserver

import android.content.Context
import com.arapps.ftpserver.internal.EmbeddedFtpServer

object FtpServerController {

    private var state: FtpServerState = FtpServerState.STOPPED
    private var server: EmbeddedFtpServer? = null

    fun start(context: Context) {
        if (state == FtpServerState.RUNNING) return
        state = FtpServerState.STARTING

        server = EmbeddedFtpServer(context).apply {
            startServer(
                onSuccess = { state = FtpServerState.RUNNING },
                onError = { state = FtpServerState.ERROR }
            )
        }
    }

    fun stop() {
        server?.stopServer()
        state = FtpServerState.STOPPED
    }

    fun isRunning(): Boolean = state == FtpServerState.RUNNING
}
