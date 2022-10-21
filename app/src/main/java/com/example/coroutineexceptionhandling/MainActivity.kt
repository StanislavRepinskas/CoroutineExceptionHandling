package com.example.coroutineexceptionhandling

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import kotlinx.coroutines.*

// https://www.netguru.com/blog/exceptions-in-kotlin-coroutines

class MainActivity : AppCompatActivity() {
    private val exceptionHandler = CoroutineExceptionHandler { coroutineContext, throwable ->
        log("exceptionHandler")
    }

    private val scope = CoroutineScope(Dispatchers.Main)
    private val scopeExceptionHandler = CoroutineScope(Dispatchers.Main + exceptionHandler)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<Button>(R.id.test1).setOnClickListener {
            test1()
        }
        findViewById<Button>(R.id.test2).setOnClickListener {
            test2()
        }
        findViewById<Button>(R.id.test3).setOnClickListener {
            test3()
        }
        findViewById<Button>(R.id.test4).setOnClickListener {
            test4()
        }
        findViewById<Button>(R.id.test5).setOnClickListener {
            test5()
        }
        findViewById<Button>(R.id.test6).setOnClickListener {
            test6()
        }
    }

    // Просто асинк операции и трай катч
    private fun test1() {
        scope.launch {
            log("test launch")

            try {
                val action1 = async {
                    runAction1()
                }
                val action2 = async {
                    runAction2()
                }

                val result = action1.await() + action2.await()
                log("test result = $result")
            } catch (e: Exception) {
                log("test catch exception")
            }
        }
    }

    // Асинк операции которые выполняются в своем контексте
    private fun test2() {
        scope.launch {
            log("test launch")
            try {
                val action1 = async {
                    runAction1_1() // = withContext(Dispatcher.IO)
                }
                val action2 = async {
                    runAction2_1() // = withContext(Dispatcher.IO)
                }

                val result = action1.await() + action2.await()
                log("test result = $result")
            } catch (e: Exception) {
                log("test catch exception")
            }
        }
    }

    // Можно поймать ошибку через глобальный эксепшн хендлер
    private fun test3() {
        // Можно создать скоуп Dispatchers.Main + exceptionHandler
        //scopeExceptionHandler.launch {
        // А можно передать обработчик в лаунч
        scope.launch(exceptionHandler) {
            log("test launch")
            try {
                val action1 = async {
                    runAction1()
                }
                val action2 = async {
                    runAction2()
                }

                val result = action1.await() + action2.await()
                log("test result = $result")
            } catch (e: Exception) {
                log("test catch exception")
            }
        }
    }

    // Правильный способ! оборачивать внешним скопом. При оборачивании в скоуп ошибка которая
    // придет в этот скоуп будет брошена выше и ее можно будет обработать в трай катч
    private fun test4() {
        scope.launch(exceptionHandler) {
            log("test launch")

            try {
                // Лучше всего делать 1 скоуп для всей операции. Хорошая документация у метода!
                // раскоментируй посмотри.
                coroutineScope {
                    // Либо так.
                    //withContext(Dispatchers.IO) {
                    val action1 = async {
                        runAction1()
                    }
                    val action2 = async {
                        runAction2()
                    }

                    val result = action1.await() + action2.await()
                    log("test result = $result")
                }
                //}
            } catch (e: Exception) {
                log("test catch exception")
                throw e
            }
        }
    }

    // Можно и так конечно, но тогда теряем фишку с отменой выполнения других корутин!
    private fun test5() {
        scope.launch {
            log("test launch")
            // Внешний катч уже не нужен
            try {
                val action1 = async {
                    try {
                        runAction1_1()
                    } catch (e: Exception) {
                        0
                    }
                }
                val action2 = async {
                    try {
                        runAction2_1()
                    } catch (e: Exception) {
                        0
                    }
                }

                val result = action1.await() + action2.await()
                log("test result = $result")
            } catch (e: Exception) {
                log("test catch exception")
            }
        }
    }

    // Вишенка! Такая корутина не завершится никогда :)
    private fun test6() {
        // Если выполнить в Dispatchers.Main - заблокирует главный поток и будет ANR.
        // Получается Dispatchers.Main работает только на 1 потоке?
        scope.launch(Dispatchers.Default) {
            log("test launch")
            var time = System.currentTimeMillis()
            while (true) {
                val currentTime = System.currentTimeMillis()
                if (currentTime - time >= 1000L) {
                    log("tic")
                    time = currentTime

                    // Что бы прервать выполнение задачи можно так
                    /*if(!isActive) {
                        break
                    }*/
                    // Или так. Любая стандартная функция или билдер проверят isActive под капотом.
                    // delay(1L)
                }
            }
            log("test finish")
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        log("scope.cancel()")
        scope.cancel()
    }

    suspend fun runAction1(): Int {
        Log.d(LOG_TAG, "runAction1 start")
        try {
            delay(5000L)
        } catch (e: Exception) {
            val t = if (e is CancellationException) "CancellationException" else "Exception"
            Log.d(LOG_TAG, "runAction1 catch $t")
        }
        Log.d(LOG_TAG, "runAction1 end")
        return 1
    }

    suspend fun runAction2(err: Boolean = true): Int {
        Log.d(LOG_TAG, "runAction2 start")
        delay(2000L)
        if (err) {
            Log.d(LOG_TAG, "runAction2 throw error")
            throw IllegalArgumentException()
        } else
            return 1
    }

    suspend fun runAction1_1(): Int = withContext(Dispatchers.IO) {
        Log.d(LOG_TAG, "runAction1 start")
        try {
            delay(5000L)
        } catch (e: Exception) {
            val t = if (e is CancellationException) "CancellationException" else "Exception"
            Log.d(LOG_TAG, "runAction1 catch $t")
        }
        Log.d(LOG_TAG, "runAction1 end")
        return@withContext 1
    }

    suspend fun runAction2_1(err: Boolean = true): Int = withContext(Dispatchers.IO) {
        Log.d(LOG_TAG, "runAction2 start")
        delay(2000L)
        if (err) {
            Log.d(LOG_TAG, "runAction2 throw error")
            throw IllegalArgumentException()
        } else
            return@withContext 1
    }

    private fun log(message: String) {
        Log.d(LOG_TAG, message)
    }

    companion object {
        const val LOG_TAG = "COROUTINE_EXCEPTION_T"
    }
}