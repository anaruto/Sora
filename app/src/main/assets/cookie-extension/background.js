try {
  const port = browser.runtime.connectNative("cookie_bridge");

  port.onMessage.addListener(async (message) => {
    if (message.action === "getAll") {
      try {
        const cookies = await browser.cookies.getAll({});
        port.postMessage({
          action: "getAllResponse",
          success: true,
          cookies: cookies
        });
      } catch (e) {
        port.postMessage({
          action: "getAllResponse",
          success: false,
          error: e.toString()
        });
      }
    } else if (message.action === "set") {
      try {
        const cookiesToSet = message.cookies || [];
        for (const c of cookiesToSet) {
          // Construct a valid URL from domain and path for browser.cookies.set
          let domain = c.domain || "";
          if (domain.startsWith(".")) {
            domain = domain.substring(1);
          }
          const protocol = c.secure ? "https://" : "http://";
          const path = c.path || "/";
          const url = protocol + domain + path;

          const details = {
            url: url,
            name: c.name,
            value: c.value,
            domain: c.domain,
            path: c.path,
            secure: !!c.secure,
            httpOnly: !!c.httpOnly
          };

          if (c.expires !== undefined && c.expires !== null) {
            // Note: browser.cookies.set expects expirationDate in SECONDS
            // c.expires is typically passed as millisecond timestamp or seconds.
            // Let's pass seconds from Kotlin.
            details.expirationDate = c.expires;
          }

          if (c.sameSite) {
            const ss = c.sameSite.toLowerCase();
            if (ss === "no_restriction" || ss === "lax" || ss === "strict") {
              details.sameSite = ss;
            } else if (ss === "none") {
              details.sameSite = "no_restriction";
            }
          }
          await browser.cookies.set(details);
        }
        port.postMessage({
          action: "setResponse",
          success: true
        });
      } catch (e) {
        port.postMessage({
          action: "setResponse",
          success: false,
          error: e.toString()
        });
      }
    }
  });
} catch (e) {
  console.error("Error in cookie background script", e);
}
