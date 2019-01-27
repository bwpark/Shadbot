package me.shadorc.shadbot.utils.object.message;

import java.util.function.Consumer;

import discord4j.core.DiscordClient;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.MessageChannel;
import discord4j.core.object.util.Snowflake;
import discord4j.core.spec.EmbedCreateSpec;
import me.shadorc.shadbot.utils.DiscordUtils;
import me.shadorc.shadbot.utils.embed.log.LogUtils;
import me.shadorc.shadbot.utils.exception.ExceptionUtils;
import reactor.core.publisher.Mono;

public class UpdateableMessage {

	private final DiscordClient client;
	private final Snowflake channelId;
	private Snowflake messageId;

	/**
	 * Sends a message that will be deleted each time the {@code send} method is called
	 *
	 * @param client - the Discord client
	 * @param channelId - the Channel ID in which send the message
	 */
	public UpdateableMessage(DiscordClient client, Snowflake channelId) {
		this.client = client;
		this.channelId = channelId;
		this.messageId = null;
	}

	/**
	 * Send a message and delete the previous one
	 *
	 * @param embed - the embed to send
	 */
	public Mono<Message> send(Consumer<? super EmbedCreateSpec> embed) {
		return Mono.justOrEmpty(this.messageId)
				.flatMap(messageId -> this.client.getMessageById(this.channelId, messageId)
						.onErrorResume(ExceptionUtils::isNotFound, err -> Mono.fromRunnable(() -> LogUtils.debug(err.toString()))))
				.flatMap(Message::delete)
				.then(this.client.getChannelById(this.channelId))
				.cast(MessageChannel.class)
				.flatMap(channel -> DiscordUtils.sendMessage(embed, channel))
				.doOnNext(message -> this.messageId = message.getId());
	}

}
