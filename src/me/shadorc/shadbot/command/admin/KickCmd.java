package me.shadorc.shadbot.command.admin;

import java.util.List;

import me.shadorc.shadbot.core.command.AbstractCommand;
import me.shadorc.shadbot.core.command.CommandCategory;
import me.shadorc.shadbot.core.command.CommandPermission;
import me.shadorc.shadbot.core.command.Context;
import me.shadorc.shadbot.core.command.annotation.Command;
import me.shadorc.shadbot.exception.IllegalCmdArgumentException;
import me.shadorc.shadbot.exception.MissingArgumentException;
import me.shadorc.shadbot.utils.BotUtils;
import me.shadorc.shadbot.utils.FormatUtils;
import me.shadorc.shadbot.utils.StringUtils;
import me.shadorc.shadbot.utils.TextUtils;
import me.shadorc.shadbot.utils.embed.HelpBuilder;
import me.shadorc.shadbot.utils.object.Emoji;
import sx.blah.discord.api.internal.json.objects.EmbedObject;
import sx.blah.discord.handle.obj.IUser;
import sx.blah.discord.handle.obj.Permissions;
import sx.blah.discord.util.Ban;
import sx.blah.discord.util.PermissionUtils;

@Command(category = CommandCategory.ADMIN, permission = CommandPermission.ADMIN, names = { "kick" })
public class KickCmd extends AbstractCommand {

	@Override
	public void execute(Context context) throws MissingArgumentException, IllegalCmdArgumentException {
		if(!context.hasArg()) {
			throw new MissingArgumentException();
		}

		List<IUser> mentionedUsers = context.getMessage().getMentions();
		if(mentionedUsers.isEmpty()) {
			throw new MissingArgumentException();
		}

		if(!PermissionUtils.hasPermissions(context.getChannel(), context.getAuthor(), Permissions.KICK)) {
			throw new IllegalArgumentException("You don't have permission to kick.");
		}

		if(!BotUtils.hasPermissions(context.getChannel(), Permissions.KICK)) {
			BotUtils.sendMessage(TextUtils.missingPerm(Permissions.KICK), context.getChannel());
			return;
		}

		if(mentionedUsers.contains(context.getAuthor())) {
			throw new IllegalCmdArgumentException("You cannot kick yourself.");
		}

		for(IUser user : mentionedUsers) {
			if(PermissionUtils.isUserHigher(context.getGuild(), user, context.getAuthor())) {
				throw new IllegalCmdArgumentException(String.format("You can't kick %s because he is higher in the role hierarchy than you.",
						user.getName()));
			}
			if(PermissionUtils.isUserHigher(context.getGuild(), user, context.getOurUser())) {
				throw new IllegalCmdArgumentException(String.format("I cannot kick %s because he is higher in the role hierarchy than me.",
						user.getName()));
			}
		}

		String reason = StringUtils.remove(context.getArg(), FormatUtils.format(mentionedUsers, user -> user.mention(false), " ")).trim();
		if(reason.length() > Ban.MAX_REASON_LENGTH) {
			throw new IllegalCmdArgumentException(String.format("Reason cannot exceed %d characters.", Ban.MAX_REASON_LENGTH));
		}

		if(reason.isEmpty()) {
			reason = "Reason not specified.";
		}

		for(IUser user : mentionedUsers) {
			if(!user.isBot()) {
				BotUtils.sendMessage(String.format(Emoji.INFO + " You were kicked by **%s** on server **%s** (Reason: **%s**).",
						context.getAuthorName(), context.getGuild().getName(), reason), user.getOrCreatePMChannel());
			}
			context.getGuild().kickUser(user, reason);
		}

		BotUtils.sendMessage(String.format(Emoji.INFO + " (Requested by **%s**) %s got kicked (Reason: %s)",
				context.getAuthorName(),
				FormatUtils.format(mentionedUsers, IUser::getName, ", "),
				reason), context.getChannel());
	}

	@Override
	public EmbedObject getHelp(String prefix) {
		return new HelpBuilder(this, prefix)
				.setDescription("Kick user(s).")
				.addArg("@user(s)", false)
				.addArg("reason", true)
				.build();
	}

}
