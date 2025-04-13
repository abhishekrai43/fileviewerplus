package com.arapps.fileviewplus

import android.content.Context
import com.arapps.ftpserver.FtpServerController

object ServerToggleController {

    fun startServer(context: Context) {
        FtpServerController.start(context)
    }

    fun stopServer() {
        FtpServerController.stop()
    }

    fun isRunning(): Boolean {
        return FtpServerController.isRunning()
    }
}
