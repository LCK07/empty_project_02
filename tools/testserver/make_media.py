"""Generates the test videos the fake server streams (needs ffmpeg on PATH). usage: python make_media.py"""
import os, subprocess
out = os.path.join(os.path.dirname(os.path.abspath(__file__)), "media")
os.makedirs(out, exist_ok=True)
ff = ["ffmpeg", "-hide_banner", "-loglevel", "error", "-y"]
# VOD: 10 min, 720p H.264, English AAC stereo + German AC3 5.1, English subtitles.
srt = os.path.join(out, "sub.srt")
with open(srt, "w", encoding="utf-8") as f:
    for i in range(60):
        f.write(f"{i+1}\n00:{i*10//60:02d}:{i*10%60:02d},000 --> 00:{i*10//60:02d}:{i*10%60+5:02d},000\nSubtitle line {i+1}\n\n")
subprocess.run(ff + ["-f", "lavfi", "-i", "testsrc2=size=1280x720:rate=25:duration=600",
                     "-f", "lavfi", "-i", "sine=frequency=440:duration=600", "-f", "lavfi", "-i", "sine=frequency=660:duration=600",
                     "-i", srt, "-map", "0:v", "-map", "1:a", "-map", "2:a", "-map", "3:s",
                     "-c:v", "libx264", "-preset", "veryfast", "-g", "50", "-pix_fmt", "yuv420p",
                     "-c:a:0", "aac", "-ac:a:0", "2", "-c:a:1", "ac3", "-ac:a:1", "6", "-c:s", "mov_text",
                     "-metadata:s:a:0", "language=eng", "-metadata:s:a:1", "language=deu", "-metadata:s:s:0", "language=eng",
                     "-movflags", "+faststart", os.path.join(out, "vod.mp4")], check=True)
# Live: 6 min MPEG-TS, 540p H.264 + AAC.
subprocess.run(ff + ["-f", "lavfi", "-i", "testsrc=size=960x540:rate=25:duration=360", "-f", "lavfi", "-i", "sine=frequency=300:duration=360",
                     "-c:v", "libx264", "-preset", "veryfast", "-g", "50", "-pix_fmt", "yuv420p", "-c:a", "aac", "-f", "mpegts",
                     os.path.join(out, "live.ts")], check=True)
print("media ok")
