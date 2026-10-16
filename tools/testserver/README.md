# Fake Xtream server (testing only)

Serves a generated catalogue (series, movies, live channels, guide) so the app can be
tested on the emulator without a real subscription. Login: `demo` / `demo`.

```
pip install pillow
python make_media.py            # once, needs ffmpeg
python fakextream.py 8089 10.0.2.2
```

In the emulator, sign in with server `http://10.0.2.2:8089`.
