"""Makes natural-sounding voice clips for Little Steps with Microsoft Edge's free neural voices.

  pip install edge-tts
  python make_voices.py            (run from this folder; writes ../web/voice and, if found, the Android assets copy)

Then open index.html again. The game plays these clips automatically.
Parent recordings (Parent settings > Your voice) still win over these clips.
"""
import asyncio, json, os, re, shutil, sys
import edge_tts

EN = [
    "Great job!", "You did it!", "Well done!", "Super work!", "Awesome!", "Nice work!",
    "Good try! Let's look again.", "That's okay. Try again.", "Almost! You can do it.", "Take your time. You can do it.",
    "Look where the hand points.", "That's okay. Look where the hand points.", "Keep going, you are doing great.",
    "You can take a break if you want.", "Break time! Pick something calm to do.",
    "Let's play!", "Ready?", "Here we go!",
    "Hello!", "Hello! Can you hear me? Let us play!",
    "Good morning!", "Good afternoon!", "Good evening!",
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
    "Lihat yang ditunjuk tangan.", "Tidak apa-apa. Lihat yang ditunjuk tangan.", "Terus ya, kamu hebat.",
    "Kamu boleh istirahat kalau mau.", "Waktu istirahat! Pilih kegiatan yang tenang.",
    "Ayo main!", "Siap?", "Ayo kita mulai!",
    "Halo!", "Halo! Apakah kamu mendengar aku? Ayo main!",
    "Selamat pagi!", "Selamat siang!", "Selamat sore!", "Selamat malam!",
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


def extract_game_lines():
    """Extract dynamic-template game instructions from ../web/index.html so they use the same girl voice."""
    html_path = os.path.join(here, "..", "web", "index.html")
    if not os.path.exists(html_path):
        return [], []
    html = open(html_path, encoding="utf-8").read()
    en, id_ = list(EN), list(ID)

    # Items in THEMES: ['emoji','en','id']
    themes_m = re.search(r"const THEMES=\[(.*?)\];", html, re.S)
    if themes_m:
        for _, w_en, w_id in re.findall(r"\['([^']+)','([^']+)','([^']+)'\]", themes_m.group(1)):
            en.append(f"Find the {w_en}.")
            id_.append(f"Cari {w_id}.")

    # Feelings: ['emoji','en','id']
    feel_m = re.search(r"const FEELINGS=\[(.*?)\];", html, re.S)
    if feel_m:
        for _, f_en, f_id in re.findall(r"\['([^']+)','([^']+)','([^']+)'\]", feel_m.group(1)):
            en.append(f"Tap the {f_en} face.")
            id_.append(f"Ketuk wajah {f_id}.")

    # META task names
    meta_m = re.search(r"const META=\{(.*?)\};", html, re.S)
    if meta_m:
        for t_en, t_id in re.findall(r"t:'([^']+)',id:'([^']+)'", meta_m.group(1)):
            en.extend([f"Here is the plan. First: {t_en}.", f"Welcome back! Next: {t_en}.", f"Break is done. Next: {t_en}."])
            id_.extend([f"Ini rencananya. Pertama: {t_id}.", f"Selamat datang kembali! Selanjutnya: {t_id}.", f"Istirahat selesai. Selanjutnya: {t_id}."])

    # ROUTINES
    rout_m = re.search(r"const ROUTINES=\[(.*?)\];", html, re.S)
    if rout_m:
        for r_en, r_id in re.findall(r"name:\['([^']+)','([^']+)'\]", rout_m.group(1)):
            en.append(f"{r_en}. Tap the steps in order. What comes first?")
            id_.append(f"{r_id}. Ketuk langkah-langkahnya berurutan. Apa yang pertama?")

    # SORTS
    sort_m = re.search(r"const SORTS=\[(.*?)\];", html, re.S)
    if sort_m:
        for a_en, a_id, b_en, b_id in re.findall(
            r"a:\['([^']+)','[^']+',\[[^\]]+\],'([^']+)'\],b:\['([^']+)','[^']+',\[[^\]]+\],'([^']+)'\]",
            sort_m.group(1),
        ):
            en.append(f"Put each picture in the right basket. {a_en}, or {b_en}?")
            id_.append(f"Masukkan tiap gambar ke keranjang yang benar. {a_id}, atau {b_id}?")

    return list(dict.fromkeys(en)), list(dict.fromkeys(id_))
roots = [os.path.join(here, "..")]
android = os.path.join(here, "..", "android", "app", "src", "main", "assets")
if os.path.isdir(android):
    roots.append(android)

async def main():
    en_lines, id_lines = extract_game_lines()
    clips = {}
    out = os.path.join(here, "..", "web", "voice")
    os.makedirs(out, exist_ok=True)
    sem = asyncio.Semaphore(8)

    async def gen(lang, i, text):
        name = f"{lang}_{i:03d}.mp3"
        async with sem:
            tts = edge_tts.Communicate(text, VOICES[lang], rate="-5%", pitch="+2Hz", volume="+0%")
            await tts.save(os.path.join(out, name))
        clips[f"{lang}|{text}"] = f"voice/{name}"

    tasks = []
    for lang, lines in (("en", en_lines), ("id", id_lines)):
        if lang not in ONLY:
            continue
        for i, text in enumerate(lines):
            tasks.append(gen(lang, i, text))
    await asyncio.gather(*tasks)
    print(f"Generated {len(tasks)} girl-voice clips.")
    with open(os.path.join(out, "clips.js"), "w", encoding="utf-8") as f:
        f.write("window.BUNDLED=" + json.dumps(clips, ensure_ascii=False, indent=1) + ";\n")
    for r in roots[1:]:
        dst = os.path.join(r, "voice")
        shutil.rmtree(dst, ignore_errors=True)
        shutil.copytree(out, dst)
        print("copied to", dst)
    print("Done. Reopen the game.")

asyncio.run(main())
