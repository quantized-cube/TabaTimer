package com.sample.android.tabatimer

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    companion object {
        private const val delayInterval: Long = 50  // milli-seconds
        private const val multiplierSecToCount: Int = 20  // == 1000 / delayInterval.toInt()
        private const val ACTION_START_PAUSE = "startPause"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var elapsedTimeCounter = 0   // == (time in seconds) * multiplierSecToCount
    private var cycleElapsedTimeCounter = 0

    private var activeTimeSec: Int = 20  // default: 20 seconds
    private var restTimeSec: Int = 10  // default: 10 seconds
    private var activeTimeCount: Int = activeTimeSec * multiplierSecToCount
    private var restTimeCount: Int = restTimeSec * multiplierSecToCount
    private var cycleTimeCount = activeTimeCount + restTimeCount

    private var activeTimeDecounter: Int = activeTimeCount
    private var restTimeDecounter: Int = activeTimeCount

    private lateinit var soundPool: SoundPool
    private var sndStart = 0
    private var sndZero = 0
    private var sndFour = 0
    private var sndSeven = 0
    private var sndFinish = 0
    private var onFlag = 0

    private lateinit var buttonStartPause: Button
    private lateinit var buttonPip: Button
    private lateinit var runnable: Runnable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        buttonStartPause = findViewById(R.id.btStartPause)
        buttonPip = findViewById(R.id.buttonPip)

        val audioAttributes = AudioAttributes.Builder()
            // USAGE_MEDIA
            // USAGE_GAME
            .setUsage(AudioAttributes.USAGE_GAME)
            // CONTENT_TYPE_MUSIC
            // CONTENT_TYPE_SPEECH, etc.
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        soundPool = SoundPool.Builder()
            .setAudioAttributes(audioAttributes)
            // according to number of streams
            .setMaxStreams(5)
            .build()

        // load mp3
        sndStart = soundPool.load(this, R.raw.start_ring_delay, 1)
        sndZero = soundPool.load(this, R.raw.start_ring, 1)
        sndFour = soundPool.load(this, R.raw.start_ring_plus_four, 1)
        sndSeven = soundPool.load(this, R.raw.start_ring_plus_seven, 1)
        sndFinish = soundPool.load(this, R.raw.start_ring_plus_twelve_and_delay, 1)

        val elapsedTimeTV = findViewById<TextView>(R.id.tvElapsedTime)
        val restTimeTV = findViewById<TextView>(R.id.tvRestTime)
        val activeTimeTV = findViewById<TextView>(R.id.tvActiveTime)

        runnable = object : Runnable {
            override fun run() {

                elapsedTimeTV.text = timeSecIntToHmsText(
                    elapsedTimeCounter / multiplierSecToCount
                )
                activeTimeTV.text = timeSecIntToHmsText(
                    maxOf(activeTimeDecounter, 0) / multiplierSecToCount
                )
                restTimeTV.text = timeSecIntToHmsText(
                    minOf(restTimeDecounter, restTimeCount) / multiplierSecToCount
                )

                if ((cycleElapsedTimeCounter == 0) || (elapsedTimeCounter == 0)) {
                    // begin of cycle
                    soundPool.play(
                        sndStart, 1.0f, 1.0f, 0, 0, 1.0f
                    )
                } else if (activeTimeDecounter == 0) {
                    // begin of rest
                    soundPool.play(
                        sndFinish, 1.0f, 1.0f, 0, 0, 1.0f
                    )
                } else if (activeTimeDecounter == 30 * multiplierSecToCount) {
                    // 30 sec to rest
                    soundPool.play(
                        sndZero, 1.0f, 1.0f, 0, 0, 1.0f
                    )
                } else if (activeTimeDecounter == 20 * multiplierSecToCount) {
                    // 20 sec to rest
                    soundPool.play(
                        sndFour, 1.0f, 1.0f, 0, 0, 1.0f
                    )
                } else if (activeTimeDecounter == 10 * multiplierSecToCount) {
                    // 10 sec to rest
                    soundPool.play(
                        sndSeven, 1.0f, 1.0f, 0, 0, 1.0f
                    )
                }

                elapsedTimeCounter++
                cycleElapsedTimeCounter = elapsedTimeCounter % cycleTimeCount
                activeTimeDecounter = activeTimeCount - cycleElapsedTimeCounter
                restTimeDecounter = cycleTimeCount - cycleElapsedTimeCounter

                handler.postDelayed(runnable, delayInterval)
            }
        }

        val buttonMinusActive = findViewById<Button>(R.id.btMinusActiveTime)
        val buttonPlusActive = findViewById<Button>(R.id.btPlusActiveTime)
        val buttonMinusRest = findViewById<Button>(R.id.btMinusRestTime)
        val buttonPlusRest = findViewById<Button>(R.id.btPlusRestTime)

        // Start / Pause / Resume
        val startPauseListener = StartPauseListener()
        buttonStartPause.setOnClickListener(startPauseListener)

        // Reset
        val buttonReset = findViewById<Button>(R.id.btReset)
        buttonReset.setOnClickListener {
            if (onFlag == 1) {
                handler.removeCallbacks(runnable)
                onFlag = 0
            }
            buttonMinusActive.isEnabled = true
            buttonPlusActive.isEnabled = true
            buttonMinusRest.isEnabled = true
            buttonPlusRest.isEnabled = true
            elapsedTimeCounter = 0
            cycleElapsedTimeCounter = 0
            activeTimeDecounter = activeTimeCount
            restTimeDecounter = restTimeCount
            buttonStartPause.text = getString(R.string.command_start)
            elapsedTimeTV.text = timeSecIntToHmsText(0)
            activeTimeTV.text = timeSecIntToHmsText(activeTimeCount / multiplierSecToCount)
            restTimeTV.text = timeSecIntToHmsText(restTimeCount / multiplierSecToCount)
        }

        // PiP
        buttonPip.setOnClickListener {
            val params = PictureInPictureParams.Builder().apply {
                setAspectRatio(Rational(4, 3))
            }.build()
            enterPictureInPictureMode(params)
        }

    }

    private inner class StartPauseListener : View.OnClickListener {

        override fun onClick(v: View?) {

//            val buttonStartPause = findViewById<Button>(R.id.btStartPause)
            val buttonMinusActive = findViewById<Button>(R.id.btMinusActiveTime)
            val buttonPlusActive = findViewById<Button>(R.id.btPlusActiveTime)
            val buttonMinusRest = findViewById<Button>(R.id.btMinusRestTime)
            val buttonPlusRest = findViewById<Button>(R.id.btPlusRestTime)

            if (onFlag == 0) {
                handler.post(runnable)
                buttonStartPause.text = getString(R.string.command_pause)
                buttonMinusActive.isEnabled = false
                buttonPlusActive.isEnabled = false
                buttonMinusRest.isEnabled = false
                buttonPlusRest.isEnabled = false
                onFlag = 1
            } else {
                handler.removeCallbacks(runnable)
                buttonStartPause.text = getString(R.string.command_resume)
                onFlag = 0
            }

        }
    }

    fun onActivePlusMinusButtonClick(view: View) {
        val activeTime = findViewById<TextView>(R.id.tvActiveTime)
        when (view.id) {
            R.id.btPlusActiveTime -> {
                activeTimeSec += 10
                activeTimeCount = activeTimeSec * multiplierSecToCount
                cycleTimeCount = activeTimeCount + restTimeCount
            }
            R.id.btMinusActiveTime -> {
                if (activeTimeSec > 10) {
                    activeTimeSec -= 10
                    activeTimeCount = activeTimeSec * multiplierSecToCount
                    cycleTimeCount = activeTimeCount + restTimeCount
                }
            }
        }
        activeTime.text = timeSecIntToHmsText(activeTimeSec)
    }

    fun onRestPlusMinusButtonClick(view: View) {
        val restTime = findViewById<TextView>(R.id.tvRestTime)
        when (view.id) {
            R.id.btPlusRestTime -> {
                restTimeSec += 10
                restTimeCount = restTimeSec * multiplierSecToCount
                cycleTimeCount = activeTimeCount + restTimeCount
            }
            R.id.btMinusRestTime -> {
                if (restTimeSec > 10) {
                    restTimeSec -= 10
                    restTimeCount = restTimeSec * multiplierSecToCount
                    cycleTimeCount = activeTimeCount + restTimeCount
                }
            }
        }
        restTime.text = timeSecIntToHmsText(restTimeSec)
    }

    // home
    override fun onUserLeaveHint() {
        // enterPictureInPictureMode()
//        val buttonPip = findViewById<Button>(R.id.buttonPip)
        buttonPip.callOnClick()
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)

        val elapsedTimeTV = findViewById<TextView>(R.id.tvElapsedTime)
        val restTimeTV = findViewById<TextView>(R.id.tvRestTime)
        val activeTimeTV = findViewById<TextView>(R.id.tvActiveTime)
//        val buttonStartPause = findViewById<Button>(R.id.btStartPause)
        val buttonReset = findViewById<Button>(R.id.btReset)
        val buttonMinusTwenty = findViewById<Button>(R.id.btMinusActiveTime)
        val buttonMinusTen = findViewById<Button>(R.id.btMinusRestTime)
        val buttonPlusTen = findViewById<Button>(R.id.btPlusRestTime)
        val buttonPlusTwenty = findViewById<Button>(R.id.btPlusActiveTime)
//        val buttonPip = findViewById<Button>(R.id.buttonPip)

        // val receiver = null
        if (isInPictureInPictureMode) {
            elapsedTimeTV.setTextSize(40F)
            buttonStartPause.setVisibility(View.INVISIBLE)
            buttonReset.setVisibility(View.INVISIBLE)
            activeTimeTV.setVisibility(View.INVISIBLE)
            restTimeTV.setVisibility(View.INVISIBLE)
            buttonMinusTwenty.setVisibility(View.INVISIBLE)
            buttonPlusTwenty.setVisibility(View.INVISIBLE)
            buttonMinusTen.setVisibility(View.INVISIBLE)
            buttonPlusTen.setVisibility(View.INVISIBLE)
            buttonPip.setVisibility(View.INVISIBLE)

            // there is some bug
            val filter = IntentFilter()
            filter.addAction(ACTION_START_PAUSE)
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent
                ) {
                    buttonStartPause.callOnClick()
                }
            }
            registerReceiver(receiver, filter)
            val actions = ArrayList<RemoteAction>()
            val actionIntent = Intent(ACTION_START_PAUSE)
            val pendingIntent = PendingIntent.getBroadcast(
                this, 0, actionIntent, 0
            )
            val icon = Icon.createWithResource(this, R.drawable.sub)
            val remoteAction = RemoteAction(
                icon, "someTitle", "someDesc", pendingIntent
            )
            actions.add(remoteAction)
            val params = PictureInPictureParams.Builder()
                .setActions(actions)
                .build()
            setPictureInPictureParams(params)
        } else {
            // Restore the full-screen UI.
            elapsedTimeTV.setTextSize(60F)
            buttonStartPause.setVisibility(View.VISIBLE)
            buttonReset.setVisibility(View.VISIBLE)
            activeTimeTV.setVisibility(View.VISIBLE)
            restTimeTV.setVisibility(View.VISIBLE)
            buttonMinusTwenty.setVisibility(View.VISIBLE)
            buttonPlusTwenty.setVisibility(View.VISIBLE)
            buttonMinusTen.setVisibility(View.VISIBLE)
            buttonPlusTen.setVisibility(View.VISIBLE)
            buttonPip.setVisibility(View.VISIBLE)

            // unregisterReceiver(receiver)
        }
    }

    private fun timeSecIntToHmsText(timeSec: Int): String {
        return if (timeSec <= 0) {
            "00:00:00"
        } else {
            val h = timeSec / 3600
            val m = timeSec % 3600 / 60
            val s = timeSec % 60
            "%1$02d:%2$02d:%3$02d".format(h, m, s)
        }
    }

//    private fun hmsTextToTimeSecInt(hms: String = "00:00:00"): Int {
//        val h = hms.substring(0, 2).toInt()
//        val m = hms.substring(3, 5).toInt()
//        val s = hms.substring(6, 8).toInt()
//        return h * 3600 + m * 60 + s
//    }

}
