/*
 * Returns the correct URL for Admin mockups in both supported modes:
 * direct file preview and navigation through Spring Boot.
 */
function adminViewUrl(staticFile, applicationPath) {
  return window.location.protocol === "file:"
    ? staticFile
    : applicationPath;
}
