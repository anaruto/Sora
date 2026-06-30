package eu.kanade.tachiyomi.browser

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

class GeckoBrowserFragment : Fragment() {
    private var geckoView: GeckoView? = null
    private var session: GeckoSession? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val frameLayout = FrameLayout(requireContext())
        geckoView = GeckoView(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        frameLayout.addView(geckoView)
        return frameLayout
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        session?.let { geckoView?.setSession(it) }
    }

    fun setSession(session: GeckoSession) {
        this.session = session
        geckoView?.setSession(session)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        geckoView = null
    }
}
