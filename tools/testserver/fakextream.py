"""A fake Xtream Codes server for testing Lumen on the emulator. Login: demo / demo."""
import io, json, os, random, re, sys, time, hashlib
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler
from urllib.parse import urlparse, parse_qs
from PIL import Image, ImageDraw, ImageFont, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
FONT = os.path.join(HERE, "..", "..", "app", "src", "main", "res", "font", "inter_extrabold.ttf")
PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8089
HOST = sys.argv[2] if len(sys.argv) > 2 else "10.0.2.2"
BASE = f"http://{HOST}:{PORT}"
NOW = int(time.time())

SERIES = ["Northbound", "Glass Harbor", "Orbit Kitchen", "The Quiet Year", "Cinder & Salt", "Atlas Lane",
          "Paper Moons", "High Ridge", "Kanal 9", "Die Brücke am Fluss", "Lighthouse Keepers", "Static Bloom",
          "Mercury Street", "Far Shore", "Night Shift Diner", "The Long Winter of Ana Petrović"]
GENRES = ["Drama", "Thriller", "Comedy", "Crime", "Mystery", "Western", "Sci-Fi", "Documentary"]
EPS = ["Arrival", "Low Water", "The Ledger", "Fog Bell", "Tide Line", "Crossing", "Ballast", "Salt Roads", "Night Ferry", "Landfall"]
MOVIE_WORDS = ["Second", "Light", "Velocity", "Club", "Deep", "Field", "Long", "Table", "Ironbark", "Paper", "Tigers", "Coldwater", "Lantern", "Bay", "Silver", "Hour", "Red", "Canyon"]
CHANNELS = ["Nordlicht 1", "Arena Sports", "Cine One", "Kids Planet", "Doku HD", "News 24", "Musik Box", "Comedy Central DE",
            "Arte", "ZDF HD", "Das Erste HD", "Sport 1", "Eurosport 2", "History", "Nature Plus", "Cartoon Time", "Weather Now", "Shop TV", "Retro Movies", "Jazz FM TV"]

random.seed(7)
series_cats = [("10", "Drama"), ("11", "Comedy"), ("12", "Crime & Thriller"), ("13", "Deutsch")]
movie_cats = [("20", "New Releases"), ("21", "Action"), ("22", "Family"), ("23", "Documentary")]
live_cats = [("1", "Germany"), ("2", "Sports"), ("3", "Movies & Kids"), ("4", "Adult (locked)")]

series = []
for i, name in enumerate(SERIES):
    sid = 100 + i
    series.append({"num": i + 1, "name": name, "series_id": sid, "cover": f"{BASE}/img/poster/s{sid}.jpg",
                   "plot": f"{name} follows a small group of people through one long, strange year. A fictional series for testing Lumen.",
                   "cast": "Ada Lind, Ben Okafor", "director": "M. Keller", "genre": GENRES[i % len(GENRES)] + ", Drama",
                   "releaseDate": f"{2015 + i % 10}-03-01", "last_modified": str(NOW - i * 86400 * 2), "rating": f"{6 + (i % 4) * 0.7:.1f}".replace(".", ",") if i % 3 == 0 else str(6.5 + (i % 3)),
                   "category_id": series_cats[i % len(series_cats)][0], "backdrop_path": [f"{BASE}/img/backdrop/s{sid}.jpg"], "youtube_trailer": ""})
movies = []
for i in range(40):
    mid = 1000 + i
    name = f"{MOVIE_WORDS[i % len(MOVIE_WORDS)]} {MOVIE_WORDS[(i * 7 + 3) % len(MOVIE_WORDS)]}"
    movies.append({"num": i + 1, "name": name, "stream_type": "movie", "stream_id": mid, "stream_icon": f"{BASE}/img/poster/m{mid}.jpg" if i % 9 else "",
                   "rating": str(5 + (i % 5)), "added": str(NOW - i * 3600 * 20), "category_id": movie_cats[i % len(movie_cats)][0],
                   "container_extension": "mp4", "custom_sid": "", "direct_source": ""})
channels = []
for i, name in enumerate(CHANNELS):
    cid = 5000 + i
    channels.append({"num": i + 1, "name": name, "stream_type": "live", "stream_id": cid, "stream_icon": f"{BASE}/img/logo/c{cid}.png" if i % 4 else None,
                     "epg_channel_id": f"ch{i + 1}.lumen" if i != 17 else "", "added": "0", "category_id": live_cats[i % len(live_cats)][0],
                     "tv_archive": 0, "tv_archive_duration": 0})

def episodes_for(sid):
    out = {}
    seasons = 2 + sid % 2
    for s in range(1, seasons + 1):
        out[str(s)] = [{"id": f"{sid}{s:02d}{e:02d}", "episode_num": str(e), "title": f"{SERIES[sid - 100]} - S{s:02d}E{e:02d} - {EPS[(e + s) % len(EPS)]}",
                        "container_extension": "mp4", "season": s,
                        "info": {"duration_secs": 360, "plot": "An episode.", "movie_image": f"{BASE}/img/still/e{sid}{s:02d}{e:02d}.jpg"}}
                       for e in range(1, 9)]
    return out

def programmes():
    out = []
    for i in range(len(CHANNELS)):
        if i == 17: continue
        t = NOW - 6 * 3600
        t -= t % 900
        k = 0
        while t < NOW + 2 * 86400:
            d = [30, 45, 60, 90, 25][(i + k) % 5] * 60
            out.append((f"ch{i + 1}.lumen", t, t + d, f"{['News', 'Football: Live', 'Coldwater', 'World Report', 'Kitchen Stories', 'Night Talk', 'Weather', 'Doku: Alpen'][(i + k) % 8]}", f"Programme {k} on {CHANNELS[i]}."))
            t += d; k += 1
    return out

def xmltv():
    f = lambda t: time.strftime("%Y%m%d%H%M%S +0000", time.gmtime(t))
    esc = lambda s: s.replace("&", "&amp;").replace("<", "&lt;")
    parts = ['<?xml version="1.0" encoding="UTF-8"?>\n<tv generator-info-name="fake">']
    for i, n in enumerate(CHANNELS):
        parts.append(f'<channel id="ch{i + 1}.lumen"><display-name>{esc(n)}</display-name></channel>')
    parts.append('<channel id="other.channel"><display-name>Not in account</display-name></channel>')
    for (c, a, b, t, d) in programmes():
        parts.append(f'<programme start="{f(a)}" stop="{f(b)}" channel="{c}"><title lang="de">{esc(t)}</title><desc>{esc(d)}</desc></programme>')
    parts.append('</tv>')
    return "\n".join(parts).encode()

_img = {}
def image(kind, key):
    k = (kind, key)
    if k in _img: return _img[k]
    h = int(hashlib.md5(key.encode()).hexdigest()[:6], 16)
    size = {"poster": (260, 390), "backdrop": (1280, 720), "still": (480, 270), "logo": (160, 60)}[kind]
    w, ht = size
    img = Image.new("RGB", size, ((h >> 16) % 120, (h >> 8) % 90, h % 140))
    layer = Image.new("RGBA", size, (0, 0, 0, 0)); d = ImageDraw.Draw(layer)
    for j in range(3):
        r = int(max(w, ht) * (0.3 + j * 0.15)); cx = (h >> (j * 4)) % w; cy = (h >> (j * 3)) % ht
        d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=((h * (j + 3)) % 255, (h * (j + 5)) % 255, (h * (j + 7)) % 255, 160))
    layer = layer.filter(ImageFilter.GaussianBlur(max(w, ht) * 0.12)); img.paste(layer, (0, 0), layer)
    d = ImageDraw.Draw(img)
    label = key
    if kind == "poster":
        idx = int(key[1:])
        label = SERIES[idx - 100] if key[0] == "s" else movies[idx - 1000]["name"]
        d.text((16, ht - 70), label, font=ImageFont.truetype(FONT, 28), fill="white")
    elif kind == "logo":
        img = Image.new("RGBA", size, (0, 0, 0, 0)); d = ImageDraw.Draw(img)
        d.text((4, 10), CHANNELS[int(key[1:]) - 5000][:10], font=ImageFont.truetype(FONT, 30), fill="white")
    elif kind == "still":
        d.text((16, ht - 50), key, font=ImageFont.truetype(FONT, 22), fill="white")
    buf = io.BytesIO(); img.save(buf, "PNG" if kind == "logo" else "JPEG", quality=80)
    _img[k] = buf.getvalue(); return _img[k]

class H(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    def log_message(self, fmt, *a): sys.stderr.write("%s\n" % (fmt % a))

    def send_body(self, body, ctype, code=200):
        self.send_response(code); self.send_header("Content-Type", ctype); self.send_header("Content-Length", str(len(body))); self.end_headers()
        if self.command != "HEAD": self.wfile.write(body)

    def do_HEAD(self): self.do_GET()

    def do_GET(self):
        u = urlparse(self.path); q = {k: v[0] for k, v in parse_qs(u.query).items()}
        p = u.path
        if p == "/player_api.php":
            if q.get("username") != "demo" or q.get("password") != "demo":
                return self.send_body(json.dumps({"user_info": {"auth": 0}}).encode(), "application/json")
            a = q.get("action")
            if os.environ.get("SLOW"): time.sleep(float(os.environ["SLOW"]))
            data = {
                None: {"user_info": {"username": "demo", "auth": 1, "status": "Active", "exp_date": str(NOW + 200 * 86400), "max_connections": "1", "active_cons": "0"},
                       "server_info": {"url": HOST, "port": str(PORT), "timezone": "Europe/Berlin"}},
                "get_live_categories": [{"category_id": c, "category_name": n, "parent_id": 0} for c, n in live_cats],
                "get_vod_categories": [{"category_id": c, "category_name": n, "parent_id": 0} for c, n in movie_cats],
                "get_series_categories": [{"category_id": c, "category_name": n, "parent_id": 0} for c, n in series_cats],
                "get_live_streams": channels, "get_vod_streams": movies, "get_series": series,
            }.get(a)
            if a == "get_series_info":
                sid = int(q.get("series_id", 0)); s = next((x for x in series if x["series_id"] == sid), None)
                data = {"seasons": [], "info": s, "episodes": episodes_for(sid)} if s else {}
            elif a == "get_vod_info":
                mid = int(q.get("vod_id", 0)); m = next((x for x in movies if x["stream_id"] == mid), None)
                data = {"info": {"plot": f"{m['name']} is a fictional film for testing.", "cast": "Ada Lind", "director": "M. Keller", "genre": "Drama, Thriller",
                                 "releasedate": "2021-05-04", "duration_secs": "360", "backdrop_path": [f"{BASE}/img/backdrop/m{mid}.jpg"]},
                        "movie_data": {"stream_id": mid, "container_extension": "mp4"}} if m else {}
            elif a == "get_short_epg":
                import base64
                sid = int(q.get("stream_id", 0)); idx = sid - 5000
                rows = [r for r in programmes() if r[0] == f"ch{idx + 1}.lumen" and r[2] > time.time()][: int(q.get("limit", 4))]
                data = {"epg_listings": [{"title": base64.b64encode(r[3].encode()).decode(), "description": base64.b64encode(r[4].encode()).decode(),
                                          "start_timestamp": str(r[1]), "stop_timestamp": str(r[2])} for r in rows]}
            return self.send_body(json.dumps(data if data is not None else []).encode(), "application/json")
        if p == "/xmltv.php":
            return self.send_body(xmltv(), "application/xml")
        m = re.match(r"^/img/(poster|backdrop|still|logo)/(\w+)\.(jpg|png)$", p)
        if m:
            return self.send_body(image(m.group(1), m.group(2)), "image/png" if m.group(3) == "png" else "image/jpeg")
        m = re.match(r"^/(live|movie|series)/demo/demo/(\w+)\.(\w+)$", p)
        if m:
            f = os.path.join(HERE, "media", "live.ts" if m.group(1) == "live" else "vod.mp4")
            return self.send_file(f, "video/mp2t" if m.group(1) == "live" else "video/mp4")
        self.send_body(b"not found", "text/plain", 404)

    def send_file(self, f, ctype):
        size = os.path.getsize(f); start, end = 0, size - 1
        r = self.headers.get("Range")
        if r and r.startswith("bytes="):
            a, _, b = r[6:].partition("-"); start = int(a or 0); end = int(b) if b else size - 1
            self.send_response(206); self.send_header("Content-Range", f"bytes {start}-{end}/{size}")
        else:
            self.send_response(200)
        self.send_header("Content-Type", ctype); self.send_header("Accept-Ranges", "bytes"); self.send_header("Content-Length", str(end - start + 1)); self.end_headers()
        if self.command == "HEAD": return
        with open(f, "rb") as fh:
            fh.seek(start); left = end - start + 1
            try:
                while left > 0:
                    chunk = fh.read(min(65536, left))
                    if not chunk: break
                    self.wfile.write(chunk); left -= len(chunk)
            except (BrokenPipeError, ConnectionResetError):
                pass

print(f"Fake Xtream on :{PORT}, base {BASE}", flush=True)
ThreadingHTTPServer(("0.0.0.0", PORT), H).serve_forever()
