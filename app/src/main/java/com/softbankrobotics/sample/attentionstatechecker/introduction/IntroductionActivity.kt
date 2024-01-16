/*
 * Copyright (C) 2018 SoftBank Robotics Europe
 * See COPYING for the license
 */
package com.softbankrobotics.sample.attentionstatechecker.introduction

import android.content.Intent
import android.os.Bundle
import android.view.View
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.QiSDK
import com.aldebaran.qi.sdk.RobotLifecycleCallbacks
import com.aldebaran.qi.sdk.`object`.conversation.*
import com.aldebaran.qi.sdk.builder.ChatBuilder
import com.aldebaran.qi.sdk.builder.QiChatbotBuilder
import com.aldebaran.qi.sdk.builder.TopicBuilder
import com.aldebaran.qi.sdk.design.activity.RobotActivity
import com.softbankrobotics.R
import com.softbankrobotics.databinding.ActivityIntroductionBinding
import com.softbankrobotics.sample.attentionstatechecker.game.GameActivity
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class IntroductionActivity : RobotActivity(), RobotLifecycleCallbacks {

    private lateinit var binding: ActivityIntroductionBinding

    private var chat: Chat? = null
    private var qiChatbot: QiChatbot? = null
    private var startBookmark: Bookmark? = null
    private var secondBookmark: Bookmark? = null

    private var timerDisposable: Disposable? = null
    private val shouldRepeatWithTimer = AtomicBoolean(true)

    private var startAtHome = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIntroductionBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        binding.startButton.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                goToBookmark(startBookmark)
            }
        }

        binding.closeButton.setOnClickListener { finishAffinity() }

        QiSDK.register(this, this)
    }

    override fun onResume() {
        super.onResume()
        hideStartButton()
    }

    override fun onDestroy() {
        QiSDK.unregister(this, this)
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        startAtHome = intent?.extras?.getBoolean("startAtHome") ?: false
    }

    override fun onRobotFocusGained(qiContext: QiContext?) {
        shouldRepeatWithTimer.set(true)

        val topic = TopicBuilder.with(qiContext)
                .withResource(R.raw.introduction)
                .build()

        val bookmarks = topic.bookmarks
        val firstBookmark = bookmarks[FIRST_BOOKMARK]
        secondBookmark = bookmarks[SECOND_BOOKMARK]
        startBookmark = bookmarks[START_BOOKMARK]

        qiChatbot = QiChatbotBuilder.with(qiContext)
                .withTopic(topic)
                .build()

        qiChatbot?.addOnBookmarkReachedListener {
            when (it.name) {
                SECOND_BOOKMARK -> displayStartButton()
                START_BOOKMARK -> checkStartButton()
                START_TIMER_BOOKMARK -> if (shouldRepeatWithTimer.getAndSet(false)) startTimer()
                STOP_TIMER_BOOKMARK -> stopTimer()
            }
        }

        qiChatbot?.addOnEndedListener { goToGame() }

        chat = ChatBuilder.with(qiContext)
                .withChatbot(qiChatbot)
                .build()

        chat?.addOnStartedListener {
            if (startAtHome) {
                goToBookmark(secondBookmark)
            } else {
                goToBookmark(firstBookmark)
            }
        }

        chat?.async()?.run()
    }

    override fun onRobotFocusLost() {
        stopTimer()

        qiChatbot?.removeAllOnBookmarkReachedListeners()
        qiChatbot?.removeAllOnEndedListeners()
        chat?.removeAllOnStartedListeners()
    }

    override fun onRobotFocusRefused(reason: String?) {
        // Not used.
    }

    private fun displayStartButton() {
        runOnUiThread {
            binding.startButton.apply {
                visibility = View.VISIBLE
            }
        }
    }

    private fun hideStartButton() {
        runOnUiThread {
            binding.startButton.apply {
                isChecked = false
                visibility = View.INVISIBLE
            }
        }
    }

    private fun checkStartButton() {
        runOnUiThread {
            binding.startButton.apply {
                isChecked = true
            }
        }
    }

    private fun goToBookmark(bookmark: Bookmark?) {
        qiChatbot?.async()?.goToBookmark(bookmark, AutonomousReactionImportance.HIGH, AutonomousReactionValidity.IMMEDIATE)
    }

    private fun startTimer() {
        timerDisposable = Single.timer(5, TimeUnit.SECONDS)
                .observeOn(Schedulers.io())
                .subscribeOn(Schedulers.io())
                .subscribe { _ ->
                    stopTimer()
                    goToBookmark(secondBookmark)
                }
    }

    private fun stopTimer() {
        timerDisposable?.takeUnless { it.isDisposed }?.dispose()
    }

    private fun goToGame() {
        runOnUiThread {
            val intent = Intent(this, GameActivity::class.java)
            startActivity(intent)
        }
    }

    private companion object {
        const val FIRST_BOOKMARK = "first"
        const val SECOND_BOOKMARK = "second"
        const val START_BOOKMARK = "start"
        const val START_TIMER_BOOKMARK = "start_timer"
        const val STOP_TIMER_BOOKMARK = "stop_timer"
    }
}
