(function() {
  function sendCookies() {
    try {
      browser.runtime.sendNativeMessage("cookie-extractor", {
        url: window.location.href,
        cookies: document.cookie
      });
    } catch (e) {
      // Ignore
    }
  }

  // Send on load
  sendCookies();

  // Send on click/navigation events with delay to capture dynamic changes
  document.addEventListener("click", function() {
    setTimeout(sendCookies, 1000);
  });
})();
