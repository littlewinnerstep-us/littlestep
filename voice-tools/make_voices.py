"""Makes natural-sounding voice clips for Little Steps with Microsoft Edge's free neural voices.

  pip install edge-tts
  python make_voices.py            (run from this folder; writes ../web/voice and, if found, the Android assets copy)

Then open index.html again. The game plays these clips automatically.
Parent recordings (Parent settings > Your voice) still win over these clips.
"""
import asyncio, json, os, shutil, sys
import edge_tts

EN = [
    "Great job!", "You did it!", "Well done!", "Super work!", "Awesome!", "Nice work!",
    "Good try! Let's look again.", "That's okay. Try again.", "Almost! You can do it.", "Take your time. You can do it.",
    "Look where the hand points.", "You can take a break if you want.", "Break time! Pick something calm to do.",
    "Let's play!", "Ready?", "Here we go!",
    "Hello!", "Hello! Can you hear me? Let us play!",
    "Look at the picture. Find the same one.",
    "Count both groups. How many altogether?",
    "Count them. How many do you see?",
    "Look at the pattern. What comes next?",
    "Look at the pattern. What is missing?",
    "Turn over two cards. Find the pairs that match.",
    "Let's calm our body. Breathe in when the circle grows. Breathe out when it gets small.",
    "Pop the bubbles.", "Breathe in", "Breathe out", "Great practice!",
    "Stretch up high", "Give yourself a big hug", "Shake your hands",
    "March in place", "Jump like a frog 3 times", "Push the wall hard",
    "Move slowly like a turtle", "Stand still like a tree", "Squeeze your hands, then let go",
]
ID = [
    "Kerja bagus!", "Kamu berhasil!", "Hebat!", "Luar biasa!", "Keren!", "Bagus sekali!",
    "Hampir! Ayo lihat lagi.", "Tidak apa-apa. Coba lagi ya.", "Sedikit lagi! Kamu pasti bisa.", "Pelan-pelan saja. Kamu pasti bisa.",
    "Lihat yang ditunjuk tangan.", "Kamu boleh istirahat kalau mau.", "Waktu istirahat! Pilih kegiatan yang tenang.",
    "Ayo main!", "Siap?", "Ayo kita mulai!",
    "Halo!", "Halo! Apakah kamu mendengar aku? Ayo main!",
    "Lihat gambarnya. Cari yang sama.",
    "Hitung kedua kelompok. Semuanya ada berapa?",
    "Hitung semuanya. Ada berapa?",
    "Lihat polanya. Apa selanjutnya?",
    "Lihat polanya. Apa yang hilang?",
    "Balik dua kartu. Cari pasangan yang cocok.",
    "Ayo tenangkan tubuh kita. Tarik napas saat lingkaran membesar. Buang napas saat lingkaran mengecil.",
    "Pecahkan gelembungnya.", "Tarik napas", "Buang napas", "Latihan yang bagus!",
    "Rentangkan tangan ke atas", "Peluk dirimu sendiri erat-erat", "Goyangkan tanganmu",
    "Berjalan di tempat", "Lompat seperti katak 3 kali", "Dorong dinding kuat-kuat",
    "Bergerak pelan seperti kura-kura", "Berdiri diam seperti pohon", "Genggam tanganmu erat, lalu lepaskan",
]
# Warm, soft voices. Change these to try others (run: edge-tts --list-voices)
VOICES = {"en": "en-US-AnaNeural", "id": "id-ID-GadisNeural"}
ONLY = sys.argv[1:] or ["en", "id"]          # e.g.  python make_voices.py en

here = os.path.dirname(os.path.abspath(__file__))
roots = [os.path.join(here, "..")]
android = os.path.join(here, "..", "android", "app", "src", "main", "assets")
if os.path.isdir(android):
    roots.append(android)

async def main():
    clips = {}
    out = os.path.join(here, "..", "web", "voice")
    os.makedirs(out, exist_ok=True)
    sem = asyncio.Semaphore(4)

    async def gen(lang, i, text):
        name = f"{lang}_{i:02d}.mp3"
        async with sem:
            tts = edge_tts.Communicate(text, VOICES[lang], rate="-5%", pitch="+2Hz", volume="+0%")
            await tts.save(os.path.join(out, name))
        clips[f"{lang}|{text}"] = f"voice/{name}"
        print("made", name, "-", text)

    tasks = []
    for lang, lines in (("en", EN), ("id", ID)):
        if lang not in ONLY:
            continue
        for i, text in enumerate(lines):
            tasks.append(gen(lang, i, text))
    await asyncio.gather(*tasks)
    with open(os.path.join(out, "clips.js"), "w", encoding="utf-8") as f:
        f.write("window.BUNDLED=" + json.dumps(clips, ensure_ascii=False, indent=1) + ";\n")
    for r in roots[1:]:
        dst = os.path.join(r, "voice")
        shutil.rmtree(dst, ignore_errors=True)
        shutil.copytree(out, dst)
        print("copied to", dst)
    print("Done. Reopen the game.")

asyncio.run(main())
