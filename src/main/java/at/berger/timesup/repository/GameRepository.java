package at.berger.timesup.repository;

import at.berger.timesup.entity.GameEntity;
import at.berger.timesup.entity.GameState;
import at.berger.timesup.model.message.GameException;
import at.berger.timesup.model.message.GameMessage;
import at.berger.timesup.model.message.Severity;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class GameRepository {

    private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(10);

    private final Map<String, GameEntity> repository = new ConcurrentHashMap<>();

    public GameEntity get(String id) {
        Objects.requireNonNull(id);
        GameEntity game = repository.get(id);
        if (game == null) {
            throw new GameException(Severity.FATAL, new GameMessage("Cannot find game with ID " + id));
        }
        return game;
    }

    public void create(GameEntity entity)  {
        Objects.requireNonNull(entity);
        Objects.requireNonNull(entity.getId());
        if (repository.containsKey(entity.getId())) {
            throw new GameException(new GameMessage("Game with ID " + entity.getId() + " already exists"));
        }
        repository.put(entity.getId(), entity);
    }

    public <T> T update(String id, Function<GameEntity, T> updateOperation) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(updateOperation);
        GameEntity gameState = get(id);
        T result = updateOperation.apply(gameState);
        repository.put(id, gameState);
        return result;
    }

    public void update(String id, Consumer<GameEntity> updateOperation) {
        update(id, (game) -> {
            updateOperation.accept(game);
            return null;
        });
    }

    public void updateIn(String id, int seconds, Consumer<GameEntity> updateOperation) {
        executorService.schedule(() -> update(id, updateOperation), seconds, TimeUnit.MILLISECONDS);
    }

    public List<GameEntity> getSetupAndPlay() {
        return repository.values().stream()
                .filter(g -> Arrays.asList(GameState.SETUP, GameState.PLAY).contains(g.getState()))
                .collect(Collectors.toList());
    }

    public void remove(String gameId) {
        repository.remove(gameId);
    }

    public void removeIn(String id, int seconds) {
        executorService.schedule(() -> remove(id), seconds, TimeUnit.MILLISECONDS);
    }
}