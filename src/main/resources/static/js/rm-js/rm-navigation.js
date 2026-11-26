/*
 * Returns the correct URL for RM mockups in both supported modes:
 * direct file preview and navigation through Spring Boot.
 */
function rmViewUrl(staticFile, applicationPath) {
  return window.location.protocol === "file:"
    ? staticFile
    : applicationPath;
}
