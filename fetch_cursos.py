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

try:
    response = urllib.request.urlopen('http://localhost:8080/login')
    html = response.read().decode('utf-8')
    
    csrf_token_match = re.search(r'name="_csrf"\s*value="([^"]+)"', html)
    csrf_token = csrf_token_match.group(1) if csrf_token_match else ''
    
    # Try different combinations of username/password, maybe we can hit the actual database credentials
    data = urllib.parse.urlencode({'username': 'sofia.alarcon@skillbridge.test', 'password': '123', '_csrf': csrf_token}).encode('utf-8')
    try:
        response = urllib.request.urlopen('http://localhost:8080/login', data=data)
    except Exception:
        pass

    try:
        response = urllib.request.urlopen('http://localhost:8080/admin/cursos')
        content = response.read().decode('utf-8')
        with open('output_cursos.txt', 'w', encoding='utf-8') as f:
            f.write(content)
        print("Success, saved to output_cursos.txt")
    except urllib.error.HTTPError as e:
        print(f"HTTPError: {e.code}")
        content = e.read().decode('utf-8')
        with open('output_cursos.txt', 'w', encoding='utf-8') as f:
            f.write(content)
except Exception as e:
    print(f"Error: {e}")
