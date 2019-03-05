package me.shadorc.shadbot.command.game.hangman;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.User;
import discord4j.core.object.util.Snowflake;
import discord4j.core.spec.EmbedCreateSpec;
import me.shadorc.shadbot.Shadbot;
import me.shadorc.shadbot.command.game.hangman.HangmanCmd.Difficulty;
import me.shadorc.shadbot.core.command.CommandInitializer;
import me.shadorc.shadbot.core.command.Context;
import me.shadorc.shadbot.core.game.GameManager;
import me.shadorc.shadbot.core.ratelimiter.RateLimiter;
import me.shadorc.shadbot.data.stats.StatsManager;
import me.shadorc.shadbot.data.stats.enums.MoneyEnum;
import me.shadorc.shadbot.listener.interceptor.MessageInterceptorManager;
import me.shadorc.shadbot.utils.DiscordUtils;
import me.shadorc.shadbot.utils.FormatUtils;
import me.shadorc.shadbot.utils.StringUtils;
import me.shadorc.shadbot.utils.embed.EmbedUtils;
import me.shadorc.shadbot.utils.object.Emoji;
import reactor.core.publisher.Mono;

public class HangmanManager extends GameManager {

	private static final List<String> IMG_LIST = List.of(
			getImageUrl("8/8b", 0),
			getImageUrl("3/30", 1),
			getImageUrl("7/70", 2),
			getImageUrl("9/97", 3),
			getImageUrl("2/27", 4),
			getImageUrl("6/6b", 5),
			getImageUrl("d/d6", 6));

	protected static final int MIN_GAINS = 200;
	private static final int MAX_BONUS = 200;

	private final RateLimiter rateLimiter;
	private final AtomicLong messageId;
	private final String word;
	private final List<String> lettersTested;

	private int failCount;

	public HangmanManager(HangmanCmd gameCmd, Context context, Difficulty difficulty) {
		super(gameCmd, context, Duration.ofMinutes(1));
		this.rateLimiter = new RateLimiter(1, Duration.ofSeconds(1));
		this.messageId = new AtomicLong(-1);
		this.word = gameCmd.getWord(difficulty);
		this.lettersTested = new ArrayList<>();
		this.failCount = 0;
	}

	@Override
	public void start() {
		this.schedule(Mono.fromRunnable(this::stop));
		MessageInterceptorManager.addInterceptor(this.getContext().getChannelId(), this);
	}

	@Override
	public Mono<Void> show() {
		final List<String> missedLetters = this.lettersTested.stream()
				.filter(letter -> !this.word.contains(letter))
				.map(String::toUpperCase)
				.collect(Collectors.toList());

		final Consumer<EmbedCreateSpec> embedConsumer = EmbedUtils.getDefaultEmbed()
				.andThen(embed -> {
					embed.setAuthor("Hangman Game", null, this.getContext().getAvatarUrl())
							.setThumbnail("https://lh5.ggpht.com/nIoJylIWCj1gKv9dxtd4CFE2aeXvG7MbvP0BNFTtTFusYlxozJRQmHizsIDxydaa7DHT=w300")
							.setDescription("Type letters or enter a word if you think you've guessed it.")
							.addField("Word", this.getRepresentation(this.word), false);

					if(!missedLetters.isEmpty()) {
						embed.addField("Misses", String.join(", ", missedLetters), false);
					}

					if(this.isScheduled()) {
						embed.setFooter(String.format("Use %scancel to cancel this game (Automatically cancelled in %d min in case of inactivity)",
								this.getContext().getPrefix(), this.getDuration().toMinutes()), null);
					} else {
						embed.setFooter("Finished.", null);
					}

					if(this.failCount > 0) {
						embed.setImage(IMG_LIST.get(Math.min(IMG_LIST.size(), this.failCount) - 1));
					}
				});

		return this.getContext().getClient()
				.getMessageById(this.getContext().getChannelId(), Snowflake.of(this.messageId.get()))
				.flatMap(message -> message.edit(spec -> spec.setEmbed(embedConsumer)))
				.onErrorResume(err -> this.getContext().getChannel()
						.flatMap(channel -> DiscordUtils.sendMessage(embedConsumer, channel)))
				.doOnNext(message -> this.messageId.set(message.getId().asLong()))
				.then();
	}

	private Mono<Void> showResultAndStop(boolean win) {
		String text;
		if(win) {
			final float bonusPerImg = (float) MAX_BONUS / IMG_LIST.size();
			final float imagesRemaining = IMG_LIST.size() - this.failCount;
			final int gains = (int) Math.ceil(MIN_GAINS + bonusPerImg * imagesRemaining);

			Shadbot.getDatabase().getDBMember(this.getContext().getGuildId(), this.getContext().getAuthorId()).addCoins(gains);
			StatsManager.MONEY_STATS.log(MoneyEnum.MONEY_GAINED, CommandInitializer.getCommand(this.getContext().getCommandName()).getName(), gains);

			text = String.format(Emoji.PURSE + " (**%s**) Well played, you found the word ! You won **%s**.",
					this.getContext().getUsername(), FormatUtils.coins(gains));
		} else {
			text = String.format(Emoji.THUMBSDOWN + " (**%s**) You lose, the word to guess was **%s** !",
					this.getContext().getUsername(), this.word);
		}

		return this.show()
				.then(this.getContext().getChannel())
				.flatMap(channel -> DiscordUtils.sendMessage(text, channel))
				.then(Mono.fromRunnable(this::stop));
	}

	private Mono<Void> checkLetter(String chr) {
		// Reset IDLE timer
		this.schedule(Mono.fromRunnable(this::stop));

		if(this.lettersTested.contains(chr)) {
			return Mono.empty();
		}

		if(!this.word.contains(chr)) {
			this.failCount++;
			if(this.failCount == IMG_LIST.size()) {
				return this.showResultAndStop(false);
			}
		}

		this.lettersTested.add(chr);

		// The word has been entirely guessed
		if(StringUtils.remove(this.getRepresentation(this.word), "\\", " ", "*").equalsIgnoreCase(this.word)) {
			return this.showResultAndStop(true);
		}

		return this.show();
	}

	private Mono<Void> checkWord(String word) {
		// Reset IDLE timer
		this.schedule(Mono.fromRunnable(this::stop));

		// If the word has been guessed
		if(this.word.equalsIgnoreCase(word)) {
			this.lettersTested.addAll(StringUtils.split(word, ""));
			return this.showResultAndStop(true);
		}

		this.failCount++;
		if(this.failCount == IMG_LIST.size()) {
			return this.showResultAndStop(false);
		}
		return this.show();
	}

	private String getRepresentation(String word) {
		return String.format("**%s**",
				FormatUtils.format(StringUtils.split(word, ""),
						letter -> this.lettersTested.contains(letter) ? letter.toUpperCase() : "\\_", " "));
	}

	@Override
	public Mono<Boolean> isIntercepted(MessageCreateEvent event) {
		return this.cancelOrDo(event.getMessage(),
				Mono.justOrEmpty(event.getMessage().getAuthor().map(User::getId))
						.flatMap(authorId -> {
							final Context context = this.getContext();

							if(!authorId.equals(context.getAuthorId())) {
								return Mono.just(false);
							}

							final String content = event.getMessage().getContent().get().toLowerCase().trim();

							// Check only if content is an unique word/letter
							if(!content.matches("[a-z]+")) {
								return Mono.just(false);
							}

							Mono<Void> checkMono = Mono.empty();
							if(content.length() == 1 && !this.rateLimiter.isLimitedAndWarn(
									context.getClient(), context.getGuildId(), context.getChannelId(), context.getAuthorId())) {
								checkMono = this.checkLetter(content);
							} else if(content.length() == this.word.length() && !this.rateLimiter.isLimitedAndWarn(
									context.getClient(), context.getGuildId(), context.getChannelId(), context.getAuthorId())) {
								checkMono = this.checkWord(content);
							}

							return checkMono.thenReturn(false);
						}));
	}

	private static String getImageUrl(String path, int num) {
		return String.format("https://upload.wikimedia.org/wikipedia/commons/thumb/%s/Hangman-%d.png/60px-Hangman-%d.png",
				path, num, num);
	}

}