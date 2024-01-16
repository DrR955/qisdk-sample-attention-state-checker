/*
 * Copyright (C) 2018 SoftBank Robotics Europe
 * See COPYING for the license
 */
package com.softbankrobotics.sample.attentionstatechecker.game

import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.annotation.RawRes
import com.aldebaran.qi.sdk.QiSDK
import com.aldebaran.qi.sdk.design.activity.RobotActivity
import com.aldebaran.qi.sdk.design.activity.conversationstatus.SpeechBarDisplayStrategy
import com.softbankrobotics.R
import com.softbankrobotics.databinding.ActivityGameBinding
import com.softbankrobotics.sample.attentionstatechecker.introduction.IntroductionActivity
import com.softbankrobotics.sample.attentionstatechecker.model.data.Direction
import com.softbankrobotics.sample.attentionstatechecker.model.data.Direction.*
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers

/**
 * The game activity.
 */
class GameActivity : RobotActivity() {

    private lateinit var binding: ActivityGameBinding

    private val gameMachine = GameMachine()

    private val gameRobot = GameRobot(gameMachine)

    private val disposables = CompositeDisposable()

    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setSpeechBarDisplayStrategy(SpeechBarDisplayStrategy.OVERLAY)
        binding = ActivityGameBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        binding.homeButton.setOnClickListener { goToHome() }
        binding.closeButton.setOnClickListener { finishAffinity() }
        binding.stopButton.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                gameMachine.postEvent(GameEvent.Stop)
            }
        }

        QiSDK.register(this, gameRobot)
    }

    override fun onResume() {
        super.onResume()
        binding.stopButton.isChecked = false

        disposables.add(gameMachine.gameState()
                .subscribeOn(Schedulers.io())
                .distinctUntilChanged()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::handleGameState))
    }

    override fun onPause() {
        disposables.clear()
        mediaPlayer?.release()
        mediaPlayer = null
        super.onPause()
    }

    override fun onDestroy() {
        QiSDK.unregister(this)
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }

    override fun onBackPressed() {
        goToHome()
    }

    private fun handleGameState(gameState: GameState) {
        when (gameState) {
            is GameState.Idle,
            is GameState.Briefing,
            is GameState.Stopping -> {
                showStop()
                hideExpectedDirection()
                hideProgress()
                showNeutralHuman()
                hideTrophy()
            }
            is GameState.Instructions -> {
                showStop()
                showExpectedDirection(gameState.expectedDirection)
                showProgress(gameState.matched, gameState.total)
                hideTrophy()
            }
            is GameState.Playing -> {
                showStop()
                showExpectedDirection(gameState.expectedDirection)
                showProgress(gameState.matched, gameState.total)
                hideTrophy()
            }
            is GameState.NotMatching -> {
                showStop()
                showExpectedDirection(gameState.expectedDirection)
                showProgress(gameState.matched, gameState.total)
                hideTrophy()
                playSound(R.raw.error)
            }
            is GameState.Matching -> {
                showStop()
                showExpectedDirection(gameState.matchingDirection)
                showProgress(gameState.matched, gameState.total)
                hideTrophy()
                playSound(R.raw.success)
            }
            is GameState.Win -> {
                hideStop()
                hideExpectedDirection()
                hideProgress()
                showTrophy()
                playSound(R.raw.big_success)
            }
            is GameState.End -> {
                goToHome()
            }
        }
    }

    private fun hideExpectedDirection() {
        binding.expectedDirectionTextView.text = ""
        binding.humanImageView.visibility = View.INVISIBLE
    }

    private fun showExpectedDirection(direction: Direction) {
        binding.expectedDirectionTextView.text = getString(R.string.look_instruction, direction.toString())
        binding.humanImageView.visibility = View.VISIBLE
        binding.humanImageView.setImageResource(humanImageFromDirection(direction))
    }

    private fun showNeutralHuman() {
        binding.humanImageView.visibility = View.VISIBLE
        binding.humanImageView.setImageResource(R.drawable.ic_user_face)
    }

    private fun hideTrophy() {
        binding.trophyImageView.visibility = View.INVISIBLE
    }

    private fun showTrophy() {
        binding.trophyImageView.visibility = View.VISIBLE
    }

    private fun humanImageFromDirection(direction: Direction) =
            when (direction) {
                UP -> R.drawable.ic_user_face_up
                DOWN -> R.drawable.ic_user_face_down
                LEFT -> R.drawable.ic_user_face_left
                RIGHT -> R.drawable.ic_user_face_right
                else -> throw IllegalStateException("Unknown direction $direction")
            }

    private fun hideProgress() {
        binding.progressTextView.visibility = View.INVISIBLE
    }

    private fun showProgress(matched: Int, total: Int) {
        binding.progressTextView.apply {
            visibility = View.VISIBLE
            text = getString(R.string.progress, matched, total)
        }
    }

    private fun hideStop() {
        binding.stopButton.visibility = View.INVISIBLE
    }

    private fun showStop() {
        binding.stopButton.visibility = View.VISIBLE
    }

    private fun goToHome() {
        val intent = Intent(this, IntroductionActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        intent.putExtra("startAtHome", true)
        startActivity(intent)
    }

    private fun playSound(@RawRes resource: Int) {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer.create(this, resource).apply { start() }
    }

    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        } else {
            val decorView = window.decorView
            decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN)
        }
    }
}
