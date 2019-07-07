package com.shadorc.shadbot.core.command;

import com.shadorc.shadbot.Config;
import com.shadorc.shadbot.Shadbot;
import com.shadorc.shadbot.exception.MissingArgumentException;
import com.shadorc.shadbot.exception.NoMusicException;
import com.shadorc.shadbot.music.GuildMusic;
import com.shadorc.shadbot.music.MusicManager;
import com.shadorc.shadbot.utils.DiscordUtils;
import com.shadorc.shadbot.utils.NumberUtils;
import com.shadorc.shadbot.utils.StringUtils;
import discord4j.core.DiscordClient;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.*;
import discord4j.core.object.entity.Channel.Type;
import discord4j.core.object.util.Permission;
import discord4j.core.object.util.Snowflake;
import reactor.core.publisher.Mono;
import reactor.util.annotation.Nullable;

import java.util.List;
import java.util.Optional;

public class Context {

    @Nullable
    private final String arg;
    private final String cmdName;
    private final MessageCreateEvent event;
    private final String prefix;

    public Context(MessageCreateEvent event, String prefix) {
        this.event = event;
        this.prefix = prefix;

        final List<String> splittedMsg = StringUtils.split(this.getContent(), 2);
        this.cmdName = splittedMsg.get(0).substring(prefix.length()).toLowerCase();
        this.arg = splittedMsg.size() > 1 ? splittedMsg.get(1).trim() : null;
    }

    public Optional<String> getArg() {
        return Optional.ofNullable(this.arg);
    }

    public User getAuthor() {
        return this.getMessage().getAuthor().orElseThrow();
    }

    public Snowflake getAuthorId() {
        return this.getAuthor().getId();
    }

    public String getAvatarUrl() {
        return this.getAuthor().getAvatarUrl();
    }

    public Mono<MessageChannel> getChannel() {
        return this.getMessage().getChannel();
    }

    public Snowflake getChannelId() {
        return this.getMessage().getChannelId();
    }

    public DiscordClient getClient() {
        return this.event.getClient();
    }

    public String getCommandName() {
        return this.cmdName;
    }

    public String getContent() {
        return this.getMessage().getContent().orElseThrow();
    }

    public Mono<Guild> getGuild() {
        return this.event.getGuild();
    }

    public Snowflake getGuildId() {
        return this.event.getGuildId().orElseThrow();
    }

    public Member getMember() {
        return this.event.getMember().orElseThrow();
    }

    public Message getMessage() {
        return this.event.getMessage();
    }

    public Mono<CommandPermission> getPermission() {
        // The author is the bot's owner
        final Mono<CommandPermission> ownerPerm = Mono.just(this.getAuthorId())
                .filter(Shadbot.getOwnerId()::equals)
                .map(ignored -> CommandPermission.OWNER);

        // Private message, the author is considered as an administrator
        final Mono<CommandPermission> dmPerm = this.getChannel()
                .map(Channel::getType)
                .filter(Type.DM::equals)
                .map(ignored -> CommandPermission.ADMIN);

        // The member is an administrator
        final Mono<CommandPermission> adminPerm = this.getChannel()
                .filterWhen(channel -> DiscordUtils.hasPermission(channel, this.getAuthorId(), Permission.ADMINISTRATOR))
                .map(ignored -> CommandPermission.ADMIN);

        return ownerPerm
                .switchIfEmpty(dmPerm)
                .switchIfEmpty(adminPerm)
                .defaultIfEmpty(CommandPermission.USER);
    }

    public String getPrefix() {
        return this.prefix;
    }

    public Mono<User> getSelf() {
        return this.getClient().getSelf();
    }

    public Mono<Member> getSelfAsMember() {
        return this.getSelf().flatMap(self -> self.asMember(this.getGuildId()));
    }

    public Snowflake getSelfId() {
        return this.getClient().getSelfId().orElseThrow();
    }

    public int getShardCount() {
        return this.getClient().getConfig().getShardCount();
    }

    public int getShardIndex() {
        return this.getClient().getConfig().getShardIndex();
    }

    public String getUsername() {
        return this.getAuthor().getUsername();
    }

    public Mono<Boolean> isChannelNsfw() {
        return this.getChannel()
                .ofType(TextChannel.class)
                .map(TextChannel::isNsfw);
    }

    public String requireArg() {
        return this.getArg()
                .map(StringUtils::normalizeSpace)
                .orElseThrow(MissingArgumentException::new);
    }

    public List<String> requireArgs(int count) {
        return this.requireArgs(count, count);
    }

    public List<String> requireArgs(int min, int max) {
        return this.requireArgs(min, max, Config.DEFAULT_COMMAND_DELIMITER);
    }

    public List<String> requireArgs(int min, int max, String delimiter) {
        final List<String> args = StringUtils.split(this.requireArg(), max, delimiter);
        if (!NumberUtils.isBetween(args.size(), min, max)) {
            throw new MissingArgumentException();
        }
        return args;
    }

    public GuildMusic requireGuildMusic() {
        final GuildMusic guildMusic = MusicManager.getInstance().getMusic(this.getGuildId());
        if (guildMusic == null || guildMusic.getTrackScheduler().isStopped()) {
            throw new NoMusicException();
        }
        return guildMusic;
    }

}