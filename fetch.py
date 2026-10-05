import urllib.request
import urllib.parse
from http.cookiejar import CookieJar
import re
import ssl

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

cj = CookieJar()
opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cj), urllib.request.HTTPSHandler(context=ctx))
urllib.request.install_opener(opener)

# We can bypass login if we just test the template rendering using a quick dummy controller
# But wait, we can't easily add a controller without recompiling.
# Let's just login. The default user might be admin@skillbridge.test with password valar575.
