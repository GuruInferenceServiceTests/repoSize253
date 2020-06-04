package tw.com.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.cloudwatchlogs.model.LogStream;
import tw.com.AwsFacade;
import tw.com.entity.OutputLogEventDecorator;
import tw.com.entity.ProjectAndEnv;
import tw.com.providers.LogClient;
import tw.com.providers.ProvidesNow;
import tw.com.providers.SavesFile;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.String.format;

public class LogRepository {
    private static final Logger logger = LoggerFactory.getLogger(LogRepository.class);

    private final LogClient logClient;
    private final ProvidesNow providesNow;
    private final SavesFile savesFile;

    List<String> required = Arrays.asList(AwsFacade.ENVIRONMENT_TAG, AwsFacade.PROJECT_TAG);
    private String fileSeperator;

    public LogRepository(LogClient logClient, ProvidesNow providesNow, SavesFile savesFile) {
        this.logClient = logClient;
        this.providesNow = providesNow;
        this.savesFile = savesFile;
        fileSeperator = File.separator;
    }

    public List<String> logGroupsFor(ProjectAndEnv projectAndEnv) {
        Map<String, Map<String, String>> groupsWithTags = logClient.getGroupsWithTags();
        List<String> matched = new LinkedList<>();

        groupsWithTags.forEach((name, tags) -> {
            if (tags.keySet().containsAll(required)) {
                if (tags.get(AwsFacade.ENVIRONMENT_TAG).equals(projectAndEnv.getEnv()) &&
                        tags.get(AwsFacade.PROJECT_TAG).equals(projectAndEnv.getProject())) {
                    matched.add(name);
                }
            }
        });
        logger.info(format("Matched %s out of %s groups to %s", matched.size(), groupsWithTags.size(), projectAndEnv));
        return matched;
    }

    public void removeOldStreamsFor(String groupName, Duration duration) {
        ZonedDateTime timestamp = providesNow.getUTCNow();
        long when = timestampFromDuration(duration, timestamp);

        logger.info(format("Remove streams from group %s if older than %s (%s)", groupName, duration, when));

        List<LogStream> streams = logClient.getStreamsFor(groupName, when);

        streams.stream().
                filter(logStream -> (logStream.lastEventTimestamp()<when))
                .forEach(oldStream -> {
                    LocalDateTime last = LocalDateTime.ofInstant(Instant.ofEpochMilli(oldStream.lastEventTimestamp()), ZoneId.systemDefault());
                    String streamName = oldStream.logStreamName();
                    logger.info(format("Deleting stream %s from group %s, last event was %s", streamName, groupName, last));
                    logClient.deleteLogStream(groupName, streamName);
        });
    }

    public void tagCloudWatchLog(ProjectAndEnv projectAndEnv, String groupToTag) {
        List<String> currnet = logGroupsFor(projectAndEnv);
        if (currnet.contains(groupToTag)) {
            logger.warn(format("Group %s is already tagged for %s", groupToTag, projectAndEnv));
            return;
        }
        logClient.tagGroupFor(projectAndEnv, groupToTag);
    }

    public List<Path> fetchLogs(ProjectAndEnv projectAndEnv, Duration duration) {
        List<Path> filenames = new LinkedList<>();
        ZonedDateTime timestamp = providesNow.getUTCNow();
        long when = timestampFromDuration(duration, timestamp);

        logger.info(format("Fetching all log entries for %s within %s days", projectAndEnv, duration.toDays()));

        // get names of all applicable log groups
        List<String> groupNames = this.logGroupsFor(projectAndEnv);
        if (groupNames.isEmpty()) {
            logger.info("No matching group streams found");
            return filenames;
        }

        logger.info("Matched groups " + groupNames);

        groupNames.forEach(groupName -> {
            List<LogStream> streamsForGroup = logClient.getStreamsFor(groupName, when);

            // todo is comparing needed, should be in time order already?
            List<String> streamNames = streamsForGroup.stream().
                    filter(stream -> stream.lastEventTimestamp()>=when).         // if within time window
                    sorted(Comparator.comparing(LogStream::lastEventTimestamp))  // and in time order
                    .map(LogStream::logStreamName).
                    collect(Collectors.toList());

            logger.info(format("Got %s streams with events in scope for group %s", streamNames.size(), groupName));

            List<Stream<OutputLogEventDecorator>> fetchLogs = logClient.fetchLogs(groupName, streamNames, when);

            Path path = formFilenameFor(groupName,timestamp);
            filenames.add(path);
            if (!savesFile.save(path, fetchLogs)) {
                logger.error(format("Unable to save file '%s' for groupname '%s'", path.toAbsolutePath().toString(), groupName));
            }

        });

        return filenames;
    }

    public Path formFilenameFor(String groupName, ZonedDateTime timestamp) {
        // group names can contain paths....
        if (groupName.contains(fileSeperator)) {
            groupName = groupName.replace(File.separatorChar, '_');
        }
        return Paths.get(format("%s_%s.log", groupName, timestamp.format(DateTimeFormatter.ISO_DATE_TIME)));
    }

    private long timestampFromDuration(Duration duration, ZonedDateTime timestamp) {
        ZonedDateTime when = timestamp.minus(duration);
        return Instant.from(when).toEpochMilli();
    }

}
