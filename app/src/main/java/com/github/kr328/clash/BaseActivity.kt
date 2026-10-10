package com.github.kr328.clash

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.app.ActivityManager
import android.content.res.Configuration
import android.graphics.Outline
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContract
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import androidx.core.view.ViewCompat
import com.github.kr328.clash.common.compat.isAllowForceDarkCompat
import com.github.kr328.clash.common.compat.isLightNavigationBarCompat
import com.github.kr328.clash.common.compat.isLightStatusBarsCompat
import com.github.kr328.clash.common.compat.isSystemBarsTranslucentCompat
import com.github.kr328.clash.core.bridge.ClashException
import com.github.kr328.clash.design.Design
import com.github.kr328.clash.design.model.DarkMode
import com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.design.ui.DayNight
import com.github.kr328.clash.design.util.resolveThemedBoolean
import com.github.kr328.clash.design.util.showExceptionToast
import com.github.kr328.clash.remote.Broadcasts
import com.github.kr328.clash.remote.Remote
import com.github.kr328.clash.util.ActivityResultLifecycle
import com.github.kr328.clash.util.ApplicationObserver
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import com.github.kr328.clash.design.R

abstract class BaseActivity<D : Design<*>> : AppCompatActivity(),
    CoroutineScope by MainScope(),
    Broadcasts.Observer {
    
    protected val uiStore by lazy { UiStore(this) }
    protected val events = Channel<Event>(Channel.UNLIMITED)
    protected var activityStarted: Boolean = false
    protected val clashRunning: Boolean
        get() = Remote.broadcasts.clashRunning
    protected var design: D? = null
        set(value) {
            field = value
            if (value != null) {
                setContentView(value.root)
            } else {
                setContentView(View(this))
            }
        }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Re-apply appearance flags now that the decor view is attached to the window
        // (insetsController is guaranteed to be available) and re-dispatch insets so that
        // our surface padding listeners (set before setContentView) see up-to-date values.
        window.isLightStatusBarsCompat = resolveThemedBoolean(android.R.attr.windowLightStatusBar)
        window.isLightNavigationBarCompat = resolveThemedBoolean(android.R.attr.windowLightNavigationBar)
        ViewCompat.requestApplyInsets(window.decorView)
    }

    private var defer: suspend () -> Unit = {}
    private var deferRunning = false
    private val nextRequestKey = AtomicInteger(0)
    private var dayNight: DayNight = DayNight.Day

    protected abstract suspend fun main()

    fun defer(operation: suspend () -> Unit) {
        this.defer = operation
        // defer 承载退出前的保存逻辑，不能被系统直接 finish 绕过——
        // 自动启用返回拦截，让系统把返回手势交给本 Activity 处理。
        setBackInterceptionEnabled(true)
    }

    suspend fun <I, O> startActivityForResult(
        contracts: ActivityResultContract<I, O>,
        input: I,
    ): O = withContext(Dispatchers.Main) {
        val requestKey = nextRequestKey.getAndIncrement().toString()

        ActivityResultLifecycle().use { lifecycle, start ->
            suspendCoroutine { c ->
                activityResultRegistry.register(requestKey, lifecycle, contracts) {
                    c.resume(it)
                }.apply { start() }.launch(input)
            }
        }
    }

    suspend fun setContentDesign(design: D) {
        suspendCoroutine<Unit> {
            window.decorView.post {
                this.design = design
                it.resume(Unit)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val systemDark = resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        enableEdgeToEdge(
            statusBarStyle = if (systemDark) {
                SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
            },
            navigationBarStyle = if (systemDark) {
                SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
            },
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        super.onCreate(savedInstanceState)
        applyDayNight()

        // Apply excludeFromRecents setting to all app tasks.
        checkNotNull(getSystemService<ActivityManager>()).appTasks.forEach { task ->
            task.setExcludeFromRecents(uiStore.hideFromRecents)
        }

        // Predictive back：回调默认 disabled，让系统播放跨 Activity / 回桌面的预见性动画；
        // 只有需要拦截返回的页面（注册了 defer 或显式调用 setBackInterceptionEnabled）才启用。
        onBackPressedDispatcher.addCallback(this, predictiveBackCallback)

        launch {
            main()
        }
    }

    override fun onStart() {
        super.onStart()
        activityStarted = true
        Remote.broadcasts.addObserver(this)
        events.trySend(Event.ActivityStart)
    }

    override fun onStop() {
        super.onStop()
        activityStarted = false
        Remote.broadcasts.removeObserver(this)
        events.trySend(Event.ActivityStop)
    }

    override fun onDestroy() {
        design?.cancel()
        cancel()
        super.onDestroy()
    }

    override fun finish() {
        if (deferRunning) return
        deferRunning = true

        val committed = backCommitRequested
        backCommitRequested = false

        launch {
            try {
                defer()
            } finally {
                withContext(NonCancellable) {
                    if (committed) {
                        // 返回手势提交的退出：先播内容缩小淡出，动画结束后才真正关闭窗口
                        playBackExitAnimation { super.finish() }
                    } else {
                        super.finish()
                    }
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        if (queryDayNight(newConfig) != dayNight) {
            ApplicationObserver.createdActivities.forEach {
                it.recreate()
            }
        }
    }

    // region predictive back

    private val backContentView: View?
        get() = findViewById(android.R.id.content)

    private var backCommitRequested = false
    private var backOutlineRadius = 0f

    private val backOutlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, backOutlineRadius)
        }
    }

    private val predictiveBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackStarted(backEvent: BackEventCompat) {
            backCommitRequested = false
            beginBackGesture()
        }

        override fun handleOnBackProgressed(backEvent: BackEventCompat) {
            if (backContentView?.clipToOutline != true) {
                beginBackGesture()
            }

            val density = resources.displayMetrics.density
            val progress = backEvent.progress
            val direction = if (backEvent.swipeEdge == BackEventCompat.EDGE_LEFT) 1f else -1f

            backContentView?.apply {
                scaleX = 1f - 0.10f * progress
                scaleY = scaleX
                translationX = direction * 24f * density * progress
                backOutlineRadius = 28f * density * progress
                invalidateOutline()
            }
        }

        override fun handleOnBackCancelled() {
            revertBackAnimation()
        }

        override fun handleOnBackPressed() {
            backCommitRequested = true
            onBackInvoked()
        }
    }

    /**
     * 启用/关闭返回拦截。启用后返回手势由本 Activity 消费（系统不再播放跨 Activity 预见性动画），
     * 手势进度驱动 [backContentView] 的缩放动画；提交时走 [onBackInvoked]。
     *
     * 提交后若不 [finish]（如弹确认框、目录栈 pop），必须调用 [revertBackAnimation] 复位内容。
     */
    protected fun setBackInterceptionEnabled(enabled: Boolean) {
        predictiveBackCallback.isEnabled = enabled
    }

    /** 内容回弹复位（手势取消，或提交后不退出的场景）。 */
    protected fun revertBackAnimation() {
        backCommitRequested = false

        val content = backContentView ?: return
        content.animate().cancel()
        content.animate()
            .scaleX(1f)
            .scaleY(1f)
            .translationX(0f)
            .alpha(1f)
            .setDuration(200)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                content.clipToOutline = false
                content.outlineProvider = ViewOutlineProvider.BACKGROUND
            }
            .start()
    }

    private fun beginBackGesture() {
        backContentView?.apply {
            animate().cancel()
            pivotX = width / 2f
            pivotY = height / 2f
            backOutlineRadius = 0f
            outlineProvider = backOutlineProvider
            clipToOutline = true
            invalidateOutline()
        }
    }

    private fun playBackExitAnimation(onEnd: () -> Unit) {
        val content = backContentView
        if (content == null) {
            onEnd()
            return
        }

        // 屏蔽默认窗口关闭转场，由内容动画收尾
        overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)

        content.animate().cancel()

        val startScale = content.scaleX
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 180
            interpolator = AccelerateInterpolator()
            addUpdateListener { animator ->
                val t = animator.animatedValue as Float
                content.scaleX = startScale + (startScale * 0.80f - startScale) * t
                content.scaleY = content.scaleX
                content.alpha = 1f - t
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animator: Animator) {
                    onEnd()
                }
            })
            start()
        }
    }

    // endregion

    open fun shouldDisplayHomeAsUpEnabled(): Boolean {
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    open fun onBackInvoked() {
        finish()
    }

    override fun onProfileChanged() {
        events.trySend(Event.ProfileChanged)
    }

    override fun onProfileUpdateCompleted(uuid: UUID?) {
        events.trySend(Event.ProfileUpdateCompleted)
    }

    override fun onProfileUpdateFailed(uuid: UUID?, reason: String?) {
        events.trySend(Event.ProfileUpdateFailed)
    }

    override fun onProfileLoaded() {
        events.trySend(Event.ProfileLoaded)
    }

    override fun onServiceRecreated() {
        events.trySend(Event.ServiceRecreated)
    }

    override fun onStarted() {
        events.trySend(Event.ClashStart)
    }

    override fun onStopped(cause: String?) {
        events.trySend(Event.ClashStop)

        if (cause != null && activityStarted) {
            launch {
                design?.showExceptionToast(ClashException(cause))
            }
        }
    }

    private fun queryDayNight(config: Configuration = resources.configuration): DayNight {
        return when (uiStore.darkMode) {
            DarkMode.Auto -> if (config.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES) DayNight.Night else DayNight.Day
            DarkMode.ForceLight -> DayNight.Day
            DarkMode.ForceDark -> DayNight.Night
        }
    }

    private fun applyDayNight(config: Configuration = resources.configuration) {
        val dayNight = queryDayNight(config)
        when (dayNight) {
            DayNight.Night -> theme.applyStyle(R.style.AppThemeDark, true)
            DayNight.Day -> theme.applyStyle(R.style.AppThemeLight, true)
        }

        window.isAllowForceDarkCompat = false
        window.isSystemBarsTranslucentCompat = true

        // Note: window.statusBarColor / navigationBarColor are intentionally not set here. They are
        // deprecated and no-ops on API 35+; the (transparent) system bar colors come from the theme,
        // and bar icon legibility is driven by the light-appearance controllers below.
        window.isLightStatusBarsCompat = resolveThemedBoolean(android.R.attr.windowLightStatusBar)
        window.isLightNavigationBarCompat = resolveThemedBoolean(android.R.attr.windowLightNavigationBar)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        this.dayNight = dayNight
    }

    enum class Event {
        ServiceRecreated,
        ActivityStart,
        ActivityStop,
        ClashStop,
        ClashStart,
        ProfileLoaded,
        ProfileChanged,
        ProfileUpdateCompleted,
        ProfileUpdateFailed,
    }
}
