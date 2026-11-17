package com.karandaev.fieldfolio.core.ui.components.dialog

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentDialog
import androidx.activity.OnBackPressedCallback
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import java.util.UUID

/** WindowManager measures this MATCH_PARENT window; screenWidthDp/screenHeightDp are never used. */
@Composable
internal fun AppDialogWindow(
  onDismissRequest: () -> Unit,
  properties: DialogProperties,
  content: @Composable (Insets) -> Unit,
) {
  val host = LocalView.current
  val composition = rememberCompositionContext()
  val latestContent = rememberUpdatedState(content)
  val latestDismiss = rememberUpdatedState(onDismissRequest)
  val id = rememberSaveable { UUID.randomUUID().toString() }
  val lightBars = MaterialTheme.colorScheme.surface.luminance() > 0.5f
  val dialog =
    remember(host, composition) {
      FullWindowDialog(host.context, host) { latestDismiss.value() }
        .also {
          it.update(properties, lightBars)
          it.composeView.setParentCompositionContext(composition)
          it.composeView.setTag(
            androidx.compose.ui.R.id.compose_view_saveable_id_tag,
            "app-dialog:$id",
          )
        }
    }
  SideEffect { dialog.update(properties, lightBars) }
  DisposableEffect(dialog) {
    dialog.composeView.setContent { latestContent.value(dialog.insets.value) }
    dialog.show()
    onDispose {
      dialog.dismiss()
      dialog.composeView.disposeComposition()
    }
  }
}

/**
 * Capture system bars before DecorView can consume them, then apply them exactly once in Compose.
 */
private class FullWindowDialog(context: Context, host: View, dismiss: () -> Unit) :
  ComponentDialog(context, android.R.style.Theme_Material_Light_NoActionBar) {
  val composeView = DialogComposeView(context, requireNotNull(window))
  val insets = mutableStateOf(systemInsets(ViewCompat.getRootWindowInsets(host)))
  private var properties = DialogProperties()
  private val inheritSecure =
    ((host.rootView.layoutParams as? WindowManager.LayoutParams)?.flags ?: 0) and
      WindowManager.LayoutParams.FLAG_SECURE != 0

  init {
    val window = requireNotNull(window)
    window.requestFeature(Window.FEATURE_NO_TITLE)
    window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    WindowCompat.setDecorFitsSystemWindows(window, false)
    window.setSoftInputMode(
      if (Build.VERSION.SDK_INT >= 30) WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
      else WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
    )
    window.attributes =
      window.attributes.apply {
        windowAnimations = 0
        // WindowManager must also stop fitting the dialog frame around system bars.
        // Decor fitting alone only changes the view hierarchy inside that frame.
        if (Build.VERSION.SDK_INT >= 30) setFitInsetsTypes(0)
        if (Build.VERSION.SDK_INT >= 28) {
          layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
      }
    @Suppress("DEPRECATION")
    window.statusBarColor = Color.TRANSPARENT
    @Suppress("DEPRECATION")
    window.navigationBarColor = Color.TRANSPARENT
    if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
    window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    window.setDimAmount(0.32f)
    val root = DialogRoot(context, window)
    root.setViewTreeViewModelStoreOwner(host.findViewTreeViewModelStoreOwner())
    root.setViewTreeSavedStateRegistryOwner(host.findViewTreeSavedStateRegistryOwner())
    root.addView(
      composeView,
      FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
      ),
    )
    ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { _, value ->
      insets.value = systemInsets(value)
      WindowInsetsCompat.CONSUMED
    }
    setContentView(root)
    setCanceledOnTouchOutside(false)
    onBackPressedDispatcher.addCallback(
      this,
      object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
          if (properties.dismissOnBackPress) dismiss()
        }
      },
    )
    setOnShowListener {
      window.decorView.setPadding(0, 0, 0, 0)
      window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
      ViewCompat.requestApplyInsets(window.decorView)
    }
  }

  fun update(value: DialogProperties, lightBars: Boolean) {
    properties = value
    val window = requireNotNull(window)
    val secure =
      when (value.securePolicy) {
        SecureFlagPolicy.SecureOn -> true
        SecureFlagPolicy.SecureOff -> false
        SecureFlagPolicy.Inherit -> inheritSecure
      }
    if (secure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    WindowCompat.getInsetsController(window, window.decorView).apply {
      isAppearanceLightStatusBars = lightBars
      isAppearanceLightNavigationBars = lightBars
    }
  }
}

private class DialogRoot(context: Context, override val window: Window) :
  FrameLayout(context), DialogWindowProvider

private fun systemInsets(value: WindowInsetsCompat?): Insets {
  if (value == null) return Insets.NONE
  val bars = value.getInsets(WindowInsetsCompat.Type.systemBars())
  // Older windows resize above the keyboard; its height must not be subtracted again.
  val ime =
    if (Build.VERSION.SDK_INT >= 30) value.getInsets(WindowInsetsCompat.Type.ime()) else Insets.NONE
  return Insets.of(
    maxOf(bars.left, ime.left),
    maxOf(bars.top, ime.top),
    maxOf(bars.right, ime.right),
    maxOf(bars.bottom, ime.bottom),
  )
}

/**
 * Text input discovers its window through the immediate ComposeView parent, as in Compose Dialog.
 */
private class DialogComposeView(context: Context, override val window: Window) :
  AbstractComposeView(context), DialogWindowProvider {
  private var body by mutableStateOf<(@Composable () -> Unit)?>(null)

  @Composable
  override fun Content() {
    body?.invoke()
  }

  fun setContent(content: @Composable () -> Unit) {
    body = content
  }
}
