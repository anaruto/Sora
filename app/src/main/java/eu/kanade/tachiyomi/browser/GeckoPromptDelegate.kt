package eu.kanade.tachiyomi.browser

import android.content.Context
import androidx.appcompat.app.AlertDialog
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession

class GeckoPromptDelegate(private val context: Context) : GeckoSession.PromptDelegate {
    
    override fun onAlertPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.AlertPrompt
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
        val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
        var completed = false

        AlertDialog.Builder(context)
            .setMessage(prompt.message)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                if (!completed) {
                    result.complete(prompt.dismiss())
                    completed = true
                }
            }
            .setOnDismissListener {
                if (!completed) {
                    result.complete(prompt.dismiss())
                    completed = true
                }
            }
            .show()
        return result
    }

    override fun onBeforeUnloadPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.BeforeUnloadPrompt
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
        // Automatically allow navigating away from pages with unsaved changes
        return GeckoResult.fromValue(prompt.confirm(AllowOrDeny.ALLOW))
    }
}
