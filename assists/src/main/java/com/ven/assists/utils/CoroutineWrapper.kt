package com.ven.assists.utils

import com.blankj.utilcode.util.LogUtils
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object CoroutineWrapper {
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        // 全局兜底：bridge/后台协程的异常不再直接崩溃进程，记录后由 JS 侧等待超时或回调错误
        LogUtils.e("[CoroutineWrapper] uncaught coroutine exception: ${throwable.message}", throwable)
    }
    private var job = Job()
    private var coroutine: CoroutineScope = CoroutineScope(job + Dispatchers.IO + exceptionHandler)
    fun launch(isMain: Boolean = false, block: suspend CoroutineScope.() -> Unit): Job {
        return coroutine.launch(block = block, context = if (isMain) Dispatchers.Main else Dispatchers.IO)
    }
}

suspend fun <T> runMain(block: suspend CoroutineScope.() -> T): T {
    return withContext(Dispatchers.Main, block = block)
}

suspend fun <T> runIO(block: suspend CoroutineScope.() -> T): T {
    return withContext(Dispatchers.IO, block = block)
}