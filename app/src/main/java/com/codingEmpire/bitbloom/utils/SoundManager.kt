package com.codingEmpire.bitbloom.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import com.codingEmpire.bitbloom.R

object SoundManager {
    private lateinit var pool: SoundPool
    private var successId = 0
    private var failureId = 0
    private var withdrawId = 0
    private var loaded = false

    fun init(context: Context) {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        pool = SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(attrs)
            .build()
            .apply {
                setOnLoadCompleteListener { _, _, _ -> loaded = true }
            }

        successId = pool.load(context, R.raw.success, 1)
        failureId = pool.load(context, R.raw.failure, 1)
        withdrawId = pool.load(context, R.raw.withdraw, 1)
    }

    fun playSuccess(context: Context) {
        if (loaded) {
            pool.play(successId, 1f, 1f, 1, 0, 1f)
        } else {
            MediaPlayer.create(context, R.raw.success).apply {
                setOnCompletionListener { mp -> mp.release() }
                start()
            }
        }
    }

    fun playFailure(context: Context) {
        if (loaded) {
            pool.play(failureId, 1f, 1f, 1, 0, 1f)
        } else {
            MediaPlayer.create(context, R.raw.failure).apply {
                setOnCompletionListener { mp -> mp.release() }
                start()
            }
        }
    }

    fun playWithdrawSuccess(context: Context) {
        if (loaded) {
            pool.play(withdrawId, 1f, 1f, 1, 0, 1f)
        } else {
            MediaPlayer.create(context, R.raw.withdraw).apply {
                setOnCompletionListener { mp -> mp.release() }
                start()
            }
        }
    }
}