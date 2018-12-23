package me.shadorc.shadbot.listener.music;

import java.util.concurrent.atomic.AtomicInteger;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;

import me.shadorc.shadbot.music.GuildMusic;
import me.shadorc.shadbot.utils.BotUtils;
import me.shadorc.shadbot.utils.FormatUtils;
import me.shadorc.shadbot.utils.TextUtils;
import me.shadorc.shadbot.utils.embed.log.LogUtils;
import me.shadorc.shadbot.utils.object.Emoji;
import reactor.core.publisher.Mono;

public class AudioEventListener extends AudioEventAdapter {

	private final GuildMusic guildMusic;
	private AtomicInteger errorCount;

	public AudioEventListener(GuildMusic guildMusic) {
		super();
		this.guildMusic = guildMusic;
		this.errorCount = new AtomicInteger(0);
	}

	@Override
	public void onTrackStart(AudioPlayer player, AudioTrack track) {
		final String message = String.format(Emoji.MUSICAL_NOTE + " Currently playing: **%s**",
				FormatUtils.trackName(track.getInfo()));
		BotUtils.sendMessage(message, this.guildMusic.getMessageChannel()).subscribe();
	}

	@Override
	public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
		if(endReason.mayStartNext) {
			this.errorCount.set(0); // Everything seems to be fine, reset error counter.
			this.nextOrEnd().subscribe();
		}
	}

	@Override
	public void onTrackException(AudioPlayer player, AudioTrack track, FriendlyException err) {
		this.errorCount.incrementAndGet();

		final String errMessage = TextUtils.cleanLavaplayerErr(err);
		final long guildId = this.guildMusic.getGuildId().asLong();

		LogUtils.info("{Guild ID: %d} %sTrack exception: %s", guildId, this.errorCount.get() > 3 ? "(Ignored) " : "", errMessage);

		final StringBuilder strBuilder = new StringBuilder();
		if(this.errorCount.get() <= 3) {
			strBuilder.append(String.format(Emoji.RED_CROSS + " Sorry, %s. I'll try to play the next available song.",
					errMessage.toLowerCase()));
		}

		if(this.errorCount.get() == 3) {
			LogUtils.info("{Guild ID: %d} Too many errors in a row. They will be ignored until a music can be played.", guildId);
			strBuilder.append("\n" + Emoji.RED_FLAG + " Too many errors in a row, I will ignore them until I find a music that can be played.");
		}

		BotUtils.sendMessage(strBuilder.toString(), this.guildMusic.getMessageChannel())
				.then(this.nextOrEnd())
				.subscribe();
	}

	@Override
	public void onTrackStuck(AudioPlayer player, AudioTrack track, long thresholdMs) {
		LogUtils.info("{Guild ID: %d} Music stuck, skipping it.", this.guildMusic.getGuildId().asLong());

		// TODO: Does the music end if an error (for example permission exception) is thrown by the first sendMessage ?
		BotUtils.sendMessage(Emoji.RED_EXCLAMATION + " Music seems stuck, I'll try to play the next available song.",
				this.guildMusic.getMessageChannel())
				.then(this.nextOrEnd())
				.subscribe();
	}

	private Mono<Void> nextOrEnd() {
		// If the next track could be started
		if(this.guildMusic.getTrackScheduler().nextTrack()) {
			return Mono.empty();
		}
		return this.guildMusic.end();
	}
}
