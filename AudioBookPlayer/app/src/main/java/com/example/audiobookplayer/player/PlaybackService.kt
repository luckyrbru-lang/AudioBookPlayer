package com.example.audiobookplayer.player

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * MediaSessionService (Media3) заменяет ручной Foreground Service из исходного плана.
 * Он сам:
 *  - создаёт и обновляет системное уведомление с play/pause/skip,
 *  - публикует состояние на экран блокировки и Bluetooth-гарнитуры/руль,
 *  - держит foreground-статус, пока идёт воспроизведение,
 *  - переживает поворот экрана и сворачивание приложения.
 *
 * UI (PlayerViewModel) подключается к нему не напрямую, а через MediaController —
 * это официальный способ Media3, который не завязан на жизненный цикл Activity.
 */
class PlaybackService : MediaSessionService() {

    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this)
            .setHandleAudioBecomingNoisy(true) // ставит на паузу при отключении наушников
            .build()

        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = mediaSession

    override fun onDestroy() {
        player.release()
        mediaSession.release()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        // Если ничего не играет и юзер смахнул приложение из "Недавних" — можно закрыть сервис.
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }
}
