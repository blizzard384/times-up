package at.berger.timesup.entity;

import at.berger.timesup.model.message.GameException;
import at.berger.timesup.model.message.GameMessage;
import lombok.*;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GameRoundEntity {
    private String id;
    private GameRoundState state;
    private String teamName;
    private String playerName;
    private String currentEntry;
    private Integer roundTime;
    private Instant startTimestamp;
    private Instant inProgressTimestamp;

    @Getter(AccessLevel.NONE) @Setter(AccessLevel.NONE)
    private List<String> availableEntries = new ArrayList<>();

    @Getter(AccessLevel.NONE) @Setter(AccessLevel.NONE)
    private List<String> correctEntries = new ArrayList<>();

    public void addEntries(List<String> entries) {
        availableEntries.addAll(entries);
        currentEntry = getRandomEntry();
    }

    public boolean addCorrectEntry(String entry) {
        removeEntry(entry);
        correctEntries.add(entry);
        return currentEntry != null;
    }

    public boolean removeEntry(String entry) {
        validateEntry(entry);
        availableEntries.remove(entry);
        currentEntry = getRandomEntry();
        return currentEntry != null;
    }

    private void validateEntry(String entry) {
        if (!availableEntries.contains(entry)) {
            throw new GameException(new GameMessage("Entry " + entry + " is not among available entries"));
        }
        if (correctEntries.contains(entry)) {
            throw new GameException(new GameMessage("Entry " + entry + " is already correct"));
        }
        if (!currentEntry.equals(entry)) {
            throw new GameException(new GameMessage("Entry " + entry + " is not current entry"));
        }
    }

    public String getRandomEntry() {
        if (availableEntries.size() == 0) {
            return null;
        }
        Random rand = new Random();
        return availableEntries.get(rand.nextInt(availableEntries.size()));
    }

    public List<String>  getCorrectEntries() {
        return new ArrayList<>(correctEntries);
    }

    public List<String>  getAvailableEntries() {
        return new ArrayList<>(availableEntries);
    }

    public void rejectEntry(String entry) {
        validateEntry(entry);
        currentEntry = getRandomEntry();
    }

    public Integer getRemainingTime() {
        if  (inProgressTimestamp == null) {
            return roundTime;
        }
        Duration between = Duration.between(inProgressTimestamp, Instant.now());
        return roundTime - Math.toIntExact(between.toMillis());
    }
}
