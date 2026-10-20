# test_photo_blog_server.py
# 실행: coverage run test_photo_blog_server.py
import os, sys, io, tempfile
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
os.environ.setdefault("DJANGO_SETTINGS_MODULE", "mysite.settings")

import django
django.setup()
from django.conf import settings
from django.test.runner import DiscoverRunner
from django.test import Client
from django.core.files.uploadedfile import SimpleUploadedFile
from django.contrib.auth.models import User
from PIL import Image
from blog.models import Post

# 임시 DB / 임시 미디어 폴더 사용 (실제 db.sqlite3, media 는 건드리지 않음)
settings.MEDIA_ROOT = tempfile.mkdtemp()
runner = DiscoverRunner(verbosity=0)
runner.setup_test_environment()
old = runner.setup_databases()

fail = 0
def check(name, got, expected):
    global fail
    ok = got == expected
    fail += not ok
    print("[%s] %s: %s" % ("PASS" if ok else "FAIL", name, got if ok else "got %s, expected %s" % (got, expected)))

def image():
    buf = io.BytesIO()
    Image.new("RGB", (4, 4)).save(buf, format="PNG")
    return SimpleUploadedFile("t.png", buf.getvalue(), content_type="image/png")

c = Client()
user = User.objects.create_user("tester", password="pw12345!")

# --- 웹 페이지 ---
check("GET /", c.get("/").status_code, 200)
check("GET /js_test/", c.get("/js_test/").status_code, 200)
check("GET /post/new/", c.get("/post/new/").status_code, 200)
check("GET /post/999/ (없는 글)", c.get("/post/999/").status_code, 404)

# --- REST API ---
check("GET /api_root/", c.get("/api_root/").status_code, 200)
check("GET Post 목록 (비어있음)", c.get("/api_root/Post/").json(), [])
r = c.post("/api_root/Post/", {"author": user.pk, "title": "t1", "text": "x", "image": image()})
check("POST Post (이미지 포함)", r.status_code, 201)
r = c.post("/api_root/Post/", {"author": user.pk, "title": "t2", "text": "x"}, content_type="application/json")
check("POST Post (이미지 없음)", r.status_code, 201)
pk = Post.objects.get(title="t2").pk
check("POST Post (필드 누락)", c.post("/api_root/Post/", {"title": "t"}, content_type="application/json").status_code, 400)
check("GET Post 목록", len(c.get("/api_root/Post/").json()), 2)
check("GET Post 상세", c.get("/api_root/Post/%d/" % pk).json()["title"], "t2")
check("PUT Post", c.put("/api_root/Post/%d/" % pk, {"author": user.pk, "title": "new", "text": "y"}, content_type="application/json").status_code, 200)
check("PATCH Post", c.patch("/api_root/Post/%d/" % pk, {"title": "p"}, content_type="application/json").json()["title"], "p")
check("DELETE Post", c.delete("/api_root/Post/%d/" % pk).status_code, 204)
check("GET 삭제된 Post", c.get("/api_root/Post/%d/" % pk).status_code, 404)

# --- 글 작성/수정 (로그인 필요) ---
c.login(username="tester", password="pw12345!")
r = c.post("/post/new/", {"title": "web", "text": "body"})
check("POST /post/new/", r.status_code, 302)
check("GET /post/<pk>/", c.get(r.url).status_code, 200)
check("POST /post/<pk>/edit/", c.post(r.url + "edit/", {"title": "web2", "text": "b"}).status_code, 302)
check("GET /", b"web2" in c.get("/").content, True)

# --- 모델 / 수정 폼 ---
post = Post.objects.get(title="web2")
check("Post.__str__", str(post), "web2")
post.publish()
check("Post.publish", post.published_date is not None, True)
check("GET /post/<pk>/edit/", c.get("/post/%d/edit/" % post.pk).status_code, 200)
check("POST 잘못된 입력", c.post("/post/new/", {"title": "", "text": ""}).status_code, 200)
check("POST 수정 잘못된 입력", c.post("/post/%d/edit/" % post.pk, {"title": "", "text": ""}).status_code, 200)

# --- 토큰 인증 ---
login = c.post("/api-token-auth/", {"username": "tester", "password": "pw12345!"}).json()
check("로그인 (token, user_id, username)", (bool(login["token"]), login["user_id"], login["username"]), (True, user.pk, "tester"))
check("토큰 발급 (잘못된 비번)", c.post("/api-token-auth/", {"username": "tester", "password": "x"}).status_code, 400)

# --- WSGI / ASGI 진입점 ---
import mysite.wsgi, mysite.asgi, blog.tests
check("WSGI application", callable(mysite.wsgi.application), True)
check("ASGI application", callable(mysite.asgi.application), True)

runner.teardown_databases(old)
runner.teardown_test_environment()
print("\n%s" % ("ALL PASSED" if not fail else "%d FAILED" % fail))
sys.exit(bool(fail))
