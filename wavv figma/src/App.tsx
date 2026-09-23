import { useState, useRef, useEffect, useCallback } from 'react'
import lyricsIcon from '@/imports/song-lyrics.png'
import airplayIcon from '@/imports/casting.png'
import listIcon from '@/imports/menu.png'
import soundOnIcon from '@/imports/sound-on-svgrepo-com.svg'
import playIconSrc from '@/imports/music-play-play-button-svgrepo-com.svg'
import pauseIconSrc from '@/imports/image-16.png'
import art0 from '@/imports/image-18.png'
import art1 from '@/imports/image-19.png'
import art2 from '@/imports/image-20.png'
import art3 from '@/imports/image-21.png'
import art4 from '@/imports/image-22.png'
import art5 from '@/imports/image-23.png'
import art6 from '@/imports/image-24.png'
import art7 from '@/imports/image-25.png'
import art8 from '@/imports/image-26.png'
import art9 from '@/imports/image-27.png'

const ARTS = [art0, art1, art2, art3, art4, art5, art6, art7, art8, art9]
const a = (i: number) => ARTS[i % ARTS.length]

const STATUS_GAP = 80

function formatTime(s: number) {
  const m = Math.floor(s / 60)
  const sec = Math.floor(s % 60)
  return `${m}:${sec.toString().padStart(2, '0')}`
}

// ─── Nav Icons ───────────────────────────────────────────────────────────────

const NAV_RED = '#FA2D48'

// Solid filled house — red when active, white when inactive
const NavHome = ({ active }: { active: boolean }) => (
  <svg width="24" height="23" viewBox="0 0 24 23" fill={active ? NAV_RED : 'rgba(255,255,255,0.88)'}>
    <path d="M12 1L1 9.5V22h7.5v-6.5h7V22H23V9.5L12 1z"/>
  </svg>
)

// Magnifier — stroke icon, red when active
const NavSearch = ({ active }: { active: boolean }) => (
  <svg width="24" height="24" viewBox="0 0 24 24" fill="none"
    stroke={active ? NAV_RED : 'rgba(255,255,255,0.88)'}
    strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
    <circle cx="10.5" cy="10.5" r="7"/>
    <path d="M16 16L22 22"/>
  </svg>
)

// Document with a music note — matches the Library icon in the reference
const NavLibrary = ({ active }: { active: boolean }) => {
  const c = active ? NAV_RED : 'rgba(255,255,255,0.88)'
  const noteFill = active ? 'rgba(255,255,255,0.92)' : 'rgba(10,10,10,0.6)'
  return (
    <svg width="22" height="24" viewBox="0 0 22 24" fill="none">
      {/* Page body */}
      <rect x="1" y="1" width="20" height="22" rx="3" fill={c}/>
      {/* Music note */}
      <path d="M13 6v8.5c-.5-.3-1.1-.5-1.8-.5C9.8 14 9 14.9 9 16s.8 2 2.2 2 2.3-.9 2.3-2V8.2l2.5-.7V6H13z"
        fill={noteFill}/>
    </svg>
  )
}

// Person silhouette — red when active
const NavYou = ({ active }: { active: boolean }) => (
  <svg width="24" height="24" viewBox="0 0 24 24" fill={active ? NAV_RED : 'rgba(255,255,255,0.88)'}>
    <circle cx="12" cy="7.5" r="4.5"/>
    <path d="M3.5 21c0-4.7 3.8-8.5 8.5-8.5s8.5 3.8 8.5 8.5H3.5z"/>
  </svg>
)

// ─── Playback Icons ───────────────────────────────────────────────────────────

const IconPlay = () => (
  <svg width="40" height="40" viewBox="0 0 32 32" fill="currentColor">
    <path d="M4.993 2.496C4.516 2.223 4 2.45 4 3v26c0 .55.516.777.993.504l22.826-13.008c.478-.273.446-.719-.031-.992L4.993 2.496z"/>
  </svg>
)
const IconPause = () => (
  <svg width="40" height="40" viewBox="0 0 32 32" fill="currentColor">
    <path d="M6 4c-.55 0-1 .45-1 1v22c0 .55.45 1 1 1h3c.55 0 1-.45 1-1V5c0-.55-.45-1-1-1H6z"/>
    <path d="M23 4c-.55 0-1 .45-1 1v22c0 .55.45 1 1 1h3c.55 0 1-.45 1-1V5c0-.55-.45-1-1-1h-3z"/>
  </svg>
)
const IconPrev = () => (
  <svg width="40" height="40" viewBox="0 0 640 640" fill="currentColor">
    <path d="M236.3 107.1C247.9 96 265 92.9 279.7 99.2C294.4 105.5 304 120 304 136L304 272.3L476.3 107.2C487.9 96 505 92.9 519.7 99.2C534.4 105.5 544 120 544 136L544 504C544 520 534.4 534.5 519.7 540.8C505 547.1 487.9 544 476.3 532.9L304 367.7L304 504C304 520 294.4 534.5 279.7 540.8C265 547.1 247.9 544 236.3 532.9L44.3 348.9C36.5 341.3 32 330.9 32 320C32 309.1 36.5 298.7 44.3 291.1L236.3 107.1z"/>
  </svg>
)
const IconNext = () => (
  <svg width="40" height="40" viewBox="0 0 640 640" fill="currentColor">
    <path d="M403.7 107.1C392.1 96 375 92.9 360.3 99.2C345.6 105.5 336 120 336 136L336 272.3L163.7 107.2C152.1 96 135 92.9 120.3 99.2C105.6 105.5 96 120 96 136L96 504C96 520 105.6 534.5 120.3 540.8C135 547.1 152.1 544 163.7 532.9L336 367.7L336 504C336 520 345.6 534.5 360.3 540.8C375 547.1 392.1 544 403.7 532.9L595.7 348.9C603.6 341.4 608 330.9 608 320C608 309.1 603.5 298.7 595.7 291.1L403.7 107.1z"/>
  </svg>
)
const IconStar = ({ filled }: { filled: boolean }) => (
  <svg width="30" height="30" viewBox="0 0 24 24" fill="currentColor">
    <circle cx="12" cy="12" r="12" fill="rgba(255,255,255,0.15)"/>
    <path d="M12 4.5l2.16 4.38L19 9.61l-3.5 3.41.83 4.81L12 15.58 7.67 17.83l.83-4.81L5 9.61l4.84-.73L12 4.5z"
      fill={filled ? 'white' : 'none'} stroke="white" strokeWidth="1.2" strokeLinejoin="round"/>
  </svg>
)
const IconMore = () => (
  <svg width="30" height="30" viewBox="0 0 24 24" fill="currentColor">
    <circle cx="12" cy="12" r="12" fill="rgba(255,255,255,0.15)"/>
    <circle cx="7.5" cy="12" r="1.5" fill="white"/>
    <circle cx="12" cy="12" r="1.5" fill="white"/>
    <circle cx="16.5" cy="12" r="1.5" fill="white"/>
  </svg>
)
const IconLyrics = () => (
  <img src={lyricsIcon} alt="Lyrics" style={{ width: 24, height: 24, objectFit: 'contain', filter: 'brightness(0) invert(1)', opacity: 0.8 }} />
)
const IconAirplay = () => (
  <img src={airplayIcon} alt="Airplay" style={{ width: 24, height: 24, objectFit: 'contain', filter: 'brightness(0) invert(1)', opacity: 0.8 }} />
)
const IconQueue = () => (
  <img src={listIcon} alt="Queue" style={{ width: 24, height: 24, objectFit: 'contain', filter: 'brightness(0) invert(1)', opacity: 0.8 }} />
)
// loop-off / loop-all / loop-1
const IconLoop = ({ mode }: { mode: 'off' | 'all' | 'one' }) => (
  <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <polyline points="17 1 21 5 17 9"/>
    <path d="M3 11V9a4 4 0 0 1 4-4h14"/>
    <polyline points="7 23 3 19 7 15"/>
    <path d="M21 13v2a4 4 0 0 1-4 4H3"/>
    {mode === 'one' && <text x="12" y="13.5" textAnchor="middle" fontSize="7" fontWeight="700" fill="currentColor" stroke="none" dy="0">1</text>}
  </svg>
)
const IconSound = () => (
  <img src={soundOnIcon} alt="Volume" style={{ width: 24, height: 24, objectFit: 'contain', filter: 'brightness(0) invert(1)', opacity: 0.8 }} />
)
const IconVolumeLow = () => (
  <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="rgba(255,255,255,0.7)" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M2 14.96V9.04C2 8.47 2.45 8 3 8h3.59c.27 0 .52-.11.71-.31L10.29 4.31C10.92 3.65 12 4.12 12 5.04v13.92c0 .93-1.09 1.39-1.72.68L7.29 16.31A1 1 0 0 0 6.58 16H3c-.55 0-1-.47-1-1.04z"/>
    <path d="M16 8.5c1.33 1.78 1.33 5.22 0 7"/>
  </svg>
)
const IconVolumeHigh = () => (
  <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="rgba(255,255,255,0.7)" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M2 14.96V9.04C2 8.47 2.45 8 3 8h3.59c.27 0 .52-.11.71-.31L10.29 4.31C10.92 3.65 12 4.12 12 5.04v13.92c0 .93-1.09 1.39-1.72.68L7.29 16.31A1 1 0 0 0 6.58 16H3c-.55 0-1-.47-1-1.04z"/>
    <path d="M16 8.5c1.33 1.78 1.33 5.22 0 7"/>
    <path d="M19 5c3.99 3.81 4.01 10.22 0 14"/>
  </svg>
)

// ─── Data ─────────────────────────────────────────────────────────────────────

const TRACKS = [
  { title: 'Neon Nights', artist: 'Synthwave Dreams', album: 'Midnight Drive', duration: 185, art: a(0) },
  { title: 'Red Swirl', artist: 'Abstract Paint', album: 'Visions', duration: 245, art: a(1) },
  { title: 'Aasa Kooda', artist: 'Sai Abhyankkar', album: 'Think Indie Tamil 2024', duration: 214, art: a(2) },
]

const RECENT = [
  { title: 'Midnight Drive', artist: 'Synthwave Dreams', art: a(0) },
  { title: 'Visions', artist: 'Abstract Paint', art: a(1) },
  { title: 'City Echoes', artist: 'Urban Sound', art: a(2) },
  { title: 'Drift', artist: 'Float Waves', art: a(3) },
  { title: 'Nova', artist: 'Astral Keys', art: a(4) },
]


const CATEGORIES = [
  { label: 'Hip-Hop', color: '#5B2D8E', art: a(0) },
  { label: 'Pop', color: '#D81B60', art: a(1) },
  { label: 'Electronic', color: '#1565C0', art: a(2) },
  { label: 'R&B', color: '#AD1457', art: a(3) },
  { label: 'Indie', color: '#C62828', art: a(4) },
  { label: 'Classical', color: '#2E7D32', art: a(5) },
  { label: 'Jazz', color: '#E65100', art: a(6) },
  { label: 'Tamil Indie', color: '#283593', art: a(7) },
  { label: 'Rock', color: '#BF360C', art: a(8) },
  { label: 'Chill', color: '#00695C', art: a(9) },
]

const PLAYLISTS = [
  { title: 'Liked Songs', count: '312 songs', art: a(0) },
  { title: 'Late Night Drive', count: '24 songs', art: a(1) },
  { title: 'Workout Mix', count: '41 songs', art: a(2) },
  { title: 'Chill Afternoons', count: '18 songs', art: a(3) },
  { title: 'Tamil Favourites', count: '56 songs', art: a(4) },
]

// ─── Page Components ──────────────────────────────────────────────────────────

const MADE_FOR_YOU = [
  {
    label: 'Daily Mix 1', sublabel: 'Mix', accent: '#c850c0',
    blobs: ['#7B1FA2', '#E91E63', '#FF4081', '#AD1457'],
    artists: 'Synthwave Dreams, Abstract Paint, Sai Abhyankkar and more',
    songs: [
      { title: 'Neon Nights', artist: 'Synthwave Dreams', art: a(0) },
      { title: 'Red Swirl', artist: 'Abstract Paint', art: a(1) },
      { title: 'Aasa Kooda', artist: 'Sai Abhyankkar', art: a(2) },
      { title: 'City Echoes', artist: 'Urban Sound', art: a(3) },
      { title: 'Drift', artist: 'Float Waves', art: a(4) },
      { title: 'Nova', artist: 'Astral Keys', art: a(5) },
      { title: 'Moonlight', artist: 'Jazz Collective', art: a(6) },
    ],
  },
  {
    label: 'Get Up!', sublabel: 'Mix', accent: '#FFB300',
    blobs: ['#E65100', '#FFA726', '#FFD54F', '#BF360C'], sphere: true,
    artists: 'Urban Sound, Float Waves, Astral Keys and more',
    songs: [
      { title: 'City Echoes', artist: 'Urban Sound', art: a(3) },
      { title: 'Drift', artist: 'Float Waves', art: a(4) },
      { title: 'Nova', artist: 'Astral Keys', art: a(5) },
      { title: 'Espresso', artist: 'Sabrina Carpenter', art: a(6) },
      { title: 'Neon Nights', artist: 'Synthwave Dreams', art: a(0) },
      { title: 'Not Like Us', artist: 'Kendrick Lamar', art: a(7) },
      { title: 'Red Swirl', artist: 'Abstract Paint', art: a(1) },
    ],
  },
  {
    label: 'Chill', sublabel: 'Mix', accent: '#00BCD4',
    blobs: ['#006064', '#00897B', '#26C6DA', '#004D40'],
    artists: 'Jazz Collective, Float Waves, Benson Boone and more',
    songs: [
      { title: 'Moonlight', artist: 'Jazz Collective', art: a(6) },
      { title: 'Drift', artist: 'Float Waves', art: a(4) },
      { title: 'Beautiful Things', artist: 'Benson Boone', art: a(8) },
      { title: 'Aasa Kooda', artist: 'Sai Abhyankkar', art: a(2) },
    ],
  },
  {
    label: 'Golden Hour', sublabel: 'Mix', accent: '#CE93D8',
    blobs: ['#4A148C', '#FFAB91', '#7B1FA2', '#311B92'], orbs: true,
    artists: 'Jazz Collective, Float Waves, Benson Boone and more',
    songs: [
      { title: 'Moonlight', artist: 'Jazz Collective', art: a(6) },
      { title: 'Drift', artist: 'Float Waves', art: a(4) },
      { title: 'Beautiful Things', artist: 'Benson Boone', art: a(8) },
      { title: 'Aasa Kooda', artist: 'Sai Abhyankkar', art: a(2) },
      { title: 'Nova', artist: 'Astral Keys', art: a(5) },
      { title: 'Red Swirl', artist: 'Abstract Paint', art: a(1) },
      { title: 'Neon Nights', artist: 'Synthwave Dreams', art: a(0) },
    ],
  },
  {
    label: 'Late Night', sublabel: 'Mix', accent: '#4DD0E1',
    blobs: ['#0277BD', '#00BCD4', '#26C6DA', '#01579B'], aurora: true,
    artists: 'Synthwave Dreams, Kendrick Lamar, Abstract Paint and more',
    songs: [
      { title: 'Neon Nights', artist: 'Synthwave Dreams', art: a(0) },
      { title: 'Not Like Us', artist: 'Kendrick Lamar', art: a(7) },
      { title: 'Red Swirl', artist: 'Abstract Paint', art: a(1) },
      { title: 'Nova', artist: 'Astral Keys', art: a(5) },
      { title: 'Moonlight', artist: 'Jazz Collective', art: a(6) },
      { title: 'City Echoes', artist: 'Urban Sound', art: a(3) },
      { title: 'Drift', artist: 'Float Waves', art: a(4) },
    ],
  },
  {
    label: 'Favourites', sublabel: 'Mix', accent: '#FF4081',
    blobs: ['#B71C1C', '#E91E63', '#FF4081', '#C62828'], glow: true,
    artists: 'Sabrina Carpenter, Chappell Roan, Sai Abhyankkar and more',
    songs: [
      { title: 'Espresso', artist: 'Sabrina Carpenter', art: a(6) },
      { title: 'Good Luck, Babe!', artist: 'Chappell Roan', art: a(9) },
      { title: 'Aasa Kooda', artist: 'Sai Abhyankkar', art: a(2) },
      { title: 'Neon Nights', artist: 'Synthwave Dreams', art: a(0) },
      { title: 'Beautiful Things', artist: 'Benson Boone', art: a(8) },
      { title: 'Not Like Us', artist: 'Kendrick Lamar', art: a(7) },
      { title: 'Nova', artist: 'Astral Keys', art: a(5) },
    ],
  },
]

const CAROUSEL_GAP = 12

// Flat list of songs for the song carousel
const CAROUSEL_SONGS = [
  { title: 'Espresso', artist: 'Sabrina Carpenter', album: 'Short n Sweet', art: a(0) },
  { title: 'Neon Nights', artist: 'Synthwave Dreams', album: 'Midnight Drive', art: a(1) },
  { title: 'Good Luck, Babe!', artist: 'Chappell Roan', album: 'The Rise and Fall', art: a(2) },
  { title: 'Aasa Kooda', artist: 'Sai Abhyankkar', album: 'Think Indie Tamil 2024', art: a(3) },
  { title: 'Not Like Us', artist: 'Kendrick Lamar', album: 'GNX', art: a(4) },
  { title: 'Beautiful Things', artist: 'Benson Boone', album: 'Fireworks & Rollerblades', art: a(5) },
  { title: 'Nova', artist: 'Astral Keys', album: 'Cosmos', art: a(6) },
  { title: 'Moonlight', artist: 'Jazz Collective', album: 'Blue Hour', art: a(7) },
]

function MadeForYouCarousel({ onOpenPlayer }: { onOpenPlayer: () => void }) {
  const COUNT = CAROUSEL_SONGS.length
  const items = [CAROUSEL_SONGS[COUNT - 1], ...CAROUSEL_SONGS, CAROUSEL_SONGS[0]]

  const [idx, setIdx] = useState(1)
  const [animated, setAnimated] = useState(true)
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null)
  const touchStartX = useRef(0)

  const resetTimer = useCallback(() => {
    if (timerRef.current) clearInterval(timerRef.current)
    timerRef.current = setInterval(() => setIdx(i => i + 1), 10000)
  }, [])

  useEffect(() => { resetTimer(); return () => { if (timerRef.current) clearInterval(timerRef.current) } }, [resetTimer])

  const onTransitionEnd = () => {
    if (idx === COUNT + 1) { setAnimated(false); setIdx(1) }
    else if (idx === 0)    { setAnimated(false); setIdx(COUNT) }
  }
  useEffect(() => {
    if (!animated) requestAnimationFrame(() => requestAnimationFrame(() => setAnimated(true)))
  }, [animated])

  const goTo = (i: number) => { setAnimated(true); setIdx(i + 1); resetTimer() }

  const onTouchStart = (e: React.TouchEvent) => { touchStartX.current = e.touches[0].clientX }
  const onTouchEnd = (e: React.TouchEvent) => {
    const dx = e.changedTouches[0].clientX - touchStartX.current
    if (Math.abs(dx) > 40) {
      const next = dx < 0 ? idx + 1 : idx - 1
      setAnimated(true); setIdx(next); resetTimer()
    }
  }

  const dotIdx = ((idx - 1) % COUNT + COUNT) % COUNT

  return (
    <div style={{ marginBottom: '24px' }}>
      <div onTouchStart={onTouchStart} onTouchEnd={onTouchEnd} style={{ overflow: 'hidden', paddingLeft: 20 }}>
        <div
          onTransitionEnd={onTransitionEnd}
          style={{
            display: 'flex', gap: CAROUSEL_GAP,
            transform: `translateX(calc(-${idx} * (100vw - 44px)))`,
            transition: animated ? 'transform 0.5s cubic-bezier(0.4, 0, 0.2, 1)' : 'none',
            willChange: 'transform',
          }}
        >
          {items.map((song, i) => {
            // queue = next 3 songs after this one (wrapping)
            const realIdx = i === 0 ? COUNT - 1 : i === COUNT + 1 ? 0 : i - 1
            const queue = [1, 2, 3].map(offset => CAROUSEL_SONGS[(realIdx + offset) % COUNT])
            return <SongCarouselCard key={i} song={song} queue={queue} onOpenPlayer={onOpenPlayer} />
          })}
        </div>
      </div>

      {/* Dot indicator */}
      <div style={{ display: 'flex', justifyContent: 'center', gap: '6px', marginTop: '14px' }}>
        {CAROUSEL_SONGS.map((_, i) => (
          <div key={i} onClick={() => goTo(i)} style={{
            height: 4, borderRadius: 2, cursor: 'pointer',
            width: i === dotIdx ? 20 : 4,
            background: i === dotIdx ? 'white' : 'rgba(255,255,255,0.25)',
            transition: 'width 0.35s ease, background 0.35s ease',
          }}/>
        ))}
      </div>
    </div>
  )
}

function SongCarouselCard({ song, queue, onOpenPlayer }: {
  song: typeof CAROUSEL_SONGS[0]
  queue: typeof CAROUSEL_SONGS
  onOpenPlayer: () => void
}) {
  const [playing, setPlaying] = useState(false)

  return (
    <div style={{
      flexShrink: 0,
      width: 'calc(100vw - 56px)',
      borderRadius: '20px', overflow: 'hidden',
      position: 'relative', isolation: 'isolate',
      height: 420,
      cursor: 'pointer',
    }} onClick={onOpenPlayer}>
      {/* Album art full background */}
      <img
        src={song.art}
        alt={song.title}
        style={{ position: 'absolute', inset: 0, width: '100%', height: '100%', objectFit: 'cover' }}
      />

      {/* Top-to-bottom gradient overlay: transparent at top → dark at bottom */}
      <div style={{
        position: 'absolute', inset: 0,
        background: 'linear-gradient(to bottom, rgba(0,0,0,0.04) 0%, rgba(0,0,0,0.15) 35%, rgba(0,0,0,0.7) 62%, rgba(0,0,0,0.92) 100%)',
      }}/>

      {/* Bottom content */}
      <div style={{
        position: 'absolute', bottom: 0, left: 0, right: 0,
        padding: '0 18px 20px',
        display: 'flex', flexDirection: 'column', gap: '14px',
      }}>
        {/* Up Next — inline, no box */}
        <div style={{ display: 'flex', alignItems: 'baseline', gap: '6px', overflow: 'hidden' }}>
          <span style={{ color: 'rgba(255,255,255,0.38)', fontSize: '11px', fontWeight: 700, letterSpacing: '0.6px', textTransform: 'uppercase', flexShrink: 0 }}>Up next</span>
          <span style={{
            color: 'rgba(255,255,255,0.55)', fontSize: '12px', fontWeight: 400,
            overflow: 'hidden', display: '-webkit-box',
            WebkitLineClamp: 2, WebkitBoxOrient: 'vertical' as const,
            lineHeight: 1.4,
          }}>
            {queue.map(q => q.title).join(' · ')}
          </span>
        </div>

        {/* Song info + play button */}
        <div style={{ display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between', gap: '12px' }}>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{ color: 'rgba(255,255,255,0.55)', fontSize: '11px', fontWeight: 600, letterSpacing: '0.5px', textTransform: 'uppercase', marginBottom: '4px' }}>{song.album}</div>
            <div style={{ color: 'white', fontSize: '24px', fontWeight: 800, letterSpacing: '-0.6px', lineHeight: 1.1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{song.title}</div>
            <div style={{ color: 'rgba(255,255,255,0.65)', fontSize: '15px', fontWeight: 400, marginTop: '3px' }}>{song.artist}</div>
          </div>

          {/* Play button — frosted glass oval */}
          <button
            onClick={e => { e.stopPropagation(); setPlaying(p => !p); onOpenPlayer() }}
            style={{
              height: 42, paddingLeft: 22, paddingRight: 22,
              borderRadius: '999px',
              background: 'rgba(255,255,255,0.18)',
              backdropFilter: 'blur(16px) saturate(1.8)',
              WebkitBackdropFilter: 'blur(16px) saturate(1.8)',
              border: '1px solid rgba(255,255,255,0.28)',
              display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '7px',
              cursor: 'pointer', flexShrink: 0,
              boxShadow: '0 2px 16px rgba(0,0,0,0.3)',
            }}
            aria-label={playing ? 'Pause' : 'Play'}
          >
            {playing
              ? <svg width="13" height="13" viewBox="0 0 24 24" fill="white"><rect x="5" y="4" width="4" height="16" rx="1.5"/><rect x="15" y="4" width="4" height="16" rx="1.5"/></svg>
              : <svg width="13" height="13" viewBox="0 0 24 24" fill="white"><path d="M5 3.5v17l15-8.5z"/></svg>
            }
            <span style={{ color: 'white', fontSize: '14px', fontWeight: 700, letterSpacing: '0.2px' }}>
              {playing ? 'Pause' : 'Play'}
            </span>
          </button>
        </div>
      </div>
    </div>
  )
}

// ─── Waveform animation ───────────────────────────────────────────────────────

// 8 bars: base heights encode the resting amplitude envelope (tallest in middle)
const WV_BASES   = [0.45, 0.65, 0.85, 1, 1, 0.85, 0.65, 0.45]
const WV_DELAYS  = [0.12, 0.28, 0.04, 0.2, 0.36, 0.08, 0.24, 0.16]
const WV_SPEEDS  = [0.72, 0.6, 0.8, 0.65, 0.75, 0.58, 0.7, 0.62]

// Linearly interpolate between two hex colors
function lerpHex(a: string, b: string, t: number): string {
  const parse = (h: string) => [
    parseInt(h.slice(1, 3), 16),
    parseInt(h.slice(3, 5), 16),
    parseInt(h.slice(5, 7), 16),
  ]
  const [ar, ag, ab] = parse(a)
  const [br, bg, bb] = parse(b)
  const r = Math.round(ar + (br - ar) * t)
  const g = Math.round(ag + (bg - ag) * t)
  const bl2 = Math.round(ab + (bb - ab) * t)
  return `rgb(${r},${g},${bl2})`
}

function Waveform({ playing, colors }: { playing: boolean; colors?: string[] }) {
  const safeColors = (colors && colors.length >= 2) ? colors : ['#7B1FA2', '#E91E63', '#FF4081', '#AD1457']
  // Map each bar index to a color interpolated across the blob colors
  const barColor = (i: number) => {
    const t = i / (WV_BASES.length - 1)
    const seg = t * (safeColors.length - 1)
    const lo = Math.floor(seg)
    const hi = Math.min(lo + 1, safeColors.length - 1)
    const frac = seg - lo
    const safe = (c: string) => (c && c.startsWith('#') && c.length >= 7 ? c : '#cc66ff')
    return lerpHex(safe(safeColors[lo]), safe(safeColors[hi]), frac)
  }

  const H = 18  // total bar container height px
  const W = 3   // bar width px
  const GAP = 2.5

  return (
    <>
      <style>{`
        @keyframes wvBar {
          0%, 100% { transform: scaleY(0.18); }
          50%       { transform: scaleY(1); }
        }
      `}</style>
      <div style={{
        display: 'flex', alignItems: 'center', gap: `${GAP}px`,
        height: `${H}px`,
        flexShrink: 0,
        opacity: playing ? 0.55 : 0.28,
        transition: 'opacity 0.6s ease',
      }}>
        {WV_BASES.map((base, i) => (
          <div
            key={i}
            style={{
              width: W,
              height: `${Math.round(base * H)}px`,
              borderRadius: '2px',
              background: barColor(i),
              transformOrigin: 'center',
              // Keep animation always running, just pause/resume it — bars freeze in place
              animation: `wvBar ${WV_SPEEDS[i]}s ease-in-out ${WV_DELAYS[i]}s infinite`,
              animationPlayState: playing ? 'running' : 'paused',
              transition: 'background 0.7s ease',
            }}
          />
        ))}
      </div>
    </>
  )
}

// ─── Shared horizontal scroll shelf ──────────────────────────────────────────

function HomeSection({ label, items, onOpenPlayer }: {
  label: string
  items: { title: string; artist?: string; art: string }[]
  onOpenPlayer: () => void
}) {
  return (
    <div style={{ paddingLeft: 20, position: 'relative', zIndex: 1 }}>
      <div style={{ color: 'rgba(255,255,255,0.5)', fontSize: '11px', fontWeight: 600, letterSpacing: '0.8px', textTransform: 'uppercase', marginBottom: '12px' }}>{label}</div>
      <div style={{ display: 'flex', gap: '12px', overflowX: 'auto', paddingRight: '20px' }} className="hide-scroll">
        {items.map((r, i) => (
          <div key={i} onClick={onOpenPlayer} style={{ flexShrink: 0, cursor: 'pointer', width: 110 }}>
            <div style={{ width: 110, height: 110, borderRadius: '12px', overflow: 'hidden', background: '#1a1a1a' }}>
              <img src={r.art} alt={r.title} style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
            </div>
            <div style={{ color: 'white', fontSize: '12px', fontWeight: 600, marginTop: '7px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{r.title}</div>
            {r.artist && <div style={{ color: 'rgba(255,255,255,0.4)', fontSize: '11px', marginTop: '2px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{r.artist}</div>}
          </div>
        ))}
      </div>
    </div>
  )
}

function HomeTileSection({ label, items, onOpenPlayer }: {
  label: string
  items: { name: string; color: string; art: string }[]
  onOpenPlayer: () => void
}) {
  return (
    <div style={{ paddingLeft: 20, position: 'relative', zIndex: 1 }}>
      <div style={{ color: 'rgba(255,255,255,0.5)', fontSize: '11px', fontWeight: 600, letterSpacing: '0.8px', textTransform: 'uppercase', marginBottom: '12px' }}>{label}</div>
      <div style={{ display: 'flex', gap: '10px', overflowX: 'auto', paddingRight: '20px' }} className="hide-scroll">
        {items.map((item, i) => (
          <div key={i} onClick={onOpenPlayer} style={{ flexShrink: 0, cursor: 'pointer', width: 140, height: 80, borderRadius: '12px', overflow: 'hidden', position: 'relative', background: item.color }}>
            <img src={item.art} alt={item.name} style={{ position: 'absolute', inset: 0, width: '100%', height: '100%', objectFit: 'cover', opacity: 0.45 }} />
            <div style={{ position: 'absolute', inset: 0, background: `linear-gradient(135deg, ${item.color}cc 0%, ${item.color}44 100%)` }} />
            <div style={{ position: 'absolute', bottom: 10, left: 12, color: 'white', fontSize: '13px', fontWeight: 700, letterSpacing: '-0.2px', textShadow: '0 1px 6px rgba(0,0,0,0.5)' }}>{item.name}</div>
          </div>
        ))}
      </div>
    </div>
  )
}

const FORGOTTEN = [
  { title: 'Midnight Drive', artist: 'Synthwave Dreams', art: a(8) },
  { title: 'Nova', artist: 'Astral Keys', art: a(5) },
  { title: 'Moonlight', artist: 'Jazz Collective', art: a(6) },
  { title: 'City Echoes', artist: 'Urban Sound', art: a(3) },
  { title: 'Drift', artist: 'Float Waves', art: a(4) },
]

const MOODS = [
  { name: 'Chill', color: '#1a4a6e', art: a(9) },
  { name: 'Focus', color: '#1a3a2e', art: a(0) },
  { name: 'Hype', color: '#6e1a2e', art: a(7) },
  { name: 'Sad', color: '#2a1a4e', art: a(3) },
  { name: 'Happy', color: '#6e4a1a', art: a(6) },
  { name: 'Late Night', color: '#1a1a4e', art: a(1) },
]

const HIP_HOP = [
  { title: 'Not Like Us', artist: 'Kendrick Lamar', art: a(7) },
  { title: 'Gods Plan', artist: 'Drake', art: a(3) },
  { title: 'HUMBLE.', artist: 'Kendrick Lamar', art: a(5) },
  { title: 'Sicko Mode', artist: 'Travis Scott', art: a(4) },
  { title: 'Mob Ties', artist: 'Drake', art: a(6) },
  { title: 'DNA.', artist: 'Kendrick Lamar', art: a(2) },
]

const MELODY = [
  { title: 'River Flows in You', artist: 'Yiruma', art: a(9) },
  { title: 'Experience', artist: 'Ludovico Einaudi', art: a(0) },
  { title: "Comptine d'un autre été", artist: 'Yann Tiersen', art: a(1) },
  { title: 'Nuvole Bianche', artist: 'Ludovico Einaudi', art: a(8) },
  { title: 'Clair de Lune', artist: 'Debussy', art: a(3) },
  { title: 'Gymnopédie No.1', artist: 'Erik Satie', art: a(6) },
]

// Fixed pink header gradient (6th mix — Favourites)
const HEADER_PINK_GRAD = `radial-gradient(ellipse at 0% 0%, #B71C1C88 0%, transparent 75%), radial-gradient(ellipse at 100% 0%, #E91E6377 0%, transparent 75%), radial-gradient(ellipse at 50% 0%, #FF408155 0%, transparent 70%)`
const HEADER_PINK_BLOBS: string[] = ['#B71C1C', '#E91E63', '#FF4081', '#C62828']

function HomePage({ onOpenPlayer, isPlaying, onScrollChange }: { onOpenPlayer: () => void; isPlaying: boolean; onScrollChange?: (top: number) => void }) {
  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden', position: 'relative' }}>

      {/* Sticky header — fixed pink gradient always */}
      <div style={{
        flexShrink: 0, position: 'relative', zIndex: 10,
        padding: '52px 20px 14px',
        backdropFilter: 'blur(24px) saturate(1.6)',
        WebkitBackdropFilter: 'blur(24px) saturate(1.6)',
        background: 'rgba(10,10,10,0.72)',
        borderBottom: '1px solid rgba(255,255,255,0.06)',
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        overflow: 'hidden',
      }}>
        <div style={{
          position: 'absolute', inset: 0,
          background: HEADER_PINK_GRAD,
          opacity: 0.85,
          pointerEvents: 'none',
        }}/>
        <div style={{ color: 'white', fontSize: '32px', fontWeight: 800, letterSpacing: '-1px', position: 'relative', zIndex: 1 }}>Wavv</div>
        <div style={{ position: 'relative', zIndex: 1 }}>
          <Waveform playing={isPlaying} colors={HEADER_PINK_BLOBS} />
        </div>
      </div>

      {/* Scrollable content */}
      <div onScroll={e => onScrollChange?.((e.currentTarget as HTMLElement).scrollTop)} style={{ flex: 1, overflowY: 'auto', overflowX: 'hidden', paddingBottom: '160px', position: 'relative' }} className="hide-scroll">

        {/* Carousel */}
        <div style={{ position: 'relative', zIndex: 1, marginTop: '10px' }}>
          <MadeForYouCarousel onOpenPlayer={onOpenPlayer} />
        </div>

        <div style={{ height: '1px', background: 'rgba(255,255,255,0.07)', margin: '4px 20px 24px' }}/>
        <HomeSection label="Recently Played" onOpenPlayer={onOpenPlayer} items={RECENT} />
        <div style={{ height: '1px', background: 'rgba(255,255,255,0.07)', margin: '24px 20px' }}/>
        <HomeSection label="Forgotten Favourites" onOpenPlayer={onOpenPlayer} items={FORGOTTEN} />
        <div style={{ height: '1px', background: 'rgba(255,255,255,0.07)', margin: '24px 20px' }}/>
        <HomeTileSection label="Moods" onOpenPlayer={onOpenPlayer} items={MOODS} />
        <div style={{ height: '1px', background: 'rgba(255,255,255,0.07)', margin: '24px 20px' }}/>
        <HomeSection label="Hip Hop" onOpenPlayer={onOpenPlayer} items={HIP_HOP} />
        <div style={{ height: '1px', background: 'rgba(255,255,255,0.07)', margin: '24px 20px' }}/>
        <HomeSection label="Melody" onOpenPlayer={onOpenPlayer} items={MELODY} />
      </div>
    </div>
  )
}


function SearchPage({ baseBottom = 174 }: { baseBottom?: number }) {
  const [query, setQuery] = useState('')
  const [focused, setFocused] = useState(false)
  const [keyboardHeight, setKeyboardHeight] = useState(0)
  const inputRef = useRef<HTMLInputElement>(null)

  // Track native keyboard height via visualViewport
  useEffect(() => {
    const vv = window.visualViewport
    if (!vv) return
    const onResize = () => {
      const kbH = Math.max(0, window.innerHeight - vv.height - vv.offsetTop)
      setKeyboardHeight(kbH)
    }
    vv.addEventListener('resize', onResize)
    vv.addEventListener('scroll', onResize)
    return () => { vv.removeEventListener('resize', onResize); vv.removeEventListener('scroll', onResize) }
  }, [])

  // Bar sits above whichever is taller: the nav+mini stack or the keyboard
  const barBottom = Math.max(baseBottom, keyboardHeight)

  const results = query.trim()
    ? [...TRACKS, ...MADE_FOR_YOU.flatMap(m => m.songs)].filter(
        t => t.title.toLowerCase().includes(query.toLowerCase()) ||
             t.artist.toLowerCase().includes(query.toLowerCase())
      ).slice(0, 12)
    : []

  const BAR_H = 72 // search bar zone height

  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', position: 'relative', overflow: 'hidden' }}>

      {/* Sticky header */}
      <div style={{
        flexShrink: 0, zIndex: 10,
        padding: '52px 20px 14px',
        backdropFilter: 'blur(24px) saturate(1.6)',
        WebkitBackdropFilter: 'blur(24px) saturate(1.6)',
        background: 'rgba(10,10,10,0.7)',
        borderBottom: '1px solid rgba(255,255,255,0.06)',
      }}>
        <div style={{ color: 'white', fontSize: '32px', fontWeight: 800, letterSpacing: '-1px' }}>Search</div>
      </div>

      {/* Scrollable content */}
      <div style={{ flex: 1, overflowY: 'auto', paddingBottom: `${barBottom + BAR_H}px` }} className="hide-scroll">
        {/* Results */}
        {results.length > 0 ? (
          <div style={{ padding: '0 20px' }}>
            <div style={{ color: 'rgba(255,255,255,0.4)', fontSize: '11px', fontWeight: 600, letterSpacing: '0.8px', textTransform: 'uppercase', marginBottom: '12px' }}>Results</div>
            {results.map((t, i) => (
              <div key={i} style={{
                display: 'flex', alignItems: 'center', gap: '14px',
                padding: '9px 0',
                borderBottom: i < results.length - 1 ? '1px solid rgba(255,255,255,0.06)' : 'none',
              }}>
                <div style={{ width: 46, height: 46, borderRadius: '8px', overflow: 'hidden', flexShrink: 0, background: '#1a1a1a' }}>
                  <img src={t.art} alt={t.title} style={{ width: '100%', height: '100%', objectFit: 'cover' }}/>
                </div>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ color: 'white', fontSize: '15px', fontWeight: 600, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{t.title}</div>
                  <div style={{ color: 'rgba(255,255,255,0.4)', fontSize: '12px', marginTop: '2px' }}>{t.artist}</div>
                </div>
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="rgba(255,255,255,0.2)" strokeWidth="2.5" strokeLinecap="round"><path d="M9 18l6-6-6-6"/></svg>
              </div>
            ))}
          </div>
        ) : !focused ? (
          /* Browse categories — Apple Music style landscape cards */
          <div style={{ padding: '0 16px' }}>
            <div style={{ color: 'rgba(255,255,255,0.45)', fontSize: '13px', fontWeight: 700, letterSpacing: '0.4px', textTransform: 'uppercase', marginBottom: '12px', paddingLeft: '4px' }}>Browse Categories</div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px' }}>
              {CATEGORIES.map((c, i) => (
                <div key={i} style={{
                  height: 100, borderRadius: '12px', overflow: 'hidden',
                  position: 'relative', cursor: 'pointer',
                  background: c.color,
                }}>
                  {/* Photo — right-aligned, tall, slightly tilted */}
                  <img
                    src={c.art}
                    alt={c.label}
                    style={{
                      position: 'absolute', right: -6, bottom: -4,
                      width: 90, height: 110,
                      objectFit: 'cover', objectPosition: 'top center',
                      borderRadius: '6px',
                      transform: 'rotate(8deg)',
                      opacity: 0.72,
                      boxShadow: '-6px 4px 16px rgba(0,0,0,0.4)',
                    }}
                  />
                  {/* Left-to-right fade so label is always readable */}
                  <div style={{
                    position: 'absolute', inset: 0,
                    backgroundImage: `linear-gradient(90deg, ${c.color} 40%, transparent 100%)`,
                  }}/>
                  {/* Label */}
                  <div style={{
                    position: 'absolute', bottom: 12, left: 12,
                    color: 'white', fontSize: '15px', fontWeight: 800,
                    letterSpacing: '-0.3px', lineHeight: 1.2,
                    textShadow: '0 1px 4px rgba(0,0,0,0.3)',
                    maxWidth: '60%',
                  }}>{c.label}</div>
                </div>
              ))}
            </div>
          </div>
        ) : (
          /* Empty state when focused but no query */
          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', paddingTop: '60px', gap: '10px' }}>
            <svg width="44" height="44" viewBox="0 0 24 24" fill="none" stroke="rgba(255,255,255,0.18)" strokeWidth="1.5" strokeLinecap="round">
              <circle cx="11" cy="11" r="7"/><path d="M21 21l-4.35-4.35"/>
            </svg>
            <div style={{ color: 'rgba(255,255,255,0.25)', fontSize: '14px' }}>Start typing to search</div>
          </div>
        )}
      </div>

      {/* Search bar — floating overlay, no divider line */}
      <div style={{
        position: 'absolute', left: 0, right: 0,
        bottom: barBottom,
        transition: 'bottom 0.28s cubic-bezier(0.4,0,0.2,1)',
        paddingTop: '10px', paddingBottom: '10px', paddingLeft: '16px', paddingRight: '16px',
        background: 'transparent',
        zIndex: 20,
      }}>
        <div style={{
          display: 'flex', alignItems: 'center', gap: '10px',
          position: 'relative',
          backdropFilter: 'blur(28px) saturate(1.8)',
          WebkitBackdropFilter: 'blur(28px) saturate(1.8)',
          borderRadius: '999px', padding: '13px 20px',
          border: '1px solid rgba(255,255,255,0.08)',
          boxShadow: '0 8px 28px rgba(0,0,0,0.5)',
          overflow: 'hidden',
          backgroundImage: 'none',
        }}>
          {/* Shifting colour wash — hue rotates slowly, content sits above */}
          <div style={{
            position: 'absolute', inset: 0, borderRadius: '999px',
            backgroundImage: 'linear-gradient(90deg, rgba(100,60,220,0.55) 0%, transparent 70%)',
            animation: 'searchHueShift 24s linear infinite',
            pointerEvents: 'none',
          }}/>
          {/* Dark base behind the wash */}
          <div style={{
            position: 'absolute', inset: 0, borderRadius: '999px',
            backgroundImage: 'linear-gradient(90deg, rgba(28,22,52,0.95), rgba(26,26,36,0.95))',
            zIndex: 0, pointerEvents: 'none',
          }}/>

          {/* Content above the wash layers */}
          <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke={focused ? 'rgba(255,255,255,0.6)' : 'rgba(255,255,255,0.3)'} strokeWidth="2.2" strokeLinecap="round" style={{ flexShrink: 0, transition: 'stroke 0.2s', position: 'relative', zIndex: 1 }}>
            <circle cx="11" cy="11" r="7"/><path d="M21 21l-4.35-4.35"/>
          </svg>
          <input
            ref={inputRef}
            value={query}
            onChange={e => setQuery(e.target.value)}
            onFocus={() => setFocused(true)}
            onBlur={() => setFocused(false)}
            placeholder="Songs, artists, albums..."
            style={{
              flex: 1, background: 'transparent', border: 'none', outline: 'none',
              color: 'white', fontSize: '16px', fontFamily: 'inherit',
              position: 'relative', zIndex: 1,
            }}
          />
          {query ? (
            <button onClick={() => { setQuery(''); inputRef.current?.focus() }} style={{ background: 'rgba(255,255,255,0.15)', border: 'none', borderRadius: '50%', width: 20, height: 20, display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer', color: 'white', fontSize: '14px', flexShrink: 0, position: 'relative', zIndex: 1 }}>×</button>
          ) : focused ? (
            <button onMouseDown={e => { e.preventDefault(); inputRef.current?.blur(); setFocused(false) }} style={{ background: 'rgba(255,255,255,0.15)', border: 'none', borderRadius: '50%', width: 20, height: 20, display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer', color: 'white', fontSize: '14px', flexShrink: 0, position: 'relative', zIndex: 1 }}>×</button>
          ) : null}
        </div>
      </div>
    </div>
  )
}

// ─── Library icon helpers — simple red stroke icons matching reference ────────
const LIB_RED = '#FA2D48'

// Stacked lines + music note tail (Playlists)
const LibIconPlaylists = () => (
  <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke={LIB_RED} strokeWidth="1.9" strokeLinecap="round">
    <line x1="3" y1="6" x2="15" y2="6"/>
    <line x1="3" y1="10" x2="15" y2="10"/>
    <line x1="3" y1="14" x2="10" y2="14"/>
    {/* Note tail */}
    <path d="M17 14v-5l4-1v5" strokeLinejoin="round"/>
    <circle cx="17" cy="14" r="1.5" fill={LIB_RED} stroke="none"/>
    <circle cx="21" cy="13" r="1.5" fill={LIB_RED} stroke="none"/>
  </svg>
)

// Microphone (Artists)
const LibIconArtists = () => (
  <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke={LIB_RED} strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round">
    <rect x="9" y="2" width="6" height="11" rx="3"/>
    <path d="M5 10a7 7 0 0014 0"/>
    <line x1="12" y1="19" x2="12" y2="22"/>
    <line x1="8" y1="22" x2="16" y2="22"/>
  </svg>
)

// Vinyl / disc (Albums)
const LibIconAlbums = () => (
  <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke={LIB_RED} strokeWidth="1.9" strokeLinecap="round">
    <circle cx="12" cy="12" r="9"/>
    <circle cx="12" cy="12" r="3"/>
    <circle cx="12" cy="12" r="1" fill={LIB_RED} stroke="none"/>
  </svg>
)

// Single music note (Songs)
const LibIconSongs = () => (
  <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke={LIB_RED} strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round">
    <path d="M9 18V7l12-3v11"/>
    <circle cx="6" cy="18" r="3" fill="none"/>
    <circle cx="18" cy="15" r="3" fill="none"/>
  </svg>
)

// Circle with down arrow (Downloaded)
const LibIconDownloaded = () => (
  <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke={LIB_RED} strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round">
    <circle cx="12" cy="12" r="9"/>
    <path d="M12 8v8"/>
    <path d="M8.5 12.5L12 16l3.5-3.5"/>
  </svg>
)

const LIBRARY_TOP_ART = [
  { title: 'Why Not More?', art: a(3) },
  { title: 'R&B Now', art: a(7) },
  { title: 'MIKE', art: a(6) },
  { title: 'Like A Ribbon', art: a(0) },
  { title: 'Earcandy', art: a(9) },
  { title: 'Jupiter', art: a(2) },
]

const LIBRARY_CATEGORIES = [
  { label: 'Playlists', icon: <LibIconPlaylists /> },
  { label: 'Artists', icon: <LibIconArtists /> },
  { label: 'Albums', icon: <LibIconAlbums /> },
  { label: 'Songs', icon: <LibIconSongs /> },
]

function LibraryPage({ onOpenPlayer }: { onOpenPlayer: () => void }) {
  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>

      {/* Sticky header */}
      <div style={{
        flexShrink: 0, zIndex: 10,
        padding: '52px 20px 14px',
        backdropFilter: 'blur(24px) saturate(1.6)',
        WebkitBackdropFilter: 'blur(24px) saturate(1.6)',
        background: 'rgba(10,10,10,0.7)',
        borderBottom: '1px solid rgba(255,255,255,0.06)',
      }}>
        <div style={{ color: 'white', fontSize: '32px', fontWeight: 800, letterSpacing: '-1px' }}>Library</div>
      </div>

      <div style={{ flex: 1, overflowY: 'auto', paddingBottom: '168px' }} className="hide-scroll">
        <div style={{ height: '16px' }} />

        {/* Album art grid — 3 columns like Apple Music */}
        <div style={{ padding: '0 18px 4px' }}>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '10px' }}>
            {LIBRARY_TOP_ART.map((item, i) => (
              <div key={i} onClick={onOpenPlayer} style={{ cursor: 'pointer' }}>
                <div style={{
                  width: '100%', aspectRatio: '1', borderRadius: '10px',
                  overflow: 'hidden', background: '#1a1a1a',
                  boxShadow: '0 4px 14px rgba(0,0,0,0.4)',
                }}>
                  <img src={item.art} alt={item.title} style={{ width: '100%', height: '100%', objectFit: 'cover' }}/>
                </div>
                <div style={{
                  color: 'rgba(255,255,255,0.82)', fontSize: '11px', fontWeight: 600,
                  marginTop: '6px', overflow: 'hidden', textOverflow: 'ellipsis',
                  whiteSpace: 'nowrap', textAlign: 'center', lineHeight: 1.3,
                }}>{item.title}</div>
              </div>
            ))}
          </div>
        </div>

        {/* Divider */}
        <div style={{ height: 1, background: 'rgba(255,255,255,0.07)', margin: '18px 20px 4px' }}/>

        {/* Category rows — Playlists, Artists, Albums, Songs */}
        <div style={{ padding: '4px 20px' }}>
          {LIBRARY_CATEGORIES.map((cat, i) => (
            <div
              key={cat.label}
              onClick={onOpenPlayer}
              style={{
                display: 'flex', alignItems: 'center', gap: '16px',
                padding: '13px 0',
                borderBottom: i < LIBRARY_CATEGORIES.length - 1 ? '1px solid rgba(255,255,255,0.07)' : 'none',
                cursor: 'pointer',
              }}
            >
              {/* Bare icon — no pill background, just the red stroke icon */}
              <div style={{ width: 26, flexShrink: 0, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                {cat.icon}
              </div>
              <span style={{ flex: 1, color: 'white', fontSize: '17px', fontWeight: 400, letterSpacing: '-0.1px' }}>{cat.label}</span>
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="rgba(255,255,255,0.22)" strokeWidth="2.5" strokeLinecap="round"><path d="M9 18l6-6-6-6"/></svg>
            </div>
          ))}
        </div>

        {/* Divider */}
        <div style={{ height: 1, background: 'rgba(255,255,255,0.07)', margin: '4px 20px 20px' }}/>

        {/* Recently Added */}
        <div style={{ padding: '0 20px' }}>
          <div style={{ color: 'white', fontSize: '20px', fontWeight: 700, letterSpacing: '-0.4px', marginBottom: '14px' }}>Recently Added</div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0' }}>
            {RECENT.map((r, i) => (
              <div
                key={i}
                onClick={onOpenPlayer}
                style={{
                  display: 'flex', alignItems: 'center', gap: '14px',
                  padding: '10px 0',
                  borderBottom: i < RECENT.length - 1 ? '1px solid rgba(255,255,255,0.06)' : 'none',
                  cursor: 'pointer',
                }}
              >
                <div style={{
                  width: 52, height: 52, borderRadius: '8px', overflow: 'hidden',
                  flexShrink: 0, background: '#1a1a1a',
                  boxShadow: '0 2px 8px rgba(0,0,0,0.35)',
                }}>
                  <img src={r.art} alt={r.title} style={{ width: '100%', height: '100%', objectFit: 'cover' }}/>
                </div>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ color: 'white', fontSize: '15px', fontWeight: 600, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{r.title}</div>
                  <div style={{ color: 'rgba(255,255,255,0.42)', fontSize: '13px', marginTop: '2px' }}>{r.artist}</div>
                </div>
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="rgba(255,255,255,0.25)" strokeWidth="2.5" strokeLinecap="round"><path d="M9 18l6-6-6-6"/></svg>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  )
}

function YouPage() {
  const stats = [
    { label: 'Minutes', value: '4,821' },
    { label: 'Artists', value: '138' },
    { label: 'Tracks', value: '2,204' },
  ]
  const settings = [
    { icon: '🎧', label: 'Audio Quality', value: 'High' },
    { icon: '📲', label: 'Download on Wi-Fi', value: 'On' },
    { icon: '🎨', label: 'Theme', value: 'Dark' },
    { icon: '🔔', label: 'Notifications', value: 'All' },
    { icon: '💾', label: 'Storage Used', value: '1.2 GB' },
    { icon: '📤', label: 'Share Profile', value: '' },
    { icon: '🔒', label: 'Privacy', value: '' },
    { icon: '❓', label: 'Help & Support', value: '' },
  ]

  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>

      {/* Sticky header */}
      <div style={{
        flexShrink: 0, zIndex: 10,
        padding: '52px 20px 14px',
        backdropFilter: 'blur(24px) saturate(1.6)',
        WebkitBackdropFilter: 'blur(24px) saturate(1.6)',
        background: 'rgba(10,10,10,0.7)',
        borderBottom: '1px solid rgba(255,255,255,0.06)',
      }}>
        <div style={{ color: 'white', fontSize: '32px', fontWeight: 800, letterSpacing: '-1px' }}>You</div>
      </div>

      <div style={{ flex: 1, overflowY: 'auto', paddingBottom: '160px' }} className="hide-scroll">
      {/* Profile hero */}
      <div style={{
        padding: '28px 20px 28px',
        background: 'linear-gradient(to bottom, rgba(123,63,245,0.25), transparent)',
        textAlign: 'center',
      }}>
        <div style={{
          width: 84, height: 84, borderRadius: '50%', margin: '0 auto 14px',
          background: 'linear-gradient(135deg, #7B3FF5, #E8553E)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          fontSize: '32px', fontWeight: 700, color: 'white',
          boxShadow: '0 0 0 3px rgba(123,63,245,0.4), 0 8px 24px rgba(0,0,0,0.4)',
        }}>A</div>
        <div style={{ color: 'white', fontSize: '22px', fontWeight: 700 }}>Arjun Kumar</div>
        <div style={{ color: 'rgba(255,255,255,0.45)', fontSize: '13px', marginTop: '4px' }}>arjun@gmail.com</div>
        <button style={{
          marginTop: '14px', background: 'rgba(255,255,255,0.1)',
          border: '1px solid rgba(255,255,255,0.15)', borderRadius: '20px',
          color: 'white', fontSize: '13px', fontWeight: 600,
          padding: '7px 20px', cursor: 'pointer', fontFamily: 'inherit',
        }}>Edit Profile</button>
      </div>

      {/* Listening stats */}
      <div style={{ margin: '0 20px 24px' }}>
        <div style={{ color: 'rgba(255,255,255,0.45)', fontSize: '11px', fontWeight: 600, letterSpacing: '0.8px', textTransform: 'uppercase', marginBottom: '12px' }}>This Month</div>
        <div style={{
          display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '10px',
        }}>
          {stats.map((s, i) => (
            <div key={i} style={{
              background: 'rgba(255,255,255,0.06)', borderRadius: '12px',
              padding: '14px 10px', textAlign: 'center',
              border: '1px solid rgba(255,255,255,0.07)',
            }}>
              <div style={{ color: 'white', fontSize: '22px', fontWeight: 700 }}>{s.value}</div>
              <div style={{ color: 'rgba(255,255,255,0.4)', fontSize: '11px', marginTop: '3px' }}>{s.label}</div>
            </div>
          ))}
        </div>
      </div>

      {/* Settings list */}
      <div style={{ margin: '0 20px' }}>
        <div style={{ color: 'rgba(255,255,255,0.45)', fontSize: '11px', fontWeight: 600, letterSpacing: '0.8px', textTransform: 'uppercase', marginBottom: '12px' }}>Settings</div>
        <div style={{ background: 'rgba(255,255,255,0.05)', borderRadius: '14px', overflow: 'hidden', border: '1px solid rgba(255,255,255,0.07)' }}>
          {settings.map((s, i) => (
            <div key={i} style={{
              display: 'flex', alignItems: 'center', gap: '12px',
              padding: '14px 16px', cursor: 'pointer',
              borderBottom: i < settings.length - 1 ? '1px solid rgba(255,255,255,0.06)' : 'none',
            }}>
              <span style={{ fontSize: '17px', width: 24, textAlign: 'center', flexShrink: 0 }}>{s.icon}</span>
              <span style={{ color: 'white', fontSize: '15px', flex: 1 }}>{s.label}</span>
              {s.value && <span style={{ color: 'rgba(255,255,255,0.35)', fontSize: '13px' }}>{s.value}</span>}
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="rgba(255,255,255,0.2)" strokeWidth="2.5" strokeLinecap="round"><path d="M9 18l6-6-6-6"/></svg>
            </div>
          ))}
        </div>
      </div>

      <div style={{ padding: '24px 20px 0', textAlign: 'center', color: 'rgba(255,255,255,0.2)', fontSize: '12px' }}>
        Rhythmica v1.0.0 · Local Music Player
      </div>
      </div>
    </div>
  )
}


// ─── Now Playing Bar ─────────────────────────────────────────────────────────
// Fixed-position card. Bottom is always anchored above the nav bar.
// Height animates: BIG_H when on home-top, SLIM_H otherwise.
// Shrinking collapses the top downward (bottom stays put).

const BAR_BOTTOM = 'calc(env(safe-area-inset-bottom, 0px) + 88px)'
const BIG_H = 144
const SLIM_H = 70

function NowPlayingBar({
  track, isPlaying, progress, big, liked,
  onTogglePlay, onPrev, onNext, onOpen, onLike, artRef, barRef,
}: {
  track: typeof TRACKS[0]; isPlaying: boolean; progress: number; big: boolean; liked: boolean;
  onTogglePlay: () => void; onPrev: () => void; onNext: () => void; onOpen: () => void; onLike: () => void;
  artRef?: React.RefObject<HTMLDivElement | null>;
  barRef?: React.RefObject<HTMLDivElement | null>;
}) {
  const artSrc = track.art
  const elapsed = Math.floor(progress * track.duration)
  const remaining = track.duration - elapsed

  const sp = (e: React.MouseEvent) => e.stopPropagation()

  return (
    <div ref={barRef} style={{
      position: 'fixed',
      bottom: BAR_BOTTOM,
      left: FLOAT_SIDE, right: FLOAT_SIDE,
      height: big ? BIG_H : SLIM_H,
      borderRadius: big ? 30 : 999,
      overflow: 'hidden',
      isolation: 'isolate',
      cursor: 'pointer',
      background: 'rgba(32,32,38,0.94)',
      backdropFilter: 'blur(30px) saturate(1.8)',
      WebkitBackdropFilter: 'blur(30px) saturate(1.8)',
      border: '1px solid rgba(255,255,255,0.1)',
      boxShadow: '0 8px 36px rgba(0,0,0,0.55)',
      transition: 'height 0.52s cubic-bezier(0.32,0.72,0,1), border-radius 0.32s cubic-bezier(0.32,0.72,0,1)',
      zIndex: 49,
    }} onClick={onOpen}>

      {/* Blurred album art background wash */}
      <div style={{
        position: 'absolute', inset: 0, zIndex: 0,
        backgroundImage: `url(${artSrc})`,
        backgroundSize: 'cover', backgroundPosition: 'center',
        filter: 'blur(28px) saturate(2) brightness(0.25)',
        transform: 'scale(1.4)',
        pointerEvents: 'none',
      }}/>

      {/* ── BIG layout ── */}
      <div style={{
        position: 'absolute', inset: 0, zIndex: 1,
        padding: '10px 16px 8px',
        display: 'flex', flexDirection: 'column', justifyContent: 'space-between',
        opacity: big ? 1 : 0,
        transform: big ? 'scale(1)' : 'scale(0.96)',
        pointerEvents: big ? 'auto' : 'none',
        transition: 'opacity 0.35s cubic-bezier(0.32,0.72,0,1) 0.12s, transform 0.4s cubic-bezier(0.32,0.72,0,1) 0.1s',
      }}>
        {/* Row 1: art + info + like */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          <div ref={big ? artRef : undefined} style={{ width: 50, height: 50, borderRadius: '11px', overflow: 'hidden', flexShrink: 0, boxShadow: '0 3px 12px rgba(0,0,0,0.5)' }}>
            <img src={artSrc} style={{ width: '100%', height: '100%', objectFit: 'cover' }} alt="" />
          </div>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{ color: 'white', fontSize: '15px', fontWeight: 700, letterSpacing: '-0.3px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{track.title}</div>
            <div style={{ color: 'rgba(255,255,255,0.5)', fontSize: '12px', marginTop: '2px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{track.artist}</div>
          </div>
          <button onClick={e => { sp(e); onLike() }} style={{ background: 'none', border: 'none', padding: '6px', cursor: 'pointer', flexShrink: 0 }} aria-label="Like">
            <svg width="20" height="20" viewBox="0 0 24 24" fill={liked ? '#FA2D48' : 'none'} stroke={liked ? '#FA2D48' : 'rgba(255,255,255,0.55)'} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/>
            </svg>
          </button>
        </div>

        {/* Row 2+3: progress + controls grouped */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
          <div>
            <div style={{ height: '2px', background: 'rgba(255,255,255,0.15)', borderRadius: '1px', overflow: 'hidden' }}>
              <div style={{ height: '100%', width: `${progress * 100}%`, background: 'rgba(255,255,255,0.8)', borderRadius: '1px', transition: 'width 0.5s linear' }}/>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: '3px' }}>
              <span style={{ color: 'rgba(255,255,255,0.32)', fontSize: '10px', fontWeight: 600 }}>{formatTime(elapsed)}</span>
              <span style={{ color: 'rgba(255,255,255,0.32)', fontSize: '10px', fontWeight: 600 }}>-{formatTime(remaining)}</span>
            </div>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '11%' }}>
          <button onClick={e => { sp(e); onPrev() }} style={{ background: 'none', border: 'none', color: 'white', padding: '4px', cursor: 'pointer' }}><IconPrev/></button>
          <button onClick={e => { sp(e); onTogglePlay() }} style={{ background: 'none', border: 'none', padding: '4px', cursor: 'pointer', display: 'flex' }}>
            <img src={isPlaying ? pauseIconSrc : playIconSrc} style={{ width: 38, height: 38, filter: 'brightness(0) invert(1)', objectFit: 'contain' }} alt="" />
          </button>
          <button onClick={e => { sp(e); onNext() }} style={{ background: 'none', border: 'none', color: 'white', padding: '4px', cursor: 'pointer' }}><IconNext/></button>
          </div>
        </div>
      </div>

      {/* ── SLIM layout ── */}
      <div style={{
        position: 'absolute', inset: 0, zIndex: 1,
        display: 'flex', alignItems: 'center', gap: '12px',
        padding: '8px 14px 8px 20px',
        opacity: big ? 0 : 1,
        pointerEvents: big ? 'none' : 'auto',
        transition: 'opacity 0.18s cubic-bezier(0.4,0,0.2,1)',
      }}>
        <div ref={!big ? artRef : undefined} style={{ width: 48, height: 48, borderRadius: '10px', overflow: 'hidden', flexShrink: 0 }}>
          <img src={artSrc} style={{ width: '100%', height: '100%', objectFit: 'cover' }} alt="" />
        </div>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ color: 'white', fontSize: '14px', fontWeight: 700, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{track.title}</div>
          <div style={{ color: 'rgba(255,255,255,0.5)', fontSize: '12px', marginTop: '2px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{track.artist}</div>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0px', flexShrink: 0 }}>
          <button onClick={e => { sp(e); onTogglePlay() }} style={{ background: 'none', border: 'none', color: 'white', padding: '8px', cursor: 'pointer' }}>
            {isPlaying
              ? <svg width="22" height="22" viewBox="0 0 32 32" fill="white"><path d="M6 4c-.55 0-1 .45-1 1v22c0 .55.45 1 1 1h3c.55 0 1-.45 1-1V5c0-.55-.45-1-1-1H6z"/><path d="M23 4c-.55 0-1 .45-1 1v22c0 .55.45 1 1 1h3c.55 0 1-.45 1-1V5c0-.55-.45-1-1-1h-3z"/></svg>
              : <svg width="22" height="22" viewBox="0 0 32 32" fill="white"><path d="M4.993 2.496C4.516 2.223 4 2.45 4 3v26c0 .55.516.777.993.504l22.826-13.008c.478-.273.446-.719-.031-.992L4.993 2.496z"/></svg>
            }
          </button>
          <button onClick={e => { sp(e); onNext() }} style={{ background: 'none', border: 'none', color: 'rgba(255,255,255,0.6)', padding: '8px', cursor: 'pointer' }}>
            <svg width="20" height="20" viewBox="0 0 640 640" fill="currentColor"><path d="M403.7 107.1C392.1 96 375 92.9 360.3 99.2C345.6 105.5 336 120 336 136L336 272.3L163.7 107.2C152.1 96 135 92.9 120.3 99.2C105.6 105.5 96 120 96 136L96 504C96 520 105.6 534.5 120.3 540.8C135 547.1 152.1 544 163.7 532.9L336 367.7L336 504C336 520 345.6 534.5 360.3 540.8C375 547.1 392.1 544 403.7 532.9L595.7 348.9C603.6 341.4 608 330.9 608 320C608 309.1 603.5 298.7 595.7 291.1L403.7 107.1z"/></svg>
          </button>
        </div>
      </div>
    </div>
  )
}

// ─── Mini Player ──────────────────────────────────────────────────────────────

function MiniPlayer({
  track, isPlaying, onTogglePlay, onOpen,
}: {
  track: typeof TRACKS[0]; isPlaying: boolean; onTogglePlay: () => void; onOpen: () => void;
}) {
  const artSrc = track.art
  return (
    <div
      onClick={onOpen}
      style={{
        borderRadius: '999px',
        overflow: 'hidden',
        position: 'relative',
        cursor: 'pointer',
        isolation: 'isolate',
        background: 'rgba(38,38,44,0.92)',
        backdropFilter: 'blur(28px) saturate(1.8)',
        WebkitBackdropFilter: 'blur(28px) saturate(1.8)',
        border: '1px solid rgba(255,255,255,0.1)',
        boxShadow: '0 6px 24px rgba(0,0,0,0.45), 0 1px 6px rgba(0,0,0,0.3)',
      }}
    >
      {/* Subtle tinted art wash behind frosted glass */}
      <div style={{
        position: 'absolute', inset: 0, zIndex: 0,
        backgroundImage: `url(${artSrc})`,
        backgroundSize: 'cover', backgroundPosition: 'center',
        filter: 'blur(24px) saturate(1.8) brightness(0.28)',
        transform: 'scale(1.3)',
      }}/>

      {/* Content */}
      <div style={{
        position: 'relative', zIndex: 1,
        display: 'flex', alignItems: 'center', gap: '12px',
        padding: '8px 14px 8px 22px',
      }}>
        {/* Album art */}
        <div style={{ width: 52, height: 52, borderRadius: '11px', overflow: 'hidden', flexShrink: 0, background: '#1a1a1a', boxShadow: '0 2px 10px rgba(0,0,0,0.4)' }}>
          <img src={artSrc} alt={track.title} style={{ width: '100%', height: '100%', objectFit: 'cover' }}/>
        </div>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ color: 'white', fontSize: '14px', fontWeight: 700, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', letterSpacing: '-0.2px' }}>{track.title}</div>
          <div style={{ color: 'rgba(255,255,255,0.52)', fontSize: '12px', marginTop: '3px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{track.artist}</div>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '2px', flexShrink: 0 }}>
          <button
            onClick={e => { e.stopPropagation(); onTogglePlay() }}
            style={{ background: 'none', border: 'none', color: 'white', padding: '8px', cursor: 'pointer' }}
            aria-label={isPlaying ? 'Pause' : 'Play'}
          >
            {isPlaying ? (
              <svg width="22" height="22" viewBox="0 0 32 32" fill="white">
                <path d="M6 4c-.55 0-1 .45-1 1v22c0 .55.45 1 1 1h3c.55 0 1-.45 1-1V5c0-.55-.45-1-1-1H6z"/>
                <path d="M23 4c-.55 0-1 .45-1 1v22c0 .55.45 1 1 1h3c.55 0 1-.45 1-1V5c0-.55-.45-1-1-1h-3z"/>
              </svg>
            ) : (
              <svg width="22" height="22" viewBox="0 0 32 32" fill="white">
                <path d="M4.993 2.496C4.516 2.223 4 2.45 4 3v26c0 .55.516.777.993.504l22.826-13.008c.478-.273.446-.719-.031-.992L4.993 2.496z"/>
              </svg>
            )}
          </button>
          <button
            onClick={e => e.stopPropagation()}
            style={{ background: 'none', border: 'none', color: 'rgba(255,255,255,0.55)', padding: '8px', cursor: 'pointer' }}
            aria-label="Next"
          >
            <svg width="20" height="20" viewBox="0 0 640 640" fill="currentColor">
              <path d="M403.7 107.1C392.1 96 375 92.9 360.3 99.2C345.6 105.5 336 120 336 136L336 272.3L163.7 107.2C152.1 96 135 92.9 120.3 99.2C105.6 105.5 96 120 96 136L96 504C96 520 105.6 534.5 120.3 540.8C135 547.1 152.1 544 163.7 532.9L336 367.7L336 504C336 520 345.6 534.5 360.3 540.8C375 547.1 392.1 544 403.7 532.9L595.7 348.9C603.6 341.4 608 330.9 608 320C608 309.1 603.5 298.7 595.7 291.1L403.7 107.1z"/>
            </svg>
          </button>
        </div>
      </div>
    </div>
  )
}

// ─── Bottom Nav ───────────────────────────────────────────────────────────────

type Tab = 'home' | 'search' | 'library' | 'you'

// Shared floating width — nav and now playing card match this
const FLOAT_SIDE = 20 // px from each edge

function BottomNav({ active, onChange }: { active: Tab; onChange: (t: Tab) => void }) {
  const tabs: { id: Tab; label: string; Icon: React.ComponentType<{ active: boolean }> }[] = [
    { id: 'home', label: 'Home', Icon: NavHome },
    { id: 'search', label: 'Search', Icon: NavSearch },
    { id: 'library', label: 'Library', Icon: NavLibrary },
    { id: 'you', label: 'You', Icon: NavYou },
  ]

  return (
    <div style={{
      position: 'absolute',
      bottom: 'calc(env(safe-area-inset-bottom, 12px) + 12px)',
      left: FLOAT_SIDE, right: FLOAT_SIDE,
      display: 'flex', alignItems: 'center',
      // Match mini player frosted glass exactly
      background: 'rgba(22,22,26,0.78)',
      backdropFilter: 'blur(28px) saturate(1.6)',
      WebkitBackdropFilter: 'blur(28px) saturate(1.6)',
      border: '1px solid rgba(255,255,255,0.07)',
      borderRadius: '999px',
      padding: '5px 5px',
      gap: '0px',
      zIndex: 50,
      boxShadow: '0 4px 18px rgba(0,0,0,0.35), 0 1px 4px rgba(0,0,0,0.2)',
      isolation: 'isolate',
      overflow: 'hidden',
    }}>
      {tabs.map(({ id, label, Icon }) => (
        <button
          key={id}
          onClick={() => onChange(id)}
          style={{
            flex: 1,
            // True oval highlight — no grey, just a very subtle tinted oval
            background: active === id ? 'rgba(250,45,72,0.13)' : 'transparent',
            border: 'none',
            borderRadius: '999px',       // full pill / oval, not squircle
            display: 'flex', flexDirection: 'column', alignItems: 'center',
            gap: '4px', padding: '10px 0 9px',
            cursor: 'pointer', fontFamily: 'inherit',
            transition: 'background 0.2s ease',
            position: 'relative', zIndex: 1,
            boxShadow: 'none',
          }}
          aria-label={label}
        >
          <Icon active={active === id} />
          <span style={{
            fontSize: '10px', fontWeight: active === id ? 700 : 500,
            // Red label when active, dim white when inactive — matches reference
            color: active === id ? NAV_RED : 'rgba(255,255,255,0.55)',
            letterSpacing: '0px',
            transition: 'color 0.2s ease',
          }}>{label}</span>
        </button>
      ))}
    </div>
  )
}

// ─── Full-screen Now Playing ──────────────────────────────────────────────────

function NowPlayingScreen({
  track, isPlaying, progress, liked,
  onTogglePlay, onLike, onPrev, onNext, onClose,
  handlePointerDown, handlePointerMove, handlePointerUp,
  openOrigin, barRef, barArtRef,
}: {
  track: typeof TRACKS[0]; isPlaying: boolean; progress: number; liked: boolean;
  onTogglePlay: () => void; onLike: () => void;
  onPrev: () => void; onNext: () => void; onClose: () => void;
  handlePointerDown: (e: React.PointerEvent<HTMLDivElement>) => void;
  handlePointerMove: (e: React.PointerEvent<HTMLDivElement>) => void;
  handlePointerUp: (e: React.PointerEvent<HTMLDivElement>) => void;
  openOrigin?: {
    bar: { top: number; left: number; width: number; height: number; borderRadius: string };
    art: { top: number; left: number; width: number; height: number; borderRadius: string };
  } | null;
  barRef?: React.RefObject<HTMLDivElement | null>;
  barArtRef?: React.RefObject<HTMLDivElement | null>;
}) {
  const albumArt = track.art
  const elapsed = Math.floor(progress * track.duration)
  const remaining = track.duration - elapsed
  const ease = '0.78s cubic-bezier(0.4, 0, 0.2, 1)'
  const safeTop = 'env(safe-area-inset-top, 0px)'
  const imgTop = `calc(${safeTop} + ${STATUS_GAP}px)`
  const CLOSE_DURATION = 620

  // ── Close with reverse morph ──
  type Rect4 = { top: number; left: number; width: number; height: number; borderRadius: string }
  const [closing, setClosing] = useState(false)
  const [closeTarget, setCloseTarget] = useState<Rect4 | null>(null)
  const [closeArtTarget, setCloseArtTarget] = useState<Rect4 | null>(null)

  const triggerClose = useCallback(() => {
    if (barRef?.current) {
      const r = barRef.current.getBoundingClientRect()
      setCloseTarget({ top: r.top, left: r.left, width: r.width, height: r.height, borderRadius: getComputedStyle(barRef.current).borderRadius })
    }
    if (barArtRef?.current) {
      const r = barArtRef.current.getBoundingClientRect()
      setCloseArtTarget({ top: r.top, left: r.left, width: r.width, height: r.height, borderRadius: getComputedStyle(barArtRef.current).borderRadius })
    }
    setClosing(true)
    setTimeout(() => onClose(), CLOSE_DURATION)
  }, [barRef, barArtRef, onClose])

  // ── Swipe-down to close ──
  const dragY = useRef(0)
  const startY = useRef(0)
  const isDraggingCard = useRef(false)
  const [translateY, setTranslateY] = useState(0)
  const [entered, setEntered] = useState(false)
  useEffect(() => {
    const id = requestAnimationFrame(() => requestAnimationFrame(() => setEntered(true)))
    return () => cancelAnimationFrame(id)
  }, [])

  const onSwipeDown = (e: React.PointerEvent) => {
    if (closing) return
    isDraggingCard.current = true
    startY.current = e.clientY
    dragY.current = 0
    e.currentTarget.setPointerCapture(e.pointerId)
  }
  const onSwipeMove = (e: React.PointerEvent) => {
    if (!isDraggingCard.current) return
    const dy = e.clientY - startY.current
    if (dy > 0) { dragY.current = dy; setTranslateY(dy) }
  }
  const onSwipeUp = () => {
    if (!isDraggingCard.current) return
    isDraggingCard.current = false
    if (dragY.current > 100) { triggerClose() } else { setTranslateY(0); dragY.current = 0 }
  }

  // ── Loop state ──
  const [loopMode, setLoopMode] = useState<'off' | 'all' | 'one'>('off')
  const cycleLoop = () => setLoopMode(m => m === 'off' ? 'all' : m === 'all' ? 'one' : 'off')

  // ── Queue panel ──
  const [showQueue, setShowQueue] = useState(false)
  const queueDragY = useRef(0)
  const queueStartY = useRef(0)
  const isQueueDragging = useRef(false)
  const [queueTranslateY, setQueueTranslateY] = useState(0)

  const onQueueSwipeDown = (e: React.PointerEvent) => {
    isQueueDragging.current = true
    queueStartY.current = e.clientY
    queueDragY.current = 0
    e.currentTarget.setPointerCapture(e.pointerId)
  }
  const onQueueSwipeMove = (e: React.PointerEvent) => {
    if (!isQueueDragging.current) return
    const dy = e.clientY - queueStartY.current
    if (dy > 0) { queueDragY.current = dy; setQueueTranslateY(dy) }
  }
  const onQueueSwipeUp = () => {
    if (!isQueueDragging.current) return
    isQueueDragging.current = false
    if (queueDragY.current > 80) { setShowQueue(false); setQueueTranslateY(0) }
    else setQueueTranslateY(0)
    queueDragY.current = 0
  }

  // ── "Next Up" label animation ──
  const nextIdx = (TRACKS.findIndex(t => t.title === track.title) + 1) % TRACKS.length
  const nextTrack = TRACKS[nextIdx]
  const [nextUpShowSong, setNextUpShowSong] = useState(false)
  const [nextUpOpacity, setNextUpOpacity] = useState(1)
  const [nudgeUp, setNudgeUp] = useState(false)

  useEffect(() => {
    if (showQueue) return
    let alive = true
    const crossfade = () => {
      if (!alive) return
      setNextUpOpacity(0)
      setTimeout(() => { if (alive) { setNextUpShowSong(s => !s); setNextUpOpacity(1) } }, 380)
    }
    const bounce = () => { if (!alive) return; setNudgeUp(true); setTimeout(() => { if (alive) setNudgeUp(false) }, 550) }
    const t1 = setInterval(crossfade, 4000)
    const t2 = setInterval(bounce, 2600)
    return () => { alive = false; clearInterval(t1); clearInterval(t2) }
  }, [showQueue, nextTrack.title])

  const screenW = window.innerWidth
  const screenH = window.innerHeight
  const MORPH = '0.62s cubic-bezier(0.32,0.72,0,1)'
  const MORPH_PROPS = `top ${MORPH}, left ${MORPH}, width ${MORPH}, height ${MORPH}, border-radius ${MORPH}, background 0.55s ease`

  // Pick the active container rect: origin (pre-enter) → fullscreen → closeTarget
  const containerRect = !entered && openOrigin
    ? { top: openOrigin.bar.top, left: openOrigin.bar.left, width: openOrigin.bar.width, height: openOrigin.bar.height, borderRadius: openOrigin.bar.borderRadius, bg: 'rgba(32,32,38,0.94)', transition: 'none' }
    : closing && closeTarget
    ? { top: closeTarget.top, left: closeTarget.left, width: closeTarget.width, height: closeTarget.height, borderRadius: closeTarget.borderRadius, bg: 'rgba(32,32,38,0.94)', transition: MORPH_PROPS }
    : { top: translateY, left: 0, width: screenW, height: screenH, borderRadius: 0, bg: '#000', transition: isDraggingCard.current ? 'none' : MORPH_PROPS }

  return (
    <div
      onPointerDown={onSwipeDown}
      onPointerMove={onSwipeMove}
      onPointerUp={onSwipeUp}
      onPointerCancel={onSwipeUp}
      style={{
        position: 'fixed', zIndex: 100,
        overflow: 'hidden',
        display: 'flex', flexDirection: 'column',
        alignItems: 'center', justifyContent: 'flex-end',
        paddingTop: safeTop,
        paddingBottom: 'env(safe-area-inset-bottom, 0px)',
        touchAction: 'none',
        top: containerRect.top,
        left: containerRect.left,
        width: containerRect.width,
        height: containerRect.height,
        borderRadius: containerRect.borderRadius,
        background: containerRect.bg,
        transition: containerRect.transition,
      }}
    >
      {/* Layer 1: bg blur */}
      <div style={{
        position: 'absolute', inset: -30, zIndex: 0,
        backgroundImage: `url(${albumArt})`,
        backgroundSize: '100% 100%', backgroundPosition: 'center',
        filter: 'blur(60px) saturate(1.1) brightness(0.95)',
        opacity: entered && !closing ? (isPlaying ? 1 : 0.5) : 0,
        transition: `opacity 0.55s ease`, pointerEvents: 'none',
      }}/>

      {/* Layer 1.5: gradient */}
      <div style={{
        position: 'absolute', bottom: 0, left: 0, right: 0, height: '45%', zIndex: 1,
        background: 'linear-gradient(to bottom, transparent, rgba(0,0,0,0.85))',
        pointerEvents: 'none',
      }}/>

      {/* Layer 1.8: ambient glow */}
      <div style={{
        position: 'absolute', bottom: '-5%', left: '-30%', right: '-30%', height: '45vh',
        backgroundImage: `url(${albumArt})`,
        backgroundSize: 'cover', backgroundPosition: 'bottom center',
        filter: 'blur(50px) saturate(2.5) brightness(0.8)',
        WebkitMaskImage: 'radial-gradient(ellipse at center, black 0%, transparent 70%)',
        maskImage: 'radial-gradient(ellipse at center, black 0%, transparent 70%)',
        opacity: isPlaying ? 1 : 0,
        transition: `opacity ${ease}`,
        animation: `ambientGlow 1.8s ease-in-out infinite alternate ${isPlaying ? 'running' : 'paused'}`,
        pointerEvents: 'none', zIndex: 2, mixBlendMode: 'screen',
      }}/>

      {/* Drag handle / close */}
      <div
        style={{
          position: 'absolute', top: `calc(${safeTop} + 12px)`, left: '50%',
          transform: 'translateX(-50%)',
          zIndex: 20, padding: '8px', cursor: 'pointer',
        }}
        onClick={triggerClose}
      >
        <div style={{ width: 36, height: 4, borderRadius: 2, background: 'rgba(255,255,255,0.3)' }}/>
      </div>

      {/* Album art — shared-element: starts at bar art position, expands to screen position */}
      <div style={{
        position: 'absolute', overflow: 'hidden',
        zIndex: showQueue ? 25 : 4,
        ...(!entered && openOrigin ? {
          top: openOrigin.art.top,
          left: openOrigin.art.left,
          width: openOrigin.art.width,
          height: openOrigin.art.height,
          borderRadius: openOrigin.art.borderRadius,
          boxShadow: 'none',
          transition: 'none',
        } : closing && closeArtTarget ? {
          top: closeArtTarget.top,
          left: closeArtTarget.left,
          width: closeArtTarget.width,
          height: closeArtTarget.height,
          borderRadius: closeArtTarget.borderRadius,
          boxShadow: 'none',
          transition: `top 0.62s cubic-bezier(0.4,0,0.2,1), left 0.62s cubic-bezier(0.4,0,0.2,1), width 0.62s cubic-bezier(0.4,0,0.2,1), height 0.62s cubic-bezier(0.4,0,0.2,1), border-radius 0.62s cubic-bezier(0.4,0,0.2,1)`,
        } : showQueue ? {
          top: 72, left: 24, width: 64, height: 64, borderRadius: '10px',
          boxShadow: '0 4px 16px rgba(0,0,0,0.5)',
          transition: `top 0.44s cubic-bezier(0.32,0.72,0,1), left 0.44s cubic-bezier(0.32,0.72,0,1), width 0.44s cubic-bezier(0.32,0.72,0,1), height 0.44s cubic-bezier(0.32,0.72,0,1), border-radius 0.44s cubic-bezier(0.32,0.72,0,1)`,
        } : {
          transition: [`top 0.62s cubic-bezier(0.32,0.72,0,1)`, `left 0.62s cubic-bezier(0.32,0.72,0,1)`, `width 0.62s cubic-bezier(0.32,0.72,0,1)`, `height 0.62s cubic-bezier(0.32,0.72,0,1)`, !isPlaying ? `box-shadow ${ease}` : 'box-shadow 0s', `border-radius 0.62s cubic-bezier(0.32,0.72,0,1)`].join(', '),
          ...(isPlaying ? {
            top: imgTop, left: 0, width: '100%', height: '100vw',
            borderRadius: '0px', boxShadow: 'none',
          } : {
            top: `calc(${safeTop} + 100px)`,
            left: 'calc(50vw - min(38vw, 160px))',
            width: 'min(76vw, 320px)', height: 'min(76vw, 320px)',
            borderRadius: '16px',
            boxShadow: '0 12px 32px rgba(0,0,0,0.35), 0 4px 12px rgba(0,0,0,0.2)',
          }),
        }),
        pointerEvents: 'none',
      }}>
        <img src={albumArt} alt={`${track.album} by ${track.artist}`}
          style={{ position: 'absolute', inset: 0, width: '100%', height: '100%', objectFit: 'cover', objectPosition: 'center', display: 'block', opacity: isPlaying ? 0 : 1, transition: `opacity ${ease}` }} draggable={false}/>
        <img src={albumArt} alt={`${track.album} by ${track.artist}`}
          style={{ position: 'absolute', inset: 0, width: '100%', height: '100%', objectFit: 'cover', objectPosition: 'center', display: 'block', opacity: isPlaying ? 1 : 0, WebkitMaskImage: 'linear-gradient(to bottom, transparent 0%, black 35%, black 60%, transparent 100%)', maskImage: 'linear-gradient(to bottom, transparent 0%, black 35%, black 60%, transparent 100%)', transition: `opacity ${ease}` }} draggable={false}/>
      </div>

      {/* Controls — stop swipe propagation so sliders and buttons work normally */}
      <div
        onPointerDown={e => e.stopPropagation()}
        onPointerUp={e => e.stopPropagation()}
        style={{
          position: 'relative', zIndex: 10,
          width: '100%', maxWidth: '480px',
          padding: '0 24px',
          paddingBottom: 'calc(env(safe-area-inset-bottom, 0px) + 24px)',
          display: 'flex', flexDirection: 'column', gap: '8px',
          opacity: entered && !closing && !showQueue ? 1 : 0,
          transform: entered && !closing && !showQueue ? 'translateY(0)' : 'translateY(12px)',
          pointerEvents: showQueue ? 'none' : 'auto',
          transition: closing || showQueue
            ? 'opacity 0.2s ease, transform 0.2s ease'
            : 'opacity 0.32s ease 0.3s, transform 0.38s cubic-bezier(0.4,0,0.2,1) 0.28s',
        }}
      >
        {/* Track info + progress — nudged upward */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '0px', marginBottom: '20px' }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <div style={{ flex: 1, minWidth: 0, paddingRight: '16px' }}>
            <div style={{ color: 'white', fontSize: 'clamp(22px, 6.5vw, 28px)', fontWeight: 700, letterSpacing: '-0.5px', lineHeight: 1.2, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', textShadow: '0 2px 12px rgba(0,0,0,0.6)' }}>{track.title}</div>
            <div style={{ color: 'rgba(255,255,255,0.7)', fontSize: 'clamp(17px, 5vw, 20px)', fontWeight: 500, letterSpacing: '-0.2px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', marginTop: '4px' }}>{track.artist}</div>
          </div>
          <div style={{ display: 'flex', gap: '24px', alignItems: 'center' }}>
            <button onClick={onLike} style={{ background: 'transparent', border: 'none', padding: '0', cursor: 'pointer', opacity: 0.9, transform: 'scale(1.15)' }} aria-label="Like"><IconStar filled={liked}/></button>
            <button style={{ background: 'transparent', border: 'none', padding: '0', cursor: 'pointer', opacity: 0.9, transform: 'scale(1.15)' }} aria-label="More"><IconMore/></button>
          </div>
        </div>

        {/* Progress */}
        <div style={{ marginTop: '10px' }}>
          <div className="progress-track"
            onPointerDown={handlePointerDown} onPointerMove={handlePointerMove}
            onPointerUp={handlePointerUp} onPointerCancel={handlePointerUp}
            role="slider" aria-label="Playback position" aria-valuenow={Math.round(progress * 100)}
            style={{ touchAction: 'none', height: '6px', background: 'rgba(255,255,255,0.2)', borderRadius: '3px', position: 'relative', cursor: 'pointer' }}>
            <div className="progress-fill" style={{ width: '100%', height: '100%', transform: `scaleX(${progress})`, transformOrigin: 'left', borderRadius: '3px', position: 'absolute', left: 0, top: 0, overflow: 'hidden', willChange: 'transform' }}>
              <div style={{ position: 'absolute', top: 0, left: 0, width: '100vw', height: '100%', backgroundImage: `url(${albumArt})`, backgroundSize: 'cover', backgroundPosition: 'center', filter: 'blur(2px) saturate(2.5) brightness(1.5)', transform: 'scale(100)', transformOrigin: 'center' }}/>
              <div style={{ position: 'absolute', inset: 0, background: 'rgba(255,255,255,0.25)' }}/>
            </div>
          </div>
          {/* Timestamps row with UHQ chip centred between them */}
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginTop: '8px', padding: '0 2px' }}>
            <span style={{ color: 'rgba(255,255,255,0.5)', fontSize: '11px', fontWeight: 600, letterSpacing: '0.2px' }}>{formatTime(elapsed)}</span>
            <div style={{ display: 'inline-flex', alignItems: 'center', background: 'rgba(255,255,255,0.1)', border: '1px solid rgba(255,255,255,0.18)', borderRadius: '5px', padding: '2px 7px' }}>
              <span style={{ color: 'rgba(255,255,255,0.7)', fontSize: '9px', fontWeight: 700, letterSpacing: '1px' }}>UHQ</span>
            </div>
            <span style={{ color: 'rgba(255,255,255,0.5)', fontSize: '11px', fontWeight: 600, letterSpacing: '0.2px' }}>-{formatTime(remaining)}</span>
          </div>
        </div>
        </div>{/* end track info + progress group */}

        <div style={{ display: 'flex', flexDirection: 'column', gap: '18px' }}>
          {/* Playback controls */}
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '11%' }}>
            <button onClick={onPrev} style={{ background: 'transparent', border: 'none', color: 'white', padding: '12px', cursor: 'pointer' }} aria-label="Previous"><IconPrev/></button>
            <button
              onPointerDown={e => { e.stopPropagation(); onTogglePlay() }}
              style={{ background: 'none', border: 'none', padding: '8px', cursor: 'pointer', flexShrink: 0, display: 'flex', alignItems: 'center', justifyContent: 'center' }}
              aria-label={isPlaying ? 'Pause' : 'Play'}
            >
              <img src={isPlaying ? pauseIconSrc : playIconSrc} alt={isPlaying ? 'Pause' : 'Play'} style={{ width: 48, height: 48, objectFit: 'contain', filter: 'brightness(0) invert(1)', borderStyle: 'none', borderColor: 'rgba(0,0,0,0)' }} />
            </button>
            <button onClick={onNext} style={{ background: 'transparent', border: 'none', color: 'white', padding: '12px', cursor: 'pointer' }} aria-label="Next"><IconNext/></button>
          </div>
        </div>

        {/* Bottom tray — swipe up opens queue */}
        <div
          style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}
          onPointerDown={e => { (e.currentTarget as HTMLDivElement & { _sy?: number })._sy = e.clientY }}
          onPointerUp={e => {
            const el = e.currentTarget as HTMLDivElement & { _sy?: number }
            if (el._sy !== undefined && el._sy - e.clientY > 28) setShowQueue(true)
            el._sy = undefined
          }}
        >
          {/* 3 icon buttons — tighter padding so row is taller */}
          <div style={{ display: 'flex', justifyContent: 'space-between', padding: '0 8px' }}>
            <button style={{ background: 'transparent', border: 'none', color: 'rgba(255,255,255,0.7)', padding: '16px 12px', cursor: 'pointer' }} aria-label="Lyrics"><IconLyrics/></button>
            <button style={{ background: 'transparent', border: 'none', color: 'rgba(255,255,255,0.7)', padding: '16px 12px', cursor: 'pointer' }} aria-label="Airplay"><IconAirplay/></button>
            <button
              onClick={cycleLoop}
              style={{ background: 'transparent', border: 'none', padding: '16px 12px', cursor: 'pointer', color: loopMode === 'off' ? 'rgba(255,255,255,0.4)' : 'white', position: 'relative' }}
              aria-label={`Loop ${loopMode}`}
            >
              <IconLoop mode={loopMode}/>
              {loopMode === 'one' && (
                <span style={{ position: 'absolute', top: '10px', right: '8px', width: '8px', height: '8px', borderRadius: '50%', background: 'white', display: 'block' }}/>
              )}
            </button>
          </div>

          {/* Next Up label / down arrow */}
          <div style={{ textAlign: 'center', paddingBottom: '4px' }}>
            {showQueue ? (
              <button
                onClick={() => setShowQueue(false)}
                style={{ background: 'none', border: 'none', cursor: 'pointer', padding: '4px', display: 'inline-flex', alignItems: 'center', justifyContent: 'center' }}
                aria-label="Close queue"
              >
                <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="rgba(255,255,255,0.55)" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><polyline points="6 9 12 15 18 9"/></svg>
              </button>
            ) : (
              <span
                onClick={() => setShowQueue(true)}
                style={{
                  color: 'rgba(255,255,255,0.45)', fontSize: '12px', fontWeight: 600, letterSpacing: '0.4px', cursor: 'pointer',
                  display: 'inline-block',
                  opacity: nextUpOpacity,
                  transform: nudgeUp ? 'translateY(-5px)' : 'translateY(0)',
                  transition: 'opacity 0.35s ease, transform 0.45s cubic-bezier(0.4,0,0.2,1)',
                }}
              >
                {nextUpShowSong ? nextTrack.title : 'Next Up'}
              </span>
            )}
          </div>
        </div>
      </div>

      {/* ── Queue Panel ── */}
      <div
        onPointerDown={onQueueSwipeDown}
        onPointerMove={onQueueSwipeMove}
        onPointerUp={onQueueSwipeUp}
        onPointerCancel={onQueueSwipeUp}
        style={{
          position: 'absolute', inset: 0, zIndex: 20,
          display: 'flex', flexDirection: 'column',
          background: 'rgba(0,0,8,0.72)',
          transform: showQueue ? `translateY(${queueTranslateY}px)` : 'translateY(100%)',
          transition: isQueueDragging.current ? 'none' : `transform 0.44s cubic-bezier(0.32,0.72,0,1)`,
          touchAction: 'none',
          paddingTop: `calc(${safeTop} + 12px)`,
          paddingBottom: 'env(safe-area-inset-bottom, 0px)',
        }}
      >
        {/* Header: aligns with the floating art (top:72, left:24, 64×64) */}
        <div style={{ height: 72, display: 'flex', alignItems: 'flex-end', paddingBottom: '0' }}>
        </div>

        {/* Art + track info side by side — art is floating absolute at left:24, so indent content */}
        <div style={{ display: 'flex', alignItems: 'center', padding: '0 24px 16px', paddingLeft: 'calc(24px + 64px + 14px)', minHeight: 64 }}>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{ color: 'white', fontSize: '16px', fontWeight: 700, letterSpacing: '-0.3px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{track.title}</div>
            <div style={{ color: 'rgba(255,255,255,0.6)', fontSize: '13px', marginTop: '2px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{track.artist}</div>
          </div>
          <div style={{ display: 'flex', gap: '16px', alignItems: 'center', flexShrink: 0, paddingLeft: '12px' }}>
            <button onClick={onLike} style={{ background: 'none', border: 'none', padding: '4px', cursor: 'pointer' }} aria-label="Like"><IconStar filled={liked}/></button>
            <button style={{ background: 'none', border: 'none', padding: '4px', cursor: 'pointer', color: 'rgba(255,255,255,0.7)' }} aria-label="More"><IconMore/></button>
          </div>
        </div>

        {/* Section label */}
        <div style={{ padding: '4px 24px 10px' }}>
          <span style={{ color: 'rgba(255,255,255,0.35)', fontSize: '11px', fontWeight: 700, letterSpacing: '0.8px', textTransform: 'uppercase' }}>Up Next</span>
        </div>

        {/* Queue list */}
        <div style={{ flex: 1, overflowY: 'auto', padding: '0 24px', paddingBottom: '8px' }}>
          {TRACKS.map((t, i) => {
            const isCurrent = t.title === track.title
            const tArt = t.art
            return (
              <div key={i} style={{ display: 'flex', alignItems: 'center', gap: '14px', padding: '10px 0', borderBottom: '1px solid rgba(255,255,255,0.06)' }}>
                <div style={{ width: 46, height: 46, borderRadius: '9px', overflow: 'hidden', flexShrink: 0, opacity: isCurrent ? 1 : 0.75, outline: isCurrent ? '2px solid rgba(255,255,255,0.4)' : 'none', outlineOffset: '2px' }}>
                  <img src={tArt} style={{ width: '100%', height: '100%', objectFit: 'cover' }} alt="" />
                </div>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ color: isCurrent ? 'white' : 'rgba(255,255,255,0.8)', fontSize: '14px', fontWeight: isCurrent ? 700 : 500, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{t.title}</div>
                  <div style={{ color: 'rgba(255,255,255,0.45)', fontSize: '12px', marginTop: '2px' }}>{t.artist}</div>
                </div>
                {isCurrent && <div style={{ width: 5, height: 5, borderRadius: '50%', background: 'white', flexShrink: 0 }} />}
              </div>
            )
          })}
        </div>

        {/* Back to Now Playing — centred at bottom */}
        <div style={{ display: 'flex', justifyContent: 'center', padding: '14px 0 20px' }}>
          <button
            onClick={() => setShowQueue(false)}
            style={{ background: 'rgba(255,255,255,0.1)', border: '1px solid rgba(255,255,255,0.15)', borderRadius: '50%', width: 40, height: 40, display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer' }}
            aria-label="Back to Now Playing"
          >
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><polyline points="6 9 12 15 18 9"/></svg>
          </button>
        </div>
      </div>
    </div>
  )
}

// ─── Root App ─────────────────────────────────────────────────────────────────

export default function App() {
  const [activeTab, setActiveTab] = useState<Tab>('home')
  const [showPlayer, setShowPlayer] = useState(false)
  const [currentTrackIndex, setCurrentTrackIndex] = useState(0)
  const [homeScrollTop, setHomeScrollTop] = useState(0)
  const [isPlaying, setIsPlaying] = useState(false)
  const [progress, setProgress] = useState(0.18)
  const [liked, setLiked] = useState(false)
  const barArtRef = useRef<HTMLDivElement>(null)
  const barRef = useRef<HTMLDivElement>(null)
  const [openOrigin, setOpenOrigin] = useState<{
    bar: { top: number; left: number; width: number; height: number; borderRadius: string };
    art: { top: number; left: number; width: number; height: number; borderRadius: string };
  } | null>(null)

  const openPlayer = useCallback(() => {
    const barEl = barRef.current
    const artEl = barArtRef.current
    if (barEl && artEl) {
      const br = barEl.getBoundingClientRect()
      const ar = artEl.getBoundingClientRect()
      setOpenOrigin({
        bar: { top: br.top, left: br.left, width: br.width, height: br.height, borderRadius: getComputedStyle(barEl).borderRadius },
        art: { top: ar.top, left: ar.left, width: ar.width, height: ar.height, borderRadius: getComputedStyle(artEl).borderRadius },
      })
    } else {
      setOpenOrigin(null)
    }
    setShowPlayer(true)
  }, [])

  const TRACK = TRACKS[currentTrackIndex]

  const rafRef = useRef<number | null>(null)
  const lastTimeRef = useRef<number | null>(null)
  const progressRef = useRef(progress)
  const isDraggingRef = useRef(false)
  progressRef.current = progress

  const tick = useCallback((now: number) => {
    if (lastTimeRef.current === null) lastTimeRef.current = now
    const delta = (now - lastTimeRef.current) / 1000
    lastTimeRef.current = now
    if (!isDraggingRef.current) {
      const next = Math.min(progressRef.current + delta / TRACK.duration, 1)
      setProgress(next)
      if (next < 1) rafRef.current = requestAnimationFrame(tick)
      else setIsPlaying(false)
    } else {
      rafRef.current = requestAnimationFrame(tick)
    }
  }, [TRACK.duration])

  useEffect(() => {
    if (isPlaying) {
      lastTimeRef.current = null
      rafRef.current = requestAnimationFrame(tick)
    } else {
      if (rafRef.current) cancelAnimationFrame(rafRef.current)
      lastTimeRef.current = null
    }
    return () => { if (rafRef.current) cancelAnimationFrame(rafRef.current) }
  }, [isPlaying, tick])

  const togglePlay = useCallback(() => setIsPlaying(p => !p), [])

  const progressRectRef = useRef<{ left: number; width: number } | null>(null)

  const handlePointerDown = (e: React.PointerEvent<HTMLDivElement>) => {
    isDraggingRef.current = true
    e.currentTarget.setPointerCapture(e.pointerId)
    const rect = e.currentTarget.getBoundingClientRect()
    progressRectRef.current = { left: rect.left, width: rect.width || 1 }
    setProgress(Math.max(0, Math.min(1, (e.clientX - rect.left) / (rect.width || 1))))
  }
  const handlePointerMove = (e: React.PointerEvent<HTMLDivElement>) => {
    if (isDraggingRef.current && progressRectRef.current) {
      const { left, width } = progressRectRef.current
      setProgress(Math.max(0, Math.min(1, (e.clientX - left) / width)))
    }
  }
  const handlePointerUp = (e: React.PointerEvent<HTMLDivElement>) => {
    isDraggingRef.current = false
    try { e.currentTarget.releasePointerCapture(e.pointerId) } catch {}
  }

  return (
    <div style={{
      width: '100vw', height: '100dvh',
      minHeight: '-webkit-fill-available',
      background: '#0a0a0a',
      display: 'flex', flexDirection: 'column',
      overflow: 'hidden',
      position: 'relative',
    }}>
      {/* Page content */}
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden', position: 'relative' }}>
        {activeTab === 'home' && <HomePage onOpenPlayer={openPlayer} isPlaying={isPlaying} onScrollChange={setHomeScrollTop} />}
        {activeTab === 'search' && <SearchPage baseBottom={184} />}
        {activeTab === 'library' && <LibraryPage onOpenPlayer={openPlayer} />}
        {activeTab === 'you' && <YouPage />}
      </div>

      {/* Now Playing Bar — self-positions via position:fixed */}
      <NowPlayingBar
        track={TRACK}
        isPlaying={isPlaying}
        progress={progress}
        big={activeTab === 'home' && homeScrollTop < 60}
        liked={liked}
        onTogglePlay={togglePlay}
        onLike={() => setLiked(l => !l)}
        onPrev={() => setCurrentTrackIndex(i => (i === 0 ? TRACKS.length - 1 : i - 1))}
        onNext={() => setCurrentTrackIndex(i => (i + 1) % TRACKS.length)}
        onOpen={openPlayer}
        artRef={barArtRef}
        barRef={barRef}
      />

      {/* Floating bottom nav */}
      <BottomNav active={activeTab} onChange={t => { setActiveTab(t); setHomeScrollTop(t === 'home' ? 0 : 999) }} />

      {/* Full-screen player */}
      {showPlayer && (
        <NowPlayingScreen
          track={TRACK}
          isPlaying={isPlaying}
          progress={progress}
          liked={liked}
          onTogglePlay={togglePlay}
          onLike={() => setLiked(l => !l)}
          onPrev={() => setCurrentTrackIndex(i => (i === 0 ? TRACKS.length - 1 : i - 1))}
          onNext={() => setCurrentTrackIndex(i => (i + 1) % TRACKS.length)}
          onClose={() => setShowPlayer(false)}
          openOrigin={openOrigin}
          barRef={barRef}
          barArtRef={barArtRef}
          handlePointerDown={handlePointerDown}
          handlePointerMove={handlePointerMove}
          handlePointerUp={handlePointerUp}
        />
      )}
    </div>
  )
}

