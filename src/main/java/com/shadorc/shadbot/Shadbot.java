package com.shadorc.shadbot;

import com.shadorc.shadbot.api.BotListStats;
import com.shadorc.shadbot.command.game.LotteryCmd;
import com.shadorc.shadbot.data.Config;
import com.shadorc.shadbot.data.credential.Credential;
import com.shadorc.shadbot.data.credential.Credentials;
import com.shadorc.shadbot.db.DatabaseManager;
import com.shadorc.shadbot.listener.*;
import com.shadorc.shadbot.utils.*;
import discord4j.core.DiscordClient;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.Event;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.object.data.stored.MessageBean;
import discord4j.core.object.entity.ApplicationInfo;
import discord4j.core.object.entity.User;
import discord4j.core.object.presence.Activity;
import discord4j.core.object.presence.Presence;
import discord4j.core.object.util.Snowflake;
import discord4j.rest.response.ResponseFunction;
import discord4j.store.api.mapping.MappingStoreService;
import discord4j.store.api.noop.NoOpStoreService;
import discord4j.store.jdk.JdkStoreService;
import reactor.blockhound.BlockHound;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.Logger;
import reactor.util.Loggers;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

public final class Shadbot {

    private static final Logger LOGGER = Loggers.getLogger(Shadbot.class);

    private static final Instant LAUNCH_TIME = Instant.now();
    private static final AtomicLong OWNER_ID = new AtomicLong();
    private static final AtomicLong SELF_ID = new AtomicLong();

    private static GatewayDiscordClient client;
    private static BotListStats botListStats;

    public static void main(String[] args) {
        LogUtils.info("Starting Shadbot V%s", Config.VERSION);

        // Set default to Locale US
        Locale.setDefault(Locale.US);

        BlockHound.builder()
                .allowBlockingCallsInside("java.io.FileInputStream", "readBytes")
                .install();

        LogUtils.info("Connecting to Discord...");

        // TODO: This is ugly
        client = DiscordClient.builder(Credentials.get(Credential.DISCORD_TOKEN))
                .onClientResponse(ResponseFunction.emptyIfNotFound())
                .build()
                .gateway()
                .setStoreService(MappingStoreService.create()
                        .setMapping(new NoOpStoreService(), MessageBean.class)
                        .setFallback(new JdkStoreService()))
                .setInitialPresence(shardInfo -> Presence.idle(Activity.playing("Connecting...")))
                .connect()
                .blockOptional()
                .orElseThrow(RuntimeException::new);

        LogUtils.info("Shadbot is connected to all shards.");

        LogUtils.info("Next lottery draw in: %s", LotteryCmd.getDelay().toString());
        Flux.interval(LotteryCmd.getDelay(), Duration.ofDays(7), Schedulers.elastic())
                .flatMap(ignored -> LotteryCmd.draw(client))
                .onErrorContinue((err, obj) -> ExceptionHandler.handleUnknownError(client, err))
                .subscribe(null, err -> ExceptionHandler.handleUnknownError(client, err));

        LogUtils.info("Scheduling presence updates.");
        Flux.interval(Duration.ZERO, Duration.ofMinutes(30), Schedulers.elastic())
                .flatMap(ignored -> {
                    final String presence = String.format("%shelp | %s", Config.DEFAULT_PREFIX,
                            Utils.randValue(TextUtils.TIP_MESSAGES));
                    return client.updatePresence(Presence.online(Activity.playing(presence)));
                })
                .onErrorContinue((err, obj) -> ExceptionHandler.handleUnknownError(client, err))
                .subscribe(null, err -> ExceptionHandler.handleUnknownError(client, err));

        LogUtils.info("Starting bot list stats scheduler.");
        Shadbot.botListStats = new BotListStats();

        final Mono<Long> getOwnerId = Shadbot.client.getApplicationInfo()
                .map(ApplicationInfo::getOwnerId)
                .map(Snowflake::asLong);

        final Mono<Long> getSelfId = Shadbot.client.getEventDispatcher()
                .on(ReadyEvent.class)
                .next()
                .map(ReadyEvent::getSelf)
                .map(User::getId)
                .map(Snowflake::asLong);

        Mono.zip(getOwnerId, getSelfId)
                .doOnNext(tuple -> {
                    final Long ownerId = tuple.getT1();
                    final Long selfId = tuple.getT2();

                    LOGGER.info("Owner ID acquired: {}", ownerId);
                    Shadbot.OWNER_ID.set(ownerId);

                    LOGGER.info("Self ID acquired: {}", selfId);
                    Shadbot.SELF_ID.set(selfId);
                })
                .block();

        LOGGER.info("Listeners registration.");
        Shadbot.register(Shadbot.client, new GatewayLifecycleListener());
        Shadbot.register(Shadbot.client, new TextChannelDeleteListener());
        Shadbot.register(Shadbot.client, new GuildCreateListener());
        Shadbot.register(Shadbot.client, new GuildDeleteListener());
        Shadbot.register(Shadbot.client, new MemberListener.MemberJoinListener());
        Shadbot.register(Shadbot.client, new MemberListener.MemberLeaveListener());
        Shadbot.register(Shadbot.client, new MessageCreateListener());
        Shadbot.register(Shadbot.client, new MessageUpdateListener());
        Shadbot.register(Shadbot.client, new VoiceStateUpdateListener());
        Shadbot.register(Shadbot.client, new ReactionListener.ReactionAddListener());
        Shadbot.register(Shadbot.client, new ReactionListener.ReactionRemoveListener());

        LOGGER.info("Shadbot is fully connected!");
        Shadbot.client.onDisconnect().block();
    }

    private static <T extends Event> void register(GatewayDiscordClient client, EventListener<T> eventListener) {
        client.getEventDispatcher()
                .on(eventListener.getEventType())
                .flatMap(event -> eventListener.execute(event)
                        .thenReturn(event.toString())
                        .elapsed()
                        .doOnNext(tuple -> {
                            if (LOGGER.isTraceEnabled()) {
                                LOGGER.trace("{} took {}ms to be processed.", tuple.getT2(), tuple.getT1());
                            }
                            if (tuple.getT1() > Duration.ofMinutes(1).toMillis()) {
                                LOGGER.warn("{} took a long time to be processed ({}ms).", tuple.getT2(), tuple.getT1());
                            }
                        })
                        .onErrorResume(err -> Mono.fromRunnable(() -> ExceptionHandler.handleUnknownError(client, err))))
                .subscribe(null, err -> ExceptionHandler.handleUnknownError(client, err));
    }

    /**
     * @return The time when this class was loaded.
     */
    public static Instant getLaunchTime() {
        return Shadbot.LAUNCH_TIME;
    }

    /**
     * @return The ID of the owner.
     */
    public static Snowflake getOwnerId() {
        return Snowflake.of(Shadbot.OWNER_ID.get());
    }

    /**
     * @return The ID of the bot.
     */
    public static Snowflake getSelfId() {
        return Snowflake.of(Shadbot.SELF_ID.get());
    }

    public static GatewayDiscordClient getClient() {
        return Shadbot.client;
    }

    public static Mono<Void> quit(ExitCode exitCode) {
        if (Shadbot.botListStats != null) {
            Shadbot.botListStats.stop();
        }

        DatabaseManager.getInstance().close();

        return Shadbot.client.logout()
                .then(Mono.fromRunnable(() -> System.exit(exitCode.getValue())));
    }

}
