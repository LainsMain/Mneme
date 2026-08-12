#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
promo_dir="$repo_root/docs/promo"
screens_dir="$repo_root/docs/screenshots"
photos_dir="$repo_root/docs/demo/photos"
logo="$repo_root/android/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png"
work_dir=$(mktemp -d /tmp/mneme-promo.XXXXXX)
output="$promo_dir/mneme-promo-1080p.mp4"
poster="$promo_dir/mneme-promo-poster.png"

mkdir -p "$promo_dir"

text_block() {
  local output_path=$1 width=$2 size=$3 color=$4 font=$5 copy=$6
  magick -background none -fill "$color" -font "$font" -pointsize "$size" \
    -gravity northwest -interline-spacing 4 -size "${width}x" caption:"$copy" "$output_path"
}

background() {
  local photo=$1 output_path=$2 accent=$3
  magick "$photo" -auto-orient -resize '1920x1080^' -gravity center \
    -extent 1920x1080 -blur 0x16 -fill '#071014B8' -colorize 72 \
    -fill "$accent" -draw 'circle 1680,90 2110,90' \
    -fill '#09131999' -draw 'rectangle 0,0 1920,1080' "$output_path"
}

plain_background() {
  local output_path=$1 accent=$2
  magick -size 1920x1080 'gradient:#071015-#10252d' \
    -fill "$accent" -draw 'circle 1690,80 2150,80' \
    -fill '#ff7c5320' -draw 'circle 80,1040 500,1040' "$output_path"
}

phone() {
  local source=$1 output_path=$2 width=$3
  local raw="$work_dir/raw-$(basename "$output_path")"
  local masked="$work_dir/masked-$(basename "$output_path")"
  magick "$source" -resize "${width}x" -bordercolor '#121a20' -border 10 "$raw"
  local dimensions
  dimensions=$(identify -format '%wx%h' "$raw")
  local image_width=${dimensions%x*}
  local image_height=${dimensions#*x}
  magick -size "$dimensions" xc:none -fill white \
    -draw "roundrectangle 0,0 $((image_width - 1)),$((image_height - 1)) 38,38" \
    "$work_dir/mask-$(basename "$output_path")"
  magick "$raw" "$work_dir/mask-$(basename "$output_path")" \
    -alpha off -compose CopyOpacity -composite "$masked"
  magick \( "$masked" -bordercolor none -border 34 \) \
    \( +clone -background '#000000B0' -shadow 65x22+0+22 \) \
    +swap -background none -layers merge +repage "$output_path"
}

place() {
  local canvas=$1 asset=$2 geometry=$3 output_path=$4
  magick "$canvas" "$asset" -gravity northwest -geometry "$geometry" -composite "$output_path"
}

# Shared phone cards.
phone "$screens_dir/journal.png" "$work_dir/phone-journal.png" 410
phone "$screens_dir/list.png" "$work_dir/phone-list.png" 335
phone "$screens_dir/month.png" "$work_dir/phone-month.png" 335
phone "$screens_dir/media.png" "$work_dir/phone-media.png" 410
phone "$screens_dir/map.png" "$work_dir/phone-map.png" 410
phone "$screens_dir/recap.png" "$work_dir/phone-recap.png" 330
phone "$screens_dir/settings.png" "$work_dir/phone-settings.png" 330

# Scene 1 — identity.
background "$photos_dir/twilight-sky.jpg" "$work_dir/scene-1.png" '#1ba9c944'
magick "$logo" -resize 230x230 "$work_dir/logo-large.png"
text_block "$work_dir/s1-title.png" 1200 122 '#F4F7F8' Inter-Bold 'Mneme'
text_block "$work_dir/s1-subtitle.png" 1200 48 '#D7E4E8' Inter-Regular 'A quieter place for your days.'
text_block "$work_dir/s1-meta.png" 1200 27 '#75D7EA' Inter-SemiBold 'PRIVATE  ·  NATIVE ANDROID  ·  YOURS'
place "$work_dir/scene-1.png" "$work_dir/logo-large.png" '+845+190' "$work_dir/scene-1a.png"
place "$work_dir/scene-1a.png" "$work_dir/s1-title.png" '+710+460' "$work_dir/scene-1b.png"
place "$work_dir/scene-1b.png" "$work_dir/s1-subtitle.png" '+575+625' "$work_dir/scene-1c.png"
place "$work_dir/scene-1c.png" "$work_dir/s1-meta.png" '+690+735' "$work_dir/scene-1-final.png"

# Scene 2 — writing.
background "$photos_dir/sunset-picnic.jpg" "$work_dir/scene-2.png" '#ff765044'
text_block "$work_dir/eyebrow.png" 690 26 '#74D9EC' Inter-SemiBold 'YOUR JOURNAL'
text_block "$work_dir/s2-title.png" 760 82 '#F5F7F8' Inter-Bold $'Write the day\nas it felt.'
text_block "$work_dir/s2-body.png" 690 34 '#C9D6DA' Inter-Regular $'Rich text, original photos, preserved metadata, and a place when it matters.'
text_block "$work_dir/s2-detail.png" 690 28 '#FFB08E' Inter-Medium 'Nothing gets in the way of the memory.'
place "$work_dir/scene-2.png" "$work_dir/eyebrow.png" '+165+230' "$work_dir/scene-2a.png"
place "$work_dir/scene-2a.png" "$work_dir/s2-title.png" '+155+300' "$work_dir/scene-2b.png"
place "$work_dir/scene-2b.png" "$work_dir/s2-body.png" '+160+525' "$work_dir/scene-2c.png"
place "$work_dir/scene-2c.png" "$work_dir/s2-detail.png" '+160+740' "$work_dir/scene-2d.png"
place "$work_dir/scene-2d.png" "$work_dir/phone-journal.png" '+1260+55' "$work_dir/scene-2-final.png"

# Scene 3 — chronology.
plain_background "$work_dir/scene-3.png" '#1ba9c938'
text_block "$work_dir/s3-kicker.png" 650 26 '#74D9EC' Inter-SemiBold 'EVERY DAY, CLOSE AT HAND'
text_block "$work_dir/s3-title.png" 690 78 '#F5F7F8' Inter-Bold $'Scroll the month.\nFind the moment.'
text_block "$work_dir/s3-body.png" 650 33 '#C9D6DA' Inter-Regular $'Move naturally between a visual calendar and a calm chronological list.'
place "$work_dir/scene-3.png" "$work_dir/s3-kicker.png" '+150+240' "$work_dir/scene-3a.png"
place "$work_dir/scene-3a.png" "$work_dir/s3-title.png" '+145+310' "$work_dir/scene-3b.png"
place "$work_dir/scene-3b.png" "$work_dir/s3-body.png" '+150+555' "$work_dir/scene-3c.png"
magick "$work_dir/phone-list.png" -background none -rotate -3 "$work_dir/phone-list-tilt.png"
magick "$work_dir/phone-month.png" -background none -rotate 3 "$work_dir/phone-month-tilt.png"
place "$work_dir/scene-3c.png" "$work_dir/phone-list-tilt.png" '+1080+135' "$work_dir/scene-3d.png"
place "$work_dir/scene-3d.png" "$work_dir/phone-month-tilt.png" '+1390+95' "$work_dir/scene-3-final.png"

# Scene 4 — media.
background "$photos_dir/market-flowers.jpg" "$work_dir/scene-4.png" '#ff76503d'
text_block "$work_dir/s4-kicker.png" 680 26 '#74D9EC' Inter-SemiBold 'YOUR PHOTO STORY'
text_block "$work_dir/s4-title.png" 740 82 '#F5F7F8' Inter-Bold $'See your life\ncome together.'
text_block "$work_dir/s4-body.png" 680 34 '#C9D6DA' Inter-Regular $'A continuous, date-aware gallery keeps every photo connected to its day.'
place "$work_dir/scene-4.png" "$work_dir/phone-media.png" '+200+55' "$work_dir/scene-4a.png"
place "$work_dir/scene-4a.png" "$work_dir/s4-kicker.png" '+1045+265' "$work_dir/scene-4b.png"
place "$work_dir/scene-4b.png" "$work_dir/s4-title.png" '+1035+330' "$work_dir/scene-4c.png"
place "$work_dir/scene-4c.png" "$work_dir/s4-body.png" '+1040+575' "$work_dir/scene-4-final.png"

# Scene 5 — map.
background "$photos_dir/canal-bicycle.jpg" "$work_dir/scene-5.png" '#1ba9c940'
text_block "$work_dir/s5-kicker.png" 710 26 '#74D9EC' Inter-SemiBold 'PLACES, NOT TRACKING'
text_block "$work_dir/s5-title.png" 760 82 '#F5F7F8' Inter-Bold $'Remember where\nit happened.'
text_block "$work_dir/s5-body.png" 700 34 '#C9D6DA' Inter-Regular $'Photo locations can fill the map automatically—and every entry stays editable.'
text_block "$work_dir/s5-detail.png" 690 28 '#FFB08E' Inter-Medium 'No location account required.'
place "$work_dir/scene-5.png" "$work_dir/s5-kicker.png" '+160+245' "$work_dir/scene-5a.png"
place "$work_dir/scene-5a.png" "$work_dir/s5-title.png" '+150+310' "$work_dir/scene-5b.png"
place "$work_dir/scene-5b.png" "$work_dir/s5-body.png" '+155+555' "$work_dir/scene-5c.png"
place "$work_dir/scene-5c.png" "$work_dir/s5-detail.png" '+155+745' "$work_dir/scene-5d.png"
place "$work_dir/scene-5d.png" "$work_dir/phone-map.png" '+1270+55' "$work_dir/scene-5-final.png"

# Scene 6 — reflection and personalisation.
plain_background "$work_dir/scene-6.png" '#a16ed43b'
text_block "$work_dir/s6-kicker.png" 900 26 '#9AE1EF' Inter-SemiBold 'REFLECT · PERSONALISE · PROTECT'
text_block "$work_dir/s6-title.png" 920 72 '#F5F7F8' Inter-Bold 'Make it feel like yours.'
text_block "$work_dir/s6-body.png" 850 31 '#C9D6DA' Inter-Regular 'Monthly recaps, expressive themes, PIN protection, and biometrics.'
place "$work_dir/scene-6.png" "$work_dir/s6-kicker.png" '+525+105' "$work_dir/scene-6a.png"
place "$work_dir/scene-6a.png" "$work_dir/s6-title.png" '+500+165' "$work_dir/scene-6b.png"
place "$work_dir/scene-6b.png" "$work_dir/s6-body.png" '+535+275' "$work_dir/scene-6c.png"
magick "$work_dir/phone-recap.png" -background none -rotate -2 "$work_dir/phone-recap-tilt.png"
magick "$work_dir/phone-settings.png" -background none -rotate 2 "$work_dir/phone-settings-tilt.png"
place "$work_dir/scene-6c.png" "$work_dir/phone-recap-tilt.png" '+485+335' "$work_dir/scene-6d.png"
place "$work_dir/scene-6d.png" "$work_dir/phone-settings-tilt.png" '+1050+325' "$work_dir/scene-6-final.png"

# Scene 7 — private backup.
plain_background "$work_dir/scene-7.png" '#1ba9c930'
text_block "$work_dir/s7-kicker.png" 900 26 '#74D9EC' Inter-SemiBold 'SELF-HOSTED BACKUP'
text_block "$work_dir/s7-title.png" 1000 76 '#F5F7F8' Inter-Bold 'Your memories. Still yours.'
text_block "$work_dir/s7-body.png" 980 32 '#C9D6DA' Inter-Regular $'Mneme encrypts your diary on the phone before it reaches the server you control.'
text_block "$work_dir/s7-foot.png" 1100 27 '#9AE1EF' Inter-Medium 'Encrypted on device   ·   Automatic backup   ·   Recovery code restore'
magick "$logo" -resize 150x150 "$work_dir/logo-backup.png"
magick -size 1080x250 xc:none \
  -stroke '#4FC6DA' -strokewidth 5 -fill none \
  -draw 'line 210,125 470,125 line 610,125 870,125' \
  -fill '#102A33' -stroke '#4FC6DA' -draw 'roundrectangle 440,55 640,195 38,38' \
  -fill '#4FC6DA' -stroke none -draw 'circle 540,100 565,100' \
  -fill '#071015' -draw 'roundrectangle 515,110 565,165 12,12' \
  -fill '#13232A' -stroke '#FF8D67' -strokewidth 4 -draw 'roundrectangle 790,40 1010,210 30,30' \
  -fill '#FF8D67' -stroke none -draw 'rectangle 835,85 965,102 rectangle 835,122 965,139 rectangle 835,159 925,176' \
  "$work_dir/backup-flow.png"
place "$work_dir/backup-flow.png" "$work_dir/logo-backup.png" '+15+50' "$work_dir/backup-flow-final.png"
place "$work_dir/scene-7.png" "$work_dir/s7-kicker.png" '+155+130' "$work_dir/scene-7a.png"
place "$work_dir/scene-7a.png" "$work_dir/s7-title.png" '+145+195' "$work_dir/scene-7b.png"
place "$work_dir/scene-7b.png" "$work_dir/s7-body.png" '+150+320' "$work_dir/scene-7c.png"
place "$work_dir/scene-7c.png" "$work_dir/backup-flow-final.png" '+420+500' "$work_dir/scene-7d.png"
place "$work_dir/scene-7d.png" "$work_dir/s7-foot.png" '+405+830' "$work_dir/scene-7-final.png"

# Scene 8 — close.
background "$photos_dir/rainy-evening.jpg" "$work_dir/scene-8.png" '#ff765038'
magick "$logo" -resize 190x190 "$work_dir/logo-end.png"
text_block "$work_dir/s8-title.png" 1320 78 '#F5F7F8' Inter-Bold $'Keep the days you\ndon’t want to lose.'
text_block "$work_dir/s8-subtitle.png" 1200 34 '#D2DEE1' Inter-Regular 'Mneme · Open-source diary for Android'
text_block "$work_dir/s8-link.png" 1200 27 '#7DDAEB' Inter-SemiBold 'github.com/LainsMain/Mneme'
place "$work_dir/scene-8.png" "$work_dir/logo-end.png" '+865+160' "$work_dir/scene-8a.png"
place "$work_dir/scene-8a.png" "$work_dir/s8-title.png" '+440+395' "$work_dir/scene-8b.png"
place "$work_dir/scene-8b.png" "$work_dir/s8-subtitle.png" '+590+635' "$work_dir/scene-8c.png"
place "$work_dir/scene-8c.png" "$work_dir/s8-link.png" '+685+720' "$work_dir/scene-8-final.png"

# A soft original ambient bed: an A-minor pad, quiet enough for later voice-over.
ffmpeg -hide_banner -loglevel error -y \
  -f lavfi -i 'sine=frequency=110:duration=39.1:sample_rate=48000' \
  -f lavfi -i 'sine=frequency=130.81:duration=39.1:sample_rate=48000' \
  -f lavfi -i 'sine=frequency=164.81:duration=39.1:sample_rate=48000' \
  -f lavfi -i 'sine=frequency=329.63:duration=39.1:sample_rate=48000' \
  -f lavfi -i 'anoisesrc=color=pink:amplitude=0.002:duration=39.1:sample_rate=48000' \
  -filter_complex \
  '[0:a]volume=0.020,tremolo=f=0.11:d=0.30[a0];[1:a]volume=0.014,tremolo=f=0.13:d=0.28[a1];[2:a]volume=0.012,tremolo=f=0.10:d=0.25[a2];[3:a]volume=0.004,tremolo=f=0.17:d=0.55[a3];[4:a]lowpass=f=900,volume=0.18[a4];[a0][a1][a2][a3][a4]amix=inputs=5:normalize=0,lowpass=f=1600,highpass=f=70,volume=18,afade=t=in:st=0:d=2.5,afade=t=out:st=35.6:d=3.5[a]' \
  -map '[a]' -c:a pcm_s16le "$work_dir/ambient.wav"

# Gentle Ken Burns movement with restrained dissolves.
ffmpeg -hide_banner -loglevel error -y \
  -loop 1 -t 5.5 -i "$work_dir/scene-1-final.png" \
  -loop 1 -t 5.5 -i "$work_dir/scene-2-final.png" \
  -loop 1 -t 5.5 -i "$work_dir/scene-3-final.png" \
  -loop 1 -t 5.5 -i "$work_dir/scene-4-final.png" \
  -loop 1 -t 5.5 -i "$work_dir/scene-5-final.png" \
  -loop 1 -t 5.5 -i "$work_dir/scene-6-final.png" \
  -loop 1 -t 5.5 -i "$work_dir/scene-7-final.png" \
  -loop 1 -t 5.5 -i "$work_dir/scene-8-final.png" \
  -i "$work_dir/ambient.wav" \
  -filter_complex \
  '[0:v]zoompan=z=min(zoom+0.00022\,1.035):x=iw/2-iw/zoom/2:y=ih/2-ih/zoom/2:d=165:s=1920x1080:fps=30,setsar=1[v0];[1:v]zoompan=z=min(zoom+0.00016\,1.028):x=iw/2-iw/zoom/2:y=ih/2-ih/zoom/2:d=165:s=1920x1080:fps=30,setsar=1[v1];[2:v]zoompan=z=min(zoom+0.00018\,1.030):x=iw/2-iw/zoom/2:y=ih/2-ih/zoom/2:d=165:s=1920x1080:fps=30,setsar=1[v2];[3:v]zoompan=z=min(zoom+0.00016\,1.028):x=iw/2-iw/zoom/2:y=ih/2-ih/zoom/2:d=165:s=1920x1080:fps=30,setsar=1[v3];[4:v]zoompan=z=min(zoom+0.00018\,1.030):x=iw/2-iw/zoom/2:y=ih/2-ih/zoom/2:d=165:s=1920x1080:fps=30,setsar=1[v4];[5:v]zoompan=z=min(zoom+0.00016\,1.028):x=iw/2-iw/zoom/2:y=ih/2-ih/zoom/2:d=165:s=1920x1080:fps=30,setsar=1[v5];[6:v]zoompan=z=min(zoom+0.00018\,1.030):x=iw/2-iw/zoom/2:y=ih/2-ih/zoom/2:d=165:s=1920x1080:fps=30,setsar=1[v6];[7:v]zoompan=z=min(zoom+0.00022\,1.035):x=iw/2-iw/zoom/2:y=ih/2-ih/zoom/2:d=165:s=1920x1080:fps=30,setsar=1[v7];[v0][v1]xfade=transition=fade:duration=0.7:offset=4.8[x1];[x1][v2]xfade=transition=fade:duration=0.7:offset=9.6[x2];[x2][v3]xfade=transition=fade:duration=0.7:offset=14.4[x3];[x3][v4]xfade=transition=fade:duration=0.7:offset=19.2[x4];[x4][v5]xfade=transition=fade:duration=0.7:offset=24.0[x5];[x5][v6]xfade=transition=fade:duration=0.7:offset=28.8[x6];[x6][v7]xfade=transition=fade:duration=0.7:offset=33.6,fade=t=out:st=38.1:d=1,format=yuv420p[v]' \
  -map '[v]' -map 8:a -t 39.1 -r 30 \
  -c:v libx264 -preset slow -crf 18 -profile:v high -level 4.1 \
  -c:a aac -b:a 192k -ac 2 -movflags +faststart "$output"

cp "$work_dir/scene-1-final.png" "$poster"

printf 'Rendered %s\nPoster %s\nWorking frames %s\n' "$output" "$poster" "$work_dir"
