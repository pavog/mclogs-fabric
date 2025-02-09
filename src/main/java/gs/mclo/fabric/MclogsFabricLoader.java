package gs.mclo.fabric;

import com.mojang.brigadier.context.CommandContext;
import gs.mclo.java.APIResponse;
import gs.mclo.java.Log;
import gs.mclo.java.MclogsAPI;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Style;
import net.minecraft.util.Formatting;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

public class MclogsFabricLoader implements DedicatedServerModInitializer {
    public static final Logger logger = LogManager.getLogger();

    /**
     * @param context command context
     * @return log files
     * @throws IOException io exception
     */
    public static String[] getLogs(CommandContext<ServerCommandSource> context) throws IOException {
        return MclogsAPI.listLogs(context.getSource().getMinecraftServer().getRunDirectory().getCanonicalPath());
    }

    /**
     * @param context command context
     * @return crash reports
     * @throws IOException io exception
     */
    public static String[] getCrashReports(CommandContext<ServerCommandSource> context) throws IOException {
        return MclogsAPI.listCrashReports(context.getSource().getMinecraftServer().getRunDirectory().getCanonicalPath());
    }

    public static int share(ServerCommandSource source, String filename) {
        MclogsAPI.mcversion = source.getMinecraftServer().getVersion();
        logger.log(Level.INFO,"Sharing "+filename);
        source.sendFeedback(new LiteralText("Sharing " + filename), false);

        Path directory = source.getMinecraftServer().getRunDirectory().toPath();
        Path logs = directory.resolve("logs");
        Path crashReports = directory.resolve("crash-reports");
        Path log = directory.resolve("logs").resolve(filename);

        if (!log.toFile().exists()) {
            log = directory.resolve("crash-reports").resolve(filename);
        }

        boolean isInAllowedDirectory = false;
        try {
            Path logPath = log.toRealPath();
            isInAllowedDirectory = (logs.toFile().exists() && logPath.startsWith(logs.toRealPath()))
                    || (crashReports.toFile().exists() && logPath.startsWith(crashReports.toRealPath()));
        }
        catch (IOException ignored) {}

        if (!log.toFile().exists() || !isInAllowedDirectory
                || !log.getFileName().toString().matches(Log.ALLOWED_FILE_NAME_PATTERN.pattern())) {
            source.sendError(new LiteralText("There is no log or crash report with the name '" + filename
                    + "'. Use '/mclogs list' to list all logs."));
            return -1;
        }

        try {
            APIResponse response = MclogsAPI.share(log);
            if (response.success) {
                LiteralText feedback = new LiteralText("Your log has been uploaded: ");
                feedback.setStyle(Style.EMPTY.withColor(Formatting.GREEN));

                LiteralText link = new LiteralText(response.url);
                Style linkStyle = Style.EMPTY.withColor(Formatting.BLUE);
                linkStyle = linkStyle.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL,response.url));
                link.setStyle(linkStyle);

                source.sendFeedback(feedback.append(link),true);
                return 1;
            }
            else {
                logger.error("An error occurred when uploading your log: ");
                logger.error(response.error);
                LiteralText error = new LiteralText("An error occurred. Check your log for more details");
                source.sendError(error);
                return 0;
            }
        }
        catch (FileNotFoundException|IllegalArgumentException e) {
            LiteralText error = new LiteralText("The log file "+filename+" doesn't exist. Use '/mclogs list' to list all logs.");
            source.sendError(error);
            return -1;
        }
        catch (IOException e) {
            source.sendError(new LiteralText("An error occurred. Check your log for more details"));
            logger.error("Could not get log file!");
            logger.error(e);
            return 0;
        }
    }

    @Override
    public void onInitializeServer() {
        MclogsAPI.userAgent = "Mclogs-fabric";
        Optional<ModContainer> mclogs = FabricLoader.getInstance().getModContainer("mclogs");
        MclogsAPI.version = mclogs.isPresent() ? mclogs.get().getMetadata().getVersion().getFriendlyString() : "unknown";

        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> {
            CommandMclogs.register(dispatcher);
            CommandMclogsList.register(dispatcher);
            CommandMclogsShare.register(dispatcher);
        });
    }
}
