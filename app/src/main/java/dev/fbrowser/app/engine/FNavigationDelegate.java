package dev.fbrowser.app.engine;

import java.util.List;

import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.WebRequestError;

/**
 * Navigation delegate written in Java on purpose.
 *
 * The signature of onLocationChange has changed between GeckoView releases (with and without the
 * permissions list and the user-gesture flag). Java lets this class provide every variant without
 * @Override, so it compiles against whichever one the pinned GeckoView version declares.
 * Everything else is forwarded to the Kotlin {@link NavigationHandler}.
 */
public final class FNavigationDelegate implements GeckoSession.NavigationDelegate {
    private final NavigationHandler handler;

    public FNavigationDelegate(NavigationHandler handler) {
        this.handler = handler;
    }

    // --- onLocationChange: all known signatures ---

    public void onLocationChange(GeckoSession session, String url) {
        handler.onLocationChange(url);
    }

    public void onLocationChange(
            GeckoSession session,
            String url,
            List<GeckoSession.PermissionDelegate.ContentPermission> perms) {
        handler.onLocationChange(url);
    }

    public void onLocationChange(
            GeckoSession session,
            String url,
            List<GeckoSession.PermissionDelegate.ContentPermission> perms,
            Boolean hasUserGesture) {
        handler.onLocationChange(url);
    }

    // --- stable part of the API ---

    @Override
    public void onCanGoBack(GeckoSession session, boolean canGoBack) {
        handler.onCanGoBack(canGoBack);
    }

    @Override
    public void onCanGoForward(GeckoSession session, boolean canGoForward) {
        handler.onCanGoForward(canGoForward);
    }

    @Override
    public GeckoResult<AllowOrDeny> onLoadRequest(GeckoSession session, LoadRequest request) {
        boolean allow = handler.onLoadRequest(request.uri, request.isRedirect, request.hasUserGesture);
        return GeckoResult.fromValue(allow ? AllowOrDeny.ALLOW : AllowOrDeny.DENY);
    }

    /** target=_blank and window.open: open in a new fBrowser tab instead of a popup window. */
    @Override
    public GeckoResult<GeckoSession> onNewSession(GeckoSession session, String uri) {
        handler.onNewWindow(uri);
        return GeckoResult.fromValue(null);
    }

    /** The error page is drawn by the app (Compose), so no page URI is returned. */
    @Override
    public GeckoResult<String> onLoadError(GeckoSession session, String uri, WebRequestError error) {
        handler.onLoadError(uri, error.category, error.code);
        return null;
    }
}
